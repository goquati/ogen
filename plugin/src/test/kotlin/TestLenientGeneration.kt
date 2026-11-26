import de.quati.ogen.plugin.intern.model.Component
import de.quati.ogen.plugin.intern.model.Endpoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Problems in a spec do not stop code generation, as with swagger-parser before: what does not fit is left out;
 * only the validator fails on them.
 */
class TestLenientGeneration {
    @Test
    fun `a spec with mistakes is still generated from`() {
        val config = validatorConfigs("lenient-generation").specs.single()
        val result = config.oasParseResult
        assertTrue(result.errors.isNotEmpty())
        val spec = config.toSpec()
        val endpoint = spec.paths.paths.single()
        // A parameter whose `in` is none of the locations is dropped, as swagger-parser dropped it.
        assertEquals(listOf("limit"), endpoint.parameters.map { (it as Endpoint.Parameter.Content).name })
        assertEquals(Endpoint.RequestBody.Empty, endpoint.requestBody)
        // `required: true` on a property is left out; a discriminator without propertyName makes an untyped value.
        val body = spec.components.schemas.values.filterIsInstance<Component.Schema.Obj>().single { "pet" in it.properties }
        assertIs<Component.Schema.Unknown>(body.properties.getValue("pet"))
    }

    @Test
    fun `the validator still fails on them`() {
        assertValidationFails("lenient-generation", failedMessage("lenient-generation"))
    }
}
