import de.quati.ogen.plugin.SpecsConfigBuilder
import de.quati.ogen.plugin.intern.tasks.Validator
import de.quati.ogen.plugin.intern.tasks.getEnumValueErrors
import org.gradle.api.GradleException
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TestEnumValidation {
    companion object {
        private val logger = LoggerFactory.getLogger(TestEnumValidation::class.java)!!
        private const val CAST_HINT = " (swagger-parser also reads a value that isn't a valid"

        private fun configs(spec: String) = SpecsConfigBuilder().apply {
            utilPackageName("de.quati.ogen.test.gen.util")
            add(packageName = "de.quati.ogen.test.gen") {
                specFile(TestEnumValidation::class.java.getResource("enum-values-$spec.yaml")!!.path)
                model {}
                validator {}
            }
        }.build()

        private fun assertErrors(spec: String, vararg expected: String) {
            val configs = configs(spec)
            assertEquals(expected.toList(), getEnumValueErrors(configs.specs.single().parseResult.openAPI))
            val e = assertFailsWith<GradleException> { Validator(logger).validate(configs) }
            assertEquals("Found ${expected.size} enum value errors.", e.message)
        }

        private fun assertValid(spec: String) {
            val configs = configs(spec)
            assertEquals(emptyList(), getEnumValueErrors(configs.specs.single().parseResult.openAPI))
            Validator(logger).validate(configs)
        }
    }

    @Test
    fun testIntegerMismatch30() {
        // swagger-parser reads 'one' and 'two' as null
        assertErrors(
            "integer-mismatch-3.0",
            "enum value null in schema 'Count' isn't allowed: the schema isn't nullable$CAST_HINT integer as null)",
        )
    }

    @Test
    fun testIntegerMismatch31() {
        assertErrors(
            "integer-mismatch-3.1",
            "enum value 'one' in schema 'Count' doesn't match the schema's type integer",
            "enum value 2.5 in schema 'Count' doesn't match the schema's type integer",
        )
    }

    @Test
    fun testNumberMismatch31() {
        assertErrors("number-mismatch-3.1", "enum value 'x' in schema 'Ratio' doesn't match the schema's type number")
    }

    @Test
    fun testBooleanMismatch31() {
        assertErrors("boolean-mismatch-3.1", "enum value 1 in schema 'Flag' doesn't match the schema's type boolean")
    }

    @Test
    fun testStringMismatch31() {
        assertErrors("string-mismatch-3.1", "enum value 1 in schema 'Name' doesn't match the schema's type string")
    }

    @Test
    fun testTypeArrayMismatch31() {
        assertErrors(
            "type-array-mismatch-3.1",
            "enum value true in schema 'Mixed' doesn't match the schema's type integer or string",
        )
    }

    @Test
    fun testNullNotNullable30() {
        assertErrors("null-not-nullable-3.0", "enum value null in schema 'Name' isn't allowed: the schema isn't nullable")
    }

    @Test
    fun testNullNotNullable31() {
        // 3.1 has no `nullable`: only a `type` that includes "null" allows null
        assertErrors("null-not-nullable-3.1", "enum value null in schema 'Name' isn't allowed: the schema isn't nullable")
    }

    @Test
    fun testIntegerDecimal30() {
        // swagger-parser reads 1.0 as null in an integer schema
        assertErrors(
            "integer-decimal-3.0",
            "enum value null in schema 'Count' isn't allowed: the schema isn't nullable$CAST_HINT integer as null)",
        )
    }

    @Test
    fun testDuplicate30() {
        assertErrors("duplicate-3.0", "enum value 'open' in schema 'State' is listed more than once")
    }

    @Test
    fun testDuplicateNumber31() {
        assertErrors("duplicate-number-3.1", "enum value 1 in schema 'Size' is listed more than once")
    }

    @Test
    fun testDuplicateNull30() {
        // swagger-parser reads 'low' as null
        assertErrors(
            "duplicate-null-3.0",
            "enum value null in schema 'Level' is listed more than once$CAST_HINT integer as null)",
        )
    }

    @Test
    fun testDuplicateNull31() {
        assertErrors("duplicate-null-3.1", "enum value null in schema 'Level' is listed more than once")
    }

    @Test
    fun testNestedProperty() {
        assertErrors("nested-property", "enum value 'fast' in schema 'Item' at 'nested.mode' is listed more than once")
    }

    @Test
    fun testArrayItems() {
        assertErrors("array-items", "enum value 'a' in schema 'Tags' at '[]' is listed more than once")
    }

    @Test
    fun testCompositions() {
        assertErrors(
            "compositions",
            "enum value 'a' in schema 'Composite' at 'allOf[0]' is listed more than once",
            "enum value 'b' in schema 'Composite' at 'anyOf[0]' is listed more than once",
            "enum value 'c' in schema 'Composite' at 'oneOf[0]' is listed more than once",
            "enum value 'd' in schema 'Composite' at 'not' is listed more than once",
            "enum value 'm' in schema 'Map' at 'additionalProperties' is listed more than once",
        )
    }

    @Test
    fun testParameter30() {
        // swagger-parser moves the path-level parameter into each operation; it is reported once
        assertErrors(
            "parameter-3.0",
            "enum value 'a' in GET /items parameter 'sort' is listed more than once",
            "enum value 'b' in GET /items parameter 'kind' at '[]' is listed more than once",
        )
    }

    @Test
    fun testParameterRef30() {
        // swagger-parser replaces the references with the component parameter; it is reported once
        assertErrors("parameter-ref-3.0", "enum value 'a' in GET /items parameter 'kind' is listed more than once")
    }

    @Test
    fun testParameter31() {
        assertErrors(
            "parameter-3.1",
            "enum value 1 in /items parameter 'sort' doesn't match the schema's type string",
            "enum value 'b' in GET /items parameter 'filter' (application/json) doesn't match the schema's type integer",
        )
    }

    @Test
    fun testRequestBody() {
        assertErrors(
            "request-body",
            "enum value 'low' in POST /items request body (application/json) at 'level' doesn't match the schema's type integer",
        )
    }

    @Test
    fun testResponse() {
        assertErrors(
            "response",
            "enum value 1 in GET /items response 200 (application/json) at '[]' doesn't match the schema's type string",
            "enum value 'x' in GET /items response 200 header 'X-Rate' is listed more than once",
        )
    }

    @Test
    fun testComponents() {
        assertErrors(
            "components",
            "enum value 'a' in component 'Kind' parameter 'kind' is listed more than once",
            "enum value 'b' in component 'Body' request body (application/json) is listed more than once",
            "enum value 'c' in response component 'Answer' (application/json) is listed more than once",
            "enum value 'd' in header component 'Rate' is listed more than once",
        )
    }

    @Test
    fun testValid30() {
        assertValid("valid-3.0")
    }

    @Test
    fun testValid31() {
        assertValid("valid-3.1")
    }

    @Test
    fun testValidNullable30() {
        assertValid("valid-nullable-3.0")
    }

    @Test
    fun testValidNullable31() {
        assertValid("valid-nullable-3.1")
    }

    @Test
    fun testValidUntyped30() {
        // swagger-parser infers the type from the first value; an untyped enum allows any value
        assertValid("valid-untyped-3.0")
    }
}
