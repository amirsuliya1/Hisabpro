package com.example.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class LegalDialogType(val title: String) {
    PRIVACY_POLICY("Privacy Policy"),
    TERMS_OF_SERVICE("Terms of Service"),
    DATA_ARCHITECTURE("Local-First Architecture & Data Security")
}

@Composable
fun LegalDisclosuresDialog(
    type: LegalDialogType,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        icon = {
            Icon(
                imageVector = when (type) {
                    LegalDialogType.PRIVACY_POLICY -> Icons.Default.Policy
                    LegalDialogType.TERMS_OF_SERVICE -> Icons.Default.Gavel
                    LegalDialogType.DATA_ARCHITECTURE -> Icons.Default.Security
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = type.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (type) {
                    LegalDialogType.PRIVACY_POLICY -> {
                        PrivacyPolicyContent()
                    }
                    LegalDialogType.TERMS_OF_SERVICE -> {
                        TermsOfServiceContent()
                    }
                    LegalDialogType.DATA_ARCHITECTURE -> {
                        DataArchitectureContent()
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Understood")
            }
        }
    )
}

@Composable
private fun PrivacyPolicyContent() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Effective Date: November 2026 • HisabPulse Business Management",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SectionHeader("1. Data Minimization & Cloud Scope")
        Text(
            "HisabPulse operates under strict data minimization. We only collect and store in Firebase Cloud four account identity attributes: (a) Google Account Email ID, (b) Registered Shop Name, (c) Physical Shop Address, and (d) Shopkeeper Contact Phone Number.",
            style = MaterialTheme.typography.bodySmall
        )

        SectionHeader("2. Zero-Knowledge Local Financial Privacy")
        Text(
            "Your financial data is 100% private to you. Invoices, customer balances (Udhaar / Khata), supplier ledgers, product catalog, batch expiry dates, inventory counts, profit margins, and daily cash drawer registers are stored exclusively in an encrypted Room SQLite database on your physical device. No financial records are ever transmitted to our servers or third-party advertisers.",
            style = MaterialTheme.typography.bodySmall
        )

        SectionHeader("3. Compliance & Standards")
        Text(
            "• Digital Personal Data Protection (DPDP) Act 2023 (India)\n• Google Play Developer Program Policies (Authentication & Least Privilege)\n• Zero Third-Party Advertising Trackers: We never sell or monetize user data.",
            style = MaterialTheme.typography.bodySmall
        )

        SectionHeader("4. Account Deletion & Rights")
        Text(
            "You have the right to delete your cloud profile at any time. Signing out or clearing app data wipes the local database from your device immediately.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun TermsOfServiceContent() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Terms of Service • HisabPulse Native Billing",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SectionHeader("1. License to Use")
        Text(
            "HisabPulse grants you a personal, non-transferable license to manage shop accounting, POS retail billing, GST invoicing, and inventory on your Android smartphone.",
            style = MaterialTheme.typography.bodySmall
        )

        SectionHeader("2. GST & Tax Calculation Disclaimer")
        Text(
            "While HisabPulse automates standard CGST, SGST, IGST, and HSN calculations, the business owner retains sole responsibility for verifying figures, reconciling GSTR reports, and filing statutory returns with the Goods and Services Tax Network (GSTN).",
            style = MaterialTheme.typography.bodySmall
        )

        SectionHeader("3. Offline Data & SAF Backup Responsibility")
        Text(
            "Because your financial ledger is stored strictly locally for privacy, you are responsible for maintaining periodic backups using the Storage Access Framework (SAF) export feature in More → Data Recovery.",
            style = MaterialTheme.typography.bodySmall
        )

        SectionHeader("4. Accuracy of Business Details")
        Text(
            "You agree to provide accurate Shop Name, Address, and Contact details to ensure invoices printed for your customers comply with applicable consumer and trade standards.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun DataArchitectureContent() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Local-First Architecture & Cloud Boundary",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("☁️ Cloud (Firebase Firestore /users/{uid})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                Text("• Google Account Email\n• Shop Name\n• Shop Address\n• Shopkeeper Contact Phone", style = MaterialTheme.typography.bodySmall)
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text("🔒 100% Local Phone Storage (Room SQLite)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                Text("• Invoices & Estimates\n• Inventory & Stock Movements\n• Customer & Supplier Khata Ledgers\n• Expenses & Daily Cash Closings\n• Loyalty Points & Custom Design Templates", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}
