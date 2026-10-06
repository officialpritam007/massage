package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassTextField
import com.example.ui.components.liquidRoundedShape
import com.example.ui.theme.LocalLiquidGlass

@Composable
fun CompactDeleteMessageDialogV3(forEveryone: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    MessageDialogFrameV3(onDismiss) {
        Text(if (forEveryone) "Delete for everyone?" else "Delete this message?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            if (forEveryone) "This message will be removed for both people." else "This message will be removed from your chat.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("Keep message") }
            TextButton(onClick = onConfirm) { Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
fun CompactEditMessageDialogV3(
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    originalValue: String? = null
) {
    MessageDialogFrameV3(onDismiss) {
        Text("Edit message", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text("Update what you sent.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        GlassTextField(
            value = value,
            onValueChange = { if (it.length <= 8000) onValueChange(it) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            singleLine = false,
            maxLines = 6,
            shape = liquidRoundedShape(22f)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton("Cancel", onDismiss, modifier = Modifier.weight(1f), isPrimary = false)
            GlassButton("Save", onSave, modifier = Modifier.weight(1f), enabled = value.isNotBlank() && (originalValue == null || value.trim() != originalValue.trim()))
        }
    }
}

@Composable
private fun MessageDialogFrameV3(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val glass = LocalLiquidGlass.current
    val maxHeight = (LocalConfiguration.current.screenHeightDp * .80f).dp
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth().imePadding().padding(horizontal = 24.dp, vertical = 16.dp), contentAlignment = Alignment.Center) {
            GlassCard(
                modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
                shape = liquidRoundedShape(32f),
                backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = if (glass.isDark) .90f else .88f),
                elevation = 18.dp
            ) {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = maxHeight).verticalScroll(rememberScrollState()).padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) { content() }
            }
        }
    }
}
