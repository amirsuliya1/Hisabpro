package com.example.ui.screens.intelligence

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.HisabPulseApp
import com.example.core.DateUtils
import com.example.data.repository.BusinessHealthSummary
import com.example.ui.components.HisabPulseTopBar
import com.example.ui.components.StatusPill
import com.example.ui.theme.LocalSemanticColors
import kotlinx.coroutines.launch

@Composable
fun BusinessHealthScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as HisabPulseApp
    val scope = rememberCoroutineScope()
    val semantic = LocalSemanticColors.current

    val history by app.container.intelligenceRepository.healthHistory.collectAsStateWithLifecycle(emptyList())
    var health by remember { mutableStateOf<BusinessHealthSummary?>(null) }
    var loading by remember { mutableStateOf(true) }

    fun recalculate() {
        scope.launch {
            loading = true
            health = app.container.intelligenceRepository.computeAndSaveBusinessHealth()
            loading = false
        }
    }

    LaunchedEffect(Unit) { recalculate() }

    Scaffold(
        topBar = {
            HisabPulseTopBar(
                title = "Business Health Score",
                subtitle = "10 weighted financial & inventory metrics",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { recalculate() }, modifier = Modifier.testTag("recompute_health_btn")) {
                        Icon(Icons.Default.Refresh, contentDescription = "Recalculate health score")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (loading) {
                item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            }
            val data = health
            if (data != null) {
                val scoreColor = when {
                    data.overallScore >= 75 -> semantic.success
                    data.overallScore >= 50 -> semantic.warning
                    else -> semantic.danger
                }
                item {
                    val trackColor = MaterialTheme.colorScheme.surfaceVariant
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            Box(modifier = Modifier.size(108.dp), contentAlignment = Alignment.Center) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val stroke = 12.dp.toPx()
                                    drawArc(
                                        color = trackColor,
                                        startAngle = 135f,
                                        sweepAngle = 270f,
                                        useCenter = false,
                                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                                    )
                                    drawArc(
                                        color = scoreColor,
                                        startAngle = 135f,
                                        sweepAngle = 270f * (data.overallScore / 100f),
                                        useCenter = false,
                                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${data.overallScore}", style = MaterialTheme.typography.headlineLarge, color = scoreColor, fontWeight = FontWeight.Bold)
                                    Text("/ 100", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    when {
                                        !data.hasMinimumData -> "Awaiting Business Activity"
                                        data.overallScore >= 75 -> "Strong Business Health"
                                        data.overallScore >= 50 -> "Moderate Health — Optimize Cash & Stock"
                                        else -> "Attention Needed"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (!data.hasMinimumData) "Metrics marked 'Insufficient data' are honestly excluded from strengths/weaknesses until transactions exist."
                                    else "Computed offline from invoices, purchases, stock velocity, receivables, and double-entry books.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (data.recommendations.isNotEmpty()) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Text("Actionable Recommendations", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                }
                                data.recommendations.forEach { rec ->
                                    Text("• $rec", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }

                if (data.strengths.isNotEmpty()) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = semantic.successContainer.copy(alpha = 0.5f))) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = semantic.success)
                                    Text("Strengths", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                }
                                data.strengths.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                }

                if (data.weaknesses.isNotEmpty()) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = semantic.warningContainer.copy(alpha = 0.6f))) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = semantic.warning)
                                    Text("Areas to Improve", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                }
                                data.weaknesses.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                }

                item { Text("10 Weighted Sub-Metrics", style = MaterialTheme.typography.titleMedium) }
                items(data.subMetrics) { metric ->
                    val (bg, fg) = when {
                        !metric.hasEnoughData -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
                        metric.score >= 75 -> semantic.successContainer to semantic.success
                        metric.score >= 55 -> semantic.warningContainer to semantic.warning
                        else -> semantic.dangerContainer to semantic.danger
                    }
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${metric.name} (${metric.weight}% wt)", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                StatusPill(metric.statusLabel, bg, fg)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Value: ${metric.valueText}", style = MaterialTheme.typography.bodySmall)
                                Text("Score: ${metric.score}/100", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                            LinearProgressIndicator(
                                progress = { metric.score / 100f },
                                modifier = Modifier.fillMaxWidth(),
                                color = fg
                            )
                        }
                    }
                }
            }

            if (history.isNotEmpty()) {
                item { Text("Health Score History (${history.size})", style = MaterialTheme.typography.titleMedium) }
                items(history.take(10), key = { it.id }) { snap ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(DateUtils.formatDateTime(snap.recordedAtMillis), style = MaterialTheme.typography.bodyMedium)
                            Text("${snap.overallScore}/100", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
