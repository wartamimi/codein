package com.codein.ide.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Raw brand palette for Codein Black: a pure black ground, near-white type, one grey surface for
 * inset cards and 1dp #1D1D1D hairlines. The UI is monochrome; hue appears only for status (success,
 * warning, error, info). Every text token clears WCAG AA and APCA Lc 75 on every surface tier.
 */
object CodeinPalette {
    /** Tier 0: the one page ground, also the editor canvas. */
    val Base = Color(0xFF000000)
    /** Tier 1: sidebars, top bar and dock share the ground and are separated by hairlines. */
    val Tier1 = Color(0xFF000000)
    /** Tier 2: inset cards, floating search, sheets, dialogs. */
    val Tier2 = Color(0xFF0B0B0B)
    /** Tier 3: hover, pressed and selected rows. */
    val Tier3 = Color(0xFF161616)
    /** Hairline source; at 30% alpha it renders #1D1D1D on the black ground. */
    val HairlineSource = Color(0xFF616161)

    val TextPrimary = Color(0xFFF9F8F9)
    val TextSecondary = Color(0xFFE5E5E4)
    val TextMuted = Color(0xFFD6D6D5)

    /** Primary action fill (pill buttons, switches, progress) and its label. */
    val Action = Color(0xFFEDEDED)
    val ActionOn = Color(0xFF040404)

    /** Brand blue. Reserved for the info status and search matches, never for decoration. */
    val Brand = Color(0xFFA3CFFF)

    val Success = Color(0xFF6EE7A0)
    val Warning = Color(0xFFFBBF24)
    val Error = Color(0xFFFF9C94)
    val Info = Brand

    // Legacy aliases kept so older call sites keep compiling against the new tiers.
    val Accent get() = Action
    val AccentOn get() = ActionOn
    val Background get() = Base
    val Panel get() = Tier1
    val Card get() = Tier2
    val CardHover get() = Tier3
}

/**
 * The app-chrome half of a theme: the four surface tiers, the accent and the hairline source. Every
 * [EditorColorScheme] carries one so switching theme recolors the whole IDE, not only the editor.
 */
@Immutable
data class ChromePalette(
    val base: Color,
    val tier1: Color,
    val tier2: Color,
    val tier3: Color,
    val accent: Color,
    val onAccent: Color,
    val secondary: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val hairlineSource: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val info: Color,
)

/** Color-coded file-type badge colors. */
object FileTypeColors {
    val Kotlin = Color(0xFFA97BFF)
    val Java = Color(0xFFF89820)
    val Xml = Color(0xFF2DD4BF)
    val Gradle = Color(0xFF34D399)
    val Json = Color(0xFFFBBF24)
    val Archive = Color(0xFFE11D48)
    val Image = Color(0xFFF472B6)
    val Markdown = Color(0xFF94A3B8)
    val Text = Color(0xFFA3A9BA)
    val Properties = Color(0xFF60A5FA)
    val Folder = Color(0xFF7DC4FF)
}

/** Git status badge colors. */
object GitStatusColors {
    val Modified = Color(0xFF60A5FA)
    val Added = Color(0xFF4ADE80)
    val Deleted = Color(0xFFF87171)
    val Untracked = Color(0xFFFBBF24)
    val Conflict = Color(0xFFE879F9)
}

/** Log level pill colors (Logcat). */
object LogLevelColors {
    val Verbose = Color(0xFF94A3B8)
    val Debug = Color(0xFF60A5FA)
    val Info = Color(0xFF4ADE80)
    val Warn = Color(0xFFFBBF24)
    val Error = Color(0xFFF87171)
    val Assert = Color(0xFFE879F9)
}

/**
 * A complete editor color scheme. Values are used both by the Compose chrome around the editor
 * (gutter, tabs, find bar) and to generate the TextMate theme handed to Sora Editor.
 */
@Immutable
data class EditorColorScheme(
    val id: String,
    val displayName: String,
    val isDark: Boolean,
    val background: Color,
    val foreground: Color,
    val gutterBackground: Color,
    val lineNumber: Color,
    val lineNumberActive: Color,
    val currentLine: Color,
    val selection: Color,
    val cursor: Color,
    val indentGuide: Color,
    val matchHighlight: Color,
    val keyword: Color,
    val string: Color,
    val number: Color,
    val comment: Color,
    val function: Color,
    val type: Color,
    val variable: Color,
    val constant: Color,
    val operator: Color,
    val annotation: Color,
    val tag: Color,
    val attribute: Color,
    val punctuation: Color,
    val error: Color,
    val warning: Color,
    val bracketColors: List<Color>,
    val chrome: ChromePalette,
)

