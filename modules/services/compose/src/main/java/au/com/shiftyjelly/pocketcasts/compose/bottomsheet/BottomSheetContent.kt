package au.com.shiftyjelly.pocketcasts.compose.bottomsheet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import au.com.shiftyjelly.pocketcasts.compose.AppTheme
import au.com.shiftyjelly.pocketcasts.compose.buttons.RowButton
import au.com.shiftyjelly.pocketcasts.compose.buttons.RowOutlinedButton
import au.com.shiftyjelly.pocketcasts.compose.components.TextH50
import au.com.shiftyjelly.pocketcasts.compose.preview.ThemePreviewParameterProvider
import au.com.shiftyjelly.pocketcasts.compose.theme
import au.com.shiftyjelly.pocketcasts.ui.extensions.inPortrait
import au.com.shiftyjelly.pocketcasts.ui.theme.Theme
import au.com.shiftyjelly.pocketcasts.utils.Util

private val ContentMaxWidthDp = 600.dp
private val ContentPadding = 16.dp
private val OutlinedBorder: BorderStroke
    @Composable
    get() = BorderStroke(2.dp, MaterialTheme.theme.colors.primaryText01)

private val PillSize = DpSize(width = 56.dp, height = 4.dp)
private val PillCornerRadius = 3.dp

class BottomSheetContentState(
    val content: Content,
) {
    data class Content(
        val imageContent: @Composable (() -> Unit)? = null,
        val summaryText: String,
        val primaryButton: Button.Primary,
        val secondaryButton: Button.Secondary? = null,
    ) {
        sealed interface Button {
            val label: String
            val onClick: (() -> Unit)?

            data class Primary(
                override val label: String,
                override val onClick: () -> Unit,
            ) : Button

            data class Secondary(
                override val label: String,
                override val onClick: (() -> Unit)? = null,
            ) : Button
        }
    }
}

@Composable
fun BottomSheetContent(
    state: BottomSheetContentState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colors.background)
            .padding(ContentPadding),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.widthIn(max = ContentMaxWidthDp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val context = LocalContext.current
            val content = state.content

            Pill()

            Spacer(modifier = Modifier.height(16.dp))

            state.content.imageContent?.let { imageContent ->
                if (LocalConfiguration.current.inPortrait() || Util.isTablet(context)) {
                    imageContent.invoke()
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            SummaryText(content)

            Spacer(modifier = Modifier.height(16.dp))

            ConfirmButton(content.primaryButton, onDismiss)

            Spacer(modifier = Modifier.height(16.dp))

            if (content.secondaryButton != null) {
                DismissButton(content.secondaryButton, onDismiss)
            }
        }
    }
}

@Composable
fun Pill(
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.theme.colors.primaryIcon02,
) {
    Box(
        modifier = modifier
            .size(PillSize)
            .clip(RoundedCornerShape(PillCornerRadius))
            .background(backgroundColor),
    )
}

@Composable
private fun SummaryText(content: BottomSheetContentState.Content) {
    TextH50(
        text = content.summaryText,
        color = MaterialTheme.theme.colors.primaryText01,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun ConfirmButton(
    primaryButton: BottomSheetContentState.Content.Button.Primary,
    onDismiss: () -> Unit,
) {
    RowButton(
        text = primaryButton.label,
        colors = ButtonDefaults.buttonColors(
            backgroundColor = MaterialTheme.theme.colors.primaryText01,
            contentColor = MaterialTheme.theme.colors.primaryInteractive02,
        ),
        includePadding = false,
        onClick = {
            onDismiss.invoke()
            primaryButton.onClick.invoke()
        },
    )
}

@Composable
private fun DismissButton(
    secondaryButton: BottomSheetContentState.Content.Button.Secondary,
    onDismiss: () -> Unit,
) {
    RowOutlinedButton(
        text = secondaryButton.label,
        border = OutlinedBorder,
        colors = ButtonDefaults.outlinedButtonColors(
            backgroundColor = MaterialTheme.theme.colors.primaryUi01,
            contentColor = MaterialTheme.theme.colors.primaryText01,
        ),
        includePadding = false,
        onClick = {
            onDismiss.invoke()
            secondaryButton.onClick?.invoke()
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun BottomSheetContentPreview(
    @PreviewParameter(ThemePreviewParameterProvider::class) themeType: Theme.ThemeType,
) {
    AppTheme(themeType) {
        BottomSheetContent(
            state = BottomSheetContentState(
                content = BottomSheetContentState.Content(
                    summaryText = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt.",
                    primaryButton = BottomSheetContentState.Content.Button.Primary(
                        label = "Confirm",
                        onClick = {},
                    ),
                    secondaryButton = BottomSheetContentState.Content.Button.Secondary(
                        label = "Not now",
                    ),
                ),
            ),
            onDismiss = {},
        )
    }
}
