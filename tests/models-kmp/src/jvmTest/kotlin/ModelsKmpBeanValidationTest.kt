import de.quati.kotlin.util.Option
import de.quati.kotlin.util.toOption
import de.quati.ogen.oas.schemas.gen.model.ConstrainedCodeDto
import de.quati.ogen.oas.schemas.gen.model.ConstrainedDto
import io.kotest.matchers.shouldBe
import jakarta.validation.Validation
import kotlin.test.Test

/** The models are generated in common code; on the JVM their annotations are the Jakarta ones. */
class ModelsKmpBeanValidationTest {
    private val validator = Validation.buildDefaultValidatorFactory().validator

    private fun violations(dto: ConstrainedDto) =
        validator.validate(dto).map { it.propertyPath.toString() }.toSet()

    private val valid = ConstrainedDto(name = "abc", code = ConstrainedCodeDto("ABC"))

    @Test
    fun `valid model`() {
        violations(valid) shouldBe emptySet()
        violations(valid.copy(age = 150.toOption(), tags = listOf("a", "b").toOption())) shouldBe emptySet()
        violations(valid.copy(age = Option.Undefined)) shouldBe emptySet()
    }

    @Test
    fun `invalid model`() {
        violations(valid.copy(name = "a")) shouldBe setOf("name")
        violations(valid.copy(code = ConstrainedCodeDto("abc"))) shouldBe setOf("code")
        violations(valid.copy(age = 151.toOption())) shouldBe setOf("age")
        violations(valid.copy(tags = listOf("a", "b", "c").toOption())) shouldBe setOf("tags")
    }
}
