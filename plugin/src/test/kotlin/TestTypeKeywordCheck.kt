import de.quati.ogen.plugin.intern.tasks.Validator
import de.quati.ogen.plugin.intern.validation.typeKeywordWarnings
import org.slf4j.event.Level
import kotlin.test.Test
import kotlin.test.assertContains

class TestTypeKeywordCheck {
    @Test
    fun testMisplacedKeywords() {
        assertWarnings(
            "type-keywords", ::typeKeywordWarnings, listOf(
                "keyword 'minLength' in 'Pet' has no effect on type object; it applies to string",
                "keyword 'maxLength' in 'Pet.count' has no effect on type integer; it applies to string",
                "keyword 'pattern' in 'Pet.count' has no effect on type integer; it applies to string",
                "keyword 'maxItems' in 'Pet.name' has no effect on type string; it applies to array",
                "keyword 'minimum' in 'Pet.name' has no effect on type string; it applies to integer or number",
                "keyword 'exclusiveMinimum' in 'Pet.name' has no effect on type string; it applies to integer or number",
                "keyword 'properties' in 'Pet.tags' has no effect on type array; it applies to object",
                "keyword 'minProperties' in 'Pet.tags' has no effect on type array; it applies to object",
                "keyword 'multipleOf' in 'Pet.tags[]' has no effect on type boolean; it applies to integer or number",
                "keyword 'items' in 'More' has no effect on type object; it applies to array",
                "keyword 'minItems' in 'More' has no effect on type object; it applies to array",
                "keyword 'properties' in 'More.code' has no effect on type string; it applies to object",
                "keyword 'additionalProperties' in 'More.code' has no effect on type string; it applies to object",
                "keyword 'required' in 'More.code' has no effect on type string; it applies to object",
                "keyword 'maximum' in 'More.code' has no effect on type string; it applies to integer or number",
                "keyword 'exclusiveMaximum' in 'More.code' has no effect on type string; it applies to integer or number",
                "keyword 'maxProperties' in 'More.list' has no effect on type array; it applies to object",
                "keyword 'uniqueItems' in 'More.flag' has no effect on type integer; it applies to array",
                "keyword 'maxLength' in '/pets:GET.parameters.limit' has no effect on type integer; it applies to string",
            ),
            parse = ::declaredTypesSpec,
        )
        // swagger-parser reads 'More' as an array; the validator reads the declared types too.
        val logger = CapturingLogger()
        Validator(logger).validate(validatorConfigs("type-keywords"))
        assertContains(
            logger.at(Level.WARN).map { it.trim() },
            "keyword 'items' in 'More' has no effect on type object; it applies to array",
        )
    }

    @Test
    fun testMisplacedKeywordsInOpenApi31() {
        assertWarnings(
            "type-keywords-3.1", ::typeKeywordWarnings, listOf(
                "keyword 'items' in 'Pet' has no effect on type object; it applies to array",
                "keyword 'prefixItems' in 'Pet' has no effect on type object; it applies to array",
                "keyword 'pattern' in 'Pet.count' has no effect on type integer or null; it applies to string",
                "keyword 'maxLength' in 'Pet.nothing' has no effect on type null; it applies to string",
                "keyword 'patternProperties' in 'Pet.tags' has no effect on type array; it applies to object",
                "keyword 'propertyNames' in 'Pet.tags' has no effect on type array; it applies to object",
                "keyword 'dependentSchemas' in 'Pet.tags' has no effect on type array; it applies to object",
                "keyword 'dependentRequired' in 'Pet.tags' has no effect on type array; it applies to object",
                "keyword 'unevaluatedProperties' in 'Pet.tags' has no effect on type array; it applies to object",
                "keyword 'contains' in 'Pet.name' has no effect on type string; it applies to array",
                "keyword 'minContains' in 'Pet.name' has no effect on type string; it applies to array",
                "keyword 'maxContains' in 'Pet.name' has no effect on type string; it applies to array",
                "keyword 'unevaluatedItems' in 'Pet.name' has no effect on type string; it applies to array",
                "keyword 'exclusiveMinimum' in 'Pet.name' has no effect on type string; it applies to integer or number",
                "keyword 'exclusiveMaximum' in 'Pet.name' has no effect on type string; it applies to integer or number",
                "keyword 'multipleOf' in 'Pet.name' has no effect on type string; it applies to integer or number",
            ),
            parse = ::declaredTypesSpec,
        )
    }

    @Test
    fun testFittingKeywords() {
        assertPasses("type-keywords-valid", ::typeKeywordWarnings, parse = ::declaredTypesSpec)
        assertPasses("type-keywords-valid-3.1", ::typeKeywordWarnings, parse = ::declaredTypesSpec)
    }
}
