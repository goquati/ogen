package de.quati.ogen.core.validation;

import de.quati.kotlin.util.Option;
import jakarta.validation.valueextraction.ExtractedValue;
import jakarta.validation.valueextraction.UnwrapByDefault;
import jakarta.validation.valueextraction.ValueExtractor;

/**
 * Lets Jakarta Bean Validation check the value of an {@link Option}: constraints on an {@code Option} property apply
 * to the value of {@link Option.Some}, and {@link Option.Undefined} passes them all, like {@code null}.
 * Registered in {@code META-INF/services}, so Hibernate Validator finds it on the classpath.
 * <p>
 * Written in Java, as Kotlin cannot annotate the unbounded wildcard that Hibernate Validator requires.
 */
@UnwrapByDefault
public final class OptionValueExtractor implements ValueExtractor<Option<@ExtractedValue ?>> {
    @Override
    public void extractValues(Option<?> originalValue, ValueReceiver receiver) {
        receiver.value(null, originalValue instanceof Option.Some<?> some ? some.getValue() : null);
    }
}
