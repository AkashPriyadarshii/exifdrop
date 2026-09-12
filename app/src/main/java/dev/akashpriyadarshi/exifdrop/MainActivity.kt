package dev.akashpriyadarshi.exifdrop

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.exifinterface.media.ExifInterface
import dev.akashpriyadarshi.exifdrop.ui.theme.ExifDropTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class TagEntry(
    val name: String,
    val value: String,
    val isSensitive: Boolean,
)

data class FileInspection(
    val uri: Uri,
    val name: String,
    val sizeStr: String,
    val mimeType: String,
    val tags: List<TagEntry>,
    val hasGps: Boolean,
)

data class SanitizerReceipt(
    val files: List<FileInspection>,
    val totalTags: Int,
    val hasGps: Boolean,
    val uris: List<Uri>,
)

private fun launchTrampoline(context: Context, uris: List<Uri>) {
    if (uris.isEmpty()) return
    val intent = Intent(if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
        component = ComponentName(context, TrampolineActivity::class.java)
        if (uris.size == 1) {
            putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        clipData = ClipData.newUri(context.contentResolver, "share", uris[0]).apply {
            for (u in uris.drop(1)) addItem(ClipData.Item(u))
        }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(intent)
}

private fun inspectUris(context: Context, uris: List<Uri>): SanitizerReceipt {
    val fileInspections = ArrayList<FileInspection>()
    var totalTagsCount = 0
    var anyGps = false

    for (uri in uris) {
        var displayName = "file_${System.currentTimeMillis()}"
        var sizeBytes = 0L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex >= 0) displayName = cursor.getString(nameIndex) ?: displayName
                    if (sizeIndex >= 0) sizeBytes = cursor.getLong(sizeIndex)
                }
            }
        } catch (e: Throwable) {
            // Ignore query failure, use fallback name
        }

        val sizeStr = when {
            sizeBytes <= 0L -> "Unknown size"
            sizeBytes < 1024 -> "$sizeBytes B"
            sizeBytes < 1024 * 1024 -> "${sizeBytes / 1024} KB"
            else -> "%.1f MB".format(sizeBytes / (1024.0 * 1024.0))
        }

        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val tags = ArrayList<TagEntry>()
        var fileHasGps = false

        if (mime.startsWith("image/")) {
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    val exif = ExifInterface(pfd.fileDescriptor)

                    val latLong = FloatArray(2)
                    if (exif.getLatLong(latLong)) {
                        fileHasGps = true
                        anyGps = true
                        tags.add(TagEntry("GPS Location", "%.4f, %.4f".format(latLong[0], latLong[1]), isSensitive = true))
                    } else {
                        val lat = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)
                        if (!lat.isNullOrBlank()) {
                            fileHasGps = true
                            anyGps = true
                            tags.add(TagEntry("GPS Latitude", lat, isSensitive = true))
                        }
                    }
                    val alt = exif.getAltitude(Double.NaN)
                    if (!alt.isNaN()) {
                        tags.add(TagEntry("GPS Altitude", "${alt.toInt()} m", isSensitive = true))
                    }

                    val make = exif.getAttribute(ExifInterface.TAG_MAKE)
                    val model = exif.getAttribute(ExifInterface.TAG_MODEL)
                    if (!make.isNullOrBlank() || !model.isNullOrBlank()) {
                        val camera = listOfNotNull(make, model).joinToString(" ")
                        tags.add(TagEntry("Device / Camera", camera, isSensitive = false))
                    }
                    val software = exif.getAttribute(ExifInterface.TAG_SOFTWARE)
                    if (!software.isNullOrBlank()) {
                        tags.add(TagEntry("Software", software, isSensitive = false))
                    }

                    val date = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                        ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                    if (!date.isNullOrBlank()) {
                        tags.add(TagEntry("Timestamp", date, isSensitive = true))
                    }

                    val lens = exif.getAttribute(ExifInterface.TAG_LENS_MODEL)
                    if (!lens.isNullOrBlank()) {
                        tags.add(TagEntry("Lens", lens, isSensitive = false))
                    }
                    val fLen = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)
                    if (!fLen.isNullOrBlank()) {
                        tags.add(TagEntry("Focal Length", fLen, isSensitive = false))
                    }
                    val iso = exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)
                    if (!iso.isNullOrBlank()) {
                        tags.add(TagEntry("ISO", iso, isSensitive = false))
                    }
                    val author = exif.getAttribute(ExifInterface.TAG_ARTIST)
                    if (!author.isNullOrBlank()) {
                        tags.add(TagEntry("Author", author, isSensitive = true))
                    }
                    val serial = exif.getAttribute(ExifInterface.TAG_BODY_SERIAL_NUMBER)
                    if (!serial.isNullOrBlank()) {
                        tags.add(TagEntry("Serial No.", serial, isSensitive = true))
                    }
                }
            } catch (e: Throwable) {
                // Ignore inspection error
            }
        } else if (mime == "application/pdf") {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val headerBuf = ByteArray(minOf(256 * 1024, if (sizeBytes > 0) sizeBytes.toInt() else 256 * 1024))
                    var read = 0
                    while (read < headerBuf.size) {
                        val count = input.read(headerBuf, read, headerBuf.size - read)
                        if (count < 0) break
                        read += count
                    }
                    val content = String(headerBuf, 0, read, Charsets.ISO_8859_1)
                    if (content.contains("/Author")) tags.add(TagEntry("PDF /Author", "Present", isSensitive = true))
                    if (content.contains("/Creator")) tags.add(TagEntry("PDF /Creator", "Present", isSensitive = false))
                    if (content.contains("/Producer")) tags.add(TagEntry("PDF /Producer", "Present", isSensitive = false))
                    if (content.contains("/CreationDate")) tags.add(TagEntry("PDF /CreationDate", "Present", isSensitive = true))
                    if (content.contains("/ModDate")) tags.add(TagEntry("PDF /ModDate", "Present", isSensitive = true))
                    if (content.contains("<x:xmpmeta") || content.contains("/Metadata")) {
                        tags.add(TagEntry("PDF XMP Stream", "Present", isSensitive = true))
                    }
                }
            } catch (e: Throwable) {
                // Ignore inspection error
            }
        }

        totalTagsCount += tags.size
        fileInspections.add(
            FileInspection(
                uri = uri,
                name = displayName,
                sizeStr = sizeStr,
                mimeType = mime,
                tags = tags,
                hasGps = fileHasGps,
            )
        )
    }

    return SanitizerReceipt(
        files = fileInspections,
        totalTags = totalTagsCount,
        hasGps = anyGps,
        uris = uris,
    )
}

