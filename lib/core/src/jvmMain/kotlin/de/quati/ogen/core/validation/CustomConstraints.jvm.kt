package de.quati.ogen.core.validation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import java.math.BigDecimal
import kotlin.reflect.KClass

@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.TYPE)
@Constraint(validatedBy = [MultipleOfValidator::class])
public actual annotation class MultipleOf(
    actual val value: String,
    actual val message: String,
    actual val groups: Array<KClass<*>>,
    actual val payload: Array<KClass<out Payload>>,
)

/** Checks [MultipleOf] for any [Number], exactly: a `Double` is compared by its decimal representation. */
public class MultipleOfValidator : ConstraintValidator<MultipleOf, Number> {
    private lateinit var divisor: BigDecimal

    override fun initialize(annotation: MultipleOf) {
        divisor = BigDecimal(annotation.value)
    }

    override fun isValid(value: Number?, context: ConstraintValidatorContext?): Boolean {
        if (value == null) return true
        val decimal = when (value) {
            is BigDecimal -> value
            is Double -> if (value.isFinite()) BigDecimal(value.toString()) else return false
            is Float -> if (value.isFinite()) BigDecimal(value.toString()) else return false
            else -> BigDecimal(value.toString())
        }
        return decimal.remainder(divisor).signum() == 0
    }
}

@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.TYPE)
@Constraint(validatedBy = [UniqueItemsValidator::class])
public actual annotation class UniqueItems(
    actual val message: String,
    actual val groups: Array<KClass<*>>,
    actual val payload: Array<KClass<out Payload>>,
)

/** Checks [UniqueItems] for any [Collection], comparing the elements with `equals`. */
public class UniqueItemsValidator : ConstraintValidator<UniqueItems, Collection<*>> {
    override fun isValid(value: Collection<*>?, context: ConstraintValidatorContext?): Boolean =
        value == null || value.size == value.toSet().size
}
