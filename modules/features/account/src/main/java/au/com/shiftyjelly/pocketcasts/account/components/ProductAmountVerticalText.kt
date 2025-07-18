package au.com.shiftyjelly.pocketcasts.account.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import au.com.shiftyjelly.pocketcasts.compose.AppTheme
import au.com.shiftyjelly.pocketcasts.compose.components.TextH30
import au.com.shiftyjelly.pocketcasts.compose.components.TextH40
import au.com.shiftyjelly.pocketcasts.compose.components.TextP60
import au.com.shiftyjelly.pocketcasts.compose.preview.ThemePreviewParameterProvider
import au.com.shiftyjelly.pocketcasts.compose.theme
import au.com.shiftyjelly.pocketcasts.ui.theme.Theme

@Composable
fun ProductAmountVerticalText(
    primaryText: String,
    modifier: Modifier = Modifier,
    secondaryText: String? = null,
    horizontalAlignment: Alignment.Horizontal = Alignment.End,
    emphasized: Boolean = true,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = horizontalAlignment,
    ) {
        if (emphasized) {
            TextH30(
                text = primaryText,
                color = MaterialTheme.theme.colors.primaryText01,
            )
        } else {
            TextH40(
                text = primaryText,
                color = MaterialTheme.theme.colors.primaryText01,
            )
        }
        if (secondaryText != null) {
            TextP60(
                text = secondaryText,
                color = MaterialTheme.theme.colors.primaryText02,
            )
        }
    }
}

@Composable
fun ProductAmountHorizontalText(
    modifier: Modifier = Modifier,
    price: String? = null,
    priceTextFontSize: TextUnit = 22.sp,
    originalPrice: String? = null,
    period: String? = null,
    originalPriceFontSize: TextUnit = 13.sp,
    lineThroughOriginalPrice: Boolean = true,
    hasBackgroundAlwaysWhite: Boolean = false,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    secondaryTextColor: Color = MaterialTheme.theme.colors.primaryText02,
    isFocusable: Boolean = false,
) {
    Row(
        modifier = modifier,
        verticalAlignment = verticalAlignment,
    ) {
        if (price != null) {
            TextH30(
                text = price,
                fontSize = priceTextFontSize,
                color = if (hasBackgroundAlwaysWhite) {
                    Color.Black
                } else {
                    MaterialTheme.theme.colors.primaryText01
                },
                modifier = if (isFocusable) {
                    Modifier.focusable()
                } else {
                    Modifier
                },
            )
        }

        if (period != null) {
            TextP60(
                text = period,
                fontSize = originalPriceFontSize,
                color = secondaryTextColor,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        if (originalPrice != null) {
            TextP60(
                text = originalPrice,
                fontSize = originalPriceFontSize,
                color = secondaryTextColor,
                style = TextStyle(
                    textDecoration = if (lineThroughOriginalPrice) TextDecoration.LineThrough else TextDecoration.None,
                ),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProductAmountPreview(
    @PreviewParameter(ThemePreviewParameterProvider::class) themeType: Theme.ThemeType,
) {
    AppTheme(themeType) {
        ProductAmountVerticalText(
            primaryText = "4 days free",
            secondaryText = "then $0.99 / month",
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ProductAmountPreviewHorizontal(
    @PreviewParameter(ThemePreviewParameterProvider::class) themeType: Theme.ThemeType,
) {
    AppTheme(themeType) {
        ProductAmountHorizontalText(
            price = "$0.99",
            period = "/year",
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ProductAmountPreviewHorizontalDiscount(
    @PreviewParameter(ThemePreviewParameterProvider::class) themeType: Theme.ThemeType,
) {
    AppTheme(themeType) {
        ProductAmountHorizontalText(
            price = "$0.99",
            originalPrice = "$1.29",
            period = "/year",
        )
    }
}
