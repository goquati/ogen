package de.quati.ogen.plugin.intern.codegen

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import de.quati.ogen.plugin.intern.model.Component
import de.quati.ogen.plugin.intern.model.ComponentName
import de.quati.ogen.plugin.intern.model.Constraints
import de.quati.ogen.plugin.intern.model.Type
import java.math.BigDecimal
import java.math.RoundingMode

/** Whether the models and the Spring controller parameters of this spec carry Jakarta Bean Validation annotations. */
context(c: CodeGenContext)
internal val beanValidation: Boolean
    get() = c.specConfig.beanValidation

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
    val mapping = getMappingOrNull()
    if (mapping != null) {
        // the keywords are those of the schema the mapping applies to, also when this refers to it
        val mapped = if (this is Component.Schema.Ref) c.allSchemas[resolveAlias().name] else this
        return when (mapped) {
            is Component.Schema.Primitiv -> mapped.annotationsFor(mapping.className())
            else -> emptyList()
        }
    }
    return when (this) {
        is Component.Schema.Primitiv -> primitiveAnnotations()
        // the elements cascade with their own `@Valid`, see withElementConstraints
        is Component.Schema.Array -> listOfNotNull(
            sizeAnnotation(constraints.minItems, constraints.maxItems),
            AnnotationSpec.builder(Poet.Validation.uniqueItems).build().takeIf { constraints.uniqueItems },
        )
        is Component.Schema.MapS ->
            listOfNotNull(sizeAnnotation(constraints.minProperties, constraints.maxProperties))

        is Component.Schema.Ref -> {
            val target = c.allSchemas[resolveAlias().name] ?: return emptyList()
            when (target) {
                is Component.Schema.Primitiv.Value,
                is Component.Schema.Array.Value,
                is Component.Schema.MapS.Value -> {
                    val inner = target.toInline().validationAnnotations(nullable = false, inContainer = false)
                    val unboxed = !inContainer && (!nullable || !target.isPrimitiveOnJvm())
                    when {
                        // the type of the value class can't carry the elements' annotations, the JVM field is a
                        // list or map, so `@Valid` on it cascades into the elements (not into nested ones)
                        unboxed -> inner + listOfNotNull(validAnnotation.takeIf { target.elementsNeedCascade() })
                        // the property of a value class of an array or map may also constrain its elements
                        inner.isNotEmpty() || target !is Component.Schema.Primitiv -> listOf(validAnnotation)
                        else -> emptyList()
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

/**
 * Whether `@Valid` must cascade into values of this schema: they are, or contain, generated data classes.
 * [visited] holds the referenced schemas already on the way, so a schema that contains itself ends the search.
 */
context(c: CodeGenContext)
private fun Component.Schema.needsCascade(
    visited: Set<ComponentName.Schema> = emptySet(),
): Boolean {
    if (getMappingOrNull() != null) return false
    return when (this) {
        is Component.Schema.Array -> items.needsCascade(visited)
        is Component.Schema.MapS -> valueSchema.needsCascade(visited)
        is Component.Schema.Ref -> {
            val name = resolveAlias().name
            when (val target = c.allSchemas[name]) {
                // a boxed value class, whose property carries the constraints
                is Component.Schema.Primitiv.Value ->
                    target.toInline().validationAnnotations(nullable = false, inContainer = false).isNotEmpty()

                is Component.Schema.Array.Value, is Component.Schema.MapS.Value -> true
                null -> false
                else -> name !in visited && target.needsCascade(visited + name)
            }
        }

        is Component.Schema.NonInline -> when (toTypeSpecData()) {
            is SchemaTypeSpecData.DataClass, is SchemaTypeSpecData.SealedInterface -> true
            is SchemaTypeSpecData.Enum, null -> false
            is SchemaTypeSpecData.ValueClass -> false
        }

        is Component.Schema.Primitiv, Component.Schema.Null, is Component.Schema.Unknown -> false
    }
}

/**
 * [type], the Kotlin type of a value of this schema, with the constraints of the items of its arrays and the values of
 * its maps on the type arguments, e.g. `List<@Size(max = 3) String>`; [transform] maps these annotations.
 * Hibernate Validator reads them from the JVM type annotations, which the compiler only writes with
 * `-Xemit-jvm-type-annotations`.
 */
context(c: CodeGenContext)
internal fun Component.Schema.withElementConstraints(
    type: TypeName,
    transform: (List<AnnotationSpec>) -> List<AnnotationSpec> = { it },
): TypeName {
    if (type !is ParameterizedTypeName || getMappingOrNull() != null) return type
    val (index, element) = when (this) {
        is Component.Schema.Array -> 0 to items
        is Component.Schema.MapS -> 1 to valueSchema
        else -> return type
    }
    val argument = type.typeArguments.getOrNull(index) ?: return type
    val annotations = transform(element.elementAnnotations())
    val annotated = element.withElementConstraints(argument, transform)
        .let { it.copy(annotations = it.annotations + annotations) }
    val arguments = type.typeArguments.toMutableList().also { it[index] = annotated }
    return type.rawType.parameterizedBy(arguments).copy(nullable = type.isNullable, annotations = type.annotations)
}

/**
 * The type of a controller parameter of this schema with its element constraints (see [withElementConstraints]), and
 * the annotations of the parameter. Spring only validates a parameter with a constraint or `@Valid` on itself, so one
 * whose elements alone are constrained gets `@Valid`.
 */
context(_: CodeGenContext)
internal fun Component.Schema.parameterValidation(
    type: TypeName,
    nullable: Boolean,
): Pair<TypeName, List<AnnotationSpec>> {
    val annotations = validationAnnotations(nullable = nullable, inContainer = false)
    val annotatedType = withElementConstraints(type)
    val needsValid = annotatedType != type && annotations.none { it.typeName == Poet.Validation.valid }
    return annotatedType to if (needsValid) annotations + validAnnotation else annotations
}

/** The annotations of an item of an array or a value of a map, including `@Valid` to cascade into it. */
context(_: CodeGenContext)
private fun Component.Schema.elementAnnotations(): List<AnnotationSpec> =
    validationAnnotations(nullable = isNullableResolved(), inContainer = true)

/** Whether the items or values of this array or map need `@Valid`. */
context(_: CodeGenContext)
private fun Component.Schema.elementsNeedCascade() = when (this) {
    is Component.Schema.Array -> items.needsCascade()
    is Component.Schema.MapS -> valueSchema.needsCascade()
    else -> false
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
private fun Component.Schema.Primitiv.primitiveAnnotations(): List<AnnotationSpec> = annotationsFor(
    Component.Schema.Primitiv.Inline(this.type, format, isNullable).getTypeName(withFlow = false).poet as ClassName
)

/** The annotations of this schema for a value of [type], also one a mapping chose; types they don't fit get none. */
private fun Component.Schema.Primitiv.annotationsFor(type: ClassName): List<AnnotationSpec> =
    when (type.canonicalName) {
        "kotlin.String" -> buildList {
            addIfNotNull(sizeAnnotation(constraints.minLength, constraints.maxLength))
            constraints.pattern?.also { add(patternAnnotation(it)) }
            if (format == "email") add(AnnotationSpec.builder(Poet.Validation.email).build())
        }

        "kotlin.Int", "kotlin.Long", "kotlin.Short", "kotlin.Byte" -> constraints.numberAnnotations(type, integral = true)
        else -> constraints.numberAnnotations(type, integral = false)
    }

private val numberClasses = setOf("java.math.BigDecimal", "java.math.BigInteger")

/**
 * `@Min`/`@Max` for integral types, whose bounds can all be written inclusively, otherwise
 * `@DecimalMin`/`@DecimalMax`, and `@MultipleOf`. Types other than numbers get none.
 */
private fun Constraints.numberAnnotations(type: ClassName, integral: Boolean): List<AnnotationSpec> {
    if (!integral && type.canonicalName !in numberClasses + setOf("kotlin.Float", "kotlin.Double"))
        return emptyList()
    return listOfNotNull(
        minimum?.let { bound ->
            if (integral) integralBound(Poet.Validation.min, Poet.Validation.decimalMin, bound.integralMin())
            else decimalBound(Poet.Validation.decimalMin, bound)
        },
        maximum?.let { bound ->
            if (integral) integralBound(Poet.Validation.max, Poet.Validation.decimalMax, bound.integralMax())
            else decimalBound(Poet.Validation.decimalMax, bound)
        },
        multipleOf?.let {
            AnnotationSpec.builder(Poet.Validation.multipleOf)
                .addMember("value = %S", it.stripTrailingZeros().toPlainString())
                .build()
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

/**
 * `@Min`/`@Max` with an inclusive integer bound; one beyond the range of `Long`, which these take, is written with
 * [decimalAnnotation] instead, so it is still checked.
 */
private fun integralBound(annotation: ClassName, decimalAnnotation: ClassName, value: BigDecimal): AnnotationSpec {
    if (value !in longRange) return decimalBound(decimalAnnotation, Constraints.Bound(value, inclusive = true))
    val literal = value.longValueExact()
        // `-9223372036854775808L` is not a valid literal: the minus applies to a number out of range
        .let { if (it == Long.MIN_VALUE) "Long.MIN_VALUE" else "${it}L" }
    return AnnotationSpec.builder(annotation).addMember("value = %L", literal).build()
}

private fun decimalBound(annotation: ClassName, bound: Constraints.Bound) =
    AnnotationSpec.builder(annotation).apply {
        addMember("value = %S", bound.value.toPlainString())
        if (!bound.inclusive) addMember("inclusive = false")
    }.build()

private fun sizeAnnotation(min: Int?, max: Int?): AnnotationSpec? {
    if (min == null && max == null) return null
    return AnnotationSpec.builder(Poet.Validation.size).apply {
        if (min != null) addMember("min = %L", min)
        if (max != null) addMember("max = %L", max)
    }.build()
}

/**
 * OpenAPI patterns match anywhere in a string, `@Pattern` only the whole string, so a pattern not anchored at both
 * ends may be surrounded by any characters.
 */
private fun patternAnnotation(pattern: String): AnnotationSpec {
    val regexp = if (pattern.isAnchored()) pattern else "[\\s\\S]*(?:$pattern)[\\s\\S]*"
    return AnnotationSpec.builder(Poet.Validation.pattern).addMember("regexp = %S", regexp).build()
}

/**
 * Whether the regex can only match a whole string: it starts with `^`, ends with an unescaped `$` and has no `|`
 * outside of groups and character classes, as in `^a|b$` each anchor belongs to one alternative only.
 */
private fun String.isAnchored(): Boolean {
    if (!startsWith("^") || !endsWith("$")) return false
    var depth = 0
    var classDepth = 0 // Java allows nested character classes, e.g. [a-z&&[^x]]
    var i = 0
    while (i < length) {
        when (this[i]) {
            '\\' -> if (i == length - 2) return false else i++ // the last `$` is escaped
            '[' -> classDepth++
            ']' -> if (classDepth > 0) classDepth--
            '(' -> if (classDepth == 0) depth++
            ')' -> if (classDepth == 0) depth--
            '|' -> if (classDepth == 0 && depth == 0) return false
        }
        i++
    }
    return true
}

private val validAnnotation = AnnotationSpec.builder(Poet.Validation.valid).build()

private fun <T : Any> MutableList<T>.addIfNotNull(value: T?) {
    if (value != null) add(value)
}

private fun Type.NonPrimitiveType.Custom.className() = packageName.className(simpleNames)

/**
 * These annotations for a property of a model: with the use-site target `field`, and in multiplatform common code
 * the annotations of `de.quati.ogen:core` instead of the Jakarta ones, which exist on the JVM only.
 */
context(_: CodeGenContext)
internal fun List<AnnotationSpec>.forModelField() = forModelType().map {
    it.toBuilder().useSiteTarget(AnnotationSpec.UseSiteTarget.FIELD).build()
}

/** These annotations for a type in a model, see [forModelField]. */
context(c: CodeGenContext)
internal fun List<AnnotationSpec>.forModelType() = map { annotation ->
    val type = annotation.typeName as ClassName
    if (!c.globalGenContext.multiplatform) annotation
    else AnnotationSpec.builder(Poet.Validation.multiplatform(type))
        .apply { annotation.members.forEach { addMember(it) } }
        .build()
}
