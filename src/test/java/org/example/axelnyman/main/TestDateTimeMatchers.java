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
     * - When entities are persisted and retrieved, nanoseconds are truncated
     * - Different environments (local vs CI) may return different precision from LocalDateTime.now()
     *
     * Example usage:
     * <pre>
     * .andExpect(jsonPath("$.createdAt", matchesTimestampIgnoringNanos(originalCreatedAt)));
     * </pre>
     *
     * @param expected The expected timestamp string in ISO-8601 format
     * @return A Hamcrest matcher that compares timestamps with microsecond precision
     */
    public static Matcher<String> matchesTimestampIgnoringNanos(String expected) {
        return new TypeSafeMatcher<String>() {
            private LocalDateTime expectedDateTime;
            private LocalDateTime actualDateTime;

            @Override
            protected boolean matchesSafely(String actual) {
                try {
                    expectedDateTime = LocalDateTime.parse(expected);
                    actualDateTime = LocalDateTime.parse(actual);

                    // Truncate both to microseconds to match PostgreSQL precision
                    LocalDateTime expectedTruncated = expectedDateTime.truncatedTo(ChronoUnit.MICROS);
                    LocalDateTime actualTruncated = actualDateTime.truncatedTo(ChronoUnit.MICROS);

                    return expectedTruncated.equals(actualTruncated);
                } catch (Exception e) {
                    return false;
                }
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("timestamp matching ")
                           .appendValue(expected)
                           .appendText(" (ignoring nanoseconds beyond microsecond precision)");
            }

            @Override
            protected void describeMismatchSafely(String actual, Description mismatchDescription) {
                mismatchDescription.appendText("was ")
                                   .appendValue(actual);
                if (expectedDateTime != null && actualDateTime != null) {
                    LocalDateTime expectedTruncated = expectedDateTime.truncatedTo(ChronoUnit.MICROS);
                    LocalDateTime actualTruncated = actualDateTime.truncatedTo(ChronoUnit.MICROS);

                    if (!expectedTruncated.equals(actualTruncated)) {
                        mismatchDescription.appendText(" (expected with microsecond precision: ")
                                           .appendValue(expectedTruncated)
                                           .appendText(", actual with microsecond precision: ")
                                           .appendValue(actualTruncated)
                                           .appendText(")");
                    }
                }
            }
        };
    }
}
