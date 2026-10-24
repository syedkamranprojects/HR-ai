package dev.hrai.jobposting.util;

import java.util.Locale;
import java.util.regex.Pattern;

/** Turns free text into a short, filesystem/URL-friendly slug. */
public final class Slug {

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final int MAX_LENGTH = 40;

    private Slug() {
    }

    public static String of(String text) {
        String slug = NON_ALPHANUMERIC.matcher(text.toLowerCase(Locale.ROOT))
                .replaceAll("-")
                .replaceAll("^-+|-+$", "");
        if (slug.length() > MAX_LENGTH) {
            slug = slug.substring(0, MAX_LENGTH).replaceAll("-+$", "");
        }
        return slug.isEmpty() ? "posting" : slug;
    }
}