object EditorColorSchemes {
    /**
     * Default theme: Codein Black. Kept under the original `codein_obsidian` id so saved preferences
     * move to the new look. Syntax is told apart by tone, not hue, to match the monochrome chrome.
     */
    val CodeinObsidian = EditorColorScheme(
        id = "codein_obsidian",
        displayName = "Codein Black",
        isDark = true,
        background = Color(0xFF000000),
        foreground = Color(0xFFE5E5E4),
        gutterBackground = Color(0xFF000000),
        lineNumber = Color(0xFF7A7A7A),
        lineNumberActive = Color(0xFFF9F8F9),
        currentLine = Color(0xFF0B0B0B),
        selection = Color(0xFF2A2A2A),
        cursor = Color(0xFFF9F8F9),
        indentGuide = Color(0xFF1D1D1D),
        matchHighlight = Color(0x59A3CFFF),
        keyword = Color(0xFFFFFFFF),
        string = Color(0xFFB9B9B8),
        number = Color(0xFFC9C9C8),
        comment = Color(0xFF8C8C8C),
        function = Color(0xFFEDEDED),
        type = Color(0xFFF9F8F9),
        variable = Color(0xFFD6D6D5),
        constant = Color(0xFFC9C9C8),
        operator = Color(0xFFA6A6A5),
        annotation = Color(0xFFB9B9B8),
        tag = Color(0xFFF9F8F9),
        attribute = Color(0xFFB9B9B8),
        punctuation = Color(0xFF9A9A99),
        error = CodeinPalette.Error,
        warning = CodeinPalette.Warning,
        bracketColors = listOf(Color(0xFFF9F8F9), Color(0xFFC9C9C8), Color(0xFF9A9A99)),
        chrome = ChromePalette(
            base = CodeinPalette.Base,
            tier1 = CodeinPalette.Tier1,
            tier2 = CodeinPalette.Tier2,
            tier3 = CodeinPalette.Tier3,
            accent = CodeinPalette.Action,
            onAccent = CodeinPalette.ActionOn,
            secondary = CodeinPalette.TextSecondary,
            textPrimary = CodeinPalette.TextPrimary,
            textSecondary = CodeinPalette.TextSecondary,
            textMuted = CodeinPalette.TextMuted,
            hairlineSource = CodeinPalette.HairlineSource,
            success = CodeinPalette.Success,
            warning = CodeinPalette.Warning,
            error = CodeinPalette.Error,
            info = CodeinPalette.Info,
        ),
    )

    val CatppuccinMocha = EditorColorScheme(
        id = "codein_dark",
        displayName = "Catppuccin Mocha",
        isDark = true,
        background = Color(0xFF1E1E2E),
        foreground = Color(0xFFCDD6F4),
        gutterBackground = Color(0xFF181825),
        lineNumber = Color(0xFF6C7086),
        lineNumberActive = Color(0xFFB4BEFE),
        currentLine = Color(0xFF2A2B3C),
        selection = Color(0xFF45475A),
        cursor = Color(0xFFF5E0DC),
        indentGuide = Color(0xFF313244),
        matchHighlight = Color(0x66F9E2AF),
        keyword = Color(0xFFCBA6F7),
        string = Color(0xFFA6E3A1),
        number = Color(0xFFFAB387),
        comment = Color(0xFF7F849C),
        function = Color(0xFF89B4FA),
        type = Color(0xFFF9E2AF),
        variable = Color(0xFFCDD6F4),
        constant = Color(0xFFFAB387),
        operator = Color(0xFF89DCEB),
        annotation = Color(0xFFF5C2E7),
        tag = Color(0xFF89B4FA),
        attribute = Color(0xFFF9E2AF),
        punctuation = Color(0xFF9399B2),
        error = Color(0xFFF38BA8),
        warning = Color(0xFFF9E2AF),
        bracketColors = listOf(Color(0xFFF9E2AF), Color(0xFFCBA6F7), Color(0xFF89B4FA)),
        chrome = ChromePalette(
            base = Color(0xFF1E1E2E),
            tier1 = Color(0xFF181825),
            tier2 = Color(0xFF262637),
            tier3 = Color(0xFF313244),
            accent = Color(0xFFCBA6F7),
            onAccent = Color(0xFF11111B),
            secondary = Color(0xFF94E2D5),
            textPrimary = Color(0xFFCDD6F4),
            textSecondary = Color(0xFFA6ADC8),
            textMuted = Color(0xFF6C7086),
            hairlineSource = Color(0xFF9399B2),
            success = Color(0xFFA6E3A1),
            warning = Color(0xFFF9E2AF),
            error = Color(0xFFF38BA8),
            info = Color(0xFF89B4FA),
        ),
    )

