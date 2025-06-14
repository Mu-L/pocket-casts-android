package au.com.shiftyjelly.pocketcasts.player.view.transcripts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import au.com.shiftyjelly.pocketcasts.compose.AppThemeWithBackground
import au.com.shiftyjelly.pocketcasts.compose.Devices
import au.com.shiftyjelly.pocketcasts.compose.components.TextP40
import au.com.shiftyjelly.pocketcasts.player.view.transcripts.TranscriptDefaults.TranscriptColors
import au.com.shiftyjelly.pocketcasts.player.view.transcripts.TranscriptDefaults.TranscriptFontFamily
import au.com.shiftyjelly.pocketcasts.player.view.transcripts.TranscriptDefaults.bottomPadding
import au.com.shiftyjelly.pocketcasts.player.view.transcripts.TranscriptViewModel.TranscriptError
import au.com.shiftyjelly.pocketcasts.player.view.transcripts.TranscriptViewModel.TranscriptState
import au.com.shiftyjelly.pocketcasts.ui.theme.Theme
import au.com.shiftyjelly.pocketcasts.images.R as IR
import au.com.shiftyjelly.pocketcasts.localization.R as LR

@Composable
fun TranscriptError(
    state: TranscriptState.Failure,
    colors: TranscriptColors,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val errorMessage = when (state.error) {
        TranscriptError.Empty -> stringResource(LR.string.transcript_empty)

        TranscriptError.FailedToLoad -> stringResource(LR.string.error_transcript_failed_to_load)
    }

    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(colors.backgroundColor())
            .padding(horizontal = 16.dp)
            .padding(bottom = bottomPadding()),
    ) {
        Icon(
            painter = painterResource(IR.drawable.ic_warning),
            contentDescription = null,
            tint = TranscriptColors.iconColor().copy(alpha = 0.5f),
        )
        TextP40(
            text = errorMessage,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
            color = TranscriptColors.textColor(),
            fontFamily = TranscriptFontFamily,
            fontWeight = FontWeight.W500,
            lineHeight = 24.sp,
        )
        Button(
            onClick = onRetry,
            modifier = Modifier.padding(top = 16.dp),
            shape = RoundedCornerShape(40.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = TranscriptColors.contentColor()),
        ) {
            TextP40(
                text = stringResource(LR.string.try_again),
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.W400,
            )
        }
    }
}

@Preview(name = "Phone")
@Composable
private fun ErrorDarkPreview() = ErrorPreview()

@Preview(name = "Landscape", device = Devices.LandscapeRegular)
@Composable
private fun ErrorLandscapePreview() = ErrorPreview()

@Preview(name = "Tablet", device = Devices.PortraitTablet)
@Composable
private fun ErrorTabletPreview() = ErrorPreview()

@Composable
private fun ErrorPreview() {
    AppThemeWithBackground(Theme.ThemeType.DARK) {
        TranscriptError(
            state = TranscriptState.Failure(
                error = TranscriptError.FailedToLoad,
            ),
            onRetry = {},
            colors = TranscriptColors(Color.Black),
        )
    }
}
