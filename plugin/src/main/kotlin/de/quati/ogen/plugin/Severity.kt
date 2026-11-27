package de.quati.ogen.plugin

/** What the validator does with what a rule finds. */
public enum class Severity {
    /** The rule doesn't run. */
    Off,

    /** Reported; fails the build only with `failOnWarnings`. */
    Warning,

    /** Reported and fails the build. */
    Error,
}
