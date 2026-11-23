// CarPlay template scene for Mochy — the one iOS surface that does not use the shared Compose UI.
// Apple only allows audio apps to display system CPTemplate views in CarPlay (never custom views),
// so this scene builds templates natively while playback, the library and Up next stay in the shared
// Kotlin runtime. Picking a row plays it and puts the rest of that list into Up next, so the car's
// Next keeps walking through the album or playlist.

import CarPlay
import Foundation
import Shared
import UIKit

final class MochyCarPlayScene: UIResponder, CPTemplateApplicationSceneDelegate {
    private let notificationCenter = NotificationCenter.default
    private let artworkCache = NSCache<NSString, UIImage>()
    private var interfaceController: CPInterfaceController?
    private var observers: [NSObjectProtocol] = []
    private var savedTemplate: CPListTemplate?
    private var recentTemplate: CPListTemplate?
    private var upNextTemplate: CPListTemplate?
    private var searchTemplate: CPSearchTemplate?
    private let searchDelegate = CarPlaySearchDelegate()
    private var searchRows: [[String: Any]] = []
    private var searchCompletion: (([CPListItem]) -> Void)?
    private var searchQuery = ""
    private var libraryRows: [String: [[String: Any]]] = [:]
    private var upcomingRows: [[String: Any]] = []
    private var currentSongID: String?
    private var isPlaying = false
    private var pendingCompletions: [String: () -> Void] = [:]

    func templateApplicationScene(
        _ scene: CPTemplateApplicationScene,
        didConnect interfaceController: CPInterfaceController
    ) {
        self.interfaceController = interfaceController
        installObservers()
        installRootTemplate()

        // This can be the first app scene. It creates the same shared player and database used by Compose.
        IosCarPlayBridgeKt.installIosCarPlayBridge()
        requestLibrary("saved")
        requestLibrary("recent")
        requestUpcoming()
        // the phone may have changed songs while the car was away; ask for what's playing now
        post("playbackRequest", payload: [:])
    }

    func templateApplicationScene(
        _ scene: CPTemplateApplicationScene,
        didDisconnect interfaceController: CPInterfaceController
    ) {
        observers.forEach { notificationCenter.removeObserver($0) }
        observers.removeAll()
        pendingCompletions.values.forEach { $0() }
        pendingCompletions.removeAll()
        self.interfaceController = nil
        savedTemplate = nil
        recentTemplate = nil
        upNextTemplate = nil
        searchTemplate = nil
        searchRows.removeAll()
        searchCompletion = nil
        searchQuery = ""
        libraryRows.removeAll()
        upcomingRows.removeAll()
    }

