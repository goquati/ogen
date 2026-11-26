# Plan: Eigener OpenAPI-Parser statt swagger-parser

Branch: `feature/own-openapi-parser`

## Ziel

`io.swagger.parser.v3:swagger-parser` und `org.openapitools:openapi-generator` aus `plugin/` entfernen, und damit auch Jackson, das nur
transitiv über diese beiden kommt.
Ersatz: ein eigener Parser auf Basis von kotlinx.serialization + snakeyaml-engine mit einem einheitlichen
Modell auf Basis von OAS 3.2. Generierter Code bleibt für alle bestehenden Specs byte-identisch.

## Ist-Zustand (was heute an swagger hängt)

| Stelle | Abhängigkeit |
|---|---|
| `model/config/SpecConfig.kt` | `OpenAPIParser().readLocation(..., isResolve = true)`, zweimal geparst (mit und ohne `inferSchemaType`) |
| `parsing/*.kt` (~680 Zeilen) | Mapping swagger-Modell → internes `Spec`-Modell |
| `validation/*.kt` (~900 Zeilen) | Alle Checks und `SpecWalker` laufen auf `io.swagger.v3.oas.models.OpenAPI` |
| `tasks/Validator.kt` | swagger-Parse-Messages als Fehler + `OpenApiEvaluator` aus openapi-generator für Warnungen/Empfehlungen |
| `SpecMergeService.kt` | `OpenAPIParser` zum Lesen der Pfade, Jackson (transitiv von swagger) zum Schreiben von YAML/JSON |
| `EndpointInfo.kt`, `codegen/generator/Util.kt` | `PathItem.HttpMethod`, `SecurityScheme.Type` (nur intern, öffentliche API ist nicht betroffen) |
| Tests | `ValidatorTestSupport.kt` (`parsedSpec`, `declaredTypesSpec`), ~15 Check-Tests auf swagger-Modell |

Implizites Verhalten von swagger-parser, das heute genutzt wird:

1. **Externe Refs werden nach `components` gezogen.** Codegen erkennt Schemas nur über
   `#/components/schemas/<Name>` (`RefString.Schema.schemaName = substringAfterLast("/")`). Der Merge-Modus
   (`tests/oas-dir`) erzeugt Refs wie `./domain/users.yaml#/paths/~1api~1public~1users`, und die Domain-Dateien
   verweisen auf `../common.yaml#/components/schemas/UserId`.
2. **Typ-Inferenz in 3.0** (`isInferSchemaType = true`): `type` wird aus `items`, `additionalProperties`,
   `properties` oder dem ersten Enum-Wert abgeleitet. Codegen nutzt die inferierte Variante, einige Checks
   die deklarierte.
3. **Strukturelle Fehlermeldungen** (`parseResult.messages`) landen direkt im Validator.

## Entscheidungen zu den offenen Punkten

### Bundler: wird gebraucht (nicht optional)
Wegen Punkt 1 oben. Der Bundler ist der Ersatz für swagger's `ExternalRefProcessor`:
- externe Schema-/Parameter-/Response-/RequestBody-/Header-/SecurityScheme-Ziele → `components/<typ>/<name>`
- Name = letztes Pointer-Segment (bzw. Dateiname, wenn das Ziel die ganze Datei ist);
  bei Kollision mit unterschiedlichem Inhalt Suffix wie bei swagger (`Name_1`), bei identischem Inhalt
  wiederverwenden. Ziel: dieselben Komponentennamen wie heute, sonst ändern sich generierte Klassennamen.
- PathItem-Refs (Merge-Modus) werden **inline** eingesetzt, nicht nach `components/pathItems` verschoben.
- Refs innerhalb eines verschobenen Fragments werden mit umgeschrieben (rekursiv, mit Zyklusschutz).

### Downgrade zum Zurückschreiben: wird nicht gebraucht
Das Plugin schreibt das OAS-Modell nie zurück. Der einzige Schreibpfad ist `SpecMergeService`, und der
schreibt eine rohe Map (Basis-Spec + `paths` mit `$ref`), kein migriertes Modell. → Kein Downgrade,
kein Encoder fürs Modell nötig (Encoder nur in Tests für Round-Trip).

