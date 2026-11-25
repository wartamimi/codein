package javax.swing;

/**
 * Minimal stand-in for Swing's {@code Icon}, which Android does not have.
 *
 * <p>The IntelliJ core inside the Kotlin compiler declares methods that return {@code Icon} and
 * its subtype {@code RowIcon}. Android 11 and older reject such a class at verification time when
 * {@code Icon} cannot be resolved ("can't resolve returned type"), which breaks every Kotlin
 * build there; newer Android versions only defer the check. Providing the interface lets
 * {@code RowIcon} resolve, so the classes verify. The compiler never paints icons, so the
 * painting method of the real interface is not needed.
 */
public interface Icon {
    int getIconWidth();

    int getIconHeight();
}
