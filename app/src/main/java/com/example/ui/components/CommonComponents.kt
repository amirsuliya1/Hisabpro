package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.DateRangeFilter
import com.example.core.Money
import com.example.data.local.entities.SearchHistory
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalSemanticColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HisabPulseTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("top_bar_back")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Navigate back"
                    )
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

@Composable
fun MoneyText(
    paise: Long,
    currencySymbol: String = "₹",
    hideAmounts: Boolean = false,
    color: Color = MaterialTheme.colorScheme.onSurface,
    fontWeight: FontWeight = FontWeight.Bold,
    fontSizeSp: Int = 16,
    modifier: Modifier = Modifier
) {
    Text(
        text = Money.formatPaise(paise, currencySymbol, hideAmounts),
        fontFamily = JetBrainsMono,
        fontWeight = fontWeight,
        fontSize = fontSizeSp.sp,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

@Composable
fun KpiMetricCard(
    title: String,
    valuePaise: Long? = null,
    customValueText: String? = null,
    currencySymbol: String = "₹",
    hideAmounts: Boolean = false,
    icon: ImageVector,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    subtitle: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "kpi_card"
) {
    Card(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (valuePaise != null) {
                MoneyText(
                    paise = valuePaise,
                    currencySymbol = currencySymbol,
                    hideAmounts = hideAmounts,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSizeSp = 17
                )
            } else {
                Text(
                    text = customValueText ?: "0",
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun StatusPill(
    label: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(50),
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun EmptyStateCard(
    title: String,
    description: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    showIllustration: Boolean = true,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (showIllustration) {
                Image(
                    painter = painterResource(id = R.drawable.img_empty_ledger),
                    contentDescription = title,
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(14.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSearchSubmit: (() -> Unit)? = null,
    trailingExtra: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                }
                trailingExtra?.invoke()
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearchSubmit?.invoke() }),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun RecentSearchHistoryBar(
    history: List<SearchHistory>,
    onSelectQuery: (String) -> Unit,
    onDeleteEntry: (Long) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Recent searches"
) {
    AnimatedVisibility(visible = history.isNotEmpty(), modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(
                    onClick = onClearAll,
                    modifier = Modifier.testTag("clear_search_history_btn")
                ) {
                    Text("Clear", style = MaterialTheme.typography.labelMedium)
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                history.forEach { item ->
                    InputChip(
                        selected = false,
                        onClick = { onSelectQuery(item.query) },
                        label = {
                            Text(
                                text = item.query,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "Recent search",
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = { onDeleteEntry(item.id) },
                                modifier = Modifier
                                    .size(20.dp)
                                    .testTag("delete_search_chip_${item.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove ${item.query} from recent searches",
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        },
                        modifier = Modifier.testTag("recent_search_chip_${item.query}")
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangeFilterBar(
    selectedFilter: DateRangeFilter,
    onFilterSelected: (DateRangeFilter, Long?, Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    var showRangeDialog by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DateRangeFilter.entries.filter { it != DateRangeFilter.ALL_TIME }.forEach { filter ->
            val selected = filter == selectedFilter
            FilterChip(
                selected = selected,
                onClick = {
                    if (filter == DateRangeFilter.CUSTOM) {
                        showRangeDialog = true
                    } else {
                        onFilterSelected(filter, null, null)
                    }
                },
                label = { Text(filter.label) },
                leadingIcon = if (filter == DateRangeFilter.CUSTOM) {
                    { Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null
            )
        }
    }

    if (showRangeDialog) {
        val state = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showRangeDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    onFilterSelected(
                        DateRangeFilter.CUSTOM,
                        state.selectedStartDateMillis,
                        state.selectedEndDateMillis?.plus(86_399_999L)
                    )
                    showRangeDialog = false
                }) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { showRangeDialog = false }) { Text("Cancel") }
            }
        ) {
            DateRangePicker(state = state, modifier = Modifier.height(420.dp))
        }
    }
}

data class MonthlyChartPoint(
    val label: String,
    val salesPaise: Long,
    val expensePaise: Long
)

@Composable
fun MonthlySalesExpenseChart(
    points: List<MonthlyChartPoint>,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val expenseColor = LocalSemanticColors.current.warning
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Monthly Sales vs Expenses", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LegendDot(color = primaryColor, text = "Sales")
                    LegendDot(color = expenseColor, text = "Expenses")
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            if (points.all { it.salesPaise == 0L && it.expensePaise == 0L }) {
                Text(
                    text = "Record invoices or expenses to populate your monthly performance graph.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = labelColor
                )
            } else {
                val maxVal = points.maxOf { maxOf(it.salesPaise, it.expensePaise) }.coerceAtLeast(1L)
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                ) {
                    val step = size.width / points.size.coerceAtLeast(1)
                    val barW = (step * 0.28f).coerceAtLeast(8f)

                    // Horizontal guide lines
                    for (i in 0..3) {
                        val y = size.height * (i / 3f)
                        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                    }

                    points.forEachIndexed { index, pt ->
                        val cx = index * step + step / 2f
                        val salesH = (pt.salesPaise.toFloat() / maxVal.toFloat()) * (size.height - 12f)
                        val expH = (pt.expensePaise.toFloat() / maxVal.toFloat()) * (size.height - 12f)

                        if (salesH > 0f) {
                            drawRoundRect(
                                color = primaryColor,
                                topLeft = Offset(cx - barW - 2f, size.height - salesH),
                                size = Size(barW, salesH),
                                cornerRadius = CornerRadius(6f, 6f)
                            )
                        }
                        if (expH > 0f) {
                            drawRoundRect(
                                color = expenseColor,
                                topLeft = Offset(cx + 2f, size.height - expH),
                                size = Size(barW, expH),
                                cornerRadius = CornerRadius(6f, 6f)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    points.forEach { pt ->
                        Text(
                            text = pt.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = labelColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = text, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun ConfirmActionDialog(
    title: String,
    message: String,
    confirmLabel: String = "Confirm",
    isDestructive: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = if (isDestructive) {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                } else ButtonDefaults.buttonColors()
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

data class TutorialGuideStep(
    val title: String,
    val description: String,
    val highlightArea: String
)

val HISAB_TUTORIAL_STEPS = listOf(
    TutorialGuideStep("Welcome to HisabPulse", "Manage your entire business offline—GST billing, inventory, Khata, POS, and double-entry books on one phone.", "Overview"),
    TutorialGuideStep("Live Business Dashboard", "Track today's sales, purchases, expenses, profit, receivables, and low stock at a glance. Tap any card to inspect details.", "Home Tab"),
    TutorialGuideStep("Fast Billing & Drafts", "Create GST/Non-GST invoices, estimates, returns, or open the Counter POS. Save drafts and edit them anytime from Customer profiles.", "Billing Tab"),
    TutorialGuideStep("Smart Inventory & Barcodes", "Add products, generate or scan barcodes with CameraX, track batches & expiry, and reconcile stock across warehouses.", "Items Tab"),
    TutorialGuideStep("Customers, Suppliers & Khata", "Manage party balances, receive partial/advance payments, edit customer draft bills, and share PDF Khata statements.", "Parties Tab"),
    TutorialGuideStep("Accounting, Reports & Backups", "Access Cashbook, Expenses, Trial Balance, P&L, Balance Sheet, GST Summaries, and AES-256 encrypted local backups.", "More Tab")
)

@Composable
fun InteractiveTutorialOverlay(
    visible: Boolean,
    stepIndex: Int,
    onStepChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(visible = visible) {
        val safeIdx = stepIndex.coerceIn(0, HISAB_TUTORIAL_STEPS.lastIndex)
        val step = HISAB_TUTORIAL_STEPS[safeIdx]
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.72f))
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusPill(
                            label = "${step.highlightArea} • ${safeIdx + 1}/${HISAB_TUTORIAL_STEPS.size}",
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        TextButton(onClick = onDismiss) { Text("Skip") }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(step.title, style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        step.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedButton(
                            onClick = { if (safeIdx > 0) onStepChange(safeIdx - 1) },
                            enabled = safeIdx > 0
                        ) { Text("Back") }
                        Button(
                            onClick = {
                                if (safeIdx < HISAB_TUTORIAL_STEPS.lastIndex) {
                                    onStepChange(safeIdx + 1)
                                } else {
                                    onDismiss()
                                }
                            }
                        ) {
                            Text(if (safeIdx == HISAB_TUTORIAL_STEPS.lastIndex) "Finish" else "Next")
                        }
                    }
                }
            }
        }
    }
}
