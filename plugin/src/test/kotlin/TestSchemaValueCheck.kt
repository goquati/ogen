import de.quati.ogen.plugin.intern.validation.invalidDefaultErrors
import de.quati.ogen.plugin.intern.validation.invalidExampleWarnings
import kotlin.test.Test
import kotlin.test.assertEquals

class TestSchemaValueCheck {
    @Test
    fun testInvalidDefaults() {
        assertErrors(
            "invalid-defaults", ::invalidDefaultErrors, listOf(
                "default in 'Pet.state' does not match its schema: 'pending' is not one of its enum values 'open', 'closed'",
                "default in 'Pet.count' does not match its schema: 'lots' is not of type integer",
                "default in 'Pet.age' does not match its schema: -1 is less than minimum 0",
                "default in 'Pet.name' does not match its schema: 'Rex the dog' is longer than maxLength 3",
                "default in 'Pet.tags' does not match its schema: at '[1]': 1 is not of type string",
                "default in 'Pet.owner' does not match its schema: required property 'name' is missing",
                "default in 'Pet.nick' does not match its schema: null is not of type string",
                "default in '/pets:GET.parameters.size' does not match its schema: 3 is not one of its enum values 1, 2",
            )
        )
        assertErrors(
            "invalid-defaults-3.1", ::invalidDefaultErrors, listOf(
                "default in 'Level' does not match its schema: 'lots' is not of type integer",
                "default in 'State' does not match its schema: null is not one of its enum values 'open', 'closed'",
                "default in 'Ratio' does not match its schema: 0.75 is not a multiple of 0.5",
                "default in 'Lang' does not match its schema: no (unquoted, YAML 1.1 reads it as false) is not of type string",
                "default in 'Start' does not match its schema: 12:30 (unquoted, YAML 1.1 reads it as 750) is not of type string",
                "default in 'Zip' does not match its schema: 01234 (unquoted, YAML 1.1 reads it as 668) is not of type string",
                // Reported by the reference and by the type next to it, but only once.
                "default in 'Amount' does not match its schema: 'many' is not of type integer",
            )
        )
    }

    // In 3.0 a nullable enum allows null; 1.0 is an integer; a discriminator picks the oneOf alternative; formats are
    // not checked.
    @Test
    fun testValidDefaults() {
        assertPasses("invalid-defaults-valid", ::invalidDefaultErrors)
        assertPasses("invalid-defaults-valid-3.1", ::invalidDefaultErrors)
    }

    // A missing readOnly property, also one another allOf member defines or one behind an allOf, and an XML example
    // are no findings.
    @Test
    fun testInvalidExamples() {
        assertWarnings(
            "invalid-examples", ::invalidExampleWarnings, listOf(
                "example in 'Pet.count' does not match its schema: 'lots' is not of type integer",
                "example in 'Pet.born' does not match its schema: 'yesterday' is not a valid date",
                "example in '/pets:GET.parameters.limit' does not match its schema: 500 is greater than maximum 100",
                "example 'sideways' in '/pets:GET.parameters.sort' does not match its schema: " +
                    "'left' is not one of its enum values 'asc', 'desc'",
                "example 'one' in '/pets:GET.responses.200.application/json' does not match its schema: " +
                    "at '[0].count': -1.5 is not of type integer",
                "example in '/pets:GET.responses.200.headers.X-Rate-Limit' does not match its schema: " +
                    "'many' is not of type integer",
                "example in '/pets:POST.responses.201.application/problem+json' does not match its schema: " +
                    "required property 'name' is missing",
            )
        )
        assertWarnings(
            "invalid-examples-3.1", ::invalidExampleWarnings, listOf(
                "examples[1] in 'Size' does not match its schema: 'big' is not of type integer",
                "examples[1] in 'Tag' does not match its schema: property 'color' is not allowed",
                "examples[1] in 'Labeled' does not match its schema: at 'extra': 'text' is not of type integer",
            )
        )
        // OpenAPI 3.0 doesn't know unevaluatedProperties (#149).
        assertEquals(emptyList(), invalidExampleWarnings(parsedSpec("unevaluated-properties-3.0")))
        // A media type of components.mediaTypes; the discriminator's defaultMapping picks Other. PetXml is only used
        // as XML.
        assertWarnings(
            "invalid-examples-3.2", ::invalidExampleWarnings, listOf(
                "example 'bird' in 'components.mediaTypes.PetJson' does not match its schema: " +
                    "at 'wings': 'many' is not of type integer",
                "example 'dog' in 'components.mediaTypes.PetJson' does not match its schema: " +
                    "at 'name': 1 is not of type string",
            )
        )
    }
}