    val OneDarkPro = EditorColorScheme(
        id = "one_dark_pro",
        displayName = "One Dark Pro",
        isDark = true,
        background = Color(0xFF282C34),
        foreground = Color(0xFFABB2BF),
        gutterBackground = Color(0xFF21252B),
        lineNumber = Color(0xFF495162),
        lineNumberActive = Color(0xFFABB2BF),
        currentLine = Color(0xFF2C313C),
        selection = Color(0xFF3E4451),
        cursor = Color(0xFF528BFF),
        indentGuide = Color(0xFF3B4048),
        matchHighlight = Color(0x66E5C07B),
        keyword = Color(0xFFC678DD),
        string = Color(0xFF98C379),
        number = Color(0xFFD19A66),
        comment = Color(0xFF7F848E),
        function = Color(0xFF61AFEF),
        type = Color(0xFFE5C07B),
        variable = Color(0xFFE06C75),
        constant = Color(0xFFD19A66),
        operator = Color(0xFF56B6C2),
        annotation = Color(0xFFE5C07B),
        tag = Color(0xFFE06C75),
        attribute = Color(0xFFD19A66),
        punctuation = Color(0xFFABB2BF),
        error = Color(0xFFE06C75),
        warning = Color(0xFFE5C07B),
        bracketColors = listOf(Color(0xFFD19A66), Color(0xFFC678DD), Color(0xFF56B6C2)),
        chrome = ChromePalette(
            base = Color(0xFF282C34),
            tier1 = Color(0xFF21252B),
            tier2 = Color(0xFF2C313C),
            tier3 = Color(0xFF3A3F4B),
            accent = Color(0xFF61AFEF),
            onAccent = Color(0xFF1B1E24),
            secondary = Color(0xFF56B6C2),
            textPrimary = Color(0xFFD7DAE0),
            textSecondary = Color(0xFFABB2BF),
            textMuted = Color(0xFF7F848E),
            hairlineSource = Color(0xFF8B92A0),
            success = Color(0xFF98C379),
            warning = Color(0xFFE5C07B),
            error = Color(0xFFE06C75),
            info = Color(0xFF61AFEF),
        ),
    )

    val TokyoNight = EditorColorScheme(
        id = "tokyo_night",
        displayName = "Tokyo Night",
        isDark = true,
        background = Color(0xFF1A1B26),
        foreground = Color(0xFFA9B1D6),
        gutterBackground = Color(0xFF16161E),
        lineNumber = Color(0xFF3B3F51),
        lineNumberActive = Color(0xFF737AA2),
        currentLine = Color(0xFF1F2335),
        selection = Color(0xFF283457),
        cursor = Color(0xFFC0CAF5),
        indentGuide = Color(0xFF292E42),
        matchHighlight = Color(0x663D59A1),
        keyword = Color(0xFF9D7CD8),
        string = Color(0xFF9ECE6A),
        number = Color(0xFFFF9E64),
        comment = Color(0xFF565F89),
        function = Color(0xFF7AA2F7),
        type = Color(0xFF2AC3DE),
        variable = Color(0xFFC0CAF5),
        constant = Color(0xFFFF9E64),
        operator = Color(0xFF89DDFF),
        annotation = Color(0xFFE0AF68),
        tag = Color(0xFFF7768E),
        attribute = Color(0xFFBB9AF7),
        punctuation = Color(0xFF89DDFF),
        error = Color(0xFFF7768E),
        warning = Color(0xFFE0AF68),
        bracketColors = listOf(Color(0xFFE0AF68), Color(0xFFBB9AF7), Color(0xFF7DCFFF)),
        chrome = ChromePalette(
            base = Color(0xFF1A1B26),
            tier1 = Color(0xFF16161E),
            tier2 = Color(0xFF1F2335),
            tier3 = Color(0xFF292E42),
            accent = Color(0xFF7AA2F7),
            onAccent = Color(0xFF15161E),
            secondary = Color(0xFF2AC3DE),
            textPrimary = Color(0xFFC0CAF5),
            textSecondary = Color(0xFFA9B1D6),
            textMuted = Color(0xFF565F89),
            hairlineSource = Color(0xFF737AA2),
            success = Color(0xFF9ECE6A),
            warning = Color(0xFFE0AF68),
            error = Color(0xFFF7768E),
            info = Color(0xFF7DCFFF),
        ),
    )

