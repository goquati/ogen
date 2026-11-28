# Validator

ogen validates every spec before it generates code. The validator reports problems with file and line; the parser's
errors and the rules that are errors by default fail the build. The `ogenValidate` task runs the validation without
generating code.

## Configuration

The `validator` block of a spec sets its naming conventions and the severities of the rules:

```kotlin
ogen {
    add(packageName = "com.example.api.gen") {
        specFile("$projectDir/specs/api.yaml")
        validator {
            naming {
                schema = NameConvention.PascalCase
                property = NameConvention.CamelCase
            }
            // Fail the build on any finding, except unused schemas
            rules(RuleSet.Strict) {
                unusedSchemas = Severity.Off
            }
        }
        model {}
    }
}
```

### Validation by default

Every spec is validated, also without a `validator` block. `validator { enabled = false }` turns validation off for
one spec, or in `ogen {}` for every spec that doesn't turn it on again.

### Settings for every spec

A `validator` block in `ogen {}` sets the validator settings of every spec, also those without a `validator` block of
their own. A spec's `validator` block starts from these settings and changes only what it sets, also inside
`naming {}`, `parameter {}` and `rules {}`; without a `validator` block in `ogen {}`, it starts from the defaults.

```kotlin
ogen {
    validator {                                             // applies to every spec below
        naming { tag = NameConvention.PascalCase }
        rules { ambiguousPaths = Severity.Error }
    }
    add(packageName = "com.example.portal") {
        specFile("$rootDir/spec/portal.yaml")               // validated with the settings above
        model {}
    }
    add(packageName = "com.example.thirdparty") {
        specFile("$projectDir/oas/thirdparty.yaml")
        validator { naming { tag = NameConvention.Any } }   // overrides only what it sets
        model {}
    }
    add(packageName = "com.example.legacy") {
        specFile("$projectDir/oas/legacy.yaml")
        validator { enabled = false }                       // not validated
        model {}
    }
}
```

## Naming conventions

`naming {}` sets the convention of each kind of name. How a violation is reported is the severity of the rule
`namingConventions`.

| Setting | Checks | Default |
|---|---|---|
| `schema` | schema names | `PascalCase` |
| `property` | property names | `CamelCase` |
| `tag` | tags | `CamelCase` |
| `operationId` | operation IDs | `CamelCase` |
| `pathSegment` | the names in paths | `KebabCase` |
| `pathExtension` | the extensions in paths | `KebabCase` |
| `enumValue` | the values of string enums | `Any` |
| `parameter { path }` | path parameters | `CamelCase` |
| `parameter { query }` | query parameters | `CamelCase` |
| `parameter { header }` | header parameters | `Any` |
| `parameter { cookie }` | cookie parameters | `Any` |

The conventions apply to every schema and parameter the generator sees, inline request and response schemas,
composition members and path-level parameters included.

Besides the predefined conventions, `NameConvention.RegexFormat("...")` and `NameConvention.Custom { name -> ... }`
define custom ones. `CamelCase`, `PascalCase`, `KebabCase` and `RegexFormat` take a block:

- `except(...)` lists exact names the convention accepts as they are (e.g. a third-party header).
- `acronyms` (on `CamelCase` and `PascalCase`) says whether a run of capitals counts as one word (`true`, the default:
  `UUID`, `HTTPError`, `apiID`), or every word is one capital followed by lower-case letters and digits, with a single
  capital allowed only as the last word, as in `PointX` (`false`).

```kotlin
naming {
    schema = NameConvention.PascalCase { acronyms = false }
    parameter {
        header = NameConvention.RegexFormat("^[A-Z][A-Za-z0-9]*(?:-[A-Z][A-Za-z0-9]*)*$") {
            except("X-HubSpot-Signature-v3")
        }
    }
}
```

### Names in paths

A path segment splits at `:`. In each piece, leading dots are dropped, the text up to the next `.` is a name
(`pathSegment`) and each part after it an extension (`pathExtension`): `{id}.json:download` checks the name
`download` and the extension `json`, and `.well-known` the name `well-known`.

A part matches if it does as written, or with every variable replaced by `xx`, or by `Xx`, since the variable's value
is unknown: `by{field}Name` in camelCase, `{Id}Report` in PascalCase, but also `report{year}` in kebab-case. A part
that is only variables is not checked. `except(...)` on these two settings names one part (`Legacy_API`), not a whole
segment. To turn off the checks on paths, set both to `Any`.

## Rules

`rules {}` sets each rule's severity: `Severity.Error` fails the build, `Severity.Warning` is only reported,
`Severity.Off` doesn't run the check. There is one property per rule (see the table below), or
`this[ValidatorRule.X] = …`.

`rules(set) { … }` first sets the severities of a rule set, a `Map<ValidatorRule, Severity>`, then those of the block:

- `RuleSet.Default`: every rule with its default severity. Without a set, every rule has its default; in a spec's
  `validator` block, `rules(RuleSet.Default)` sets the defaults again for that spec, over what `ogen {}` sets.
- `RuleSet.Strict`: every rule an error, also those that are off by default such as `operationIdRequired`, so any
  finding fails the build.
- `RuleSet.Lenient`: allows as much as possible, e.g. for specs of third parties. What makes a spec invalid stays an
  error, other problems stay warnings, recommendations (naming conventions among them) are off.

