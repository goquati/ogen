import de.quati.ogen.plugin.intern.validation.contradictoryConstraintErrors
import kotlin.test.Test

class TestContradictoryConstraintCheck {
    @Test
    fun testContradictoryConstraints30() {
        assertErrors(
            "contradictory-constraints", ::contradictoryConstraintErrors, listOf(
                "'Pet.count': minimum 10 is above maximum 1",
                "'Pet.weight': minimum 2.5 is above maximum 2.25",
                "'Pet.bothExclusive': minimum 3 (exclusiveMinimum: true) equals maximum 3 (exclusiveMaximum: true)",
                "'Pet.name': minLength 5 is above maxLength 2",
                "'Pet.tags': minItems 3 is above maxItems 1",
                "'Pet.tags[]': minLength 3 is above maxLength 1",
                "'Pet.labels.additionalProperties': minLength 2 is above maxLength 1",
                "'Pet.owner': minProperties 2 is above maxProperties 1",
                "'Pet.owner.age': minimum 18 is above maximum 17",
                "'Code': minLength 4 is above maxLength 3",
                "'Extended.allOf[1].rank': minimum 3 is above maximum 2",
                "'Choice.oneOf[0]': minLength 3 is above maxLength 2",
                "'Choice.anyOf[0]': minimum 3 is above maximum 2",
                "'Choice.not': minItems 3 is above maxItems 2",
                // Reported once, as the component, although swagger-parser inlines it into both operations.
                "'components.parameters.Limit': minimum 5 is above maximum 1",
                "'components.requestBodies.NewPet.application/json': minProperties 3 is above maxProperties 2",
                "'components.responses.Error.application/json': minLength 9 is above maxLength 8",
                "'components.headers.Trace.text/plain': minLength 7 is above maxLength 6",
                "'components.callbacks.Notify.{\$request.body#/url}:POST.requestBody.application/json': minimum 6 is above maximum 5",
                // In 3.0, swagger-parser moves path-level parameters into every operation; reported at the first.
                "'/pets/{petId}:GET.parameters.petId': minimum 10 is above maximum 1",
                "'/pets/{petId}:GET.parameters.size': minimum 5 equals maximum 5 (exclusiveMaximum: true)",
                "'/pets/{petId}:GET.parameters.filter.application/json': minLength 4 is above maxLength 3",
                "'/pets/{petId}:GET.responses.200.application/json': minItems 3 is above maxItems 1",
                "'/pets/{petId}:GET.responses.200.application/json[]': minLength 3 is above maxLength 1",
                "'/pets/{petId}:GET.responses.200.headers.X-Rate-Limit': minimum 2 (exclusiveMinimum: true) equals maximum 2",
                "'/pets/{petId}:PUT.requestBody.application/json': minProperties 2 is above maxProperties 1",
                "'/pets/{petId}:PUT.callbacks.onUpdate.{\$request.body#/url}:POST.requestBody.application/json': minimum 8 is above maximum 7",
            ).map { "contradictory constraints in $it" }
        )
    }

    @Test
    fun testContradictoryConstraints31() {
        assertErrors(
            "contradictory-constraints-3.1", ::contradictoryConstraintErrors, listOf(
                "'Pet.exclusiveLower': exclusiveMinimum 5 equals maximum 5",
                "'Pet.exclusiveUpper': minimum 5 equals exclusiveMaximum 5",
                "'Pet.bothExclusive': exclusiveMinimum 1 equals exclusiveMaximum 1",
                "'Pet.tighterExclusive': exclusiveMinimum 5 equals maximum 5",
                "'Pet.minimumAboveExclusiveMaximum': minimum 6 is above exclusiveMaximum 5",
                "'Pet.exclusiveAboveExclusive': exclusiveMinimum 3 is above exclusiveMaximum 2",
                "'Pet.patternProperties[^x-]': minLength 2 is above maxLength 1",
                "'Pair[0]': minimum 2 is above maximum 1",
                "'Conditional.contains': minLength 3 is above maxLength 2",
                "'Conditional.then.low': minimum 5 is above maximum 1",
                "'components.pathItems.Ping:GET.parameters.count': minimum 4 is above maximum 3",
                // In 3.1, path-level parameters stay on the path item.
                "'/items/{itemId}.parameters.itemId': minimum 10 is above maximum 1",
                "'webhooks.newPet:POST.requestBody.application/json': exclusiveMinimum 6 is above maximum 5",
                "'webhooks.newPet:POST.requestBody.multipart/form-data.encoding.file.headers.X-Part': minLength 4 is above maxLength 3",
            ).map { "contradictory constraints in $it" }
        )
    }

    @Test
    fun testConsistentConstraints() {
        assertPasses("consistent-constraints", ::contradictoryConstraintErrors)
        assertPasses("consistent-constraints-3.1", ::contradictoryConstraintErrors)
    }
}
