import de.quati.ogen.plugin.NameConvention
import de.quati.ogen.plugin.SpecsConfigBuilder
import de.quati.ogen.plugin.intern.model.config.SpecConfigs
import de.quati.ogen.plugin.intern.tasks.Validator
import io.swagger.v3.oas.models.OpenAPI
import org.gradle.api.GradleException
import org.slf4j.LoggerFactory
import org.slf4j.Marker
import org.slf4j.event.Level
import org.slf4j.helpers.LegacyAbstractLogger
import org.slf4j.helpers.MessageFormatter
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private val defaultLogger = LoggerFactory.getLogger("ValidatorTest")!!

/** Validates a spec from the test resources; every name format is Any, so only the check under test can fail. */
internal fun validatorConfigs(
    spec: String,
    validator: SpecsConfigBuilder.SpecConfingBuilder.ValidatorConfig.() -> Unit = {},
): SpecConfigs = SpecsConfigBuilder().apply {
    utilPackageName("de.quati.ogen.test.gen.util")
    add(packageName = "de.quati.ogen.test.gen") {
        specFile(Validator::class.java.getResource("/$spec.yaml")!!.path)
        model {}
        validator {
            tagFormat = NameConvention.Any
            operationIdFormat = NameConvention.Any
            propertyNameFormat = NameConvention.Any
            schemaNameFormat = NameConvention.Any
            pathSegmentFormat = NameConvention.Any
            parameterFormat {
                path = NameConvention.Any
                query = NameConvention.Any
            }
            validator()
        }
    }
}.build()

internal fun parsedSpec(spec: String): OpenAPI = validatorConfigs(spec).specs.single().parseResult.openAPI

/** The spec as the validator's messages name it. */
internal fun specName(spec: String) = "spec '${validatorConfigs(spec).specs.single().inputConfig}'"

internal fun failedMessage(spec: String) = "Validation of the ${specName(spec)} failed."

internal fun errorsMessage(spec: String, count: Int) = "Found $count errors in the ${specName(spec)}."

internal fun warningsMessage(spec: String) =
    "Validation of the ${specName(spec)} failed due to warnings (failOnWarnings = true)."

internal fun validate(
    spec: String,
    validator: SpecsConfigBuilder.SpecConfingBuilder.ValidatorConfig.() -> Unit = {},
) = Validator(defaultLogger).validate(validatorConfigs(spec, validator))

internal fun assertValidationFails(
    spec: String,
    message: String,
    validator: SpecsConfigBuilder.SpecConfingBuilder.ValidatorConfig.() -> Unit = {},
) {
    val e = assertFailsWith<GradleException> { validate(spec, validator = validator) }
    assertEquals(message, e.message)
}

/** Asserts the check's messages, and that validation fails on them. */
internal fun assertErrors(
    spec: String,
    check: (OpenAPI) -> List<String>,
    expected: List<String>,
    validator: SpecsConfigBuilder.SpecConfingBuilder.ValidatorConfig.() -> Unit = {},
) {
    assertEquals(expected, check(parsedSpec(spec)))
    assertValidationFails(spec, errorsMessage(spec, expected.size), validator)
}

/** Asserts the check's messages, and that validation fails on them with `failOnWarnings` only. */
internal fun assertWarnings(spec: String, check: (OpenAPI) -> List<String>, expected: List<String>) {
    assertEquals(expected, check(parsedSpec(spec)))
    validate(spec)
    assertValidationFails(spec, warningsMessage(spec)) { failOnWarnings = true }
}

/** Asserts that the check finds nothing and that the spec validates. */
internal fun assertPasses(spec: String, check: (OpenAPI) -> List<String>) {
    assertEquals(emptyList(), check(parsedSpec(spec)))
    validate(spec)
}

/** Keeps every log entry, to assert what the validator prints. */
internal class CapturingLogger : LegacyAbstractLogger() {
    val entries = mutableListOf<Pair<Level, String>>()

    fun at(level: Level) = entries.filter { it.first == level }.map { it.second }

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
