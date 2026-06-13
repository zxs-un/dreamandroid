package io.github.dreamandroid.local.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Error message card with error icon, message text, and optional dismiss-on-click.
 * Standard Material 3 error container styling.
 *
 * @param message The error message to display.
 * @param modifier Optional modifier applied to the Card.
 * @param onDismiss If non-null, the Card becomes clickable and dismisses on tap.
 */
@Composable
fun ErrorMessageCard(
    message: String,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
) {
    val cardModifier = modifier.fillMaxWidth()
    val colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.errorContainer,
    )

    if (onDismiss != null) {
        Card(
            onClick = onDismiss,
            modifier = cardModifier,
            colors = colors,
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    } else {
        Card(
            modifier = cardModifier,
            colors = colors,
        ) {
            Text(
                text = message,
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}
