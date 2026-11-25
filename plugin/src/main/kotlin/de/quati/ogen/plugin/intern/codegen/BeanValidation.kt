package de.quati.ogen.plugin.intern.codegen

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import de.quati.ogen.plugin.intern.model.Component
import de.quati.ogen.plugin.intern.model.Constraints
import de.quati.ogen.plugin.intern.model.Type
import de.quati.ogen.plugin.intern.model.config.GeneratorConfig
import java.math.BigDecimal
import java.math.RoundingMode

/** Whether the code of this spec carries Jakarta Bean Validation annotations. */
context(c: CodeGenContext)
internal val beanValidationEnabled: Boolean
    get() = c.specConfig.generatorConfigs.any { it is GeneratorConfig.ServerSpringV4 && it.beanValidation }

/**
 * The Jakarta Bean Validation annotations that check a value of this schema, without use-site target.
 *
 * A value class is only a class of its own on the JVM when it is boxed; otherwise the JVM holds the wrapped value.
 * Its constraints are then put on the value itself, and for a boxed one `@Valid` cascades into the value class,
 * whose property carries them.
 *
 * @param nullable whether the Kotlin type of the value is nullable
 * @param inContainer whether the value is a type argument, e.g. of `Option`, so value classes are boxed
 */
context(c: CodeGenContext)
internal fun Component.Schema.validationAnnotations(
    nullable: Boolean,
    inContainer: Boolean,
): List<AnnotationSpec> {
    if (!beanValidationEnabled) return emptyList()
    val mapping = getMappingOrNull()
    if (mapping != null) return when (this) {
        is Component.Schema.Primitiv -> constraints.numberAnnotations(mapping.className(), integral = false)
        else -> emptyList()
    }
    return when (this) {
        is Component.Schema.Primitiv -> primitiveAnnotations()
        is Component.Schema.Array -> buildList {
            addIfNotNull(sizeAnnotation(constraints.minItems, constraints.maxItems))
            if (items.needsCascade()) add(validAnnotation)
        }

        is Component.Schema.MapS -> buildList {
            addIfNotNull(sizeAnnotation(constraints.minProperties, constraints.maxProperties))
            if (valueSchema.needsCascade()) add(validAnnotation)
        }

        is Component.Schema.Ref -> {
            val target = c.allSchemas[resolveAlias().name] ?: return emptyList()
            when (target) {
                is Component.Schema.Primitiv.Value,
                is Component.Schema.Array.Value,
                is Component.Schema.MapS.Value -> {
                    val inner = target.toInline().validationAnnotations(nullable = false, inContainer = false)
                    val unboxed = !inContainer && (!nullable || !target.isPrimitiveOnJvm())
                    when {
                        inner.isEmpty() -> emptyList()
                        unboxed -> inner
                        else -> listOf(validAnnotation)
                    }
                }

                else -> if (needsCascade()) listOf(validAnnotation) else emptyList()
            }
        }

        is Component.Schema.Obj, is Component.Schema.SealedInterface, is Component.Schema.Composed ->
            if (needsCascade()) listOf(validAnnotation) else emptyList()

        is Component.Schema.EnumString, is Component.Schema.Unknown, Component.Schema.Null -> emptyList()
    }
}

/** Whether `@Valid` must cascade into values of this schema: they are, or contain, generated data classes. */
context(c: CodeGenContext)
private fun Component.Schema.needsCascade(): Boolean {
    if (getMappingOrNull() != null) return false
    return when (this) {
        is Component.Schema.Array -> items.needsCascade()
        is Component.Schema.MapS -> valueSchema.needsCascade()
        is Component.Schema.Ref -> c.allSchemas[resolveAlias().name]?.needsCascade() ?: false
        is Component.Schema.NonInline -> when (toTypeSpecData()) {
            is SchemaTypeSpecData.DataClass, is SchemaTypeSpecData.SealedInterface -> true
            is SchemaTypeSpecData.Enum, null -> false
            is SchemaTypeSpecData.ValueClass -> false
        }

        is Component.Schema.Primitiv, Component.Schema.Null, is Component.Schema.Unknown -> false
    }
}

/** Whether the value class of this schema wraps a JVM primitive, so a nullable one is boxed. */
context(_: CodeGenContext)
private fun Component.Schema.Value.isPrimitiveOnJvm() = toInline().getTypeName(withFlow = false) in setOf(
    Type.PrimitiveType.Int,
    Type.PrimitiveType.Long,
    Type.PrimitiveType.Float,
    Type.PrimitiveType.Double,
    Type.PrimitiveType.Boolean,
)

