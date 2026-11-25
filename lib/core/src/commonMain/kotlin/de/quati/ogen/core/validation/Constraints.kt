@file:OptIn(ExperimentalMultiplatform::class)

package de.quati.ogen.core.validation

import kotlin.reflect.KClass

/*
 * Jakarta Bean Validation annotations for common code. On the JVM they are the Jakarta annotations themselves,
 * so Hibernate Validator checks generated models in a multiplatform module, too; on other platforms they don't
 * exist and the compiler drops them.
 * The signatures must equal those of the Jakarta annotations.
 */

/** [jakarta.validation.Payload] on the JVM. */
public expect interface Payload

/** [jakarta.validation.constraints.Pattern.Flag] on the JVM. */
public expect enum class PatternFlag {
    UNIX_LINES, CASE_INSENSITIVE, COMMENTS, MULTILINE, DOTALL, UNICODE_CASE, CANON_EQ,
}

/** [jakarta.validation.Valid] on the JVM. */
@OptionalExpectation
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
public expect annotation class Valid()

/** [jakarta.validation.constraints.Size] on the JVM. */
@OptionalExpectation
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
public expect annotation class Size(
    val message: String = "{jakarta.validation.constraints.Size.message}",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
    val min: Int = 0,
    val max: Int = Int.MAX_VALUE,
)

/** [jakarta.validation.constraints.Min] on the JVM. */
@OptionalExpectation
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
public expect annotation class Min(
    val value: Long,
    val message: String = "{jakarta.validation.constraints.Min.message}",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

/** [jakarta.validation.constraints.Max] on the JVM. */
@OptionalExpectation
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
public expect annotation class Max(
    val value: Long,
    val message: String = "{jakarta.validation.constraints.Max.message}",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

/** [jakarta.validation.constraints.DecimalMin] on the JVM. */
@OptionalExpectation
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
public expect annotation class DecimalMin(
    val value: String,
    val message: String = "{jakarta.validation.constraints.DecimalMin.message}",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
    val inclusive: Boolean = true,
)

/** [jakarta.validation.constraints.DecimalMax] on the JVM. */
@OptionalExpectation
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
public expect annotation class DecimalMax(
    val value: String,
    val message: String = "{jakarta.validation.constraints.DecimalMax.message}",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
    val inclusive: Boolean = true,
)

/** [jakarta.validation.constraints.Email] on the JVM. */
@OptionalExpectation
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
public expect annotation class Email(
    val message: String = "{jakarta.validation.constraints.Email.message}",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
    val regexp: String = ".*",
    val flags: Array<PatternFlag> = [],
)

/** [jakarta.validation.constraints.Pattern] on the JVM. */
@OptionalExpectation
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
public expect annotation class Pattern(
    val regexp: String,
    val flags: Array<PatternFlag> = [],
    val message: String = "{jakarta.validation.constraints.Pattern.message}",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)
