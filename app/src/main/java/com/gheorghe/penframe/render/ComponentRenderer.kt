package com.gheorghe.penframe.render

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextAlign
import com.gheorghe.penframe.model.ComponentKind
import com.gheorghe.penframe.model.ComponentKind.*
import com.gheorghe.penframe.model.Node
import com.gheorghe.penframe.model.toColor
import kotlin.math.max
import kotlin.math.min

const val DEFAULT_PRIMARY = 0xFF6750A4

/** Material 3 roles derived from one primary color, so recoloring a component recolors it all. */
class Palette(val primary: Color) {
    private fun tint(base: Long, amount: Float) = lerp(Color(base.toInt()), primary, amount)
    val onPrimary = if (primary.luminance() > 0.55f) Color(0xFF1D1B20) else Color.White
    val primaryContainer = lerp(primary, Color.White, 0.76f)
    val onPrimaryContainer = lerp(primary, Color.Black, 0.55f)
    val secondaryContainer = lerp(lerp(primary, Color(0xFF8A8A8A), 0.5f), Color.White, 0.74f)
    val onSecondaryContainer = lerp(primary, Color(0xFF1D1B20), 0.7f)
    val tertiaryContainer = Color(0xFFFFD8E4)
    val onTertiaryContainer = Color(0xFF31111D)
    val surface = tint(0xFFFEFBFF, 0.02f)
    val surfaceLow = tint(0xFFF7F2FA, 0.03f)
    val surfaceContainer = tint(0xFFF3EDF7, 0.03f)
    val surfaceHigh = tint(0xFFECE6F0, 0.04f)
    val surfaceHighest = tint(0xFFE6E0E9, 0.04f)
    val onSurface = Color(0xFF1D1B20)
    val onSurfaceVariant = Color(0xFF49454F)
    val outline = Color(0xFF79747E)
    val outlineVariant = Color(0xFFCAC4D0)
    val error = Color(0xFFB3261E)
    val inverseSurface = Color(0xFF322F35)
    val inverseOnSurface = Color(0xFFF5EFF7)
    val inversePrimary = lerp(primary, Color.White, 0.55f)
    val disabledContainer = onSurface.copy(alpha = 0.12f)
    val disabledContent = onSurface.copy(alpha = 0.38f)
}

// MARK: Small drawing helpers (all in the component's local coordinates)

private fun DrawScope.rr(x: Float, y: Float, w: Float, h: Float, r: Float, c: Color) =
    drawRoundRect(c, Offset(x, y), Size(w, h), CornerRadius(r, r))

private fun DrawScope.rrLine(x: Float, y: Float, w: Float, h: Float, r: Float, c: Color, width: Float = 1f) =
    drawRoundRect(c, Offset(x + width / 2, y + width / 2), Size(w - width, h - width), CornerRadius(r, r), style = Stroke(width))

private fun DrawScope.dot(cx: Float, cy: Float, r: Float, c: Color) = drawCircle(c, r, Offset(cx, cy))

private fun DrawScope.icon(name: String, cx: Float, cy: Float, size: Float, c: Color) =
    drawIcon(name, Offset(cx - size / 2, cy - size / 2), size, c)

/** A cheap, soft elevation shadow that also works in software (export) rendering. */
private fun DrawScope.elevation(x: Float, y: Float, w: Float, h: Float, r: Float, level: Float = 1f) {
    for (i in 1..4) {
        val s = i * 1.1f * level
        drawRoundRect(
            Color.Black.copy(alpha = 0.035f * level),
            Offset(x - s * 0.4f, y + s * 0.6f), Size(w + s * 0.8f, h + s * 0.6f), CornerRadius(r + s, r + s),
        )
    }
}

private fun DrawScope.topRounded(x: Float, y: Float, w: Float, h: Float, r: Float, c: Color) {
    val path = Path().apply {
        addRoundRect(RoundRect(x, y, x + w, y + h, topLeftCornerRadius = CornerRadius(r), topRightCornerRadius = CornerRadius(r)))
    }
    drawPath(path, c)
}

private fun DrawScope.checkMark(x: Float, y: Float, s: Float, c: Color, width: Float = 2f) {
    val path = Path().apply {
        moveTo(x + s * 0.22f, y + s * 0.52f)
        lineTo(x + s * 0.42f, y + s * 0.72f)
        lineTo(x + s * 0.8f, y + s * 0.3f)
    }
    drawPath(path, c, style = Stroke(width, cap = StrokeCap.Round))
}

private fun items(s: String) = s.split(",").map { it.trim() }.filter { it.isNotEmpty() }
private fun Node.bool(key: String) = prop(key).equals("true", ignoreCase = true)
private fun Node.num(key: String, fallback: Float = 0f) = prop(key).toFloatOrNull() ?: fallback
private fun Node.int(key: String, fallback: Int = 0) = prop(key).toFloatOrNull()?.toInt() ?: fallback

