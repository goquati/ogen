import de.quati.ogen.plugin.SpecsConfigBuilder
import de.quati.ogen.plugin.intern.tasks.Validator
import org.slf4j.Marker
import org.slf4j.event.Level
import org.slf4j.helpers.LegacyAbstractLogger
import org.slf4j.helpers.MessageFormatter
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class TestValidator {
    private class CapturingLogger : LegacyAbstractLogger() {
        val entries = mutableListOf<Pair<Level, String>>()

        override fun getFullyQualifiedCallerName(): String? = null
        override fun handleNormalizedLoggingCall(
            level: Level, marker: Marker?, messagePattern: String?, arguments: Array<out Any?>?, throwable: Throwable?,
        ) {
            entries.add(level to MessageFormatter.basicArrayFormat(messagePattern, arguments))
        }

        override fun isTraceEnabled() = true
        override fun isDebugEnabled() = true
        override fun isInfoEnabled() = true
        override fun isWarnEnabled() = true
        override fun isErrorEnabled() = true
    }

    private fun configs(spec: String) = SpecsConfigBuilder().apply {
        utilPackageName("de.quati.ogen.test.gen.util")
        add(packageName = "de.quati.ogen.test.gen") {
            specFile(TestValidator::class.java.getResource("$spec.yaml")!!.path)
            model {}
            validator {}
        }
    }.build()

    private fun warnings(spec: String): List<String> {
        val logger = CapturingLogger()
        Validator(logger = logger).validate(configs = configs(spec))
        return logger.entries.filter { it.first == Level.WARN }.map { it.second }
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
        val configs = configs("validator-warnings")
        val spec = configs.specs.single().parseResult.openAPI
        val schemas = spec.components.schemas
        schemas.getValue("Orphan").name = "Custom"
        Validator(logger = CapturingLogger()).validate(configs = configs)
        assertEquals(mapOf("Pet" to null, "Owner" to null, "Orphan" to "Custom"), schemas.mapValues { it.value.name })
        assertEquals(null, schemas.getValue("Owner").properties.getValue("age").name)
        assertEquals(listOf("listPets", null), spec.paths.values.map { it.get.operationId })
    }
}
