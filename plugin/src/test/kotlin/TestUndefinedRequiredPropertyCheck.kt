import de.quati.ogen.plugin.intern.validation.undefinedRequiredPropertyErrors
import kotlin.test.Test

class TestUndefinedRequiredPropertyCheck {
    @Test
    fun testRequiredPropertiesUndefined() {
        assertErrors(
            "required-properties-undefined", ::undefinedRequiredPropertyErrors, listOf(
                "colour" to "Item",
                "street" to "Item.address",
                "label" to "Item.tags[]",
                "code" to "Closed",
                "key" to "Dictionary.additionalProperties",
                "age" to "Extended.allOf[1]",
                "nickanme" to "TightenedTypo",
                "nickanme" to "RequiresNickname",
                "nmae" to "EitherName.oneOf[1]",
                "nickname" to "AnyName.anyOf[0]",
                "cell" to "Grid.allOf[0].rows[][]",
                "size" to "components.parameters.Paging",
                "label" to "components.requestBodies.NewItem.application/json",
                "reason" to "components.responses.NotFound.application/json",
                "region" to "/items:POST.parameters.scope",
                "kind" to "/items:POST.parameters.filter",
                "field" to "/items:POST.parameters.sort.application/json",
                "title" to "/items:POST.requestBody.application/json",
                "identifier" to "/items:POST.responses.201.application/json",
                "traceId" to "/items:POST.responses.201.headers.X-Trace",
            ).map { (name, location) -> "required property '$name' in '$location' is not defined in its properties" }
        )
    }

    @Test
    fun testRequiredPropertiesUndefined31() {
        assertErrors(
            "required-properties-undefined-3.1", ::undefinedRequiredPropertyErrors, listOf(
                "code" to "Closed",
                "code" to "ClosedUnevaluated",
                "nmae" to "RefSibling",
                "zzz" to "SharedLater.x",
            ).map { (name, location) -> "required property '$name' in '$location' is not defined in its properties" }
        )
    }

    // allOf members, additionalProperties and schemas under `not` may require names the schema doesn't define.
    @Test
    fun testRequiredPropertiesDefined() {
        assertPasses("required-properties-defined", ::undefinedRequiredPropertyErrors)
        assertPasses("required-properties-defined-3.1", ::undefinedRequiredPropertyErrors)
    }
}