/** Draws a component instance into its own box (0,0)-(w,h). */
fun DrawScope.drawComponent(node: Node, ctx: RenderContext) {
    val kind = ComponentKind.of(node.component) ?: return
    val p = Palette(node.fill?.toColor() ?: Color(DEFAULT_PRIMARY.toInt()))
    val w = node.w
    val h = node.h
    when (kind) {
        BUTTON_FILLED, BUTTON_TONAL, BUTTON_OUTLINED, BUTTON_TEXT, BUTTON_ELEVATED -> button(kind, node, p, ctx, w, h)
        ICON_BUTTON -> {
            val d = min(w, h) * 0.84f
            val cx = w / 2; val cy = h / 2
            val selected = node.bool("selected")
            val content = when (node.prop("style")) {
                "filled" -> { dot(cx, cy, d / 2, p.primary); p.onPrimary }
                "tonal" -> { dot(cx, cy, d / 2, p.secondaryContainer); p.onSecondaryContainer }
                "outlined" -> { drawCircle(p.outlineVariant, d / 2 - 0.5f, Offset(cx, cy), style = Stroke(1f)); p.onSurfaceVariant }
                else -> if (selected) p.primary else p.onSurfaceVariant
            }
            icon(node.prop("icon"), cx, cy, d * 0.6f, content)
        }
        FAB -> {
            val r = min(w, h) * when (node.prop("size")) { "small" -> 0.3f; "large" -> 0.29f; else -> 0.286f }
            val (bg, fg) = when (node.prop("style")) {
                "secondary" -> p.secondaryContainer to p.onSecondaryContainer
                "tertiary" -> p.tertiaryContainer to p.onTertiaryContainer
                "surface" -> p.surfaceHigh to p.primary
                else -> p.primaryContainer to p.onPrimaryContainer
            }
            elevation(0f, 0f, w, h, r, 1.6f)
            rr(0f, 0f, w, h, r, bg)
            icon(node.prop("icon"), w / 2, h / 2, if (node.prop("size") == "large") 36f else 24f, fg)
        }
        FAB_EXTENDED -> {
            elevation(0f, 0f, w, h, 16f, 1.6f)
            rr(0f, 0f, w, h, 16f, p.primaryContainer)
            val label = node.prop("label")
            val tw = ctx.width(label, 16f, 500)
            val iconName = node.prop("icon")
            val total = tw + if (iconName != "none") 36f else 0f
            var x = (w - total) / 2
            if (iconName != "none") { icon(iconName, x + 12, h / 2, 24f, p.onPrimaryContainer); x += 36f }
            textMid(ctx, label, x, h / 2, 16f, p.onPrimaryContainer, 500)
        }
        SEGMENTED_BUTTON -> {
            val list = items(node.prop("items")).ifEmpty { listOf("One") }
            val sel = node.int("selected")
            val seg = w / list.size
            rrLine(0f, 0f, w, h, h / 2, p.outline)
            list.forEachIndexed { i, label ->
                val x = i * seg
                if (i == sel) {
                    val path = Path().apply {
                        addRoundRect(RoundRect(x + 0.5f, 0.5f, x + seg - 0.5f, h - 0.5f,
                            topLeftCornerRadius = CornerRadius(if (i == 0) h / 2 else 0f),
                            bottomLeftCornerRadius = CornerRadius(if (i == 0) h / 2 else 0f),
                            topRightCornerRadius = CornerRadius(if (i == list.lastIndex) h / 2 else 0f),
                            bottomRightCornerRadius = CornerRadius(if (i == list.lastIndex) h / 2 else 0f)))
                    }
                    drawPath(path, p.secondaryContainer)
                }
                if (i > 0) drawLine(p.outline, Offset(x, 0f), Offset(x, h), 1f)
                val tw = ctx.width(label, 14f, 500) + if (i == sel) 26f else 0f
                var tx = x + (seg - tw) / 2
                if (i == sel) { checkMark(tx, h / 2 - 9, 18f, p.onSecondaryContainer); tx += 26f }
                textMid(ctx, label, tx, h / 2, 14f, if (i == sel) p.onSecondaryContainer else p.onSurface, 500, seg - 8)
            }
        }
        SPLIT_BUTTON -> {
            val tw = w - 46
            val left = Path().apply { addRoundRect(RoundRect(0f, 0f, tw, h, topLeftCornerRadius = CornerRadius(h / 2), bottomLeftCornerRadius = CornerRadius(h / 2), topRightCornerRadius = CornerRadius(4f), bottomRightCornerRadius = CornerRadius(4f))) }
            drawPath(left, p.primary)
            val right = Path().apply { addRoundRect(RoundRect(tw + 2, 0f, w, h, topRightCornerRadius = CornerRadius(h / 2), bottomRightCornerRadius = CornerRadius(h / 2), topLeftCornerRadius = CornerRadius(4f), bottomLeftCornerRadius = CornerRadius(4f))) }
            drawPath(right, p.primary)
            textCenter(ctx, node.prop("label"), tw / 2, h / 2, 14f, p.onPrimary, 500, tw - 16)
            icon("keyboard_arrow_down", tw + 2 + 22, h / 2, 20f, p.onPrimary)
        }

        CHECKBOX -> checkbox(node.prop("state"), node.bool("error"), node.bool("enabled"), w / 2, h / 2, p)
        CHECKBOX_LABEL -> {
            checkbox(node.prop("state"), false, node.bool("enabled"), 20f, h / 2, p)
            textMid(ctx, node.prop("label"), 44f, h / 2, 16f, if (node.bool("enabled")) p.onSurface else p.disabledContent, maxWidth = w - 44)
        }
        RADIO -> radio(node.bool("selected"), node.bool("enabled"), w / 2, h / 2, p)
        RADIO_LABEL -> {
            radio(node.bool("selected"), node.bool("enabled"), 20f, h / 2, p)
            textMid(ctx, node.prop("label"), 44f, h / 2, 16f, if (node.bool("enabled")) p.onSurface else p.disabledContent, maxWidth = w - 44)
        }
        SWITCH -> switch(node.bool("checked"), node.bool("icon"), node.bool("enabled"), 0f, 0f, w, h, p)
        SWITCH_LABEL -> {
            val sup = node.prop("supporting")
            val enabled = node.bool("enabled")
            if (sup.isEmpty()) {
                textMid(ctx, node.prop("label"), 16f, h / 2, 16f, if (enabled) p.onSurface else p.disabledContent, maxWidth = w - 92)
            } else {
                text(ctx, node.prop("label"), 16f, h / 2 - 22, 16f, if (enabled) p.onSurface else p.disabledContent, maxWidth = w - 92)
                text(ctx, sup, 16f, h / 2 + 1, 14f, p.onSurfaceVariant, maxWidth = w - 92)
            }
            switch(node.bool("checked"), false, enabled, w - 68, h / 2 - 16, 52f, 32f, p)
        }
        SLIDER -> slider(listOf(node.num("value", 40f)), node.int("steps"), node.bool("enabled"), w, h, p)
        RANGE_SLIDER -> slider(listOf(node.num("start", 25f), node.num("end", 70f)), 0, node.bool("enabled"), w, h, p)
        CHIP_ASSIST -> chip(ctx, p, w, h, node.prop("label"), leading = node.prop("icon"), selected = false)
        CHIP_FILTER -> chip(ctx, p, w, h, node.prop("label"), leading = if (node.bool("selected")) "check" else "none", selected = node.bool("selected"))
        CHIP_INPUT -> {
            rrLine(0f, 0f, w, h, 8f, p.outlineVariant)
            var x = 8f
            if (node.bool("avatar")) {
                dot(16f, h / 2, 12f, p.primaryContainer)
                textCenter(ctx, node.prop("label").take(1).uppercase(), 16f, h / 2, 12f, p.onPrimaryContainer, 600)
                x = 34f
            }
            textMid(ctx, node.prop("label"), x, h / 2, 14f, p.onSurfaceVariant, 500, w - x - 30)
            icon("close", w - 17, h / 2, 18f, p.onSurfaceVariant)
        }
        CHIP_SUGGESTION -> chip(ctx, p, w, h, node.prop("label"), leading = "none", selected = false)
        CHIP_GROUP -> {
            val list = items(node.prop("items"))
            val sel = node.int("selected")
            var x = 0f
            list.forEachIndexed { i, label ->
                val cw = ctx.width(label, 14f, 500) + 32f + if (i == sel) 26f else 0f
                if (x + cw > w + 1) return@forEachIndexed
                drawContextChip(ctx, p, x, cw, h, label, i == sel)
                x += cw + 8f
            }
        }

        TEXT_FIELD_FILLED, TEXT_FIELD_OUTLINED, DROPDOWN -> textField(kind, node, p, ctx, w, h)
        TEXT_AREA -> {
            rrLine(0f, 0f, w, h, 4f, p.outline)
            rr(10f, -8f, ctx.width(node.prop("label"), 12f) + 8f, 14f, 2f, Color.White)
            text(ctx, node.prop("label"), 14f, -8f, 12f, p.onSurfaceVariant)
            val value = node.prop("value")
            val layout = ctx.layout(value.ifEmpty { " " }, 16f, p.onSurface, maxWidth = w - 32, maxLines = max(1, ((h - 40) / 20).toInt()))
            drawText(layout, topLeft = Offset(16f, 16f))
            val counter = node.prop("counter")
            text(ctx, counter, w - 16 - ctx.width(counter, 12f), h + 4, 12f, p.onSurfaceVariant)
        }
        SEARCH_BAR -> {
            rr(0f, 0f, w, h, h / 2, p.surfaceHigh)
            icon("menu", 28f, h / 2, 24f, p.onSurface)
            textMid(ctx, node.prop("placeholder"), 56f, h / 2, 16f, p.onSurfaceVariant, maxWidth = w - 120)
            if (node.bool("avatar")) {
                dot(w - 32, h / 2, 15f, p.primaryContainer)
                icon("person", w - 32, h / 2, 20f, p.onPrimaryContainer)
            } else icon("search", w - 32, h / 2, 24f, p.onSurfaceVariant)
        }

        TOP_APP_BAR -> {
            rr(0f, 0f, w, h, 0f, p.surface)
            val style = node.prop("style")
            val rowY = 32f
            val nav = node.prop("navIcon")
            if (nav != "none") icon(nav, 28f, rowY, 24f, p.onSurface)
            val actions = node.int("actions").coerceIn(0, 3)
            val names = listOf("more_vert", "favorite_border", "search").take(actions)
            names.forEachIndexed { i, n -> icon(n, w - 28 - i * 48, rowY, 24f, p.onSurfaceVariant) }
            val title = node.prop("title")
            when (style) {
                "center" -> textCenter(ctx, title, w / 2, rowY, 22f, p.onSurface, maxWidth = w - 140)
                "medium" -> text(ctx, title, 16f, h - 48, 24f, p.onSurface, maxWidth = w - 32)
                "large" -> text(ctx, title, 16f, h - 60, 28f, p.onSurface, maxWidth = w - 32)
                else -> textMid(ctx, title, if (nav != "none") 56f else 16f, rowY, 22f, p.onSurface, maxWidth = w - 72 - actions * 48)
            }
        }
        BOTTOM_APP_BAR -> {
            rr(0f, 0f, w, h, 0f, p.surfaceContainer)
            listOf("search", "favorite_border", "mic", "image").take(node.int("actions").coerceIn(0, 4))
                .forEachIndexed { i, n -> icon(n, 28f + i * 48, h / 2, 24f, p.onSurfaceVariant) }
            if (node.bool("fab")) {
                rr(w - 72, h / 2 - 28, 56f, 56f, 16f, p.primaryContainer)
                icon("add", w - 44, h / 2, 24f, p.onPrimaryContainer)
            }
        }
        NAVIGATION_BAR -> {
            rr(0f, 0f, w, h, 0f, p.surfaceContainer)
            val labels = items(node.prop("items"))
            val icons = items(node.prop("icons"))
            val sel = node.int("selected")
            val slot = w / max(1, labels.size)
            labels.forEachIndexed { i, label ->
                val cx = slot * i + slot / 2
                if (i == sel) rr(cx - 32, 12f, 64f, 32f, 16f, p.secondaryContainer)
                icon(icons.getOrElse(i) { "home" }, cx, 28f, 24f, if (i == sel) p.onSecondaryContainer else p.onSurfaceVariant)
                if (node.bool("badge") && i == 1) dot(cx + 10, 18f, 3f, p.error)
                textCenter(ctx, label, cx, 58f, 12f, if (i == sel) p.onSurface else p.onSurfaceVariant, if (i == sel) 600 else 500, slot - 4)
            }
        }
        NAVIGATION_RAIL -> {
            rr(0f, 0f, w, h, 0f, p.surface)
            val cx = w / 2
            icon("menu", cx, 28f, 24f, p.onSurfaceVariant)
            var y = 64f
            if (node.bool("fab")) { rr(cx - 28, y, 56f, 56f, 16f, p.primaryContainer); icon("edit", cx, y + 28, 24f, p.onPrimaryContainer); y += 96f } else y += 16f
            val labels = items(node.prop("items"))
            val icons = items(node.prop("icons"))
            val sel = node.int("selected")
            labels.forEachIndexed { i, label ->
                if (i == sel) rr(cx - 28, y, 56f, 32f, 16f, p.secondaryContainer)
                icon(icons.getOrElse(i) { "home" }, cx, y + 16, 24f, if (i == sel) p.onSecondaryContainer else p.onSurfaceVariant)
                textCenter(ctx, label, cx, y + 44, 12f, if (i == sel) p.onSurface else p.onSurfaceVariant, if (i == sel) 600 else 500, w - 4)
                y += 72f
            }
        }
        NAVIGATION_DRAWER -> {
            val path = Path().apply { addRoundRect(RoundRect(0f, 0f, w, h, topRightCornerRadius = CornerRadius(16f), bottomRightCornerRadius = CornerRadius(16f))) }
            drawPath(path, p.surfaceLow)
            text(ctx, node.prop("header"), 28f, 22f, 14f, p.onSurfaceVariant, 600)
            val labels = items(node.prop("items"))
            val icons = items(node.prop("icons"))
            val sel = node.int("selected")
            labels.forEachIndexed { i, label ->
                val y = 56f + i * 56f
                if (i == sel) rr(12f, y, w - 24, 56f, 28f, p.secondaryContainer)
                val c = if (i == sel) p.onSecondaryContainer else p.onSurfaceVariant
                icon(icons.getOrElse(i) { "inbox" }, 40f, y + 28, 24f, c)
                textMid(ctx, label, 68f, y + 28, 14f, c, 500, w - 100)
            }
        }
        TABS -> {
            rr(0f, 0f, w, h, 0f, p.surface)
            val list = items(node.prop("items")).ifEmpty { listOf("Tab") }
            val sel = node.int("selected")
            val tab = w / list.size
            drawLine(p.surfaceHighest, Offset(0f, h - 0.5f), Offset(w, h - 0.5f), 1f)
            list.forEachIndexed { i, label ->
                val cx = tab * i + tab / 2
                textCenter(ctx, label, cx, h / 2, 14f, if (i == sel) p.primary else p.onSurfaceVariant, 500, tab - 8)
                if (i == sel) {
                    if (node.prop("style") == "secondary") drawRect(p.primary, Offset(tab * i, h - 2), Size(tab, 2f))
                    else {
                        val tw = ctx.width(label, 14f, 500)
                        rr(cx - tw / 2, h - 3, tw, 3f, 1.5f, p.primary)
                    }
                }
            }
        }
        BREADCRUMB -> {
            var x = 0f
            val list = items(node.prop("items"))
            list.forEachIndexed { i, label ->
                val last = i == list.lastIndex
                x += textMid(ctx, label, x, h / 2, 14f, if (last) p.onSurface else p.primary, if (last) 500 else 400)
                if (!last) { icon("chevron_right", x + 12, h / 2, 18f, p.onSurfaceVariant); x += 24f }
            }
        }
        PAGE_INDICATOR -> {
            val count = node.int("count", 4).coerceIn(1, 10)
            val sel = node.int("selected")
            val total = count * 8f + (count - 1) * 8f + 8f
            var x = (w - total) / 2
            for (i in 0 until count) {
                if (i == sel) { rr(x, h / 2 - 4, 16f, 8f, 4f, p.primary); x += 24f } else { dot(x + 4, h / 2, 4f, p.outlineVariant); x += 16f }
            }
        }

        CARD_FILLED, CARD_ELEVATED, CARD_OUTLINED -> {
            when (kind) {
                CARD_FILLED -> rr(0f, 0f, w, h, 12f, p.surfaceHighest)
                CARD_ELEVATED -> { elevation(0f, 0f, w, h, 12f); rr(0f, 0f, w, h, 12f, p.surfaceLow) }
                else -> { rr(0f, 0f, w, h, 12f, p.surface); rrLine(0f, 0f, w, h, 12f, p.outlineVariant) }
            }
            var y = 16f
            if (node.bool("media")) {
                val mh = h * 0.45f
                topRounded(0f, 0f, w, mh, 12f, p.primaryContainer)
                icon("image", w / 2, mh / 2, 40f, p.onPrimaryContainer.copy(alpha = 0.6f))
                y = mh + 14
            }
            text(ctx, node.prop("title"), 16f, y, 16f, p.onSurface, 500, w - 32)
            text(ctx, node.prop("subtitle"), 16f, y + 24, 14f, p.onSurfaceVariant, maxWidth = w - 32)
            val action = node.prop("action")
            if (action.isNotEmpty()) {
                val bw = ctx.width(action, 14f, 500) + 48
                rr(w - 16 - bw, h - 56, bw, 40f, 20f, p.primary)
                textCenter(ctx, action, w - 16 - bw / 2, h - 36, 14f, p.onPrimary, 500)
            }
        }
        LIST_ITEM -> listItem(ctx, p, node.prop("headline"), node.prop("supporting"), node.prop("leading"),
            node.prop("trailing"), node.prop("trailingIcon"), 0f, w, h)
        LIST -> {
            val list = items(node.prop("items"))
            val rowH = h / max(1, list.size)
            list.forEachIndexed { i, label ->
                val y = i * rowH
                dot(36f, y + rowH / 2, 20f, p.primaryContainer)
                textCenter(ctx, label.take(1).uppercase(), 36f, y + rowH / 2, 16f, p.onPrimaryContainer, 500)
                if (node.bool("supporting")) {
                    text(ctx, label, 72f, y + rowH / 2 - 21, 16f, p.onSurface, maxWidth = w - 88)
                    text(ctx, "Supporting text", 72f, y + rowH / 2 + 1, 14f, p.onSurfaceVariant, maxWidth = w - 88)
                } else textMid(ctx, label, 72f, y + rowH / 2, 16f, p.onSurface, maxWidth = w - 88)
                if (node.bool("dividers") && i < list.lastIndex) drawLine(p.outlineVariant, Offset(16f, y + rowH), Offset(w - 16, y + rowH), 1f)
            }
        }
        DIVIDER -> drawRect(p.outlineVariant, Offset(0f, 0f), Size(w, max(1f, h)))
        BOTTOM_SHEET -> {
            topRounded(0f, 0f, w, h, 28f, p.surfaceLow)
            rr(w / 2 - 16, 22f, 32f, 4f, 2f, p.onSurfaceVariant.copy(alpha = 0.4f))
            text(ctx, node.prop("title"), 24f, 48f, 16f, p.onSurface, 500)
            val names = listOf("content_copy", "share", "email", "chat", "bookmark", "link", "download", "more_horiz")
            val n = node.int("items", 4).coerceIn(0, 8)
            val slot = (w - 32) / 4
            for (i in 0 until n) {
                val cx = 16 + slot * (i % 4) + slot / 2
                val cy = 112f + (i / 4) * 96f
                dot(cx, cy, 28f, p.secondaryContainer)
                icon(names[i], cx, cy, 24f, p.onSecondaryContainer)
                textCenter(ctx, names[i].replace('_', ' ').replaceFirstChar { it.uppercase() }, cx, cy + 44, 12f, p.onSurfaceVariant, maxWidth = slot)
            }
        }
        SIDE_SHEET -> {
            val path = Path().apply { addRoundRect(RoundRect(0f, 0f, w, h, topLeftCornerRadius = CornerRadius(16f), bottomLeftCornerRadius = CornerRadius(16f))) }
            drawPath(path, p.surfaceLow)
            text(ctx, node.prop("title"), 24f, 24f, 22f, p.onSurfaceVariant)
            icon("close", w - 32, 38f, 24f, p.onSurfaceVariant)
            for (i in 0 until 5) {
                rr(24f, 90f + i * 52, w - 48, 36f, 8f, p.surfaceHighest)
            }
        }
        DIALOG -> {
            rr(0f, 0f, w, h, 28f, p.surfaceHigh)
            var y = 24f
            val iconName = node.prop("icon")
            val centered = iconName != "none"
            if (centered) { icon(iconName, w / 2, y + 12, 24f, p.primary); y += 40f }
            val title = ctx.layout(node.prop("title"), 24f, p.onSurface, maxWidth = w - 48, maxLines = 2,
                align = if (centered) TextAlign.Center else TextAlign.Start)
            drawText(title, topLeft = Offset(24f, y))
            y += title.size.height + 16
            val body = ctx.layout(node.prop("body"), 14f, p.onSurfaceVariant, maxWidth = w - 48, maxLines = 6, lineHeight = 1.43f)
            drawText(body, topLeft = Offset(24f, y))
            var x = w - 24
            val confirm = node.prop("confirm")
            val dismiss = node.prop("dismiss")
            if (confirm.isNotEmpty()) { x -= ctx.width(confirm, 14f, 500); text(ctx, confirm, x, h - 24 - 20, 14f, p.primary, 500) }
            if (dismiss.isNotEmpty()) { x -= 24 + ctx.width(dismiss, 14f, 500); text(ctx, dismiss, x, h - 24 - 20, 14f, p.primary, 500) }
        }
        SNACKBAR -> {
            elevation(0f, 0f, w, h, 4f)
            rr(0f, 0f, w, h, 4f, p.inverseSurface)
            val action = node.prop("action")
            val aw = ctx.width(action, 14f, 500)
            textMid(ctx, node.prop("text"), 16f, h / 2, 14f, p.inverseOnSurface, maxWidth = w - aw - 48)
            if (action.isNotEmpty()) textMid(ctx, action, w - 16 - aw, h / 2, 14f, p.inversePrimary, 500)
        }
        TOOLTIP -> {
            if (node.prop("style") == "rich") {
                elevation(0f, 0f, w, h, 12f)
                rr(0f, 0f, w, h, 12f, p.surfaceContainer)
                textCenter(ctx, node.prop("text"), w / 2, h / 2, 14f, p.onSurfaceVariant, maxWidth = w - 16)
            } else {
                rr(0f, 0f, w, h, 4f, p.inverseSurface)
                textCenter(ctx, node.prop("text"), w / 2, h / 2, 12f, p.inverseOnSurface, maxWidth = w - 16)
            }
        }
        MENU -> {
            elevation(0f, 0f, w, h, 4f, 1.4f)
            rr(0f, 0f, w, h, 4f, p.surfaceContainer)
            val labels = items(node.prop("items"))
            val icons = items(node.prop("icons"))
            labels.forEachIndexed { i, label ->
                val cy = 8f + i * 48f + 24f
                val ic = icons.getOrNull(i)
                val x = if (ic != null) { icon(ic, 24f, cy, 24f, p.onSurfaceVariant); 52f } else 12f
                textMid(ctx, label, x, cy, 14f, p.onSurface, maxWidth = w - x - 12)
            }
        }
        ACCORDION -> {
            val expanded = node.bool("expanded")
            textMid(ctx, node.prop("title"), 16f, 28f, 16f, p.onSurface, 500, w - 64)
            icon(if (expanded) "expand_less" else "expand_more", w - 28, 28f, 24f, p.onSurfaceVariant)
            if (expanded) {
                val body = ctx.layout(node.prop("body"), 14f, p.onSurfaceVariant, maxWidth = w - 32, maxLines = max(1, ((h - 72) / 20).toInt()))
                drawText(body, topLeft = Offset(16f, 60f))
            }
            drawLine(p.outlineVariant, Offset(0f, h - 0.5f), Offset(w, h - 0.5f), 1f)
        }

        BADGE -> {
            val count = node.prop("count")
            if (count.isEmpty()) dot(w / 2, h / 2, min(w, h) / 2.6f, p.error)
            else {
                rr(0f, 0f, w, h, h / 2, p.error)
                textCenter(ctx, count, w / 2, h / 2, 11f, Color.White, 500)
            }
        }
        PROGRESS_LINEAR -> {
            val v = (node.num("value", 60f) / 100f).coerceIn(0f, 1f)
            val th = max(2f, h)
            val split = w * v
            rr(0f, 0f, split, th, th / 2, p.primary)
            if (split + 4 < w) rr(split + 4, 0f, w - split - 4, th, th / 2, p.secondaryContainer)
            dot(w - th / 2, th / 2, th / 2, p.primary)
        }
        PROGRESS_CIRCULAR -> {
            val v = (node.num("value", 70f) / 100f).coerceIn(0f, 1f)
            val stroke = max(3f, min(w, h) / 12f)
            val inset = stroke / 2
            val s = Size(w - stroke, h - stroke)
            drawArc(p.secondaryContainer, -90f + 360f * v + 8f, 360f * (1 - v) - 16f, false, Offset(inset, inset), s, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(p.primary, -90f, 360f * v, false, Offset(inset, inset), s, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        BANNER -> {
            rr(0f, 0f, w, h, 0f, p.surfaceLow)
            dot(36f, 36f, 20f, p.primary)
            icon(node.prop("icon"), 36f, 36f, 24f, p.onPrimary)
            val body = ctx.layout(node.prop("text"), 14f, p.onSurface, maxWidth = w - 88, maxLines = 2, lineHeight = 1.43f)
            drawText(body, topLeft = Offset(72f, 18f))
            val action = node.prop("action")
            text(ctx, action, w - 24 - ctx.width(action, 14f, 500), h - 36, 14f, p.primary, 500)
            drawLine(p.outlineVariant, Offset(0f, h - 0.5f), Offset(w, h - 0.5f), 1f)
        }
        RATING -> {
            val count = node.int("count", 5).coerceIn(1, 10)
            val value = node.num("value", 4f)
            val size = min(h, w / count)
            for (i in 0 until count) {
                val filled = i < value
                icon(if (filled) "star" else "star", size * i + size / 2, h / 2, size * 0.9f,
                    if (filled) Color(0xFFFFB300) else p.outlineVariant)
            }
        }

        DATE_PICKER -> datePicker(ctx, p, node, w, h)
        TIME_PICKER -> timePicker(ctx, p, node, w, h)
        STEPPER -> {
            rrLine(0f, 0f, w, h, h / 2, p.outline)
            drawLine(p.onSurfaceVariant, Offset(h / 2 - 6, h / 2), Offset(h / 2 + 6, h / 2), 2f, StrokeCap.Round)
            textCenter(ctx, node.prop("value"), w / 2, h / 2, 16f, p.onSurface, 500)
            icon("add", w - h / 2, h / 2, 20f, p.onSurfaceVariant)
        }

        ICON -> icon(node.prop("icon"), w / 2, h / 2, min(w, h), node.fill?.toColor() ?: p.onSurfaceVariant)
        AVATAR -> {
            val r = min(w, h) / 2
            when (node.prop("style")) {
                "icon" -> { dot(w / 2, h / 2, r, p.primaryContainer); icon("person", w / 2, h / 2, r * 1.2f, p.onPrimaryContainer) }
                "photo" -> {
                    drawCircle(Brush.linearGradient(listOf(p.primaryContainer, p.primary)), r, Offset(w / 2, h / 2))
                    icon("person", w / 2, h / 2 + r * 0.15f, r * 1.5f, Color.White.copy(alpha = 0.85f))
                }
                else -> { dot(w / 2, h / 2, r, p.primaryContainer); textCenter(ctx, node.prop("initials"), w / 2, h / 2, r * 0.8f, p.onPrimaryContainer, 500) }
            }
        }
        IMAGE_PLACEHOLDER -> {
            rr(0f, 0f, w, h, 12f, p.surfaceHighest)
            icon("image", w / 2, h / 2, min(48f, min(w, h) * 0.5f), p.onSurfaceVariant.copy(alpha = 0.6f))
            val label = node.prop("label")
            if (label.isNotEmpty()) text(ctx, label, 0f, h + 6, 12f, p.onSurfaceVariant, maxWidth = w)
        }
        CAROUSEL -> {
            val n = node.int("items", 3).coerceIn(1, 5)
            val weights = listOf(0.56f, 0.28f, 0.16f, 0.12f, 0.1f).take(n)
            val total = weights.sum()
            var x = 0f
            weights.forEachIndexed { i, wt ->
                val iw = (w - (n - 1) * 8) * wt / total
                rr(x, 0f, iw, h, 28f, lerp(p.primaryContainer, p.primary, i * 0.18f))
                if (iw > 60) icon("image", x + iw / 2, h / 2, 32f, Color.White.copy(alpha = 0.7f))
                x += iw + 8
            }
        }
        VIDEO_PLAYER -> {
            rr(0f, 0f, w, h, 12f, Color(0xFF111111))
            dot(w / 2, h / 2, 28f, Color.White.copy(alpha = 0.18f))
            icon("play", w / 2, h / 2, 36f, Color.White)
            val v = (node.num("progress", 35f) / 100f).coerceIn(0f, 1f)
            rr(16f, h - 22, w - 32, 4f, 2f, Color.White.copy(alpha = 0.3f))
            rr(16f, h - 22, (w - 32) * v, 4f, 2f, p.primaryContainer)
            dot(16 + (w - 32) * v, h - 20, 6f, Color.White)
            text(ctx, node.prop("time"), 16f, h - 46, 12f, Color.White)
        }
        MAP -> {
            rr(0f, 0f, w, h, 12f, Color(0xFFE8EFE2))
            drawLine(Color.White, Offset(0f, h * 0.35f), Offset(w, h * 0.55f), 10f)
            drawLine(Color.White, Offset(w * 0.3f, 0f), Offset(w * 0.45f, h), 8f)
            drawLine(Color(0xFFFFE08A), Offset(w * 0.7f, 0f), Offset(w * 0.6f, h), 6f)
            rr(w * 0.08f, h * 0.62f, w * 0.18f, h * 0.24f, 8f, Color(0xFFC8E6C9))
            drawLine(Color(0xFFB3D9F2), Offset(w * 0.75f, h), Offset(w, h * 0.7f), 18f)
            if (node.bool("pin")) icon("location_on", w * 0.52f, h * 0.42f, 40f, Color(0xFFE53935))
        }
        CHART_BAR -> {
            val values = node.prop("values").split(",").mapNotNull { it.trim().toFloatOrNull() }.ifEmpty { listOf(1f) }
            val maxV = values.max().coerceAtLeast(1f)
            drawLine(p.outlineVariant, Offset(24f, h - 20), Offset(w, h - 20), 1f)
            for (i in 1..3) drawLine(p.outlineVariant.copy(alpha = 0.5f), Offset(24f, (h - 20) * i / 4f), Offset(w, (h - 20) * i / 4f), 1f)
            val slot = (w - 32) / values.size
            values.forEachIndexed { i, v ->
                val bh = (h - 32) * (v / maxV)
                rr(32 + slot * i + slot * 0.2f, h - 20 - bh, slot * 0.6f, bh, 4f, if (i == values.indexOf(maxV)) p.primary else p.primaryContainer)
            }
        }

        STATUS_BAR -> {
            node.props["bg"]?.toLongOrNull()?.let { drawRect(it.toColor(), size = Size(w, h)) }
            val c = if (node.bool("dark")) Color.White else Color(0xFF1D1B20)
            textMid(ctx, node.prop("time"), 24f, h / 2, 14f, c, 500)
            icon("battery", w - 30, h / 2, 18f, c)
            icon("signal", w - 52, h / 2, 16f, c)
            icon("wifi", w - 74, h / 2, 16f, c)
        }
        SYSTEM_NAV -> {
            val c = if (node.bool("dark")) Color.White else Color(0xFF1D1B20)
            if (node.prop("style") == "buttons") {
                val cy = h / 2
                val back = Path().apply { moveTo(w * 0.25f + 6, cy - 7); lineTo(w * 0.25f - 6, cy); lineTo(w * 0.25f + 6, cy + 7); close() }
                drawPath(back, c, style = Stroke(2f))
                drawCircle(c, 7f, Offset(w / 2, cy), style = Stroke(2f))
                drawRoundRect(c, Offset(w * 0.75f - 7, cy - 7), Size(14f, 14f), CornerRadius(2f), style = Stroke(2f))
            } else rr(w / 2 - 54, h / 2 - 2, 108f, 4f, 2f, c.copy(alpha = 0.7f))
        }
        KEYBOARD -> keyboard(ctx, p, node.bool("dark"), w, h)
        NOTIFICATION -> {
            elevation(0f, 0f, w, h, 24f, 0.8f)
            rr(0f, 0f, w, h, 24f, p.surfaceHigh)
            dot(26f, 26f, 10f, p.primary)
            icon("chat", 26f, 26f, 12f, p.onPrimary)
            val header = "${node.prop("app")} • ${node.prop("time")}"
            textMid(ctx, header, 44f, 26f, 12f, p.onSurfaceVariant)
            text(ctx, node.prop("title"), 16f, 42f, 14f, p.onSurface, 600, w - 80)
            text(ctx, node.prop("text"), 16f, 62f, 14f, p.onSurfaceVariant, maxWidth = w - 80)
            dot(w - 36, h / 2 + 6, 20f, p.primaryContainer)
            textCenter(ctx, node.prop("title").take(1), w - 36, h / 2 + 6, 16f, p.onPrimaryContainer, 500)
        }
    }
}

private fun DrawScope.button(kind: ComponentKind, node: Node, p: Palette, ctx: RenderContext, w: Float, h: Float) {
    val enabled = node.bool("enabled")
    val r = h / 2
    val content = when (kind) {
        BUTTON_FILLED -> { rr(0f, 0f, w, h, r, if (enabled) p.primary else p.disabledContainer); p.onPrimary }
        BUTTON_TONAL -> { rr(0f, 0f, w, h, r, if (enabled) p.secondaryContainer else p.disabledContainer); p.onSecondaryContainer }
        BUTTON_OUTLINED -> { rrLine(0f, 0f, w, h, r, if (enabled) p.outline else p.disabledContainer); p.primary }
        BUTTON_ELEVATED -> { if (enabled) elevation(0f, 0f, w, h, r); rr(0f, 0f, w, h, r, if (enabled) p.surfaceLow else p.disabledContainer); p.primary }
        else -> p.primary
    }.let { if (enabled) it else p.disabledContent }
    val label = node.prop("label")
    val iconName = node.prop("icon")
    val hasIcon = iconName != "none" && iconName.isNotEmpty()
    val tw = ctx.width(label, 14f, 500)
    val total = tw + if (hasIcon) 26f else 0f
    var x = (w - total) / 2
    if (hasIcon) { icon(iconName, x + 9, h / 2, 18f, content); x += 26f }
    textMid(ctx, label, x, h / 2, 14f, content, 500, w - 16)
}

private fun DrawScope.checkbox(state: String, error: Boolean, enabled: Boolean, cx: Float, cy: Float, p: Palette) {
    val x = cx - 9; val y = cy - 9
    val accent = when { !enabled -> p.disabledContent; error -> p.error; else -> p.primary }
    when (state) {
        "unchecked" -> rrLine(x, y, 18f, 18f, 2f, if (!enabled) p.disabledContent else if (error) p.error else p.onSurfaceVariant, 2f)
        "indeterminate" -> { rr(x, y, 18f, 18f, 2f, accent); drawLine(p.onPrimary, Offset(x + 4, cy), Offset(x + 14, cy), 2f, StrokeCap.Round) }
        else -> { rr(x, y, 18f, 18f, 2f, accent); checkMark(x, y, 18f, p.onPrimary) }
    }
}

private fun DrawScope.radio(selected: Boolean, enabled: Boolean, cx: Float, cy: Float, p: Palette) {
    val c = when { !enabled -> p.disabledContent; selected -> p.primary; else -> p.onSurfaceVariant }
    drawCircle(c, 9f, Offset(cx, cy), style = Stroke(2f))
    if (selected) dot(cx, cy, 5f, c)
}

private fun DrawScope.switch(checked: Boolean, showIcon: Boolean, enabled: Boolean, x: Float, y: Float, w: Float, h: Float, p: Palette) {
    val r = h / 2
    if (checked) {
        rr(x, y, w, h, r, if (enabled) p.primary else p.disabledContainer)
        val tr = h * 0.375f
        dot(x + w - r, y + r, tr, if (enabled) p.onPrimary else p.surface)
        if (showIcon) checkMark(x + w - r - 8, y + r - 8, 16f, p.primary)
    } else {
        rr(x, y, w, h, r, if (enabled) p.surfaceHighest else p.surfaceHighest.copy(alpha = 0.5f))
        rrLine(x, y, w, h, r, if (enabled) p.outline else p.disabledContainer, 2f)
        dot(x + r, y + r, h * 0.25f, if (enabled) p.outline else p.disabledContent)
    }
}

private fun DrawScope.slider(values: List<Float>, steps: Int, enabled: Boolean, w: Float, h: Float, p: Palette) {
    val cy = h / 2
    val active = if (enabled) p.primary else p.disabledContent
    val inactive = if (enabled) p.secondaryContainer else p.disabledContainer
    val positions = values.map { w * (it / 100f).coerceIn(0f, 1f) }.sorted()
    val th = 16f
    if (positions.size == 1) {
        val px = positions[0]
        if (px - 6 > 0) rr(0f, cy - th / 2, px - 6, th, th / 2, active)
        if (px + 6 < w) rr(px + 6, cy - th / 2, w - px - 6, th, th / 2, inactive)
        rr(px - 2, cy - 22, 4f, 44f, 2f, active)
    } else {
        val (a, b) = positions
        if (a - 6 > 0) rr(0f, cy - th / 2, a - 6, th, th / 2, inactive)
        rr(a + 6, cy - th / 2, b - a - 12, th, 4f, active)
        if (b + 6 < w) rr(b + 6, cy - th / 2, w - b - 6, th, th / 2, inactive)
        rr(a - 2, cy - 22, 4f, 44f, 2f, active)
        rr(b - 2, cy - 22, 4f, 44f, 2f, active)
    }
    if (steps > 0) for (i in 0..steps) {
        val sx = w * i / steps
        dot(sx.coerceIn(6f, w - 6f), cy, 2f, if (positions.any { sx <= it }) p.onPrimary else p.onSecondaryContainer)
    }
}

private fun DrawScope.chip(ctx: RenderContext, p: Palette, w: Float, h: Float, label: String, leading: String, selected: Boolean) {
    if (selected) rr(0f, 0f, w, h, 8f, p.secondaryContainer) else rrLine(0f, 0f, w, h, 8f, p.outlineVariant)
    var x = 16f
    if (leading != "none" && leading.isNotEmpty()) {
        if (leading == "check") checkMark(8f, h / 2 - 9, 18f, p.onSecondaryContainer)
        else icon(leading, 17f, h / 2, 18f, p.primary)
        x = 34f
    }
    textMid(ctx, label, x, h / 2, 14f, if (selected) p.onSecondaryContainer else p.onSurfaceVariant, 500, w - x - 12)
}

private fun DrawScope.drawContextChip(ctx: RenderContext, p: Palette, x: Float, w: Float, h: Float, label: String, selected: Boolean) {
    translate(x) { chip(ctx, p, w, h, label, if (selected) "check" else "none", selected) }
}

private inline fun DrawScope.translate(x: Float, block: DrawScope.() -> Unit) {
    drawContext.transform.translate(x, 0f)
    block()
    drawContext.transform.translate(-x, 0f)
}

private fun DrawScope.textField(kind: ComponentKind, node: Node, p: Palette, ctx: RenderContext, w: Float, h: Float) {
    val state = node.prop("state")
    val focused = state == "focused"
    val error = state == "error"
    val disabled = state == "disabled"
    val accent = when { error -> p.error; focused -> p.primary; disabled -> p.disabledContent; else -> p.onSurfaceVariant }
    val label = node.prop("label")
    val value = node.prop("value")
    val leading = node.prop("leading").ifEmpty { "none" }
    val trailing = if (kind == DROPDOWN) "expand_more" else node.prop("trailing").ifEmpty { "none" }
    val x0 = if (leading != "none") 52f else 16f
    val floating = value.isNotEmpty() || focused
    if (kind == TEXT_FIELD_OUTLINED) {
        rrLine(0f, 0f, w, h, 4f, if (focused || error) accent else p.outline, if (focused || error) 2f else 1f)
        if (floating) {
            rr(x0 - 4, -8f, ctx.width(label, 12f) + 8f, 16f, 2f, Color.White)
            text(ctx, label, x0, -8f, 12f, accent)
            textMid(ctx, value, x0, h / 2, 16f, if (disabled) p.disabledContent else p.onSurface, maxWidth = w - x0 - 52)
        } else textMid(ctx, label, x0, h / 2, 16f, accent, maxWidth = w - x0 - 52)
    } else {
        topRounded(0f, 0f, w, h, 4f, if (disabled) p.disabledContainer.copy(alpha = 0.04f) else p.surfaceHighest)
        val lineW = if (focused || error) 2f else 1f
        drawRect(if (focused || error) accent else p.onSurfaceVariant, Offset(0f, h - lineW), Size(w, lineW))
        if (floating) {
            text(ctx, label, x0, 8f, 12f, accent)
            text(ctx, value, x0, 26f, 16f, if (disabled) p.disabledContent else p.onSurface, maxWidth = w - x0 - 52)
        } else textMid(ctx, label, x0, h / 2, 16f, accent, maxWidth = w - x0 - 52)
    }
    if (leading != "none") icon(leading, 28f, h / 2, 24f, p.onSurfaceVariant)
    if (trailing != "none") icon(if (error) "error" else trailing, w - 28, h / 2, 24f, if (error) p.error else p.onSurfaceVariant)
    val supporting = node.prop("supporting")
    if (supporting.isNotEmpty()) text(ctx, supporting, 16f, h + 4, 12f, if (error) p.error else p.onSurfaceVariant, maxWidth = w - 32)
}

private fun DrawScope.listItem(
    ctx: RenderContext, p: Palette, headline: String, supporting: String, leading: String,
    trailing: String, trailingIcon: String, y: Float, w: Float, h: Float,
) {
    val cy = y + h / 2
    var x = 16f
    when (leading) {
        "icon" -> { icon("person", 28f, cy, 24f, p.onSurfaceVariant); x = 56f }
        "avatar" -> { dot(36f, cy, 20f, p.primaryContainer); textCenter(ctx, headline.take(1).uppercase(), 36f, cy, 16f, p.onPrimaryContainer, 500); x = 72f }
        "image" -> { rr(16f, cy - 28, 56f, 56f, 8f, p.primaryContainer); icon("image", 44f, cy, 24f, p.onPrimaryContainer); x = 88f }
        "checkbox" -> { checkbox("checked", false, true, 28f, cy, p); x = 56f }
    }
    val right = when (trailing) { "switch" -> 84f; "text" -> 56f; "none" -> 16f; else -> 52f }
    if (supporting.isNotEmpty()) {
        text(ctx, headline, x, cy - 21, 16f, p.onSurface, maxWidth = w - x - right)
        text(ctx, supporting, x, cy + 1, 14f, p.onSurfaceVariant, maxWidth = w - x - right)
    } else textMid(ctx, headline, x, cy, 16f, p.onSurface, maxWidth = w - x - right)
    when (trailing) {
        "icon" -> icon(trailingIcon, w - 28, cy, 24f, p.onSurfaceVariant)
        "switch" -> switch(true, false, true, w - 68, cy - 16, 52f, 32f, p)
        "checkbox" -> checkbox("checked", false, true, w - 28, cy, p)
        "text" -> textMid(ctx, "100+", w - 44, cy, 11f, p.onSurfaceVariant, 500)
    }
}

private fun DrawScope.datePicker(ctx: RenderContext, p: Palette, node: Node, w: Float, h: Float) {
    rr(0f, 0f, w, h, 28f, p.surfaceHigh)
    val sel = node.int("selected", 14).coerceIn(1, 31)
    text(ctx, "Select date", 24f, 16f, 14f, p.onSurfaceVariant, 500)
    text(ctx, "Nov $sel", 24f, 40f, 32f, p.onSurface)
    drawLine(p.outlineVariant, Offset(0f, 100f), Offset(w, 100f), 1f)
    textMid(ctx, node.prop("month"), 24f, 124f, 14f, p.onSurfaceVariant, 500)
    icon("arrow_back", w - 72, 124f, 20f, p.onSurfaceVariant)
    icon("arrow_forward", w - 32, 124f, 20f, p.onSurfaceVariant)
    val cell = (w - 24) / 7
    listOf("S", "M", "T", "W", "T", "F", "S").forEachIndexed { i, d ->
        textCenter(ctx, d, 12 + cell * i + cell / 2, 164f, 14f, p.onSurface)
    }
    val rowH = min(44f, (h - 240) / 6)
    for (day in 1..30) {
        val col = (day - 1) % 7
        val row = (day - 1) / 7
        val cx = 12 + cell * col + cell / 2
        val cy = 196f + row * rowH
        if (day == sel) dot(cx, cy, 20f, p.primary)
        textCenter(ctx, day.toString(), cx, cy, 14f, if (day == sel) p.onPrimary else p.onSurface)
    }
    text(ctx, "OK", w - 24 - ctx.width("OK", 14f, 500), h - 44, 14f, p.primary, 500)
    text(ctx, "Cancel", w - 72 - ctx.width("Cancel", 14f, 500), h - 44, 14f, p.primary, 500)
}

private fun DrawScope.timePicker(ctx: RenderContext, p: Palette, node: Node, w: Float, h: Float) {
    rr(0f, 0f, w, h, 28f, p.surfaceHigh)
    text(ctx, "Select time", 24f, 20f, 12f, p.onSurfaceVariant, 500)
    val hour = node.int("hour", 9).coerceIn(1, 12)
    val minute = node.int("minute", 41).coerceIn(0, 59)
    rr(24f, 48f, 96f, 72f, 8f, p.primaryContainer)
    textCenter(ctx, hour.toString().padStart(2, '0'), 72f, 84f, 48f, p.onPrimaryContainer)
    textCenter(ctx, ":", 136f, 84f, 48f, p.onSurface)
    rr(152f, 48f, 96f, 72f, 8f, p.surfaceHighest)
    textCenter(ctx, minute.toString().padStart(2, '0'), 200f, 84f, 48f, p.onSurface)
    val am = node.prop("period") == "AM"
    rrLine(260f, 48f, 44f, 72f, 8f, p.outline)
    rr(260.5f, 48.5f, 43f, 35.5f, 8f, if (am) p.tertiaryContainer else Color.Transparent)
    rr(260.5f, 84f, 43f, 35.5f, 8f, if (!am) p.tertiaryContainer else Color.Transparent)
    textCenter(ctx, "AM", 282f, 66f, 14f, p.onSurfaceVariant, 500)
    textCenter(ctx, "PM", 282f, 102f, 14f, p.onSurfaceVariant, 500)
    val r = min(128f, (w - 48) / 2)
    val center = Offset(w / 2, 144f + r)
    drawCircle(p.surfaceHighest, r, center)
    val angle = Math.toRadians((hour % 12) * 30.0 - 90)
    val hand = Offset(center.x + (r - 24) * kotlin.math.cos(angle).toFloat(), center.y + (r - 24) * kotlin.math.sin(angle).toFloat())
    drawLine(p.primary, center, hand, 2f)
    dot(center.x, center.y, 4f, p.primary)
    dot(hand.x, hand.y, 22f, p.primary)
    for (n in 1..12) {
        val a = Math.toRadians(n * 30.0 - 90)
        val pos = Offset(center.x + (r - 24) * kotlin.math.cos(a).toFloat(), center.y + (r - 24) * kotlin.math.sin(a).toFloat())
        textCenter(ctx, n.toString(), pos.x, pos.y, 16f, if (n == hour) p.onPrimary else p.onSurface)
    }
    text(ctx, "OK", w - 24 - ctx.width("OK", 14f, 500), h - 44, 14f, p.primary, 500)
    text(ctx, "Cancel", w - 72 - ctx.width("Cancel", 14f, 500), h - 44, 14f, p.primary, 500)
}

private fun DrawScope.keyboard(ctx: RenderContext, p: Palette, dark: Boolean, w: Float, h: Float) {
    val bg = if (dark) Color(0xFF1F1D23) else Color(0xFFE8E2EC)
    val key = if (dark) Color(0xFF4A4458) else Color.White
    val special = if (dark) Color(0xFF332F3A) else Color(0xFFCFC8D6)
    val fg = if (dark) Color.White else p.onSurface
    drawRect(bg, size = Size(w, h))
    listOf("I", "the", "I'm").forEachIndexed { i, s -> textCenter(ctx, s, w / 6 + i * w / 3, 20f, 15f, fg) }
    val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    val keyW = (w - 6) / 10
    val keyH = (h - 48) / 4 - 6
    rows.forEachIndexed { r, letters ->
        val y = 40f + r * (keyH + 6)
        val offset = (w - letters.length * keyW) / 2
        letters.forEachIndexed { i, ch ->
            rr(offset + i * keyW + 3, y, keyW - 6, keyH, 6f, key)
            textCenter(ctx, ch.toString(), offset + i * keyW + keyW / 2, y + keyH / 2, 20f, fg)
        }
        if (r == 2) {
            rr(3f, y, keyW * 1.4f - 6, keyH, 6f, special)
            textCenter(ctx, "⇧", keyW * 0.7f, y + keyH / 2, 18f, fg)
            rr(w - keyW * 1.4f + 3, y, keyW * 1.4f - 6, keyH, 6f, special)
            icon("arrow_back", w - keyW * 0.7f, y + keyH / 2, 20f, fg)
        }
    }
    val y = 40f + 3 * (keyH + 6)
    rr(3f, y, keyW * 1.6f - 6, keyH, 6f, special)
    textCenter(ctx, "?123", keyW * 0.8f, y + keyH / 2, 14f, fg, 500)
    rr(keyW * 1.6f + 3, y, keyW - 6, keyH, 6f, key)
    textCenter(ctx, ",", keyW * 2.1f, y + keyH / 2, 18f, fg)
    rr(keyW * 2.6f + 3, y, keyW * 4.8f - 6, keyH, 6f, key)
    rr(keyW * 7.4f + 3, y, keyW - 6, keyH, 6f, key)
    textCenter(ctx, ".", keyW * 7.9f, y + keyH / 2, 18f, fg)
    rr(keyW * 8.4f + 3, y, w - keyW * 8.4f - 6, keyH, 6f, p.primary)
    icon("check", keyW * 8.4f + (w - keyW * 8.4f) / 2, y + keyH / 2, 22f, p.onPrimary)
}