    val Dracula = EditorColorScheme(
        id = "dracula",
        displayName = "Dracula",
        isDark = true,
        background = Color(0xFF282A36),
        foreground = Color(0xFFF8F8F2),
        gutterBackground = Color(0xFF21222C),
        lineNumber = Color(0xFF6272A4),
        lineNumberActive = Color(0xFFF8F8F2),
        currentLine = Color(0xFF343746),
        selection = Color(0xFF44475A),
        cursor = Color(0xFFF8F8F0),
        indentGuide = Color(0xFF3B3D4D),
        matchHighlight = Color(0x66FFB86C),
        keyword = Color(0xFFFF79C6),
        string = Color(0xFFF1FA8C),
        number = Color(0xFFBD93F9),
        comment = Color(0xFF6272A4),
        function = Color(0xFF50FA7B),
        type = Color(0xFF8BE9FD),
        variable = Color(0xFFF8F8F2),
        constant = Color(0xFFBD93F9),
        operator = Color(0xFFFF79C6),
        annotation = Color(0xFFFFB86C),
        tag = Color(0xFFFF79C6),
        attribute = Color(0xFF50FA7B),
        punctuation = Color(0xFFF8F8F2),
        error = Color(0xFFFF5555),
        warning = Color(0xFFFFB86C),
        bracketColors = listOf(Color(0xFFF1FA8C), Color(0xFFFF79C6), Color(0xFF8BE9FD)),
        chrome = ChromePalette(
            base = Color(0xFF282A36),
            tier1 = Color(0xFF21222C),
            tier2 = Color(0xFF303241),
            tier3 = Color(0xFF3B3D4E),
            accent = Color(0xFFBD93F9),
            onAccent = Color(0xFF191A21),
            secondary = Color(0xFF8BE9FD),
            textPrimary = Color(0xFFF8F8F2),
            textSecondary = Color(0xFFC6C8D1),
            textMuted = Color(0xFF6272A4),
            hairlineSource = Color(0xFF8C93B5),
            success = Color(0xFF50FA7B),
            warning = Color(0xFFFFB86C),
            error = Color(0xFFFF5555),
            info = Color(0xFF8BE9FD),
        ),
    )

    /** Kept under its original id so saved preferences keep working. */
    val CleanDaylight = EditorColorScheme(
        id = "github_light",
        displayName = "Clean Daylight",
        isDark = false,
        background = Color(0xFFFFFFFF),
        foreground = Color(0xFF1F2328),
        gutterBackground = Color(0xFFF6F8FA),
        lineNumber = Color(0xFF8C959F),
        lineNumberActive = Color(0xFF1F2328),
        currentLine = Color(0xFFF2F5F8),
        selection = Color(0xFFCCE1FF),
        cursor = Color(0xFF0969DA),
        indentGuide = Color(0xFFE1E4E8),
        matchHighlight = Color(0x66FFDF5D),
        keyword = Color(0xFFCF222E),
        string = Color(0xFF0A3069),
        number = Color(0xFF0550AE),
        comment = Color(0xFF6E7781),
        function = Color(0xFF8250DF),
        type = Color(0xFF953800),
        variable = Color(0xFF1F2328),
        constant = Color(0xFF0550AE),
        operator = Color(0xFFCF222E),
        annotation = Color(0xFF953800),
        tag = Color(0xFF116329),
        attribute = Color(0xFF0550AE),
        punctuation = Color(0xFF1F2328),
        error = Color(0xFFCF222E),
        warning = Color(0xFF9A6700),
        bracketColors = listOf(Color(0xFF0550AE), Color(0xFF8250DF), Color(0xFF116329)),
        chrome = ChromePalette(
            base = Color(0xFFFFFFFF),
            tier1 = Color(0xFFF6F8FA),
            tier2 = Color(0xFFEEF1F5),
            tier3 = Color(0xFFE4E8ED),
            accent = Color(0xFF6E56CF),
            onAccent = Color(0xFFFFFFFF),
            secondary = Color(0xFF0F766E),
            textPrimary = Color(0xFF1F2328),
            textSecondary = Color(0xFF59636E),
            textMuted = Color(0xFF8C959F),
            hairlineSource = Color(0xFF6E7781),
            success = Color(0xFF1A7F37),
            warning = Color(0xFF9A6700),
            error = Color(0xFFCF222E),
            info = Color(0xFF0969DA),
        ),
    )

    val all: List<EditorColorScheme> = listOf(CodeinObsidian, CatppuccinMocha, OneDarkPro, TokyoNight, Dracula, CleanDaylight)

    val Default: EditorColorScheme get() = CodeinObsidian
    val DefaultLight: EditorColorScheme get() = CleanDaylight

    fun byId(id: String): EditorColorScheme = all.firstOrNull { it.id == id } ?: CodeinObsidian
}
