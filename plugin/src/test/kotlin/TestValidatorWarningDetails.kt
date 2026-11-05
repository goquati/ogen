import de.quati.ogen.plugin.intern.tasks.Validator
import org.slf4j.event.Level
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class TestValidatorWarningDetails {
    private fun warnings(spec: String): List<String> {
        val logger = CapturingLogger()
        Validator(logger = logger).validate(configs = validatorConfigs(spec))
        return logger.at(Level.WARN)
    }

    private val invalidType =
        "\tThe 'type' attribute must be one of 'null', 'boolean', 'object', 'array', 'number', 'string', or 'integer'.\n"
    private val getWithBody =
        "\tWhile technically allowed, GET/HEAD with request body may indicate programming error, and is considered an anti-pattern.\n"

    @Test
    fun testWarningsNameTheirLocation() {
        val warnings = warnings("validator-warnings")
        assertContains(warnings, invalidType + "\t\tSchema 'Pet' uses the 'strin' type, which is not a valid type.\n")
        assertContains(warnings, invalidType + "\t\tSchema 'Owner.age' uses the 'strin' type, which is not a valid type.\n")
        assertContains(warnings, getWithBody + "\t\tGET listPets contains a request body and is considered an anti-pattern.\n")
        assertContains(warnings, getWithBody + "\t\tGET /owners contains a request body and is considered an anti-pattern.\n")
    }

    @Test
    fun testWarningWithoutDetails() {
        val warnings = warnings("validator-warnings")
        assertContains(warnings, "\tUnused model: Orphan\n")
    }

    @Test
    fun testLocationNamesAreRestored() {
        val configs = validatorConfigs("validator-warnings")
        val spec = configs.specs.single().parseResult.openAPI
        val schemas = spec.components.schemas
        schemas.getValue("Orphan").name = "Custom"
        Validator(logger = CapturingLogger()).validate(configs = configs)
        assertEquals(mapOf("Pet" to null, "Owner" to null, "Orphan" to "Custom"), schemas.mapValues { it.value.name })
        assertEquals(null, schemas.getValue("Owner").properties.getValue("age").name)
        assertEquals(listOf("listPets", null), spec.paths.values.map { it.get.operationId })
    }
}