    private func installObservers() {
        observe("librarySnapshot") { [weak self] payload in self?.receiveLibrary(payload) }
        observe("upcomingSnapshot") { [weak self] payload in self?.receiveUpcoming(payload) }
        observe("playResult") { [weak self] payload in self?.receivePlayResult(payload) }
        observe("playbackChanged") { [weak self] payload in self?.receivePlaybackState(payload) }
        observe("searchSnapshot") { [weak self] payload in self?.receiveSearchResults(payload) }
        searchDelegate.onQuery = { [weak self] query, completion in
            guard let self else {
                completion([])
                return
            }
            self.searchRows = []
            self.searchQuery = query
            // a previous query that never answered must not leave CarPlay waiting
            self.searchCompletion?([])
            if query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                self.searchCompletion = nil
                completion([])
                return
            }
            // the shared runtime searches; the answer arrives as the searchSnapshot notification
            self.searchCompletion = completion
            self.post("searchRequest", payload: ["query": query])
        }
        searchDelegate.onSelect = { [weak self] item, completion in
            guard let self,
                  let info = item.userInfo as? [String: Any],
                  let id = info["id"] as? String
            else {
                completion()
                return
            }
            self.play(id: id, from: "search", completion: completion)
        }
    }

    private func observe(_ suffix: String, handler: @escaping (String?) -> Void) {
        let name = Notification.Name("com.mochyy.mochymusic.ios.carplay.\(suffix)")
        observers.append(notificationCenter.addObserver(forName: name, object: nil, queue: .main) { notification in
            handler(notification.object as? String)
        })
    }

    private func installRootTemplate() {
        let saved = CPListTemplate(
            title: "Library",
            sections: [CPListSection(items: [placeholder("Loading library…")])]
        )
        saved.tabTitle = "Library"
        saved.tabImage = UIImage(systemName: "music.note.list")

        let recent = CPListTemplate(
            title: "Recently Played",
            sections: [CPListSection(items: [placeholder("Loading recent tracks…")])]
        )
        recent.tabTitle = "Recent"
        recent.tabImage = UIImage(systemName: "clock")

        let upNext = CPListTemplate(
            title: "Up Next",
            sections: [CPListSection(items: [placeholder("Nothing queued")])]
        )
        upNext.tabTitle = "Up Next"
        upNext.tabImage = UIImage(systemName: "text.line.first.and.arrowtriangle.forward")

        savedTemplate = saved
        recentTemplate = recent
        upNextTemplate = upNext

        // the search tab is the one place the car can ask for anything the phone can find
        let search = CPSearchTemplate()
        search.tabTitle = "Search"
        search.tabImage = UIImage(systemName: "magnifyingglass")
        search.delegate = searchDelegate
        searchTemplate = search

        interfaceController?.setRootTemplate(
            CPTabBarTemplate(templates: [saved, recent, upNext, search]),
            animated: false,
            completion: nil
        )
    }

    private func placeholder(_ title: String) -> CPListItem {
        let item = CPListItem(text: title, detailText: nil)
        item.isEnabled = false
        return item
    }

    private func requestLibrary(_ collection: String) {
        post("libraryRequest", payload: [
            "collection": collection,
            "limit": CPListTemplate.maximumItemCount,
        ])
    }

    private func requestUpcoming() {
        post("upcomingRequest", payload: [:])
    }

    private func receiveLibrary(_ encoded: String?) {
        guard let payload = decode(encoded),
              let collection = payload["collection"] as? String,
              let rows = payload["items"] as? [[String: Any]]
        else { return }

        libraryRows[collection] = rows
        updateList(collection)
    }

    private func receiveUpcoming(_ encoded: String?) {
        guard let payload = decode(encoded),
              let rows = payload["items"] as? [[String: Any]]
        else { return }

        upcomingRows = rows
        updateUpNext()
    }

    private func updateList(_ collection: String) {
        guard let rows = libraryRows[collection] else { return }
        var items = rows.prefix(CPListTemplate.maximumItemCount).compactMap { row -> CPListItem? in
            listItem(row, collection: collection)
        }
        if !items.isEmpty {
            // one tap starts the list from its top and queues the rest, as the phone's Play all does
            let playAll = CPListItem(text: "Play all", detailText: nil)
            playAll.handler = { [weak self] _, completion in
                guard let self, let firstID = rows.first?["id"] as? String else {
                    completion()
                    return
                }
                self.play(id: firstID, from: collection, completion: completion)
            }
            items.insert(playAll, at: 0)
        }

        let visibleItems = items.isEmpty ? [placeholder(emptyText(for: collection))] : items
        let section = CPListSection(
            items: visibleItems,
            header: rows.isEmpty ? nil : "\(rows.count) songs",
            sectionIndexTitle: nil
        )
        if collection == "saved" {
            savedTemplate?.updateSections([section])
        } else if collection == "recent" {
            recentTemplate?.updateSections([section])
        }
    }

    private func updateUpNext() {
        let items = upcomingRows.prefix(CPListTemplate.maximumItemCount).compactMap { row -> CPListItem? in
            listItem(row, collection: "upcoming")
        }
        let visibleItems = items.isEmpty ? [placeholder("Nothing queued")] : items
        upNextTemplate?.updateSections([CPListSection(
            items: visibleItems,
            header: upcomingRows.isEmpty ? nil : "\(upcomingRows.count) next",
            sectionIndexTitle: nil
        )])
    }

    private func listItem(_ row: [String: Any], collection: String) -> CPListItem? {
        guard let id = row["id"] as? String,
              let title = row["title"] as? String
        else { return nil }

        let item = CPListItem(text: title, detailText: row["artist"] as? String)
        item.isExplicitContent = row["isExplicit"] as? Bool ?? false
        item.isPlaying = id == currentSongID && isPlaying
        item.userInfo = ["id": id, "collection": collection]
        if let artworkURL = row["artworkURL"] as? String {
            applyArtwork(item, url: artworkURL)
        }
        item.handler = { [weak self] _, completion in
            guard let self else {
                completion()
                return
            }
            self.play(id: id, from: collection, completion: completion)
        }
        return item
    }

    private func emptyText(for collection: String) -> String {
        collection == "saved" ? "No saved tracks yet" : "No recently played tracks"
    }

    private func receiveSearchResults(_ encoded: String?) {
        guard let payload = decode(encoded),
              let rows = payload["items"] as? [[String: Any]]
        else { return }
        // an answer to a query the driver has already typed past is dropped
        if let answered = payload["query"] as? String, !answered.isEmpty, answered != searchQuery { return }
        searchRows = rows
        let items = rows.prefix(CPListTemplate.maximumItemCount).compactMap { row -> CPListItem? in
            listItem(row, collection: "search")
        }
        // the delegate is still waiting on this query; a newer one replaced the handler already
        searchCompletion?(items)
        searchCompletion = nil
    }

    /// The ids of the list the tapped row came from, in the order the car shows them.
    private func rowIDs(for collection: String) -> [String] {
        let rows: [[String: Any]]
        switch collection {
        case "upcoming": rows = upcomingRows
        case "search": rows = searchRows
        default: rows = libraryRows[collection] ?? []
        }
        return rows.prefix(CPListTemplate.maximumItemCount).compactMap { $0["id"] as? String }
    }

    private func play(id: String, from collection: String, completion: @escaping () -> Void) {
        let requestID = UUID().uuidString
        pendingCompletions[requestID] = completion
        post("playRequest", payload: [
            "requestId": requestID,
            "collection": collection,
            "id": id,
            "ids": rowIDs(for: collection),
        ])

        DispatchQueue.main.asyncAfter(deadline: .now() + 50) { [weak self] in
            guard let self, let completion = self.pendingCompletions.removeValue(forKey: requestID) else { return }
            completion()
        }
    }

    private func receivePlayResult(_ encoded: String?) {
        guard let payload = decode(encoded),
              let requestID = payload["requestId"] as? String,
              let completion = pendingCompletions.removeValue(forKey: requestID)
        else { return }

        let ready = payload["ready"] as? Bool ?? false
        completion()
        // CarPlay refuses a template that is already in the stack, so only push Now Playing once.
        if ready, let controller = interfaceController,
           !controller.templates.contains(where: { $0 === CPNowPlayingTemplate.shared }) {
            controller.pushTemplate(CPNowPlayingTemplate.shared, animated: true, completion: nil)
        }
    }

    private func receivePlaybackState(_ encoded: String?) {
        guard let payload = decode(encoded) else { return }
        currentSongID = payload["songId"] as? String
        isPlaying = payload["isPlaying"] as? Bool ?? false
        updateList("saved")
        updateList("recent")
        updateUpNext()
    }

    /// The row image, loaded once per URL: CarPlay shows covers in lists, and the list is rebuilt on
    /// every library change.
    private func applyArtwork(_ item: CPListItem, url: String) {
        guard let parsed = URL(string: url), parsed.scheme == "https" else { return }
        let key = url as NSString
        if let cached = artworkCache.object(forKey: key) {
            item.setImage(cached)
            return
        }
        URLSession.shared.dataTask(with: parsed) { [weak self, weak item] data, _, _ in
            guard let self, let item, let data, let image = UIImage(data: data) else { return }
            self.artworkCache.setObject(image, forKey: key)
            DispatchQueue.main.async { item.setImage(image) }
        }.resume()
    }

    private func decode(_ encoded: String?) -> [String: Any]? {
        guard let encoded, let data = encoded.data(using: .utf8) else { return nil }
        return (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
    }

    private func post(_ suffix: String, payload: [String: Any]) {
        guard let data = try? JSONSerialization.data(withJSONObject: payload),
              let encoded = String(data: data, encoding: .utf8)
        else { return }
        notificationCenter.post(
            name: Notification.Name("com.mochyy.mochymusic.ios.carplay.\(suffix)"),
            object: encoded
        )
    }
}


/// The car's search box. CarPlay asks for results on every pause in typing and keeps the completion
/// until it is called, so the shared runtime's answer can arrive later.
final class CarPlaySearchDelegate: NSObject, CPSearchTemplateDelegate {
    var onQuery: (String, @escaping ([CPListItem]) -> Void) -> Void = { _, completion in completion([]) }
    var onSelect: (CPListItem, @escaping () -> Void) -> Void = { _, completion in completion() }

    func searchTemplate(
        _ searchTemplate: CPSearchTemplate,
        updatedSearchText searchText: String,
        completionHandler: @escaping ([CPListItem]) -> Void
    ) {
        onQuery(searchText, completionHandler)
    }

    func searchTemplate(
        _ searchTemplate: CPSearchTemplate,
        selectedResult item: CPListItem,
        completionHandler: @escaping () -> Void
    ) {
        onSelect(item, completionHandler)
    }
}
