package org.example.axelnyman.main;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Custom Hamcrest matchers for testing timestamp comparisons.
 *
 * These matchers handle precision differences between Java LocalDateTime (nanosecond precision)
 * and PostgreSQL timestamps (microsecond precision).
 */
public class TestDateTimeMatchers {

    /**
     * Matches timestamp strings while ignoring nanosecond precision differences.
     *
     * This matcher is needed because:
     * - Java LocalDateTime supports nanosecond precision (9 decimal places)
     * - PostgreSQL TIMESTAMP stores microsecond precision (6 decimal places)
     * - PostgreSQL may round nanoseconds to nearest microsecond, while Java truncates
     * - Different environments (local vs CI) may have different rounding behavior
     *
     * The matcher allows timestamps to differ by up to 2 microseconds to account for
     * rounding differences between Java truncation and PostgreSQL rounding.
     *
     * Example usage:
     * <pre>
     * .andExpect(jsonPath("$.createdAt", matchesTimestampIgnoringNanos(originalCreatedAt)));
     * </pre>
     *
     * @param expected The expected timestamp string in ISO-8601 format
     * @return A Hamcrest matcher that compares timestamps with microsecond tolerance
     */
    public static Matcher<String> matchesTimestampIgnoringNanos(String expected) {
        return new TypeSafeMatcher<String>() {
            private static final long TOLERANCE_MICROS = 2L;
            private LocalDateTime expectedDateTime;
            private LocalDateTime actualDateTime;
            private long microsecondsDifference;

            @Override
            protected boolean matchesSafely(String actual) {
                try {
                    expectedDateTime = LocalDateTime.parse(expected);
                    actualDateTime = LocalDateTime.parse(actual);

                    // Truncate both to microseconds to match PostgreSQL precision
                    LocalDateTime expectedTruncated = expectedDateTime.truncatedTo(ChronoUnit.MICROS);
                    LocalDateTime actualTruncated = actualDateTime.truncatedTo(ChronoUnit.MICROS);

                    // Calculate absolute difference in microseconds
                    microsecondsDifference = Math.abs(ChronoUnit.MICROS.between(expectedTruncated, actualTruncated));

                    // Allow tolerance of 2 microseconds to account for rounding differences
                    return microsecondsDifference <= TOLERANCE_MICROS;
                } catch (Exception e) {
                    return false;
                }
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("timestamp matching ")
                           .appendValue(expected)
                           .appendText(" (within ")
                           .appendValue(TOLERANCE_MICROS)
                           .appendText(" microseconds, ignoring nanoseconds)");
            }

            @Override
            protected void describeMismatchSafely(String actual, Description mismatchDescription) {
                mismatchDescription.appendText("was ")
                                   .appendValue(actual);
                if (expectedDateTime != null && actualDateTime != null) {
                    LocalDateTime expectedTruncated = expectedDateTime.truncatedTo(ChronoUnit.MICROS);
                    LocalDateTime actualTruncated = actualDateTime.truncatedTo(ChronoUnit.MICROS);

                    mismatchDescription.appendText(" (expected: ")
                                       .appendValue(expectedTruncated)
                                       .appendText(", actual: ")
                                       .appendValue(actualTruncated)
                                       .appendText(", difference: ")
                                       .appendValue(microsecondsDifference)
                                       .appendText(" microseconds)");
                }
            }
        };
    }
}
