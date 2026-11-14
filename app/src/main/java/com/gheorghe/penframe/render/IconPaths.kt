package com.gheorghe.penframe.render

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath

/**
 * Material icons available to components and the Icon element, keyed by a stable name that is
 * stored in projects. Drawn as plain paths so they work on the canvas, in previews and in exports.
 */
object IconPaths {
    val vectors: Map<String, ImageVector> = linkedMapOf(
        "add" to Icons.Rounded.Add,
        "edit" to Icons.Rounded.Edit,
        "delete" to Icons.Rounded.Delete,
        "favorite" to Icons.Rounded.Favorite,
        "favorite_border" to Icons.Rounded.FavoriteBorder,
        "star" to Icons.Rounded.Star,
        "home" to Icons.Rounded.Home,
        "search" to Icons.Rounded.Search,
        "person" to Icons.Rounded.Person,
        "account_circle" to Icons.Rounded.AccountCircle,
        "settings" to Icons.Rounded.Settings,
        "menu" to Icons.Rounded.Menu,
        "more_vert" to Icons.Rounded.MoreVert,
        "more_horiz" to Icons.Rounded.MoreHoriz,
        "arrow_back" to Icons.AutoMirrored.Rounded.ArrowBack,
        "arrow_forward" to Icons.AutoMirrored.Rounded.ArrowForward,
        "chevron_right" to Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        "expand_more" to Icons.Rounded.ExpandMore,
        "expand_less" to Icons.Rounded.ExpandLess,
        "close" to Icons.Rounded.Close,
        "check" to Icons.Rounded.Check,
        "notifications" to Icons.Rounded.Notifications,
        "share" to Icons.Rounded.Share,
        "content_copy" to Icons.Rounded.ContentCopy,
        "shopping_cart" to Icons.Rounded.ShoppingCart,
        "email" to Icons.Rounded.Email,
        "inbox" to Icons.Rounded.Inbox,
        "send" to Icons.AutoMirrored.Rounded.Send,
        "phone" to Icons.Rounded.Phone,
        "chat" to Icons.Rounded.Chat,
        "event" to Icons.Rounded.Event,
        "calendar" to Icons.Rounded.CalendarMonth,
        "schedule" to Icons.Rounded.Schedule,
        "place" to Icons.Rounded.Place,
        "map" to Icons.Rounded.Map,
        "camera" to Icons.Rounded.PhotoCamera,
        "image" to Icons.Rounded.Image,
        "photo_library" to Icons.Rounded.PhotoLibrary,
        "videocam" to Icons.Rounded.Videocam,
        "play" to Icons.Rounded.PlayArrow,
        "pause" to Icons.Rounded.Pause,
        "music" to Icons.Rounded.MusicNote,
        "mic" to Icons.Rounded.Mic,
        "volume" to Icons.Rounded.VolumeUp,
        "library" to Icons.AutoMirrored.Rounded.LibraryBooks,
        "bookmark" to Icons.Rounded.Bookmark,
        "list" to Icons.AutoMirrored.Rounded.List,
        "grid" to Icons.Rounded.GridView,
        "dashboard" to Icons.Rounded.Dashboard,
        "info" to Icons.Rounded.Info,
        "help" to Icons.Rounded.Help,
        "warning" to Icons.Rounded.Warning,
        "error" to Icons.Rounded.Error,
        "lock" to Icons.Rounded.Lock,
        "visibility" to Icons.Rounded.Visibility,
        "visibility_off" to Icons.Rounded.VisibilityOff,
        "wifi" to Icons.Rounded.Wifi,
        "wifi_off" to Icons.Rounded.WifiOff,
        "bluetooth" to Icons.Rounded.Bluetooth,
        "battery" to Icons.Rounded.BatteryFull,
        "signal" to Icons.Rounded.SignalCellularAlt,
        "cloud" to Icons.Rounded.Cloud,
        "download" to Icons.Rounded.Download,
        "upload" to Icons.Rounded.Upload,
        "refresh" to Icons.Rounded.Refresh,
        "filter" to Icons.Rounded.FilterList,
        "sort" to Icons.Rounded.Sort,
        "thumb_up" to Icons.Rounded.ThumbUp,
        "attach" to Icons.Rounded.AttachFile,
        "link" to Icons.Rounded.Link,
        "language" to Icons.Rounded.Language,
        "dark_mode" to Icons.Rounded.DarkMode,
        "light_mode" to Icons.Rounded.LightMode,
        "payments" to Icons.Rounded.Payments,
        "credit_card" to Icons.Rounded.CreditCard,
        "local_offer" to Icons.Rounded.LocalOffer,
        "restaurant" to Icons.Rounded.Restaurant,
        "flight" to Icons.Rounded.Flight,
        "directions_car" to Icons.Rounded.DirectionsCar,
        "fitness" to Icons.Rounded.FitnessCenter,
        "pets" to Icons.Rounded.Pets,
        "work" to Icons.Rounded.Work,
        "school" to Icons.Rounded.School,
        "logout" to Icons.AutoMirrored.Rounded.Logout,
        "keyboard_arrow_down" to Icons.Rounded.KeyboardArrowDown,
        "mood" to Icons.Rounded.Mood,
        "location_on" to Icons.Rounded.LocationOn,
    )

    val names: List<String> get() = vectors.keys.toList()

    private val cache = HashMap<String, Path>()

    /** The icon as one path in its 24×24 viewport, or null for "none"/unknown names. */
    fun path(name: String?): Path? {
        if (name.isNullOrBlank() || name == "none") return null
        cache[name]?.let { return it }
        val vector = vectors[name] ?: return null
        val combined = Path()
        fun walk(group: VectorGroup) {
            for (child in group) {
                when (child) {
                    is VectorPath -> combined.addPath(child.pathData.toPath())
                    is VectorGroup -> walk(child)
                }
            }
        }
        walk(vector.root)
        cache[name] = combined
        return combined
    }
}

/** Draws icon [name] into a [size]×[size] square at [topLeft]. */
fun DrawScope.drawIcon(name: String?, topLeft: Offset, size: Float, color: Color) {
    val path = IconPaths.path(name) ?: return
    translate(topLeft.x, topLeft.y) {
        scale(size / 24f, size / 24f, pivot = Offset.Zero) {
            drawPath(path, color)
        }
    }
}
