import de.quati.ogen.plugin.NameConvention
import de.quati.ogen.plugin.SpecsConfigBuilder
import de.quati.ogen.plugin.intern.validation.namingConventionErrors
import kotlin.test.Test

class TestNamingConventionCheck {
    companion object {
        private val strictNames: SpecsConfigBuilder.SpecConfingBuilder.ValidatorConfig.() -> Unit = {
            tagFormat = NameConvention.CamelCase
            operationIdFormat = NameConvention.CamelCase
            propertyNameFormat = NameConvention.CamelCase
            schemaNameFormat = NameConvention.PascalCase
            pathSegmentFormat = NameConvention.KebabCase
            stringEnumFormat = NameConvention.KebabCase
            parameterFormat {
                path = NameConvention.CamelCase
                query = NameConvention.CamelCase
                header = NameConvention.CamelCase
                cookie = NameConvention.CamelCase
            }
        }

        private fun assertNamingErrors(spec: String, expected: List<String>) {
            val config = validatorConfigs(spec, strictNames).specs.single().validatorConfig!!
            assertErrors(spec, { namingConventionErrors(it, config) }, expected, strictNames)
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
                // In 3.0, swagger-parser moves a path-level parameter into each operation; it is named at the first.
                "query parameter 'Page_Size' in '/items/{itemId}:GET' does not match the query parameter format camelCase",
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
    @Test
    fun testPathSegments() {
        assertNamingErrors(
            "naming-path-segments",
            listOf("path segment 'Items' in '/Items' does not match the path segment format kebab-case"),
        )
    }
}