### Migration 3.0 → 3.1 (konkret)
Läuft auf dem `JsonElement`-Baum, aber nur an **Schema-Positionen** (eigener Walker, der weiß, wo Schemas
stehen: `components.schemas`, `parameter.schema`, `header.schema`, `mediaType.schema`, `items`,
`properties.*`, `additionalProperties`, `allOf/anyOf/oneOf/not`, `prefixItems`, …).

| 3.0 | 3.1 | Hinweis |
|---|---|---|
| `nullable: true` + `type: X` | `type: [X, "null"]` | |
| `nullable: true` + `enum` | zusätzlich `null` in `enum` | nur wenn `enum` vorhanden |
| `nullable: true` ohne `type`/`$ref`/Komposition | entfernt | Diagnose → ersetzt `NullableWithoutTypeCheck` |
| `nullable: false` | entfernt | |
| `minimum: n` + `exclusiveMinimum: true` | `exclusiveMinimum: n` | `minimum` entfernen |
| `exclusiveMinimum: false` | entfernt | analog `exclusiveMaximum` |
| `$ref` mit Nachbar-Keywords | Nachbarn entfernt | 3.0 ignoriert sie; Diagnose als Warnung |
| `example` | bleibt | in 3.1 deprecated, aber gültig |
| `format: binary/byte` | bleibt | **nicht** zu `contentMediaType`/`contentEncoding` migrieren, Codegen hängt an `format` |
| `openapi: 3.0.x` | `3.1.0` | |

3.1 → 3.2: rein additiv, nur `openapi` hochsetzen. `sourceVersion` bleibt erhalten.

Folge für `Spec.Version` (8 Verwendungen): `isNullable()` liest nur noch `type`-Arrays. `Spec.Version` bleibt
für Diagnosen und Stellen, die echtes 3.0-Verhalten brauchen; jede Verwendung wird einzeln geprüft.

### Typ-Inferenz (3.0)
Nicht als Teil der Migration, sondern explizit in `SchemaParsing` (eine Funktion `effectiveType()`), nur
wenn `sourceVersion == 3.0`. Damit gibt es nur noch **ein** geparstes Modell; `declaredTypesSpec` entfällt,
Checks lesen das deklarierte `type`.

## Architektur

Neues Paket `de.quati.ogen.plugin.intern.oas`:

```
oas/
  model/        @Serializable data classes (3.2-basiert), RefOr<T>, Extensions, Schema
  serial/       RefOrSerializer, ExtensionsSerializer, SchemaSerializer (bekannte Keywords + Rest-Map),
                BoolOrSchema (für items/additionalProperties), StringOrList (type)
  read/         YamlReader (snakeyaml-engine → JsonElement), InputDetection, SourcePositions
  load/         ResourceLoader (File, Classpath, Http mit Allowlist), DocumentSet (Cache nach absoluter URI)
  ref/          JsonPointer (encode/decode ~0 ~1, %-Encoding), RefAbsolutizer ($id/$self), Resolver
  migrate/      SchemaPositionWalker, Migration30To31, Migration31To32
  bundle/       Bundler
  OasParser.kt  Fassade: path → ParseResult(openApi, sourceVersion, diagnostics)
  Diagnostic.kt Fehler/Warnungen mit Datei + JSON Pointer + Zeile/Spalte
```

Pipeline:
```
read root → Version erkennen → Lade-Phase (rekursiv alle Dateien) → Refs absolut machen
→ Migration (Root + Fragmente nach Zieltyp) → Bundler → decode in Modell → ParseResult
```
Fehler werden als `Diagnostic` gesammelt statt sofort geworfen, damit der Validator mehrere melden kann.

## Phasen

Jede Phase endet mit grünem Build. Swagger bleibt bis Phase 7 drin, damit alt und neu verglichen werden können.

