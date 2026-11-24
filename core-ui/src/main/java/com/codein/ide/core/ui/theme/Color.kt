package com.codein.ide.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Raw brand palette for the Codein design system. Dark-first slate/charcoal surfaces. */
object CodeinPalette {
    val Background = Color(0xFF0F1117)
    val Panel = Color(0xFF161922)
    val Card = Color(0xFF1E222E)
    val CardHover = Color(0xFF252A38)
    val Hairline = Color(0xFF2A2F3D)
    val HairlineStrong = Color(0xFF363C4E)
    val TextPrimary = Color(0xFFE6E8EF)
    val TextSecondary = Color(0xFFA3A9BA)
    val TextMuted = Color(0xFF6C7386)

    val Accent = Color(0xFF8B7CF6)
    val AccentContainer = Color(0xFF2E2752)
    val AccentOn = Color(0xFF0F1117)
    val Teal = Color(0xFF2DD4BF)
    val TealContainer = Color(0xFF113A36)
    val Sky = Color(0xFF7DC4FF)

    val Success = Color(0xFF4ADE80)
    val Warning = Color(0xFFFBBF24)
    val Error = Color(0xFFF87171)
    val ErrorContainer = Color(0xFF3B1A1E)
    val Info = Color(0xFF60A5FA)

    // Light surfaces (GitHub Light companion theme for the app chrome).
    val LightBackground = Color(0xFFFFFFFF)
    val LightPanel = Color(0xFFF6F8FA)
    val LightCard = Color(0xFFEEF1F5)
    val LightHairline = Color(0xFFD0D7DE)
    val LightTextPrimary = Color(0xFF1F2328)
    val LightTextSecondary = Color(0xFF59636E)
    val LightAccent = Color(0xFF6E56CF)
}

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
)

object EditorColorSchemes {
    val CatppuccinMocha = EditorColorScheme(
        id = "codein_dark",
        displayName = "Codein Dark (Catppuccin Mocha)",
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
    )

    val GitHubLight = EditorColorScheme(
        id = "github_light",
        displayName = "GitHub Light",
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
    )

    val all: List<EditorColorScheme> = listOf(CatppuccinMocha, OneDarkPro, TokyoNight, Dracula, GitHubLight)

    fun byId(id: String): EditorColorScheme = all.firstOrNull { it.id == id } ?: CatppuccinMocha
}
