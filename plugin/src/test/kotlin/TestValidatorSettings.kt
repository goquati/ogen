import de.quati.ogen.plugin.NameConvention
import de.quati.ogen.plugin.RuleSet
import de.quati.ogen.plugin.Severity
import de.quati.ogen.plugin.ValidatorRule
import de.quati.ogen.plugin.SpecsConfigBuilder
import de.quati.ogen.plugin.intern.model.config.ValidatorConfig
import de.quati.ogen.plugin.intern.tasks.Validator
import org.gradle.api.GradleException
import org.slf4j.helpers.NOPLogger
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** `validator {}` in `ogen {}`, which every spec inherits and can override, and `rules {}`. */
class TestValidatorSettings {
    private val specPath = Validator::class.java.getResource("/validator-several-errors.yaml")!!.path

    private fun SpecsConfigBuilder.spec(block: SpecsConfigBuilder.SpecConfingBuilder.() -> Unit = {}) =
        add(packageName = "de.quati.ogen.test.gen") {
            specFile(specPath)
            model {}
            block()
        }

    /** The validator settings of each spec, as the validator gets them. */
    private fun resolved(block: SpecsConfigBuilder.() -> Unit): List<ValidatorConfig?> = SpecsConfigBuilder().apply {
        utilPackageName("de.quati.ogen.test.gen.util")
        block()
    }.build().specs.map { it.validatorConfig }

    private fun SpecsConfigBuilder.SpecConfingBuilder.ValidatorConfig.acceptAnyNames() {
        naming {
            tag = NameConvention.Any
            operationId = NameConvention.Any
            property = NameConvention.Any
            schema = NameConvention.Any
            pathSegment = NameConvention.Any
            parameter {
                path = NameConvention.Any
                query = NameConvention.Any
            }
        }
    }

    @Test
    fun `without any validator block every spec is validated with the defaults`() {
        val config = assertNotNull(resolved { spec() }.single())
        assertEquals(NameConvention.PascalCase, config.naming.schema)
        assertEquals(emptyMap(), config.rules)
        assertEquals(Severity.Warning, config.severity(ValidatorRule.NamingConventions))
        assertEquals(Severity.Off, config.severity(ValidatorRule.OperationIdRequired))
    }

    @Test
    fun `a spec's own block alone validates it with the defaults for the rest`() {
        val config = assertNotNull(resolved { spec { validator { naming { tag = NameConvention.PascalCase } } } }.single())
        assertEquals(NameConvention.PascalCase, config.naming.tag)
        assertEquals(NameConvention.PascalCase, config.naming.schema)
        assertEquals(NameConvention.CamelCase, config.naming.parameter.path)
    }

    @Test
    fun `the block in ogen validates every spec, also one declared before it`() {
        val configs = resolved {
            spec()
            validator { naming { tag = NameConvention.PascalCase } }
            spec()
        }
        assertEquals(listOf(NameConvention.PascalCase, NameConvention.PascalCase), configs.map { it?.naming?.tag })
    }

    @Test
    fun `a spec's block changes only what it sets, also inside naming and parameter`() {
        val config = assertNotNull(
            resolved {
                validator {
                    naming {
                        tag = NameConvention.PascalCase
                        enumValue = NameConvention.KebabCase
                        parameter {
                            path = NameConvention.KebabCase
                            query = NameConvention.KebabCase
                        }
                    }
                }
                spec {
                    validator {
                        naming {
                            tag = NameConvention.Any
                            parameter { query = NameConvention.Any }
                        }
                    }
                }
            }.single()
        )
        assertEquals(NameConvention.Any, config.naming.tag)
        assertEquals(NameConvention.KebabCase, config.naming.enumValue)
        assertEquals(NameConvention.CamelCase, config.naming.operationId)
        assertEquals(NameConvention.KebabCase, config.naming.parameter.path)
        assertEquals(NameConvention.Any, config.naming.parameter.query)
    }

    @Test
    fun `enabled = false turns validation off for one spec`() {
        val configs = resolved {
            validator { naming { tag = NameConvention.PascalCase } }
            spec { validator { enabled = false } }
            spec()
        }
        assertNull(configs[0])
        assertNotNull(configs[1])
    }

    @Test
    fun `enabled = false in ogen turns validation off unless a spec turns it on`() {
        val configs = resolved {
            validator { enabled = false }
            spec()
            spec { validator { enabled = true } }
        }
        assertNull(configs[0])
        assertNotNull(configs[1])
    }

    @Test
    fun `rules are inherited and overridden rule by rule`() {
        val config = assertNotNull(
            resolved {
                validator {
                    rules {
                        unusedSchemas = Severity.Off
                        ambiguousPaths = Severity.Error
                    }
                }
                spec { validator { rules { ambiguousPaths = Severity.Warning } } }
            }.single()
        )
        assertEquals(Severity.Off, config.severity(ValidatorRule.UnusedSchemas))
        assertEquals(Severity.Warning, config.severity(ValidatorRule.AmbiguousPaths))
        assertEquals(Severity.Error, config.severity(ValidatorRule.EnumValues))
    }

