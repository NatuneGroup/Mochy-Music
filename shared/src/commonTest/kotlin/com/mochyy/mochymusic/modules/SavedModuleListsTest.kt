package com.mochyy.mochymusic.modules

import kotlin.test.Test
import kotlin.test.assertEquals

// web_modules, local_modules and code_modules, written in the same json shapes the registry always wrote
class SavedModuleListsTest {

    private val store = FakeModuleStore()
    private val lists = SavedModuleLists(store)

    @Test
    fun anEmptyListReadsAsNone() {
        assertEquals(emptyList(), lists.web())
        assertEquals(emptyList(), lists.local())
        assertEquals(emptyList(), lists.code())
    }

    @Test
    fun aWebModuleIsItsBaseAndItsManifest() {
        lists.addWeb("https://lyrics.example", """{ "id": "lyrics" }""")
        assertEquals(listOf(SavedWebModule("https://lyrics.example", """{ "id": "lyrics" }""")), lists.web())
        assertEquals("""[{"base":"https://lyrics.example","manifest":"{ \"id\": \"lyrics\" }"}]""", store.string("web_modules"))
    }

    @Test
    fun addingTheSameOneAgainReplacesItAndPutsItLast() {
        lists.addWeb("https://a.example", "one")
        lists.addWeb("https://b.example", "two")
        lists.addWeb("https://a.example", "one again")

        assertEquals(listOf("https://b.example", "https://a.example"), lists.web().map { it.base })
        assertEquals("one again", lists.web().last().manifest)

        lists.removeWeb("https://b.example")
        assertEquals(listOf("https://a.example"), lists.web().map { it.base })
    }

    @Test
    fun aLocalModuleKeepsItsFileAndManifest() {
        lists.addLocal("file.look", "look.zip", """{ "id": "look" }""")
        assertEquals(listOf(SavedLocalModule("file.look", "look.zip", """{ "id": "look" }""")), lists.local())
        assertEquals("""[{"id":"file.look","file":"look.zip","manifest":"{ \"id\": \"look\" }"}]""", store.string("local_modules"))
        lists.removeLocal("file.look")
        assertEquals(emptyList(), lists.local())
    }

    @Test
    fun aCodeModuleKeepsItsFolder() {
        lists.addCode("code.module", "code-module", "code.zip")
        assertEquals(listOf(SavedCodeModule("code.module", "code-module", "code.zip")), lists.code())
        assertEquals("""[{"id":"code.module","folder":"code-module","file":"code.zip"}]""", store.string("code_modules"))
        lists.addCode("code.module", "code-module-2", "code2.zip")
        assertEquals(listOf("code-module-2"), lists.code().map { it.folder })
        lists.removeCode("code.module")
        assertEquals(emptyList(), lists.code())
    }

    @Test
    fun aListThatIsNotTheRightJsonReadsEmptyTheWayJsonArrayDid() {
        store.putString("web_modules", "not json at all")
        assertEquals(emptyList(), lists.web())

        store.putString("local_modules", """[1, "two"]""")
        assertEquals(emptyList(), lists.local())

        store.putString("code_modules", """{ "id": "not a list" }""")
        assertEquals(emptyList(), lists.code())
    }

    @Test
    fun rowsMissingAFieldReadAsBlankRatherThanFailing() {
        store.putString("web_modules", """[{"base": "https://a.example"}, {"manifest": "only a manifest"}]""")
        val rows = lists.web()
        assertEquals(listOf("https://a.example", ""), rows.map { it.base })
        assertEquals(listOf("", "only a manifest"), rows.map { it.manifest })
    }
}