The parser's errors always fail the build. On a spec with parser errors, the rules that are errors by default,
`unusedSchemas`, `invalidExamples` and `namingConventions` don't run, whatever their severity.

### Errors by default

| Rule | Reports |
|---|---|
| `versionRequirements` | fields the spec's version requires: `paths`, `responses` and `items` in 3.0, a response's `description` |
| `brokenPathTemplates` | path templates with an empty `{}`, an unclosed `{` or a stray `}` |
| `queryOrFragmentInPaths` | a query string or fragment in a path key |
| `pathParameters` | path parameters missing from the path template, and template variables without a parameter |
| `identicalPaths` | paths that differ only in their variables' names |
| `undefinedSecuritySchemes` | security requirements naming a scheme `components.securitySchemes` doesn't define |
| `enumValues` | enum values that don't match their schema's type or are listed twice |
| `invalidDefaults` | a `default` its schema doesn't allow (of another type, outside its `enum`, below its `minimum`, …) |
| `invalidTypes` | a `type` that isn't a JSON Schema type |
| `contradictoryConstraints` | contradictory constraints such as `minimum` above `maximum` |
| `undefinedRequiredProperties` | `required` names without a matching property |
| `compositionTypes` | compositions no value can match: `allOf` members whose types exclude each other or the schema's own type, or a type that excludes every `oneOf` or `anyOf` member |

### Warnings by default

| Rule | Reports |
|---|---|
| `trailingSlashes` | paths with a trailing slash |
| `missingSuccessResponses` | operations without a 2xx or 3xx response |
| `undeclaredTags` | operation tags missing from the top-level `tags`, when it declares any |
| `invalidPatterns` | `pattern` regexes and `patternProperties` keys that don't compile as Java regexes (OpenAPI's are JavaScript's, which differ slightly) |
| `ambiguousPaths` | two paths that share a method where neither is more concrete (`/{entity}/me` and `/books/{id}`) |
| `redundantBounds` | in 3.1 and later, `maximum` next to `exclusiveMaximum` or `minimum` next to `exclusiveMinimum`, where only the tighter bound has an effect |
| `keywordsForOtherTypes` | keywords that apply to none of the schema's declared types, such as `maxLength` on an integer |
| `nullableWithoutType` | `nullable` without a `type` in OpenAPI 3.0 |
| `unusedSchemas` | component schemas no operation uses, see [Unused schemas](#unused-schemas) |
| `sameKotlinNames` | property names of one schema with the same Kotlin name (`xTenant` and `x-tenant`), which get distinct names, see [Property names](models.md#property-names) |
| `invalidExamples` | examples their schema doesn't allow, see [Defaults and examples](#defaults-and-examples) |

### Recommendations

Warnings by default; `RuleSet.Lenient` turns them off.

| Rule | Reports |
|---|---|
| `nullableIn31` | `nullable` in OpenAPI 3.1 and later, which has no effect there |
| `deprecatedKeywords` | schema keywords the spec's version deprecates: `example` since 3.1 (use `examples`), the XML Object's `attribute` and `wrapped` since 3.2 (use `nodeType`) |
| `ignoredNextToRef` | keywords next to a `$ref` that are ignored |
| `exclusiveFlagWithoutBound` | `exclusiveMinimum: true` without `minimum`, or `exclusiveMaximum: true` without `maximum`, in 3.0 |
| `unsupportedOperations` | operations ogen generates no code for (3.2's `additionalOperations`) |
| `oneOfWithProperties` | `properties` next to `oneOf` |
| `getWithBody` | a request body on GET or HEAD |
| `headerUnderscores` | header names with `_` |
| `namingConventions` | names that don't match the [naming conventions](#naming-conventions) |

### Off by default

| Rule | Reports |
|---|---|
| `operationIdRequired` | operations under `paths` without an `operationId`, for projects that choose every generated method name in the spec instead of letting ogen derive it |

## Unused schemas

A component schema that no operation uses on purpose, such as an enum the code uses directly, is kept from the
`unusedSchemas` warning by `x-ogen-keep: true` (or `"true"`) on the schema; the schemas it refers to count as used,
too. Specs that are only generated as models, often with no operation at all, get a warning for every schema; such
projects set `rules { unusedSchemas = Severity.Off }`.

## Defaults and examples

`invalidDefaults` checks the `default` of schemas. `invalidExamples` checks the `example` and `examples` of schemas,
parameters, headers and JSON media types (`json` and `+json` subtypes such as `application/problem+json`, and
`components.mediaTypes`). Examples of other media types, such as XML or the wildcard `*/*`, are often the serialized
text and not checked, nor is an Example Object's `externalValue`.

Defaults and examples are checked as JSON Schema does, except that:

- in OpenAPI 3.0, a nullable `enum` allows `null`;
- a missing required property that is `readOnly` or `writeOnly` is no finding;
- a `discriminator` (and 3.2's `defaultMapping`) picks the `oneOf` or `anyOf` alternative;
- formats are checked only for examples, and only `date`, `date-time`, `time`, `uuid`, `email`, `ipv4`, `int32` and
  `int64`;
- `unevaluatedProperties` follows the rules in [Objects and `additionalProperties`](models.md#unevaluatedproperties).

Unquoted values are read as YAML 1.1, as swagger-parser did: `no` is `false`, `12:30` is `750` and `010` is `8`; quote
such values to keep them as written.