    @Test
    fun `a rule set applies first, then the rules the block sets`() {
        val config = assertNotNull(
            resolved {
                validator { rules { getWithBody = Severity.Error } }
                spec { validator { rules(RuleSet.Lenient) { unusedSchemas = Severity.Off } } }
            }.single()
        )
        assertEquals(Severity.Off, config.severity(ValidatorRule.GetWithBody))
        assertEquals(Severity.Off, config.severity(ValidatorRule.HeaderUnderscores))
        assertEquals(Severity.Off, config.severity(ValidatorRule.NamingConventions))
        assertEquals(Severity.Off, config.severity(ValidatorRule.UnusedSchemas))
        assertEquals(Severity.Warning, config.severity(ValidatorRule.TrailingSlashes))
        assertEquals(Severity.Error, config.severity(ValidatorRule.EnumValues))
    }

    @Test
    fun `rules can be set by enum, too`() {
        val config = assertNotNull(
            resolved { validator { rules { this[ValidatorRule.UnusedSchemas] = Severity.Error } }; spec() }.single()
        )
        assertEquals(Severity.Error, config.severity(ValidatorRule.UnusedSchemas))
    }

    @Test
    fun `the predefined sets`() {
        assertEquals(ValidatorRule.entries.toSet(), RuleSet.Default.keys)
        assertEquals(setOf(Severity.Error), RuleSet.Strict.values.toSet())
        assertEquals(ValidatorRule.entries.toSet(), RuleSet.Lenient.keys)
        assertEquals(Severity.Error, RuleSet.Lenient[ValidatorRule.EnumValues])
        assertEquals(Severity.Warning, RuleSet.Lenient[ValidatorRule.UnusedSchemas])
        assertEquals(Severity.Off, RuleSet.Lenient[ValidatorRule.NamingConventions])
        assertEquals(Severity.Off, RuleSet.Lenient[ValidatorRule.OperationIdRequired])
    }

    // validator-several-errors has three errors (path parameter, enum value, required property) and a trailing slash.
    @Test
    fun `an error rule turned off or to a warning doesn't fail the build`() {
        val spec = "validator-several-errors"
        assertValidationFails(spec, errorsMessage(spec, 2)) { rules { enumValues = Severity.Off } }
        validate(spec) {
            rules {
                enumValues = Severity.Warning
                pathParameters = Severity.Off
                undefinedRequiredProperties = Severity.Warning
            }
        }
        assertValidationFails(spec, errorsMessage(spec, 1)) {
            rules(RuleSet.Strict) {
                enumValues = Severity.Off
                pathParameters = Severity.Off
                undefinedRequiredProperties = Severity.Off
            }
        }
    }

    @Test
    fun `a warning rule turned to an error fails the build`() {
        val spec = "validator-several-errors"
        assertValidationFails(spec, errorsMessage(spec, 4)) { rules { trailingSlashes = Severity.Error } }
    }

    @Test
    fun `naming conventions are a rule, too`() {
        val spec = "validator-several-errors"
        assertValidationFails(spec, errorsMessage(spec, 3)) {
            naming { schema = NameConvention.CamelCase }
            rules { namingConventions = Severity.Off }
        }
    }

    // The tasks share the parse result of each spec, so it must be the same instance on every build().
    @Test
    fun `build returns the same spec instances with the settings of ogen declared later`() {
        val builder = SpecsConfigBuilder().apply {
            utilPackageName("de.quati.ogen.test.gen.util")
            spec()
        }
        val first = builder.build().specs.single()
        builder.validator { naming { tag = NameConvention.PascalCase } }
        val second = builder.build().specs.single()
        assertSame(first, second)
        assertSame(first.oasParseResult, second.oasParseResult)
        assertEquals(NameConvention.PascalCase, second.validatorConfig?.naming?.tag)
    }

    // The spec's only path refers to a schema that doesn't exist, so every schema would look unused.
    @Test
    fun `checks that need a valid spec don't run on one with parser errors, whatever their severity`() {
        val file = createTempFile(suffix = ".yaml").apply {
            writeText(
                """
                openapi: 3.1.0
                info: {title: t, version: '1'}
                paths:
                  /pets:
                    get:
                      responses:
                        '200':
                          description: ok
                          content:
                            application/json:
                              schema: {${'$'}ref: '#/components/schemas/Missing'}
                components:
                  schemas:
                    Unused: {type: object}
                """.trimIndent()
            )
        }
        try {
            val configs = SpecsConfigBuilder().apply {
                utilPackageName("de.quati.ogen.test.gen.util")
                add(packageName = "de.quati.ogen.test.gen") {
                    specFile(file.toString())
                    model {}
                    validator { rules { unusedSchemas = Severity.Error } }
                }
            }.build()
            val logger = CapturingLogger()
            val e = assertFailsWith<GradleException> { Validator(logger).validate(configs) }
            assertTrue(e.message!!.endsWith("failed."), e.message)
            assertTrue(logger.entries.any { "cannot resolve" in it.second }, logger.entries.toString())
            assertTrue(logger.entries.none { "is not used by any operation" in it.second }, logger.entries.toString())
        } finally {
            file.deleteIfExists()
        }
    }

    @Test
    fun `a spec's rules override a set of ogen`() {
        val configs = SpecsConfigBuilder().apply {
            utilPackageName("de.quati.ogen.test.gen.util")
            validator {
                acceptAnyNames()
                rules(RuleSet.Strict) {
                    enumValues = Severity.Off
                    pathParameters = Severity.Off
                    undefinedRequiredProperties = Severity.Off
                }
            }
            spec { validator { rules { trailingSlashes = Severity.Warning } } }
        }.build()
        Validator(NOPLogger.NOP_LOGGER).validate(configs)
    }
}
