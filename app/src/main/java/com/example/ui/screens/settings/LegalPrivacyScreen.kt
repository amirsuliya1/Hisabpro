package com.example.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.HisabPulseTopBar

@Composable
fun LegalPrivacyScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = { HisabPulseTopBar(title = "Legal & Privacy", onBack = onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Privacy Policy Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        androidx.compose.foundation.layout.Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Policy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Privacy Policy & Data Minimization", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        HorizontalDivider()
                        Text(
                            text = "Effective Date: November 2026 • HisabPulse Business Management",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "1. Data Minimization & Cloud Scope\nHisabPulse operates under strict data minimization. We only collect and store in Firebase Cloud four account identity attributes: (a) Google Account Email ID, (b) Registered Shop Name, (c) Physical Shop Address, and (d) Shopkeeper Contact Phone Number.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "2. Zero-Knowledge Local Financial Privacy\nYour financial data is 100% private to you. Invoices, customer balances (Udhaar / Khata), supplier ledgers, product catalog, batch expiry dates, inventory counts, profit margins, and daily cash drawer registers are stored exclusively in an encrypted Room SQLite database on your physical device. No financial records are ever transmitted to our servers or third-party advertisers.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "3. Compliance & Standards\n• Digital Personal Data Protection (DPDP) Act 2023 (India)\n• Google Play Developer Program Policies (Authentication & Least Privilege)\n• Zero Third-Party Advertising Trackers: We never sell or monetize user data.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "4. Account Deletion & Rights\nYou have the right to delete your cloud profile at any time. Signing out or clearing app data wipes the local database from your device immediately.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Terms of Service Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        androidx.compose.foundation.layout.Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Gavel, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Terms of Service", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        HorizontalDivider()
                        Text(
                            text = "1. License to Use\nHisabPulse grants you a personal, non-transferable license to manage shop accounting, POS retail billing, GST invoicing, and inventory on your Android smartphone.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "2. GST & Tax Calculation Disclaimer\nWhile HisabPulse automates standard CGST, SGST, IGST, and HSN calculations, the business owner retains sole responsibility for verifying figures, reconciling GSTR reports, and filing statutory returns with the Goods and Services Tax Network (GSTN).",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "3. Offline Data & SAF Backup Responsibility\nBecause your financial ledger is stored strictly locally for privacy, you are responsible for maintaining periodic backups using the Storage Access Framework (SAF) export feature in More → Data Recovery.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "4. Accuracy of Business Details\nYou agree to provide accurate Shop Name, Address, and Contact details to ensure invoices printed for your customers comply with applicable consumer and trade standards.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Data Architecture & Local Security Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        androidx.compose.foundation.layout.Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Local-First Architecture & Cloud Boundary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        HorizontalDivider()
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
            }
        }
    }
}
