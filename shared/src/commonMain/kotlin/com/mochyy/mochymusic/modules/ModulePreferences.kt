// every switch, pick and setting value the module registry keeps, in one place: the same keys the registry wrote by hand, so nobody's choices are lost

package com.mochyy.mochymusic.modules

/**
 * What the registry remembers about the modules between starts: which are on, what each choice and setting is set to, when a
 * copy was added and installed, the order they're listed and asked in, and the look player picked last. Every key and every
 * default is the one the registry already had in `source_modules`, so an existing install reads exactly as it did.
 */
class ModulePreferences(private val store: ModuleStore) {

    /** a module starts off until it's turned on; [fallback] says what a module with no switch of its own is (youtube starts on) */
    fun isEnabled(id: String, fallback: Boolean = false): Boolean = store.boolean("enabled_$id", fallback)

    fun setEnabled(id: String, on: Boolean) = store.putBoolean("enabled_$id", on)

    /** choice id to the option picked; a choice not in here is on its first option */
    fun picks(id: String, choiceIds: List<String>): Map<String, String> =
        choiceIds.mapNotNull { choice -> store.string("pick_${id}_$choice")?.let { choice to it } }.toMap()

    fun setPick(id: String, choiceId: String, optionId: String) = store.putString("pick_${id}_$choiceId", optionId)

    /** what each of a module's own settings is set to, the field's default until someone changes it; [defaults] is the field order */
    fun fieldValues(id: String, defaults: Map<String, String>): Map<String, String> =
        defaults.mapValues { (field, fallback) -> store.string("field_${id}_$field") ?: fallback }

    fun setField(id: String, fieldId: String, value: String) = store.putString("field_${id}_$fieldId", value)

    /** when it was added, null for built-in ones and ones added before mochy kept the date */
    fun addedAt(id: String): Long? = store.long("added_$id", 0L).takeIf { it > 0L }

    /** when this copy went in: every update moves it, unlike [addedAt], so the store can tell a newer zip from this one */
    fun installedAt(id: String): Long? = store.long("installed_$id", 0L).takeIf { it > 0L } ?: addedAt(id)

    /** the first added date stays through updates */
    fun markAdded(id: String, at: Long) {
        if (!store.contains("added_$id")) store.putLong("added_$id", at)
    }

    fun markInstalled(id: String, at: Long) = store.putLong("installed_$id", at)

    /** the order modules are listed and asked in: what was dragged on the modules page, else the provider order the songs already use */
    fun order(): List<String> =
        (store.string("module_order") ?: store.string("provider_order")).orEmpty().split(',').filter { it.isNotBlank() }

    /** a new order for the modules listed; the ones it leaves out keep their places after them */
    fun setOrder(ids: List<String>) = store.putString("module_order", (ids + order()).distinct().joinToString(","))

    /** the look player picked last time, "" before the first pick */
    fun playerPick(): String = store.string("player_pick").orEmpty()

    fun setPlayerPick(key: String) = store.putString("player_pick", key)

    /** everything kept about a module that's been taken away for good */
    fun forget(id: String, choiceIds: List<String>, fieldIds: List<String>) {
        store.remove("enabled_$id")
        store.remove("added_$id")
        store.remove("installed_$id")
        choiceIds.forEach { store.remove("pick_${id}_$it") }
        fieldIds.forEach { store.remove("field_${id}_$it") }
    }
}
