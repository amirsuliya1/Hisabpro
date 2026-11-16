package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.BrandingAssetManager
import com.example.core.SignatureStrokePoint
import kotlinx.coroutines.launch

private val INK_OPTIONS = listOf(
    "Navy" to Color(0xFF0F2D5C),
    "Ink Black" to Color(0xFF0F172A),
    "Royal Blue" to Color(0xFF1D4ED8)
)

@Composable
fun SignaturePadDialog(
    title: String = "Draw Authorized Signature",
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val completedStrokes = remember { mutableStateListOf<List<Offset>>() }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var selectedInk by remember { mutableStateOf(INK_OPTIONS.first().second) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Draw, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        INK_OPTIONS.forEach { (name, ink) ->
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(ink)
                                    .border(
                                        width = if (selectedInk == ink) 2.5.dp else 1.dp,
                                        color = if (selectedInk == ink) MaterialTheme.colorScheme.primary else Color.LightGray,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedInk = ink },
                                contentAlignment = Alignment.Center
                            ) {
                                if (selectedInk == ink) {
                                    Icon(Icons.Default.Check, contentDescription = name, tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }

                Text(
                    "Sign cleanly with your finger inside the box below. Saved directly to your invoices & PDFs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFFAFAFC))
                        .border(1.5.dp, Color(0xFFCBD5E1), RoundedCornerShape(14.dp))
                        .onSizeChanged { canvasSize = it }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { start ->
                                    errorMsg = null
                                    currentStroke = listOf(start)
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    currentStroke = currentStroke + change.position
                                },
                                onDragEnd = {
                                    if (currentStroke.isNotEmpty()) {
                                        completedStrokes.add(currentStroke)
                                    }
                                    currentStroke = emptyList()
                                },
                                onDragCancel = {
                                    currentStroke = emptyList()
                                }
                            )
                        }
                        .testTag("signature_draw_canvas")
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Signature guide line
                        val guideY = size.height * 0.76f
                        drawLine(
                            color = Color(0xFFCBD5E1),
                            start = Offset(24f, guideY),
                            end = Offset(size.width - 24f, guideY),
                            strokeWidth = 2f
                        )

                        fun drawStrokePath(pts: List<Offset>) {
                            if (pts.size == 1) {
                                drawCircle(color = selectedInk, radius = 4f, center = pts.first())
                            } else if (pts.size > 1) {
                                val path = Path().apply {
                                    moveTo(pts.first().x, pts.first().y)
                                    var prev = pts.first()
                                    for (i in 1 until pts.size) {
                                        val cur = pts[i]
                                        val mid = Offset((prev.x + cur.x) / 2f, (prev.y + cur.y) / 2f)
                                        quadraticTo(prev.x, prev.y, mid.x, mid.y)
                                        prev = cur
                                    }
                                    lineTo(prev.x, prev.y)
                                }
                                drawPath(
                                    path = path,
                                    color = selectedInk,
                                    style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                )
                            }
                        }

                        completedStrokes.forEach { drawStrokePath(it) }
                        if (currentStroke.isNotEmpty()) drawStrokePath(currentStroke)
                    }

                    if (completedStrokes.isEmpty() && currentStroke.isEmpty()) {
                        Text(
                            text = "Sign Here",
                            color = Color(0xFF94A3B8),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }

                if (errorMsg != null) {
                    Text(errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            if (completedStrokes.isNotEmpty()) {
                                completedStrokes.removeAt(completedStrokes.lastIndex)
                            }
                        },
                        enabled = !isSaving && completedStrokes.isNotEmpty()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Undo")
                    }
                    TextButton(
                        onClick = {
                            completedStrokes.clear()
                            currentStroke = emptyList()
                            errorMsg = null
                        },
                        enabled = !isSaving && completedStrokes.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = onDismiss, enabled = !isSaving) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            if (completedStrokes.isEmpty()) {
                                errorMsg = "Draw your signature before saving"
                                return@Button
                            }
                            isSaving = true
                            scope.launch {
                                val mapped = completedStrokes.map { stroke ->
                                    stroke.map { SignatureStrokePoint(it.x, it.y) }
                                }
                                BrandingAssetManager.saveSignatureStrokesToPng(
                                    context = context,
                                    strokes = mapped,
                                    canvasWidth = canvasSize.width.toFloat().coerceAtLeast(1f),
                                    canvasHeight = canvasSize.height.toFloat().coerceAtLeast(1f),
                                    strokeColorInt = selectedInk.toArgb()
                                ).onSuccess { savedUri ->
                                    isSaving = false
                                    onSaved(savedUri)
                                }.onFailure { err ->
                                    isSaving = false
                                    errorMsg = err.message ?: "Failed to save signature"
                                }
                            }
                        },
                        enabled = !isSaving && completedStrokes.isNotEmpty(),
                        modifier = Modifier.testTag("save_drawn_signature_btn")
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}
