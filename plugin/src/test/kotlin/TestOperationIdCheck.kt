import de.quati.ogen.plugin.Severity
import de.quati.ogen.plugin.intern.validation.operationIdRequiredErrors
import kotlin.test.Test

class TestOperationIdCheck {
    private val missing = listOf(
        "operation '/items:POST' has no operationId",
        "operation '/items/{itemId}:DELETE' has no operationId",
    )

    @Test
    fun testMissingOperationIds() {
        assertErrors(
            "operation-id-missing", ::operationIdRequiredErrors, missing,
            validator = { rules { operationIdRequired = Severity.Error } },
        )
    }

    // The callback's operation has no operationId either, but ogen generates no method for it.
    // The rule is off by default, so ogen names such operations itself without a finding.
    @Test
    fun testOffByDefault() {
        validate("operation-id-missing")
    }

    @Test
    fun testEveryOperationWithOperationId() {
        assertPasses("declared-tags", ::operationIdRequiredErrors)
    }
}