/**
 * Launcher shell: status-led hub + settings + pre-flight sanitizer receipt.
 * The share trampoline stays the primary 2-tap entry; this is the companion surface.
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
    val coroutineScope = rememberCoroutineScope()
    var inspectedReceipt by remember { mutableStateOf<SanitizerReceipt?>(null) }
    var isInspecting by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            coroutineScope.launch(Dispatchers.IO) {
                isInspecting = true
                val receipt = inspectUris(context, uris)
                withContext(Dispatchers.Main) {
                    inspectedReceipt = receipt
                    isInspecting = false
                }
            }
        }
    }

    inspectedReceipt?.let { receipt ->
        SanitizerReceiptSheet(
            receipt = receipt,
            onDismiss = { inspectedReceipt = null },
            onStripAndShare = {
                val targets = receipt.uris
                inspectedReceipt = null
                launchTrampoline(context, targets)
            }
        )
    }

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
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf("JPEG", "PNG", "WEBP", "PDF").forEach { fmt ->
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            text = fmt,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))

            // Status hero: mono readout, green when verified.
            StatusHero()
            Spacer(Modifier.height(24.dp))

            // Settings list, hairline-divided.
            SettingsList(context)
            Spacer(Modifier.height(24.dp))

            // Action / empty state
            EmptyState(
                isInspecting = isInspecting,
                onPickFiles = { picker.launch(arrayOf("image/*", "application/pdf")) }
            )

            Spacer(Modifier.height(32.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(20.dp))

            // Ecosystem, Author, Social Footer
            AppFooter()
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

private fun formatCacheSize(context: Context): String {
    val dir = File(context.cacheDir, "cleaned")
    val bytes = dir.listFiles()?.sumOf { it.length() } ?: 0L
    return when {
        bytes == 0L -> "0 B"
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    }
}

@Composable
private fun SettingsList(context: Context) {
    Column {
        var pattern by remember { mutableStateOf(Prefs.filenamePattern(context)) }
        var cacheDays by remember { mutableStateOf(Prefs.cacheAgeDays(context)) }
        var stripGps by remember { mutableStateOf(Prefs.stripGps(context)) }
        var keepOrient by remember { mutableStateOf(Prefs.keepOrientation(context)) }
        var cacheSize by remember { mutableStateOf(formatCacheSize(context)) }

        SettingRow("Filename") {
            Segmented(
                options = listOf("neutral" to "share_<hash>", "original" to "keep name"),
                selected = pattern,
                onSelect = { pattern = it; Prefs.setFilenamePattern(context, it) },
            )
        }
        SettingRow("Cache retention") {
            Segmented(
                options = listOf("1" to "1 day", "7" to "7 days", "30" to "30 days"),
                selected = cacheDays.toString(),
                onSelect = { cacheDays = it.toInt(); Prefs.setCacheAgeDays(context, it.toInt()) },
            )
        }
        SettingRow("Storage ($cacheSize)") {
            Surface(
                onClick = {
                    File(context.cacheDir, "cleaned").deleteRecursively()
                    cacheSize = formatCacheSize(context)
                    Toast.makeText(context, "Cleaned cache purged", Toast.LENGTH_SHORT).show()
                },
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.error,
            ) {
                Text(
                    text = "Clear now",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
        ToggleRow("Strip GPS", "remove location tags across JPEG, PNG, and WebP", stripGps) {
            stripGps = it; Prefs.setStripGps(context, it)
        }
        ToggleRow("Keep orientation", "photos stay upright across JPEG, PNG, and WebP", keepOrient) {
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
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyState(isInspecting: Boolean, onPickFiles: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (isInspecting) {
            CircularProgressIndicator(
                modifier = Modifier.size(36.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Inspecting metadata…",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Analyzing EXIF, GPS, and XMP streams",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Share from any app or pick directly",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Inspect and strip files before destination handoff",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Surface(
                onClick = onPickFiles,
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Row(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Inspect & strip files",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SanitizerReceiptSheet(
    receipt: SanitizerReceipt,
    onDismiss: () -> Unit,
    onStripAndShare: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            // Receipt Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "SANITIZER RECEIPT",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                    Text(
                        text = "Pre-flight metadata inspection",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = if (receipt.hasGps) MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                    else if (receipt.totalTags > 0) MaterialTheme.colorScheme.surface
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    border = BorderStroke(
                        1.dp,
                        if (receipt.hasGps) MaterialTheme.colorScheme.error
                        else if (receipt.totalTags > 0) MaterialTheme.colorScheme.outline
                        else MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text(
                        text = when {
                            receipt.hasGps -> "GPS EMBEDDED"
                            receipt.totalTags > 0 -> "${receipt.totalTags} TAGS FOUND"
                            else -> "CLEAN SOURCE"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (receipt.hasGps) MaterialTheme.colorScheme.error
                        else if (receipt.totalTags > 0) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))

            // Scrollable List of Files
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                receipt.files.forEach { file ->
                    FileReceiptCard(file)
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(16.dp))

            // Action Button: STRIP ALL & SHARE
            Surface(
                onClick = onStripAndShare,
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "STRIP ALL & SHARE (${receipt.files.size} FILE${if (receipt.files.size > 1) "S" else ""})",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Surface(
                onClick = onDismiss,
                shape = MaterialTheme.shapes.small,
                color = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp)
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Dismiss",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun FileReceiptCard(file: FileInspection) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = file.sizeStr,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (file.tags.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "No metadata found. Clean source file.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    file.tags.forEach { tag ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = tag.name,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = if (tag.isSensitive) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(0.4f),
                            )
                            Text(
                                text = tag.value,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(0.4f),
                            )
                            Text(
                                text = "PURGE",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(0.2f),
                                textAlign = TextAlign.End,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppFooter() {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 24.dp),
    ) {
        // Tagline & Meta
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "EXIFDROP v0.1.0",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "APACHE 2.0 · 100% OFFLINE",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))

        // Three columns: Ecosystem, Author, Social
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Ecosystem
            FooterSection(
                title = "ECOSYSTEM",
                links = listOf(
                    "design-genius" to "https://github.com/AkashPriyadarshii/design-genius",
                    "akash-design-engineering" to "https://github.com/AkashPriyadarshii/akash-design-engineering",
                    "tdlib-android" to "https://github.com/AkashPriyadarshii/tdlib-android",
                    "kharcha" to "https://github.com/AkashPriyadarshii/kharcha",
                ),
                onLinkClick = { uriHandler.openUri(it) },
            )

            // Author
            FooterSection(
                title = "AUTHOR — AKASH PRIYADARSHI (PATNA, BIHAR, INDIA)",
                links = listOf(
                    "GitHub" to "https://github.com/AkashPriyadarshii",
                    "Portfolio" to "https://akashpriyadarshi.vercel.app",
                    "LinkedIn" to "https://linkedin.com/in/akash-priyadarshi-1aa51b37a",
                    "Resume" to "https://akashpriyadarshii.github.io/Resume/",
                ),
                onLinkClick = { uriHandler.openUri(it) },
            )

            // Social
            FooterSection(
                title = "SOCIAL",
                links = listOf(
                    "X / Twitter" to "https://x.com/Akash__ydv001",
                    "Threads" to "https://www.threads.com/@free_dev2026",
                    "Instagram" to "https://www.instagram.com/akash.priyadarshii/",
                    "Reddit" to "https://reddit.com/user/DragonfruitWeak2801",
                ),
                onLinkClick = { uriHandler.openUri(it) },
            )
        }
    }
}

@Composable
private fun FooterSection(
    title: String,
    links: List<Pair<String, String>>,
    onLinkClick: (String) -> Unit,
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            links.forEach { (label, url) ->
                Surface(
                    onClick = { onLinkClick(url) },
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}