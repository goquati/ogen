import de.quati.ogen.plugin.NameConvention
import de.quati.ogen.plugin.Severity
import de.quati.ogen.plugin.SpecsConfigBuilder
import de.quati.ogen.plugin.intern.validation.namingConventionErrors
import de.quati.ogen.plugin.intern.validation.pathSegmentParts
import kotlin.test.Test
import kotlin.test.assertEquals

class TestNamingConventionCheck {
    companion object {
        private val strictNames: SpecsConfigBuilder.SpecConfingBuilder.ValidatorConfig.() -> Unit = {
            naming {
                tag = NameConvention.CamelCase
                operationId = NameConvention.CamelCase
                property = NameConvention.CamelCase
                schema = NameConvention.PascalCase
                pathSegment = NameConvention.KebabCase { except("{tenantId}_Data") }
                pathExtension = NameConvention.KebabCase
                enumValue = NameConvention.KebabCase
                parameter {
                    path = NameConvention.CamelCase
                    query = NameConvention.CamelCase
                    header = NameConvention.CamelCase
                    cookie = NameConvention.CamelCase
                }
            }
            rules { namingConventions = Severity.Error }
        }

        private fun assertNamingErrors(
            spec: String,
            expected: List<String>,
            validator: SpecsConfigBuilder.SpecConfingBuilder.ValidatorConfig.() -> Unit = strictNames,
        ) {
            val config = validatorConfigs(spec, validator).specs.single().validatorConfig!!
            assertErrors(spec, { namingConventionErrors(it, config) }, expected, validator)
        }

        private fun pathSegments(format: NameConvention): SpecsConfigBuilder.SpecConfingBuilder.ValidatorConfig.() -> Unit = {
            strictNames()
            naming {
                pathSegment = format
                // The variables are named to fit the segment, which the path parameter format would reject.
                parameter { path = NameConvention.Any }
            }
        }
    }

    // One bad name in each place the check covers.
    @Test
    fun testEveryName() {
        assertNamingErrors(
            "naming-everywhere",
            listOf(
                "path segment 'Bad_Segment' in '/Bad_Segment' does not match the path segment format kebab-case",
                "tag 'Items' in '/items/{itemId}:GET' does not match the tag format camelCase",
                "operationId 'Get_Item' in '/items/{itemId}:GET' does not match the operationId format camelCase",
                "tag 'Items' in '/items/{itemId}:POST' does not match the tag format camelCase",
                "operationId 'On_Created' in '/items/{itemId}:POST.callbacks.onCreated.{\$request.body#/callbackUrl}:POST' does not match the operationId format camelCase",
                "query parameter 'Sort_Order' in 'components.parameters.SortOrder' does not match the query parameter format camelCase",
                // A parameter of the Path Item is reported where it is declared.
                "query parameter 'Page_Size' in '/items/{itemId}' does not match the query parameter format camelCase",
                "header parameter 'x_trace_id' in '/items/{itemId}:GET' does not match the header parameter format camelCase",
                "cookie parameter 'Session_Id' in '/items/{itemId}:GET' does not match the cookie parameter format camelCase",
                "schema name 'item_model' in 'components.schemas' does not match the schema name format PascalCase",
                "property 'Bad_Prop' in 'item_model' does not match the property format camelCase",
                "enum value 'Open_State' in 'item_model.state' does not match the enum value format kebab-case",
                "property 'Member_Prop' in 'Composed.allOf[1]' does not match the property format camelCase",
                "enum value 'Asc_Order' in 'components.parameters.SortOrder' does not match the enum value format kebab-case",
                "property 'Inline_Name' in '/items/{itemId}:GET.responses.200.application/json' does not match the property format camelCase",
                "property 'Body_Field' in '/items/{itemId}:POST.requestBody.application/json' does not match the property format camelCase",
            ),
        )
    }

    @Test
    fun testEveryName31() {
        assertNamingErrors(
            "naming-everywhere-3.1",
            listOf(
                "operationId 'Item_Created' in 'webhooks.itemCreated:POST' does not match the operationId format camelCase",
                // In 3.1, a path-level parameter stays on its path.
                "query parameter 'Page_Size' in '/items/{itemId}' does not match the query parameter format camelCase",
            ),
        )
    }

    // The root path, a trailing slash and a double slash leave an empty segment, which the convention must not reject.
    // A segment's names are the text up to its first `.`, split at `:`; the parts after it are extensions. A part
    // that is only variables is not checked; in the others, a variable counts as `xx` or `Xx`.
    @Test
    fun testPathSegments() {
        assertNamingErrors(
            "naming-path-segments",
            listOf(
                "path segment 'Items' in '/Items' does not match the path segment format kebab-case",
                "path segment 'Cancel_It' in '/items/{id}:Cancel_It' does not match the path segment format kebab-case",
                "path segment 'item_{id}' in '/items/item_{id}' does not match the path segment format kebab-case",
                "path segment 'Report_{year}' in '/reports/Report_{year}.CSV' does not match the path segment format kebab-case",
                "path extension 'CSV' in '/reports/Report_{year}.CSV' does not match the path extension format kebab-case",
                "path segment '{name}_{rev}' in '/files/{name}_{rev}' does not match the path segment format kebab-case",
            ),
        )
    }

    @Test
    fun testPathSegmentsCamelCase() {
        assertNamingErrors(
            "naming-path-segments-camel",
            listOf(
                "path segment 'report-{year}' in '/reports/report-{year}' does not match the path segment format camelCase without acronyms",
            ),
            pathSegments(NameConvention.CamelCase { acronyms = false }),
        )
    }

    @Test
    fun testPathSegmentsPascalCase() {
        assertNamingErrors(
            "naming-path-segments-pascal",
            listOf(
                "path segment 'report{Year}' in '/Reports/report{Year}' does not match the path segment format PascalCase without acronyms",
                "path extension 'CSV' in '/Reports/Summary.CSV' does not match the path extension format kebab-case",
            ),
            pathSegments(NameConvention.PascalCase { acronyms = false }),
        )
    }

    @Test
    fun testPathSegmentParts() {
        assertEquals(listOf("{id}", "cancel") to emptyList(), pathSegmentParts("{id}:cancel"))
        assertEquals(listOf("{id}", "download") to listOf("json"), pathSegmentParts("{id}.json:download"))
        assertEquals(listOf("archive") to listOf("tar", "gz"), pathSegmentParts("archive.tar.gz"))
        assertEquals(listOf("well-known") to emptyList(), pathSegmentParts(".well-known"))
        // A `.` or `:` inside a variable splits nothing.
        assertEquals(listOf("{user.id}", "{a:b}") to listOf("json"), pathSegmentParts("{user.id}:{a:b}.json"))
        assertEquals(listOf("a\u0000b") to emptyList(), pathSegmentParts("a\u0000b"))
    }

    // The placeholder has no digit, so a convention without digits accepts a part with a variable.
    @Test
    fun testPathSegmentsWithoutDigits() {
        assertNamingErrors(
            "naming-path-segments-camel",
            listOf(
                "path segment 'report-{year}' in '/reports/report-{year}' does not match the path segment format ^[a-z]+([A-Z][a-z]+)*$",
            ),
            pathSegments(NameConvention.RegexFormat("^[a-z]+([A-Z][a-z]+)*$")),
        )
    }
}
