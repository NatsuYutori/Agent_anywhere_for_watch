package com.agentsanywhere.app.watch.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import com.agentsanywhere.app.R
import com.agentsanywhere.app.ui.designsystem.LocalAAColors

/**
 * The product wordmark, in the face the official clients use.
 *
 * The web client renders `.aa-wordmark` as `var(--font-caveat)`, and the phone app ships the same
 * file as `caveat_wght.ttf` with weight 500 - so this is the same font rather than a lookalike,
 * and at Medium weight it matches the wordmark everywhere else the product is signed.
 *
 * `lineHeight` is set just above the font size on purpose: Caveat has long descenders, and the
 * inherited line box would otherwise clip the "g" and "y".
 */
private val WatchWordmarkFontFamily = FontFamily(
    Font(R.font.caveat_wght, weight = FontWeight.Medium),
)

@Composable
internal fun WatchWordmark(
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = LocalAAColors.current.ink,
    textAlign: TextAlign? = null,
) {
    Text(
        text = "Agents Anywhere",
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontFamily = WatchWordmarkFontFamily,
        fontWeight = FontWeight.Medium,
        lineHeight = fontSize * 1.15f,
        letterSpacing = TextUnit.Unspecified,
        textAlign = textAlign,
        maxLines = 1,
    )
}