### Phase 0 – Setup
- `kotlin("plugin.serialization")`, `kotlinx-serialization-json`, `snakeyaml-engine` in `plugin/gradle/libs.versions.toml`.
- Prüfen, dass `kotlinx-serialization-json` auf dem Gradle-Buildscript-Classpath nicht mit anderen Plugins
  kollidiert (Plugins teilen sich den Classloader). Version konservativ wählen; Shading nur falls nötig.
  **Erledigt:** `kotlinx-serialization-json` 1.9.0, weil Gradle Plugins mit seiner eingebetteten `kotlin-stdlib`
  lädt und erst 1.9.x mit stdlib 2.2 auskommt (passt zu `apiVersion 2.2`; ab 1.11 wird 2.3+ gebraucht, ab 1.12
  2.4+). Dazu `snakeyaml-engine` 3.2 und `json-schema-validator` 1.5.8 (nur Tests).
- Test-Dependency: JSON-Schema-Validator + offizielle OAS-Schemas (3.0, 3.1, 3.2) als Test-Resources.

### Phase 1 – Lesen
- `YamlReader`: snakeyaml-engine `LoadSettings` mit `allowDuplicateKeys = false`, YAML 1.2 Core Schema,
  direkt Node → `JsonElement`. Nicht-String-Keys → `toString()`. Anker/Aliase auflösen, Alias-Limit setzen.
- Zahlen: Ganzzahlen und Dezimalzahlen verlustfrei (`JsonPrimitive` aus dem Original-String), weil `multipleOf`,
  `minimum` usw. in `Constraints` landen.
- Positionen: Seitentabelle JSON Pointer → (Zeile, Spalte) aus den YAML-Marks, für Diagnosen.
- Eingabe-Erkennung: erstes Nicht-Whitespace-Zeichen `{` → JSON, sonst YAML (BOM beachten).
- Tests: Duplicate Keys, Anker, Nicht-String-Keys, JSON-Eingabe, `invalid-yaml.yaml`.
- **Erledigt** (`intern/oas/read`, `intern/oas/ref/JsonPointer.kt`, `intern/oas/Diagnostic.kt`). Abweichungen und Befunde:
  - JSON läuft ebenfalls über snakeyaml-engine (YAML 1.2 ist eine Obermenge von JSON). `Json.parseToJsonElement`
    behält bei doppelten Keys stillschweigend den letzten Wert; so sind doppelte Keys auch in JSON ein Fehler,
    und JSON-Specs bekommen Positionen. Die `{`-Erkennung bestimmt nur noch das gemeldete Format.
  - snakeyaml-engine expandiert Merge-Keys (`<<: *anchor`), obwohl sie nicht zu YAML 1.2 gehören. Das bleibt so.
  - Vergleichstest gegen Jackson über alle Specs im Repo (`TestSourceReaderCompat`, fällt in Phase 8 weg):
    identisch bis auf YAML-1.1-Booleans (`yes`/`no`/`on`/`off` ohne Quotes sind jetzt Strings). Betroffen sind
    genau die drei Test-Specs, die diesen Effekt prüfen → siehe Phase 7.

### Phase 1b – SpecMergeService ohne swagger und Jackson
Braucht nur den Reader aus Phase 1, nicht das Modell. Deshalb kommt die Phase früh, und Jackson
verschwindet schon hier aus dem Code.
- **Lesen (`readSpecData`):** statt `OpenAPIParser(... isResolve = true)` nur `YamlReader` auf die einzelne Datei,
  ohne Refs aufzulösen. Daraus `openapi` (String) und die Keys von `paths` lesen. Das ist günstiger als heute
  und reicht, weil der Merge selbst nur `$ref`s auf die Pfade schreibt.
  Gleiche Semantik wie heute: Dateien, die sich nicht lesen lassen, werden mit Warnung übersprungen.
  Dateien ohne `paths` (z. B. `common.yaml`) liefern keine Pfade, ihr `openapi` zählt aber für die Version mit.
  Vorher prüfen: Hat swagger heute Dateien ohne `openapi`-Key verworfen? Das Verhalten wird 1:1 übernommen.
