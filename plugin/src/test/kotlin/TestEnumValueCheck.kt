import de.quati.ogen.plugin.intern.validation.enumValueErrors
import kotlin.test.Test

class TestEnumValueCheck {
    companion object {
        private fun assertEnumErrors(spec: String, vararg expected: String) =
            assertErrors("enum-values-$spec", ::enumValueErrors, expected.toList())

        private fun assertEnumPasses(spec: String) = assertPasses("enum-values-$spec", ::enumValueErrors)
    }

    @Test
    fun testIntegerMismatch30() = assertEnumErrors(
        "integer-mismatch",
        "enum value 'one' in 'Count' does not match the schema's type integer",
        "enum value 'two' in 'Count' does not match the schema's type integer",
    )

    @Test
    fun testIntegerMismatch31() = assertEnumErrors(
        "integer-mismatch-3.1",
        "enum value 'one' in 'Count' does not match the schema's type integer",
        "enum value 2.5 in 'Count' does not match the schema's type integer",
    )

    @Test
    fun testNumberMismatch31() =
        assertEnumErrors("number-mismatch-3.1", "enum value 'x' in 'Ratio' does not match the schema's type number")

    @Test
    fun testBooleanMismatch31() =
        assertEnumErrors("boolean-mismatch-3.1", "enum value 1 in 'Flag' does not match the schema's type boolean")

    @Test
    fun testStringMismatch31() =
        assertEnumErrors("string-mismatch-3.1", "enum value 1 in 'Name' does not match the schema's type string")

    @Test
    fun testTypeArrayMismatch31() =
        assertEnumErrors("type-array-mismatch-3.1", "enum value true in 'Mixed' does not match the schema's type integer or string")

    @Test
    fun testNullNotNullable30() =
        assertEnumErrors("null-not-nullable", "enum value null in 'Name' is not allowed: the schema is not nullable")

    // 3.1 has no `nullable`: only a `type` that includes "null" allows null.
    @Test
    fun testNullNotNullable31() =
        assertEnumErrors("null-not-nullable-3.1", "enum value null in 'Name' is not allowed: the schema is not nullable")

    // 1.0 is the same JSON value as 1.
    @Test
    fun testIntegerDecimal30() = assertEnumErrors("integer-decimal", "enum value 1 in 'Count' is listed more than once")

    @Test
    fun testDuplicate30() = assertEnumErrors("duplicate", "enum value 'open' in 'State' is listed more than once")

    @Test
    fun testDuplicateByte() = assertEnumErrors("duplicate-byte", "enum value 'YQ==' in 'Token' is listed more than once")

    @Test
    fun testDuplicateNumber31() = assertEnumErrors("duplicate-number-3.1", "enum value 1 in 'Size' is listed more than once")

    @Test
    fun testDuplicateNull30() =
        assertEnumErrors("duplicate-null", "enum value 'low' in 'Level' does not match the schema's type integer")

    @Test
    fun testDuplicateNull31() = assertEnumErrors("duplicate-null-3.1", "enum value null in 'Level' is listed more than once")

    // Strings in a boolean enum are no booleans; unquoted no and off are strings in YAML 1.2.
    @Test
    fun testStringsInBooleanEnum30() = assertEnumErrors(
        "boolean-cast",
        "enum value '0' in 'Flag' does not match the schema's type boolean",
        "enum value 'FALSE' in 'Flag' does not match the schema's type boolean",
        "enum value 'True' in 'Flag' does not match the schema's type boolean",
        "enum value 'no' in 'WordsOnly' does not match the schema's type boolean",
        "enum value 'off' in 'WordsOnly' does not match the schema's type boolean",
        "enum value true in 'Twice' is listed more than once",
    )

    @Test
    fun testStringsInBooleanEnum31() = assertEnumErrors(
        "boolean-cast-3.1",
        "enum value '0' in 'Flag' does not match the schema's type boolean",
        "enum value 'FALSE' in 'Flag' does not match the schema's type boolean",
        "enum value 'True' in 'Flag' does not match the schema's type boolean",
    )

    // YAML 1.2 reads unquoted yes, no, on and off as strings, so they are no duplicates of true and false.
    @Test
    fun testYamlBooleanWordsAreStrings() {
        assertEnumPasses("yaml-booleans")
        assertEnumPasses("yaml-booleans-3.1")
    }

    @Test
    fun testNestedProperty() =
        assertEnumErrors("nested-property", "enum value 'fast' in 'Item.nested.mode' is listed more than once")

    @Test
    fun testArrayItems() = assertEnumErrors("array-items", "enum value 'a' in 'Tags[]' is listed more than once")

    @Test
    fun testCompositions() = assertEnumErrors(
        "compositions",
        "enum value 'a' in 'Composite.allOf[0]' is listed more than once",
        "enum value 'c' in 'Composite.oneOf[0]' is listed more than once",
        "enum value 'b' in 'Composite.anyOf[0]' is listed more than once",
        "enum value 'd' in 'Composite.not' is listed more than once",
        "enum value 'm' in 'Map.additionalProperties' is listed more than once",
    )

    // A parameter of the Path Item is reported where it is declared.
    @Test
    fun testParameter30() = assertEnumErrors(
        "parameter",
        "enum value 'a' in '/items.parameters.sort' is listed more than once",
        "enum value 'b' in '/items:GET.parameters.kind[]' is listed more than once",
    )

    // A referenced parameter is reported once, as the component.
    @Test
    fun testParameterRef30() =
        assertEnumErrors("parameter-ref", "enum value 'a' in 'components.parameters.Kind' is listed more than once")

    @Test
    fun testParameter31() = assertEnumErrors(
        "parameter-3.1",
        "enum value 1 in '/items.parameters.sort' does not match the schema's type string",
        "enum value 'b' in '/items:GET.parameters.filter.application/json' does not match the schema's type integer",
    )

    @Test
    fun testRequestBody() = assertEnumErrors(
        "request-body",
        "enum value 'low' in '/items:POST.requestBody.application/json.level' does not match the schema's type integer",
    )

    @Test
    fun testResponse() = assertEnumErrors(
        "response",
        "enum value 1 in '/items:GET.responses.200.application/json[]' does not match the schema's type string",
        "enum value 'x' in '/items:GET.responses.200.headers.X-Rate' is listed more than once",
    )

    @Test
    fun testComponents() = assertEnumErrors(
        "components",
        "enum value 'a' in 'components.parameters.Kind' is listed more than once",
        "enum value 'b' in 'components.requestBodies.Body.application/json' is listed more than once",
        "enum value 'c' in 'components.responses.Answer.application/json' is listed more than once",
        "enum value 'd' in 'components.headers.Rate' is listed more than once",
    )

    @Test
    fun testValid() {
        assertEnumPasses("valid")
        assertEnumPasses("valid-3.1")
        assertEnumPasses("valid-nullable")
        assertEnumPasses("valid-nullable-3.1")
        assertEnumPasses("valid-yaml-booleans-3.1")
        // swagger-parser infers the type from the first value; an untyped enum allows any value.
        assertEnumPasses("valid-untyped")
    }
}
