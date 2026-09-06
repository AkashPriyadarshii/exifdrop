package dev.akashpriyadarshi.exifdrop

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akashpriyadarshi.exifdrop.ui.theme.ExifDropTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check

/**
 * Launcher shell: status-led hub + settings. Announced as "no UI in v0.1" is over; the
 * share trampoline stays the real entry, this is the companion surface.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ExifDropTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            // Wordmark
            Text(
                text = "ExifDrop",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Metadata stripper — share a photo or PDF",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))

            // Status hero: mono readout, green when verified.
            StatusHero()
            Spacer(Modifier.height(24.dp))

            // Settings list, hairline-divided.
            SettingsList(context)
            Spacer(Modifier.height(24.dp))

            // Honest empty state.
            EmptyState()
        }
    }
}

@Composable
private fun StatusHero() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
            Spacer(Modifier.size(12.dp))
            Column {
                Text(
                    text = "Ready",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "strip nothing stored · fully offline",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SettingsList(context: Context) {
    Column {
        var pattern by remember { mutableStateOf(Prefs.filenamePattern(context)) }
        var cacheDays by remember { mutableStateOf(Prefs.cacheAgeDays(context)) }
        var stripGps by remember { mutableStateOf(Prefs.stripGps(context)) }
        var keepOrient by remember { mutableStateOf(Prefs.keepOrientation(context)) }

        SettingRow("Filename") {
            Segmented(
                options = listOf("neutral" to "share_<hash>", "original" to "keep name"),
                selected = pattern,
                onSelect = { pattern = it; Prefs.setFilenamePattern(context, it) },
            )
        }
        SettingRow("Cache") {
            Segmented(
                options = listOf("1" to "1 day", "7" to "7 days", "30" to "30 days"),
                selected = cacheDays.toString(),
                onSelect = { cacheDays = it.toInt(); Prefs.setCacheAgeDays(context, it.toInt()) },
            )
        }
        ToggleRow("Strip GPS", "remove location tags · JPEG/PNG, WebP always stripped", stripGps) {
            stripGps = it; Prefs.setStripGps(context, it)
        }
        ToggleRow("Keep orientation", "photos stay upright · JPEG/PNG, WebP always stripped", keepOrient) {
            keepOrient = it; Prefs.setKeepOrientation(context, it)
        }
    }
}

@Composable
private fun SettingRow(label: String, value: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
        horizontalArrangement = Arrangement.End,
    ) { value() }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun ToggleRow(label: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = sub,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            ),
        )
    }
}

@Composable
private fun Segmented(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (key, label) ->
            val isSel = key == selected
            Surface(
                onClick = { onSelect(key) },
                shape = MaterialTheme.shapes.small,
                color = if (isSel) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (isSel) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = if (isSel) FontFamily.Monospace else null,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "No strips yet",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "Share a photo or PDF and pick ExifDrop to clean it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}