- **Basis-Datei (`BaseConfig.File`):** `YamlReader` → `JsonObject` statt `ObjectMapper.readValue(Map)`.
- **`BaseConfig.Data.additional`** (`Map<String, Any?>` aus der Gradle-DSL): kleine Konvertierung
  `Any?` → `JsonElement` (Map, List, String, Number, Boolean, null; alles andere ist ein Fehler mit Key-Pfad).
- **Versionsvergleich:** `maxOrNull()` auf Strings ist heute lexikografisch (`"3.10"` < `"3.2"`).
  Bei der Gelegenheit semantisch vergleichen.
- **Schreiben:** Das Ergebnis als `JsonObject` mit Insertion-Order aufbauen.
  - JSON: `Json { prettyPrint = true }`. Prüfen, ob die Einrückung zum bisherigen Jackson-Output passt
    (Jackson ohne Pretty-Print schreibt eine Zeile, also das alte Verhalten übernehmen).
  - YAML: snakeyaml-engine `Dump` mit eigenem `Representer`, sodass die Ausgabe der bisherigen von Jackson
    entspricht. Die Merge-Dateien in `tests/oas-dir/*/merged.yaml` sind eingecheckt und dürfen keinen Diff bekommen:
    Keys ohne Quotes, String-Werte in `"…"`, kein `---`, Block-Style mit Einrückung 2, Sequenzen ohne
    zusätzliche Einrückung (`- jwt: []`), leere Collections im Flow-Style (`[]`, `{}`), Zahlen und Booleans ohne Quotes.
- **Tests:** Golden-Test, der beide `tests/oas-dir`-Merges erzeugt und byte-genau mit den eingecheckten
  `merged.yaml` vergleicht. Dazu Unit-Tests für JSON-Ausgabe, `additional`-Konvertierung, unlesbare Dateien
  und Versionsauswahl.
- Danach sind `com.fasterxml.jackson` und `OpenAPIParser` in `SpecMergeService.kt` weg.

### Phase 2 – Modell
- Alle OAS-3.2-Objekte als `@Serializable data class`, neuere Felder nullable.
  Inkl. 3.2-Neuerungen: `query`-Methode und `additionalOperations` am PathItem, `$self`, Tag `parent`/`kind`,
  `itemSchema` am MediaType, `discriminator.defaultMapping`.
- `RefOr<T>`: `sealed interface { Ref(ref, summary, description); Value(value: T) }`, Serializer prüft auf `$ref`.
- Extensions: Serializer pro Objekt trennt `x-*` in `extensions: Map<String, JsonElement>`.
  Unbekannte Nicht-`x-`-Keys → Diagnose (Fehler), nicht stillschweigend ignorieren.
- `Schema`: häufige Keywords als Felder (`ref`, `type: List<String>?`, `format`, `properties`, `items`,
  `additionalProperties: BoolOrSchema?`, `required`, `enum`, `const`, `allOf/anyOf/oneOf/not`, `discriminator`,
  alle Constraint-Keywords, `default`, `example(s)`, `readOnly/writeOnly`, `deprecated`, `title`, `description`)
  plus `other: Map<String, JsonElement>`. `true`/`false` als Schema erlauben (3.1).
- Tests: Round-Trip decode → encode == Eingabe (nach Migration) für alle Test-Resources;
  JSON-Schema-Validierung der Eingaben gegen das offizielle Meta-Schema.

### Phase 3 – Laden, Refs, Auflösen
- `ResourceLoader` (interface) mit `FileResourceLoader`; HTTP standardmäßig aus, optional per Allowlist.
- Lade-Phase: BFS über alle `$ref`-Ziele, Cache nach normalisierter absoluter URI, Zyklusschutz.
- `RefAbsolutizer`: jeden `$ref` gegen die Basis-URI der Datei auflösen (`$self` am Root, `$id` in Schemas).
- `Resolver`: `resolve(ref, expectedType)` mit korrekt decodiertem JSON Pointer; kein Vorab-Dereferenzieren.
- Tests: relative Pfade (`../common.yaml`), Pointer mit `~0`/`~1`/`%20`, zirkuläre Schemas, Datei-Zyklen,
  fehlende Datei/fehlender Pointer → Diagnose mit Ort.

