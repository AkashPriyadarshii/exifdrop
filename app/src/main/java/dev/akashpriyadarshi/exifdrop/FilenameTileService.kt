package dev.akashpriyadarshi.exifdrop

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick settings tile to quickly toggle whether ExifDrop scrambles filenames
 * (`share_<hash>`) or preserves original filenames.
 */
class FilenameTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val current = Prefs.filenamePattern(this)
        val next = if (current == "neutral") "original" else "neutral"
        Prefs.setFilenamePattern(this, next)
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val isNeutral = Prefs.filenamePattern(this) == "neutral"
        tile.state = if (isNeutral) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.label = "Filename"
            tile.subtitle = if (isNeutral) "Scrambled" else "Original"
        } else {
            tile.label = if (isNeutral) "Scramble names" else "Keep names"
        }
        tile.updateTile()
    }
}
