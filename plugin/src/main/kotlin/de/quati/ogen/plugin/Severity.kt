package de.quati.ogen.plugin

/** What the validator does with what a rule finds. */
public enum class Severity {
    /** The rule doesn't run. */
    Off,

    /** Reported; doesn't fail the build. */
    Warning,

    /** Reported and fails the build. */
    Error,
}
