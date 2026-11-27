import de.quati.ogen.plugin.NameConvention
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestNameConvention {
    private fun NameConvention.assertMatches(vararg names: String) =
        names.forEach { assertTrue(matches(it), "$this should accept '$it'") }

    private fun NameConvention.assertRejects(vararg names: String) =
        names.forEach { assertFalse(matches(it), "$this should reject '$it'") }

    // By default, both conventions accept acronyms.
    @Test
    fun testDefaults() {
        NameConvention.PascalCase.assertMatches("Item", "ItemV2", "UUID", "HTTPError", "ApiID")
        NameConvention.PascalCase.assertRejects("item", "Item_Model", "Item-Model", "")
        NameConvention.CamelCase.assertMatches("item", "itemV2", "apiID", "httpError")
        NameConvention.CamelCase.assertRejects("Item", "item_name", "item-name", "")
        NameConvention.KebabCase.assertMatches("items", "item-2", "a-b-c")
        NameConvention.KebabCase.assertRejects("Items", "item_2", "-item", "item-", "")
    }

    // An explicit `acronyms = true` is the default.
    @Test
    fun testAcronymsTrue() {
        NameConvention.PascalCase { acronyms = true }.assertMatches("UUID", "HTTPError")
        NameConvention.CamelCase { acronyms = true }.assertMatches("apiID")
    }

    // Without acronyms, every word is one capital followed by lower-case letters and digits.
    @Test
    fun testAcronymsFalse() {
        val pascal = NameConvention.PascalCase { acronyms = false }
        pascal.assertMatches("Item", "ItemV2", "HttpError", "ApiId", "X2")
        pascal.assertRejects("UUID", "HTTPError", "ApiID", "item", "Item_Model", "")
        val camel = NameConvention.CamelCase { acronyms = false }
        camel.assertMatches("item", "itemV2", "httpError", "apiId", "a")
        camel.assertRejects("apiID", "httpERROR", "Item", "item_name", "")
    }

    // Without acronyms, only the last word may be a single capital: two in a row would be an acronym.
    @Test
    fun testAcronymsFalseSingleCapital() {
        val pascal = NameConvention.PascalCase { acronyms = false }
        pascal.assertMatches("PointX", "X", "Point2X", "ApiI")
        pascal.assertRejects("PointXY", "XY", "PointXYz", "ApiIDs", "XItem")
        val camel = NameConvention.CamelCase { acronyms = false }
        camel.assertMatches("pointX", "getA", "aB", "point2X")
        camel.assertRejects("pointXY", "getAB", "aBC", "getAValue")
    }

    @Test
    fun testExcept() {
        val header = NameConvention.RegexFormat("^[A-Z][A-Za-z0-9]*(?:-[A-Z][A-Za-z0-9]*)*$") {
            except("X-HubSpot-Signature-v3")
        }
        header.assertMatches("X-Trace-Id", "X-HubSpot-Signature-v3")
        header.assertRejects("x-trace-id", "X-HubSpot-Signature-v4")

        // Several names per call, several calls.
        val schema = NameConvention.PascalCase {
            acronyms = false
            except("UUID", "HTTPError")
            except("item_model")
        }
        schema.assertMatches("Item", "UUID", "HTTPError", "item_model")
        schema.assertRejects("ApiID", "other_model")

        NameConvention.CamelCase { except("Legacy_Name") }.assertMatches("item", "Legacy_Name")
        NameConvention.KebabCase { except("Legacy") }.assertMatches("items", "Legacy")
        NameConvention.KebabCase { except("Legacy") }.assertRejects("Other")
        NameConvention.RegexFormat(Regex("^[a-z]+$")).assertRejects("Legacy")
    }

    // The name in a finding says how the convention is configured; exceptions never show up in a finding.
    @Test
    fun testToString() {
        assertEquals("PascalCase", NameConvention.PascalCase.toString())
        assertEquals("PascalCase", NameConvention.PascalCase { except("x") }.toString())
        assertEquals("PascalCase without acronyms", NameConvention.PascalCase { acronyms = false }.toString())
        assertEquals("camelCase without acronyms", NameConvention.CamelCase { acronyms = false }.toString())
        assertEquals("kebab-case", NameConvention.KebabCase { except("x") }.toString())
        assertEquals("^[a-z]+$", NameConvention.RegexFormat("^[a-z]+$") { except("x") }.toString())
    }
}
