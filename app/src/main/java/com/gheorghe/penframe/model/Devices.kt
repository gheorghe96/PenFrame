package com.gheorghe.penframe.model

enum class DeviceCategory(val title: String) {
    ANDROID_PHONE("Android phones"),
    FOLDABLE("Foldables"),
    TABLET("Tablets"),
    IOS("iPhone & iPad"),
    WEAR("Watches"),
    LAYOUT("Window size classes"),
    DESKTOP("Desktop & TV"),
}

/**
 * A device screen in dp (Android) or pt (iOS): the logical size apps lay out against at the
 * default display scale, portrait. Corner radius is the visible screen rounding.
 */
data class DevicePreset(
    val id: String,
    val name: String,
    val width: Int,
    val height: Int,
    val category: DeviceCategory,
    val cornerRadius: Float = 0f,
    val statusBar: Int = 24,
    val navBar: Int = 24,
    val round: Boolean = false,
)

object Devices {
    val all: List<DevicePreset> = listOf(
        // Android phones
        DevicePreset("pixel-9", "Pixel 9", 412, 923, DeviceCategory.ANDROID_PHONE, 36f, 52, 24),
        DevicePreset("pixel-9-pro-xl", "Pixel 9 Pro XL", 448, 998, DeviceCategory.ANDROID_PHONE, 38f, 52, 24),
        DevicePreset("pixel-9a", "Pixel 9a", 412, 915, DeviceCategory.ANDROID_PHONE, 32f, 48, 24),
        DevicePreset("pixel-8", "Pixel 8", 412, 915, DeviceCategory.ANDROID_PHONE, 34f, 48, 24),
        DevicePreset("galaxy-s25-ultra", "Galaxy S25 Ultra", 412, 891, DeviceCategory.ANDROID_PHONE, 22f, 36, 24),
        DevicePreset("galaxy-s25", "Galaxy S25", 360, 780, DeviceCategory.ANDROID_PHONE, 30f, 36, 24),
        DevicePreset("galaxy-s24-ultra", "Galaxy S24 Ultra", 384, 832, DeviceCategory.ANDROID_PHONE, 18f, 36, 24),
        DevicePreset("galaxy-s22-ultra", "Galaxy S22 Ultra", 384, 824, DeviceCategory.ANDROID_PHONE, 20f, 36, 24),
        DevicePreset("galaxy-a55", "Galaxy A55", 384, 832, DeviceCategory.ANDROID_PHONE, 24f, 36, 24),
        DevicePreset("oneplus-13", "OnePlus 13", 412, 915, DeviceCategory.ANDROID_PHONE, 32f, 40, 24),
        DevicePreset("android-compact", "Android compact (small)", 360, 640, DeviceCategory.ANDROID_PHONE, 0f, 24, 48),
        DevicePreset("android-medium", "Android medium", 360, 800, DeviceCategory.ANDROID_PHONE, 16f, 24, 24),
        // Foldables
        DevicePreset("z-fold6-cover", "Galaxy Z Fold6 (cover)", 360, 846, DeviceCategory.FOLDABLE, 20f, 36, 24),
        DevicePreset("z-fold6-inner", "Galaxy Z Fold6 (unfolded)", 720, 846, DeviceCategory.FOLDABLE, 20f, 36, 24),
        DevicePreset("z-flip6", "Galaxy Z Flip6", 360, 880, DeviceCategory.FOLDABLE, 28f, 36, 24),
        DevicePreset("pixel-9-pro-fold-inner", "Pixel 9 Pro Fold (unfolded)", 828, 900, DeviceCategory.FOLDABLE, 24f, 40, 24),
        DevicePreset("pixel-9-pro-fold-cover", "Pixel 9 Pro Fold (cover)", 412, 978, DeviceCategory.FOLDABLE, 30f, 40, 24),
        // Tablets
        DevicePreset("pixel-tablet", "Pixel Tablet", 1280, 800, DeviceCategory.TABLET, 18f, 32, 32),
        DevicePreset("tab-s10", "Galaxy Tab S10+", 1280, 800, DeviceCategory.TABLET, 16f, 32, 32),
        DevicePreset("tab-s10-ultra", "Galaxy Tab S10 Ultra", 1478, 924, DeviceCategory.TABLET, 16f, 32, 32),
        DevicePreset("tab-s9-fe", "Galaxy Tab S9 FE", 1200, 750, DeviceCategory.TABLET, 16f, 32, 32),
        DevicePreset("tablet-portrait", "Android tablet (portrait)", 800, 1280, DeviceCategory.TABLET, 18f, 32, 32),
        // Apple
        DevicePreset("iphone-16", "iPhone 16", 393, 852, DeviceCategory.IOS, 55f, 54, 34),
        DevicePreset("iphone-16-pro", "iPhone 16 Pro", 402, 874, DeviceCategory.IOS, 62f, 62, 34),
        DevicePreset("iphone-16-pro-max", "iPhone 16 Pro Max", 440, 956, DeviceCategory.IOS, 62f, 62, 34),
        DevicePreset("iphone-se", "iPhone SE", 375, 667, DeviceCategory.IOS, 0f, 20, 0),
        DevicePreset("ipad-air-11", "iPad Air 11\"", 820, 1180, DeviceCategory.IOS, 18f, 24, 20),
        DevicePreset("ipad-pro-13", "iPad Pro 13\"", 1032, 1376, DeviceCategory.IOS, 18f, 24, 20),
        // Watches
        DevicePreset("wear-small-round", "Wear OS small round", 192, 192, DeviceCategory.WEAR, 96f, 0, 0, round = true),
        DevicePreset("wear-large-round", "Wear OS large round", 227, 227, DeviceCategory.WEAR, 113f, 0, 0, round = true),
        DevicePreset("galaxy-watch-44", "Galaxy Watch 44mm", 216, 216, DeviceCategory.WEAR, 108f, 0, 0, round = true),
        DevicePreset("wear-square", "Wear OS square", 180, 180, DeviceCategory.WEAR, 24f, 0, 0),
        // Material window size classes
        DevicePreset("class-compact", "Compact (phone)", 412, 917, DeviceCategory.LAYOUT, 0f, 24, 24),
        DevicePreset("class-medium", "Medium (foldable / small tablet)", 700, 840, DeviceCategory.LAYOUT, 0f, 24, 24),
        DevicePreset("class-expanded", "Expanded (tablet)", 1280, 800, DeviceCategory.LAYOUT, 0f, 24, 24),
        DevicePreset("class-large", "Large (desktop window)", 1440, 900, DeviceCategory.LAYOUT, 0f, 0, 0),
        // Desktop & TV
        DevicePreset("chromebook", "Chromebook", 1366, 768, DeviceCategory.DESKTOP, 0f, 0, 0),
        DevicePreset("desktop-1440", "Desktop 1440", 1440, 1024, DeviceCategory.DESKTOP, 0f, 0, 0),
        DevicePreset("desktop-1920", "Desktop Full HD", 1920, 1080, DeviceCategory.DESKTOP, 0f, 0, 0),
        DevicePreset("android-tv", "Android TV", 960, 540, DeviceCategory.DESKTOP, 0f, 0, 0),
    )

    fun byId(id: String?): DevicePreset? = all.firstOrNull { it.id == id }

    val grouped: Map<DeviceCategory, List<DevicePreset>> get() = all.groupBy { it.category }
}