context(_: CodeGenContext)
private fun Component.Schema.Primitiv.primitiveAnnotations(): List<AnnotationSpec> =
    when (val type = Component.Schema.Primitiv.Inline(this.type, format, isNullable).getTypeName(withFlow = false)) {
        Type.PrimitiveType.String -> buildList {
            addIfNotNull(sizeAnnotation(constraints.minLength, constraints.maxLength))
            constraints.pattern?.also { add(patternAnnotation(it)) }
            if (format == "email") add(AnnotationSpec.builder(Poet.Spring.Validation.email).build())
        }

        Type.PrimitiveType.Int, Type.PrimitiveType.Long ->
            constraints.numberAnnotations(type.poet as ClassName, integral = true)

        Type.PrimitiveType.Float, Type.PrimitiveType.Double ->
            constraints.numberAnnotations(type.poet as ClassName, integral = false)

        else -> emptyList()
    }

private val numberClasses = setOf("java.math.BigDecimal", "java.math.BigInteger")

/**
 * `@Min`/`@Max` for integral types, whose bounds can all be written inclusively, otherwise
 * `@DecimalMin`/`@DecimalMax`. Types other than numbers get none.
 */
private fun Constraints.numberAnnotations(type: ClassName, integral: Boolean): List<AnnotationSpec> {
    if (!integral && type.canonicalName !in numberClasses + setOf("kotlin.Float", "kotlin.Double"))
        return emptyList()
    return listOfNotNull(
        minimum?.let { bound ->
            if (integral) integralBound(Poet.Spring.Validation.min, bound.integralMin())
            else decimalBound(Poet.Spring.Validation.decimalMin, bound)
        },
        maximum?.let { bound ->
            if (integral) integralBound(Poet.Spring.Validation.max, bound.integralMax())
            else decimalBound(Poet.Spring.Validation.decimalMax, bound)
        },
    )
}

/** The smallest integer the lower bound admits. */
private fun Constraints.Bound.integralMin(): BigDecimal {
    val ceil = value.setScale(0, RoundingMode.CEILING)
    return if (!inclusive && ceil.compareTo(value) == 0) ceil + BigDecimal.ONE else ceil
}

/** The largest integer the upper bound admits. */
private fun Constraints.Bound.integralMax(): BigDecimal {
    val floor = value.setScale(0, RoundingMode.FLOOR)
    return if (!inclusive && floor.compareTo(value) == 0) floor - BigDecimal.ONE else floor
}

private val longRange = BigDecimal(Long.MIN_VALUE)..BigDecimal(Long.MAX_VALUE)

private fun integralBound(annotation: ClassName, value: BigDecimal): AnnotationSpec? {
    if (value !in longRange) return null
    return AnnotationSpec.builder(annotation).addMember("value = %LL", value.toBigInteger()).build()
}

private fun decimalBound(annotation: ClassName, bound: Constraints.Bound) =
    AnnotationSpec.builder(annotation).apply {
        addMember("value = %S", bound.value.toPlainString())
        if (!bound.inclusive) addMember("inclusive = false")
    }.build()

private fun sizeAnnotation(min: Int?, max: Int?): AnnotationSpec? {
    if (min == null && max == null) return null
    return AnnotationSpec.builder(Poet.Spring.Validation.size).apply {
        if (min != null) addMember("min = %L", min)
        if (max != null) addMember("max = %L", max)
    }.build()
}

/**
 * OpenAPI patterns match anywhere in a string, `@Pattern` only the whole string, so a pattern not anchored at both
 * ends may be surrounded by any characters.
 */
private fun patternAnnotation(pattern: String): AnnotationSpec {
    val anchored = pattern.startsWith("^") && pattern.endsWith("$") && !pattern.endsWith("\\$")
    val regexp = if (anchored) pattern else "[\\s\\S]*(?:$pattern)[\\s\\S]*"
    return AnnotationSpec.builder(Poet.Spring.Validation.pattern).addMember("regexp = %S", regexp).build()
}

private val validAnnotation = AnnotationSpec.builder(Poet.Spring.Validation.valid).build()

private fun <T : Any> MutableList<T>.addIfNotNull(value: T?) {
    if (value != null) add(value)
}

private fun Type.NonPrimitiveType.Custom.className() = packageName.className(simpleNames)

/** These annotations with the use-site target `field`, for constructor properties. */
internal fun List<AnnotationSpec>.onField() =
    map { it.toBuilder().useSiteTarget(AnnotationSpec.UseSiteTarget.FIELD).build() }
