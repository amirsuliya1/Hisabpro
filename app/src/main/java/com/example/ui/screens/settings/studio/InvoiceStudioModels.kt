package com.example.ui.screens.settings.studio

import androidx.compose.ui.graphics.Color
import com.example.data.local.entities.BusinessProfile

data class InvoiceColorScheme(
    val id: String,
    val name: String,
    val primaryHex: String,
    val surfaceTintHex: String,
    val secondaryHex: String = "#0284C7"
)

val STUDIO_COLOR_SCHEMES = listOf(
    InvoiceColorScheme("corporate", "Corporate Navy", "#0F2D5C", "#EFF6FF", "#0284C7"),
    InvoiceColorScheme("cobalt", "Cobalt Cyan", "#1D4ED8", "#EFF6FF", "#0EA5E9"),
    InvoiceColorScheme("emerald", "Emerald Mint", "#059669", "#ECFDF5", "#14B8A6"),
    InvoiceColorScheme("teal", "Ocean Teal", "#0F766E", "#F0FDFA", "#06B6D4"),
    InvoiceColorScheme("indigo", "Royal Indigo", "#4F46E5", "#EEF2FF", "#6366F1"),
    InvoiceColorScheme("orange", "Hisab Orange", "#EA580C", "#FFF7ED", "#F59E0B"),
    InvoiceColorScheme("crimson", "Crimson Coral", "#BE123C", "#FFF1F2", "#FB7185"),
    InvoiceColorScheme("charcoal", "Charcoal Noir", "#18181B", "#F4F4F5", "#475569")
)

val CUSTOM_SWATCH_COLORS = listOf(
    "#0F2D5C", "#1E3A8A", "#0284C7", "#0EA5E9",
    "#0F766E", "#059669", "#4F46E5", "#7C3AED",
    "#BE123C", "#EA580C", "#D97706", "#18181B"
)

val CORE_TEMPLATE_CODES = setOf(
    "CORPORATE_WAVE",
    "MODERN_ORANGE",
    "CLASSIC_GST",
    "COMPACT_POS",
    "MODERN_RETAIL",
    "WHOLESALE_PRO",
    "MINIMAL_CLEAN",
    "DETAILED_GST",
    "THERMAL_58"
)

enum class StudioTab(val label: String) {
    STYLE("Style & Colors"),
    BRANDING("Logo & Sign"),
    SECTIONS("Sections"),
    COPY("Copy & Save")
}

data class StudioEditableBusiness(
    val businessName: String = "",
    val tagline: String = "",
    val logoUri: String = "",
    val phone: String = "",
    val email: String = "",
    val website: String = "",
    val address: String = "",
    val gstin: String = "",
    val upiId: String = "",
    val signatureLabel: String = "Authorized Signature",
    val signatureUri: String = "",
    val sealUri: String = ""
) {
    companion object {
        fun fromProfile(profile: BusinessProfile?): StudioEditableBusiness {
            if (profile == null) return StudioEditableBusiness(businessName = "HisabPulse Store")
            return StudioEditableBusiness(
                businessName = profile.businessName,
                tagline = profile.tagline,
                logoUri = profile.logoUri,
                phone = profile.phone,
                email = profile.email,
                website = profile.website,
                address = profile.toFullAddressString(),
                gstin = profile.gstin,
                upiId = profile.upiId,
                signatureLabel = profile.signatureLabel.ifBlank { "Authorized Signature" },
                signatureUri = profile.signatureUri,
                sealUri = profile.sealUri
            )
        }
    }
}

fun parseColorSafe(hex: String, fallback: Color = Color(0xFF0F2D5C)): Color {
    return runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)
}