### Phase 4 – Migration
- `SchemaPositionWalker` auf `JsonElement`.
- `Migration30To31` und `Migration31To32` gemäß Tabelle oben; Fragmente aus externen Dateien werden beim
  Auflösen nach Zieltyp migriert (genau einmal pro URI + Typ).
- Tests pro Regel, plus: Ergebnis validiert gegen das 3.1-/3.2-Meta-Schema.

### Phase 5 – Bundler + Fassade
- Bundler gemäß Entscheidung oben; `OasParser` setzt die Pipeline zusammen.
- Vergleichstest: für alle Specs in `plugin/src/test/resources` und `tests/` die Komponentennamen von
  swagger-parser und neuem Parser vergleichen.

### Phase 6 – Umstellung von `parsing/`
- `parsing/*.kt` auf das neue Modell umschreiben; `ParserContext`/`SchemaLocation` bleiben.
- Eigene Enums `HttpMethod` und `SecuritySchemeType` statt swagger-Typen (`EndpointInfo.Method.of`, `Util.kt`).
- Typ-Inferenz für 3.0 in `SchemaParsing`.
- **Golden-Test:** internes `Spec` (data classes) aus altem und neuem Pfad für alle Specs vergleichen —
  muss identisch sein. Danach `tests/`-Build: generierter Code ohne Diff.
- Umschalten in `SpecConfig.toCodeGenContext()`.

### Phase 7 – Validator
- `SpecWalker` und alle Checks auf das neue Modell portieren. Tests bleiben inhaltlich gleich,
  `ValidatorTestSupport` liefert das neue Modell; `declaredTypesSpec` entfällt.
- `NullableWithoutTypeCheck` wird Migrations-Diagnose (Meldungstext bleibt).
- `EnumValueCheck`: Die Hinweise auf YAML 1.1 (`YAML_BOOLEAN_HINT`, Teile von `BOOLEAN_CAST_HINT`) entfallen,
  weil der Reader YAML 1.2 liest. `enum-values-yaml-booleans*.yaml` und `enum-values-boolean-cast.yaml` samt
  Tests anpassen. Verhaltensänderung für Nutzer: `enum: [yes, no]` ohne Quotes ist jetzt eine String-Enum.
- swagger-Messages → eigene `Diagnostic`s (YAML-, Struktur-, Ref-Fehler).
- `OpenApiEvaluator` ersetzen: die genutzten Regeln selbst als Checks (siehe offene Frage 1).

### Phase 8 – Aufräumen
- swagger-parser und openapi-generator aus `build.gradle.kts`/`libs.versions.toml` entfernen;
  `grep -r "io.swagger\|openapitools\|jackson"` muss leer sein.
- README: unterstützte Versionen 3.0/3.1/3.2 erwähnen.

## Teststrategie (übergreifend)
- Unit-Tests pro Baustein (Phasen 1–5).
- Golden-Test altes vs. neues `Spec` (Phase 6) — der wichtigste Sicherheitsanker, wird nach Phase 8 gelöscht.
- `tests/`-Composite-Build: generierter Code vorher/nachher ohne Diff.
- Bestehende Validator-Tests müssen unverändert grün bleiben (bis auf Meldungen, die aus swagger stammen).

## Weitere Entscheidungen

1. **openapi-generator-Regeln:** Nur „unbenutzte Schemas“ (Empfehlung) und „`properties` neben
   `oneOf`/`anyOf`“ werden portiert. Der Rest entfällt, weil ogen's eigene Checks ihn abdecken.
2. **Meldungstexte:** Meldungen, die heute direkt von swagger-parser kommen, dürfen sich ändern.
   Sie werden durch eigene Texte mit Datei:Zeile ersetzt. Tests, die solche Texte prüfen, werden angepasst.
3. **Unbekannte Keys:** Unbekannte Felder ohne `x-` in OAS-Objekten sind ein Fehler, wie heute.
4. **SpecMergeService** wird mit umgestellt (Phase 1b), damit am Ende weder swagger noch Jackson übrig bleiben.
