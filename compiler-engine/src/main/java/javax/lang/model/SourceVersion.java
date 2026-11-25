package javax.lang.model;

/**
 * Minimal stand-in for {@code javax.lang.model.SourceVersion}, which Android does not ship.
 *
 * <p>ECJ's batch {@code FileSystem} probes {@code SourceVersion.valueOf("RELEASE_12")} in a static
 * initializer to detect a Java 12+ host and only handles {@link IllegalArgumentException}. Without
 * this class the probe fails with {@link NoClassDefFoundError} and the compiler cannot start.
 * Declaring the constants up to {@code RELEASE_8} makes the probe report a pre-12 host, which is
 * correct for ART. Annotation processing (the only other user) is disabled with {@code -proc:none}.
 */
public enum SourceVersion {
    RELEASE_0,
    RELEASE_1,
    RELEASE_2,
    RELEASE_3,
    RELEASE_4,
    RELEASE_5,
    RELEASE_6,
    RELEASE_7,
    RELEASE_8;

    public static SourceVersion latest() {
        return RELEASE_8;
    }

    public static SourceVersion latestSupported() {
        return RELEASE_8;
    }

    public static boolean isIdentifier(CharSequence name) {
        if (name == null || name.length() == 0) return false;
        String s = name.toString();
        if (!Character.isJavaIdentifierStart(s.codePointAt(0))) return false;
        for (int i = Character.charCount(s.codePointAt(0)); i < s.length(); ) {
            int cp = s.codePointAt(i);
            if (!Character.isJavaIdentifierPart(cp)) return false;
            i += Character.charCount(cp);
        }
        return true;
    }
}
