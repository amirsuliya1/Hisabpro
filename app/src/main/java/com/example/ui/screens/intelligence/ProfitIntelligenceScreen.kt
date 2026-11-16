package com.example.ui.screens.intelligence

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.HisabPulseApp
import com.example.core.DateRangeFilter
import com.example.core.DateUtils
import com.example.core.Money
import com.example.core.PdfGenerator
import com.example.data.repository.ProfitIntelligenceSummary
import com.example.ui.components.DateRangeFilterBar
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.HisabPulseTopBar
import com.example.ui.components.MoneyText
import com.example.ui.components.StatusPill
import com.example.ui.theme.LocalSemanticColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProfitIntelligenceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as HisabPulseApp
    val scope = rememberCoroutineScope()
    val semantic = LocalSemanticColors.current

    val profile by app.container.businessRepository.businessProfile.collectAsStateWithLifecycle(null)
    val settings by app.container.businessRepository.appSettings.collectAsStateWithLifecycle(null)
    val currency = profile?.currencySymbol ?: "₹"
    val hide = settings?.hideAmounts ?: false

    var rangeFilter by remember { mutableStateOf(DateRangeFilter.THIS_MONTH) }
    var customStart by remember { mutableStateOf<Long?>(null) }
    var customEnd by remember { mutableStateOf<Long?>(null) }
    var summary by remember { mutableStateOf<ProfitIntelligenceSummary?>(null) }
    var loading by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableIntStateOf(0) }

    fun load() {
        scope.launch {
            loading = true
            val (s, e) = if (rangeFilter == DateRangeFilter.CUSTOM && customStart != null && customEnd != null) {
                customStart!! to customEnd!!
            } else {
                DateUtils.resolveRange(rangeFilter)
            }
            summary = app.container.intelligenceRepository.computeProfitIntelligence(s, e)
            loading = false
        }
    }

    LaunchedEffect(rangeFilter, customStart, customEnd) { load() }

    Scaffold(
        topBar = {
            HisabPulseTopBar(
                title = "Profit Intelligence",
                subtitle = "Realized COGS, margins & loss alerts",
                onBack = onBack,
                actions = {
                    val data = summary
                    if (data != null) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    val file = withContext(Dispatchers.IO) {
                                        val rows = data.byProduct.map {
                                            listOf(
                                                it.productName,
                                                it.categoryName,
                                                Money.formatQty(Money.fromPaise(it.qtySoldScaled)),
                                                Money.formatPlain(Money.fromPaise(it.revenuePaise)),
                                                Money.formatPlain(Money.fromPaise(it.cogsPaise)),
                                                Money.formatPlain(Money.fromPaise(it.grossProfitPaise)),
                                                "${"%.1f".format(it.marginPercent)}%"
                                            )
                                        }
                                        PdfGenerator.generateTabularReportPdf(
                                            context = context,
                                            fileName = "Profit_Intelligence_${DateUtils.formatIsoDate(System.currentTimeMillis())}.pdf",
                                            title = "${profile?.businessName ?: "HisabPulse"} — Profit Intelligence",
                                            subtitle = "Net Profit: ${Money.formatPaise(data.netProfitPaise, currency)} (${rangeFilter.label})",
                                            headers = listOf("Product", "Category", "Qty", "Revenue", "COGS", "Gross Profit", "Margin"),
                                            rows = rows
                                        )
                                    }
                                    PdfGenerator.shareFile(context, file, "application/pdf", "Share Profit Report")
                                }
                            },
                            modifier = Modifier.testTag("export_profit_pdf_btn")
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF")
                        }
                        IconButton(
                            onClick = {
                                scope.launch {
                                    val xlsx = withContext(Dispatchers.IO) {
                                        val headers = listOf("Product", "Category", "Qty", "Revenue", "COGS", "Discount", "GrossProfit", "MarginPct")
                                        val rows = data.byProduct.map {
                                            listOf(
                                                it.productName,
                                                it.categoryName,
                                                Money.formatQty(Money.fromPaise(it.qtySoldScaled)),
                                                Money.formatPlain(Money.fromPaise(it.revenuePaise)),
                                                Money.formatPlain(Money.fromPaise(it.cogsPaise)),
                                                Money.formatPlain(Money.fromPaise(it.discountPaise)),
                                                Money.formatPlain(Money.fromPaise(it.grossProfitPaise)),
                                                "${"%.1f".format(it.marginPercent)}%"
                                            )
                                        }
                                        PdfGenerator.exportXlsx(
                                            context = context,
                                            fileName = "Profit_Intelligence_${DateUtils.formatIsoDate(System.currentTimeMillis())}.xlsx",
                                            sheetName = "ProfitByProduct",
                                            headers = headers,
                                            rows = rows
                                        )
                                    }
                                    PdfGenerator.shareFile(
                                        context,
                                        xlsx,
                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                        "Export Profit Excel"
                                    )
                                }
                            },
                            modifier = Modifier.testTag("export_profit_xlsx_btn")
                        ) {
                            Icon(Icons.Default.TableChart, contentDescription = "Export Excel")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                DateRangeFilterBar(
                    selectedFilter = rangeFilter,
                    onFilterSelected = { f, cs, ce ->
                        rangeFilter = f
                        customStart = cs
                        customEnd = ce
                    }
                )
            }
            if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            val tabs = listOf("Overview", "By Product", "By Category", "By Invoice", "Daily / Monthly")
            ScrollableTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { idx, title ->
                    Tab(selected = selectedTab == idx, onClick = { selectedTab = idx }, text = { Text(title) })
                }
            }

            val data = summary
            if (data == null && !loading) {
                EmptyStateCard(
                    title = "No Profit Data",
                    description = "Finalize invoices and record business expenses to see margin intelligence."
                )
                return@Column
            }
            if (data == null) return@Column

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (selectedTab) {
                    0 -> {
                        if (data.lossMakingInvoices.isNotEmpty()) {
                            item {
                                Card(colors = CardDefaults.cardColors(containerColor = semantic.warningContainer)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Warning, contentDescription = "Loss alert", tint = semantic.warning)
                                        Column {
                                            Text(
                                                "${data.lossMakingInvoices.size} Loss-Making Invoice(s) Detected",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                "Net revenue after discount fell below product purchase cost. Review in 'By Invoice' tab.",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Profit & Margin Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    ProfitRowLine("Net Sales Revenue (excl. GST)", data.totalRevenuePaise, currency, hide)
                                    ProfitRowLine("Cost of Goods Sold (COGS)", data.totalCogsPaise, currency, hide)
                                    ProfitRowLine("Discounts Allowed", data.totalDiscountsPaise, currency, hide)
                                    HorizontalDivider()
                                    ProfitRowLine(
                                        "Gross Profit (${"%.1f".format(data.grossMarginPercent)}%)",
                                        data.grossProfitPaise,
                                        currency,
                                        hide,
                                        bold = true
                                    )
                                    ProfitRowLine("Operating Expenses", data.operatingExpensesPaise, currency, hide)
                                    HorizontalDivider()
                                    ProfitRowLine(
                                        "Net Profit (${"%.1f".format(data.netMarginPercent)}%)",
                                        data.netProfitPaise,
                                        currency,
                                        hide,
                                        bold = true
                                    )
                                }
                            }
                        }
                        if (data.topProfitableProducts.isNotEmpty()) {
                            item { Text("Top Profitable Products", style = MaterialTheme.typography.titleMedium) }
                            items(data.topProfitableProducts) { row ->
                                ProductProfitCard(row, currency, hide)
                            }
                        }
                    }
                    1 -> {
                        if (data.byProduct.isEmpty()) {
                            item { EmptyStateCard("No Product Sales", "No finalized item sales in selected period.") }
                        } else {
                            items(data.byProduct) { row -> ProductProfitCard(row, currency, hide) }
                        }
                    }
                    2 -> {
                        if (data.byCategory.isEmpty()) {
                            item { EmptyStateCard("No Category Sales", "No finalized category sales in selected period.") }
                        } else {
                            items(data.byCategory) { row -> ProductProfitCard(row, currency, hide) }
                        }
                    }
                    3 -> {
                        if (data.byInvoice.isEmpty()) {
                            item { EmptyStateCard("No Finalized Invoices", "No ledger-affecting invoices in selected period.") }
                        } else {
                            items(data.byInvoice, key = { it.invoiceId }) { inv ->
                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text(inv.invoiceNumber, fontWeight = FontWeight.Bold)
                                                if (inv.isLossMaking) {
                                                    StatusPill("LOSS", semantic.dangerContainer, semantic.danger)
                                                }
                                            }
                                            Text("${inv.customerName} • ${DateUtils.formatDate(inv.dateMillis)}", style = MaterialTheme.typography.bodySmall)
                                            Text("Rev ${Money.formatPaise(inv.revenuePaise, currency, hide)} • COGS ${Money.formatPaise(inv.cogsPaise, currency, hide)}", style = MaterialTheme.typography.labelSmall)
                                        }
                                        MoneyText(
                                            paise = inv.profitPaise,
                                            currencySymbol = currency,
                                            hideAmounts = hide,
                                            color = if (inv.profitPaise >= 0L) semantic.success else semantic.danger
                                        )
                                    }
                                }
                            }
                        }
                    }
                    4 -> {
                        item { Text("Monthly Breakdown", style = MaterialTheme.typography.titleMedium) }
                        if (data.monthlyProfits.isEmpty()) {
                            item { EmptyStateCard("No Monthly History", "Record sales and expenses to populate monthly profits.") }
                        } else {
                            items(data.monthlyProfits) { m ->
                                PeriodProfitCard(m.periodLabel, m.revenuePaise, m.cogsPaise, m.expensePaise, m.netProfitPaise, currency, hide)
                            }
                        }
                        if (data.dailyProfits.isNotEmpty()) {
                            item { Spacer(Modifier.height(6.dp)); Text("Daily Breakdown", style = MaterialTheme.typography.titleMedium) }
                            items(data.dailyProfits) { d ->
                                PeriodProfitCard(d.periodLabel, d.revenuePaise, d.cogsPaise, d.expensePaise, d.netProfitPaise, currency, hide)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfitRowLine(label: String, paise: Long, currency: String, hide: Boolean, bold: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        MoneyText(paise = paise, currencySymbol = currency, hideAmounts = hide, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun ProductProfitCard(row: com.example.data.repository.ProductProfitRow, currency: String, hide: Boolean) {
    val semantic = LocalSemanticColors.current
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(row.productName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${row.categoryName} • Qty ${Money.formatQty(Money.fromPaise(row.qtySoldScaled))} • Margin ${"%.1f".format(row.marginPercent)}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            MoneyText(
                paise = row.grossProfitPaise,
                currencySymbol = currency,
                hideAmounts = hide,
                color = if (row.grossProfitPaise >= 0L) semantic.success else semantic.danger
            )
        }
    }
}

@Composable
private fun PeriodProfitCard(label: String, rev: Long, cogs: Long, exp: Long, net: Long, currency: String, hide: Boolean) {
    val semantic = LocalSemanticColors.current
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.Bold)
                Text(
                    "Rev ${Money.formatPaise(rev, currency, hide)} • COGS ${Money.formatPaise(cogs, currency, hide)} • Exp ${Money.formatPaise(exp, currency, hide)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            MoneyText(paise = net, currencySymbol = currency, hideAmounts = hide, color = if (net >= 0L) semantic.success else semantic.danger)
        }
    }
}
