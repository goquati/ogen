import de.quati.ogen.plugin.intern.validation.pathParameterErrors
import kotlin.test.Test

class TestPathParameterCheck {
    @Test
    fun testPathParamsInTemplate() {
        assertPasses("path-params-in-template", ::pathParameterErrors)
        assertPasses("path-params-in-template-3.1", ::pathParameterErrors)
    }

    // A parameter of the Path Item is reported once, at the path.
    @Test
    fun testPathParamsNotInTemplate() {
        assertErrors(
            "path-params-not-in-template", ::pathParameterErrors, listOf(
                "path parameter 'filterId' in '/items/all' has no '{filterId}' in the path",
                "path parameter 'itemId' in '/items/all:GET' has no '{itemId}' in the path",
                "path parameter 'ItemId' in '/items/{itemId}:GET' has no '{ItemId}' in the path",
                "path parameter 'ownerId' in '/items/{itemId}:GET' has no '{ownerId}' in the path",
            )
        )
    }

    // The same in 3.1.
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
