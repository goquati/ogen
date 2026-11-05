import de.quati.ogen.plugin.SpecsConfigBuilder
import de.quati.ogen.plugin.intern.model.config.SpecConfigs
import de.quati.ogen.plugin.intern.tasks.Validator
import de.quati.ogen.plugin.intern.tasks.getContradictoryConstraintErrors
import org.gradle.api.GradleException
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TestValidatorContradictoryConstraints {
    companion object {
        private val logger = LoggerFactory.getLogger(TestValidatorContradictoryConstraints::class.java)!!

        private fun configs(spec: String): SpecConfigs = SpecsConfigBuilder().apply {
            utilPackageName("de.quati.ogen.test.gen.util")
            add(packageName = "de.quati.ogen.test.gen") {
                specFile(TestValidatorContradictoryConstraints::class.java.getResource("$spec.yaml")!!.path)
                model {}
                validator {}
            }
        }.build()

        private fun errors(spec: String) =
            getContradictoryConstraintErrors(configs(spec).specs.single().parseResult.openAPI)
    }

    @Test
    fun testContradictoryConstraintsFailValidation() {
        val e = assertFailsWith<GradleException> {
            Validator(logger).validate(configs("validator-contradictory-constraints-v30"))
        }
        assertEquals("Found 27 contradictory constraints.", e.message)
    }

    @Test
    fun testContradictoryConstraintsV30() {
        assertEquals(
            listOf(
                "schema 'Pet', property 'count': minimum 10 is above maximum 1",
                "schema 'Pet', property 'weight': minimum 2.5 is above maximum 2.25",
                "schema 'Pet', property 'bothExclusive': minimum 3 (exclusiveMinimum: true) equals maximum 3 (exclusiveMaximum: true)",
                "schema 'Pet', property 'name': minLength 5 is above maxLength 2",
                "schema 'Pet', property 'tags': minItems 3 is above maxItems 1",
                "schema 'Pet', property 'tags[]': minLength 3 is above maxLength 1",
                "schema 'Pet', property 'labels.additionalProperties': minLength 2 is above maxLength 1",
                "schema 'Pet', property 'owner': minProperties 2 is above maxProperties 1",
                "schema 'Pet', property 'owner.age': minimum 18 is above maximum 17",
                "schema 'Code': minLength 4 is above maxLength 3",
                "schema 'Extended', property 'allOf[1].rank': minimum 3 is above maximum 2",
                "schema 'Choice' at 'anyOf[0]': minimum 3 is above maximum 2",
                "schema 'Choice' at 'oneOf[0]': minLength 3 is above maxLength 2",
                "schema 'Choice' at 'not': minItems 3 is above maxItems 2",
                // Reported once, although swagger-parser inlines it into both operations.
                "parameter 'Limit': minimum 5 is above maximum 1",
                "request body 'NewPet' (application/json): minProperties 3 is above maxProperties 2",
                "response 'Error' (application/json): minLength 9 is above maxLength 8",
                "header 'Trace' (text/plain): minLength 7 is above maxLength 6",
                "callback 'Notify' '{\$request.body#/url}:POST' request body (application/json): minimum 6 is above maximum 5",
                // In 3.0, swagger-parser moves path-level parameters into every operation; reported at the first.
                "'/pets/{petId}:GET' parameter 'petId': minimum 10 is above maximum 1",
                "'/pets/{petId}:GET' parameter 'size': minimum 5 equals maximum 5 (exclusiveMaximum: true)",
                "'/pets/{petId}:GET' parameter 'filter' (application/json): minLength 4 is above maxLength 3",
                "'/pets/{petId}:GET' response '200' (application/json): minItems 3 is above maxItems 1",
                "'/pets/{petId}:GET' response '200' (application/json) at '[]': minLength 3 is above maxLength 1",
                "'/pets/{petId}:GET' response '200' header 'X-Rate-Limit': minimum 2 (exclusiveMinimum: true) equals maximum 2",
                "'/pets/{petId}:PUT' request body (application/json): minProperties 2 is above maxProperties 1",
                "'/pets/{petId}:PUT' callback 'onUpdate' '{\$request.body#/url}:POST' request body (application/json): minimum 8 is above maximum 7",
            ),
            errors("validator-contradictory-constraints-v30"),
        )
    }

    @Test
    fun testContradictoryConstraintsV31() {
        assertEquals(
            listOf(
                "schema 'Pet', property 'exclusiveLower': exclusiveMinimum 5 equals maximum 5",
                "schema 'Pet', property 'exclusiveUpper': minimum 5 equals exclusiveMaximum 5",
                "schema 'Pet', property 'bothExclusive': exclusiveMinimum 1 equals exclusiveMaximum 1",
                "schema 'Pet', property 'tighterExclusive': exclusiveMinimum 5 equals maximum 5",
                "schema 'Pet', property 'minimumAboveExclusiveMaximum': minimum 6 is above exclusiveMaximum 5",
                "schema 'Pet', property 'exclusiveAboveExclusive': exclusiveMinimum 3 is above exclusiveMaximum 2",
                "schema 'Pet' at 'patternProperties[^x-]': minLength 2 is above maxLength 1",
                "schema 'Pair' at '[0]': minimum 2 is above maximum 1",
                "path item 'Ping:GET' parameter 'count': minimum 4 is above maximum 3",
                // In 3.1, path-level parameters stay on the path item.
                "'/items/{itemId}' parameter 'itemId': minimum 10 is above maximum 1",
                "webhook 'newPet:POST' request body (application/json): exclusiveMinimum 6 is above maximum 5",
            ),
            errors("validator-contradictory-constraints-v31"),
        )
    }

    @Test
    fun testConsistentConstraintsV30() {
        Validator(logger).validate(configs("validator-consistent-constraints-v30"))
    }

    @Test
    fun testConsistentConstraintsV31() {
        Validator(logger).validate(configs("validator-consistent-constraints-v31"))
    }
}
