import de.quati.ogen.plugin.intern.validation.pathParameterErrors
import kotlin.test.Test

class TestPathParameterCheck {
    @Test
    fun testPathParamsInTemplate() {
        assertPasses("path-params-in-template", ::pathParameterErrors)
        assertPasses("path-params-in-template-3.1", ::pathParameterErrors)
    }

    // OpenAPI 3.0: swagger-parser copies path-level parameters into each operation.
    @Test
    fun testPathParamsNotInTemplate() {
        assertErrors(
            "path-params-not-in-template", ::pathParameterErrors, listOf(
                "path parameter 'filterId' in '/items/all:GET' has no '{filterId}' in the path",
                "path parameter 'itemId' in '/items/all:GET' has no '{itemId}' in the path",
                "path parameter 'filterId' in '/items/all:DELETE' has no '{filterId}' in the path",
                "path parameter 'ItemId' in '/items/{itemId}:GET' has no '{ItemId}' in the path",
                "path parameter 'ownerId' in '/items/{itemId}:GET' has no '{ownerId}' in the path",
            )
        )
    }

    // OpenAPI 3.1: path-level parameters stay on the path item.
    @Test
    fun testPathParamsNotInTemplate31() {
        assertErrors(
            "path-params-not-in-template-3.1", ::pathParameterErrors, listOf(
                "path parameter 'filterId' in '/items/all' has no '{filterId}' in the path",
                "path parameter 'itemId' in '/items/all:GET' has no '{itemId}' in the path",
                "path parameter 'ItemId' in '/items/{itemId}:GET' has no '{ItemId}' in the path",
                "path parameter 'ownerId' in '/items/{itemId}:GET' has no '{ownerId}' in the path",
            )
        )
    }
}
