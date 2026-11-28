# Models

`model {}` generates Kotlin data classes, enums and sealed interfaces with
[kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) support. Optional properties have the type
`Option<T>` from `de.quati:kotlin-util`, so a property that is left out differs from one that is `null`.

## Property names

A property's Kotlin name is its name in camelCase, with `@SerialName` keeping the JSON name when they differ. When
several names have the same camelCase form, e.g. `xTenant` and `x-tenant`, the name that already is that form keeps
it and the others get `_` appended until their name is free (`xTenant`, `xTenant_`). A class discriminator the class
has no property for takes no name. Since adding or reordering such a name can move a Kotlin name to another JSON
key without breaking the code that uses it, the validator warns about them (`sameKotlinNames`).

## Objects and `additionalProperties`

What a `type: object` schema without `allOf`, `anyOf` or `oneOf` becomes, the same in every OpenAPI version and
wherever the schema is used (with a composition, any `additionalProperties` makes it `JsonElement`, and so does an
`allOf` or `anyOf` of classes that allow other keys, also through `patternProperties`):

| Schema | Kotlin type |
|---|---|
| `properties`, without `additionalProperties` and `patternProperties`, or with `additionalProperties: false` and no `patternProperties` | a class |
| `properties` and `additionalProperties: true` or a schema, or `patternProperties` | a class with a map of the other keys |
| no `properties`, `additionalProperties: true` or `{}` | `Map<String, JsonElement>` |
| no `properties`, `additionalProperties` with a schema, or `patternProperties` | `Map<String, T>` |
| no `properties`, `additionalProperties: false` and no `patternProperties` | `JsonElement` |
| no `properties`, no `additionalProperties` and no `patternProperties` | `JsonElement` |

### `unevaluatedProperties`

Without `additionalProperties`, `unevaluatedProperties` (OpenAPI 3.1 and later) counts as `additionalProperties`,
as in such an object only `properties` and `patternProperties` evaluate keys. Next to `if`/`then`/`else`,
`dependentSchemas` or `$dynamicRef`, which evaluate keys too, and in OpenAPI 3.0, it is ignored. Next to these
applicators (OpenAPI 3.1 and later), a required key that `properties` doesn't declare and `unevaluatedProperties`
allows is still kept, as an untyped property; in 3.0 the validator reports such a key as not defined. Example
validation follows the same rules.

### The map of other keys

A class that allows other keys keeps them in its last property, `additionalProperties: Map<String, T>` (with a `_`
appended while a declared property has that name). Its serializer reads every key the schema doesn't declare into
the map and writes the map's entries next to the declared properties; it works with `Json` only.

```yaml
Thing:
  type: object
  properties: { id: { type: string } }
  additionalProperties: { type: integer }
```

```kotlin
val thing = ThingDto(id = "a".toOption(), additionalProperties = mapOf("count" to 3))
// {"id":"a","count":3}
```

- `T` is the type of the `additionalProperties` schema, `JsonElement` for `true`.
- `patternProperties` share the map with `additionalProperties`, in a class as in a plain map: when all their schemas
  (and that of `additionalProperties`, unless it is `false`) are the same, `T` is its type, otherwise `JsonElement`.
  The keys are not matched against the patterns. Without `additionalProperties`, keys that match no pattern are
  allowed too, so `T` is `JsonElement`. A pattern whose schema is `false` allows no key and counts for nothing.
- Without `additionalProperties` and `patternProperties`, the class has no map: unknown keys are ignored or rejected
  as the `Json` configuration says (`ignoreUnknownKeys`).
- A key of the map that the schema declares as a property fails serialization.
- A class discriminator that `Json` writes into the object (in a sealed interface, or in every object with
  `ClassDiscriminatorMode.ALL_JSON_OBJECTS`) is no additional property.

### Required names without a property

A `required` name that `properties` doesn't declare becomes a property of its own when the object allows it, with the
schema that allows it: that of the matching pattern (`JsonElement` if several with different schemas match; patterns
are matched as Java regexes, and one that doesn't compile as such may match, which makes it `JsonElement`), otherwise
that of `additionalProperties`. Without `additionalProperties`, it is `JsonElement` if there are patterns (other than
`false`), and the name is left as it is (to an `allOf` sibling, say) if there are none. Such a property is required
and checked like any declared one, and it makes a class also of an object without `properties`:

```yaml
Thing:
  type: object
  required: [tenant]
  properties: { id: { type: string } }
  additionalProperties: { type: string }
```

```kotlin
ThingDto(id: Option<String>, tenant: String, additionalProperties: Map<String, String>)
```

With `additionalProperties: false`, a required name that no pattern allows is a spec error, and so is one that a
pattern with `false` matches.

## Bean Validation

`beanValidation = true` in a spec's `add {}` block annotates the model properties with the Jakarta Bean Validation
constraints of their schemas, and the Spring controller parameters too; request bodies get `@Valid`. Spring then
answers invalid requests with `400 Bad Request`. Generators without validation, such as `clientKtor`, ignore it. If the
models are generated in another module (`model { skipGeneration = true }`, see
[Shared models](../README.md#shared-models)), set `beanValidation` on the spec in both modules.

The annotations are those of Jakarta (`jakarta.validation:jakarta.validation-api`); the Spring server needs
`spring-boot-starter-validation`. Models in multiplatform common code use those of `de.quati.ogen.core.validation`
instead: on the JVM they are the Jakarta annotations, which then needs `jakarta.validation-api` at compile time; on
other platforms the compiler drops them. `de.quati.ogen:core` registers a value extractor, so constraints on optional
(`Option`) properties apply to their values.

They check `minLength`, `maxLength`, `pattern` and `format: email` of strings, `minimum`, `maximum`,
`exclusiveMinimum`, `exclusiveMaximum` and `multipleOf` of integers and numbers (also those mapped to `BigDecimal` or
`BigInteger`), `minItems`, `maxItems` and `uniqueItems` of arrays and `minProperties` and `maxProperties` of maps, and
cascade into generated objects. Jakarta has no constraints for `multipleOf` and `uniqueItems`, so these are
`@MultipleOf` and `@UniqueItems` of `de.quati.ogen.core.validation`, on every platform.

Constraints of the items of arrays and the values of maps go on the type arguments, e.g. `List<@Size(max = 3) String>`;
Hibernate Validator reads them from JVM type annotations, so with `beanValidation` the plugin adds
`-Xemit-jvm-type-annotations` to the Kotlin JVM compilations.

With `beanValidation`, `minProperties` and `maxProperties` of a class with a map are checked as the map's size when
every property is required, so that the number of keys is known: a class that only has required names from `required`
keeps the bounds of the map it was. With an optional property they are not checked, nor in a variant of a sealed
interface, whose class discriminator is a key only when the value is (de)serialized as the interface.
