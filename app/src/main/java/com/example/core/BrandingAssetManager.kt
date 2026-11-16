package com.example.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

data class SignatureStrokePoint(val x: Float, val y: Float)

object BrandingAssetManager {
    private val bitmapCache = object : LruCache<String, Bitmap>(24) {}

    private fun brandingDir(context: Context): File =
        File(context.filesDir, "branding").apply { mkdirs() }

    fun clearCacheFor(uriOrPath: String?) {
        if (uriOrPath.isNullOrBlank()) return
        bitmapCache.snapshot().keys
            .filter { it.startsWith("$uriOrPath|") }
            .forEach { bitmapCache.remove(it) }
    }

    suspend fun saveImageUriToInternalPng(
        context: Context,
        sourceUri: Uri,
        prefix: String,
        maxDimensionPx: Int = 512
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
            } ?: error("Unable to read selected image")

            var sampleSize = 1
            while ((bounds.outWidth / sampleSize) > maxDimensionPx * 2 ||
                (bounds.outHeight / sampleSize) > maxDimensionPx * 2
            ) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val rawBitmap = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOpts)
            } ?: error("Unable to decode selected image")

            val scaledBitmap = scaleBitmapToFit(rawBitmap, maxDimensionPx, maxDimensionPx)
            if (scaledBitmap !== rawBitmap) rawBitmap.recycle()

            val outFile = File(brandingDir(context), "${prefix}_${System.currentTimeMillis()}.png")
            FileOutputStream(outFile).use { fos ->
                scaledBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                fos.flush()
            }
            if (scaledBitmap !== rawBitmap) scaledBitmap.recycle()
            val resultUri = Uri.fromFile(outFile).toString()
            clearCacheFor(resultUri)
            resultUri
        }
    }

    suspend fun saveSignatureStrokesToPng(
        context: Context,
        strokes: List<List<SignatureStrokePoint>>,
        canvasWidth: Float,
        canvasHeight: Float,
        strokeColorInt: Int = Color.rgb(15, 45, 92),
        strokeWidthPx: Float = 6f,
        prefix: String = "signature"
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(strokes.any { it.isNotEmpty() }) { "Please draw your signature first" }
            val outWidth = 640
            val outHeight = 220
            val bmp = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(Color.TRANSPARENT)

            val scaleX = if (canvasWidth > 0f) outWidth / canvasWidth else 1f
            val scaleY = if (canvasHeight > 0f) outHeight / canvasHeight else 1f
            val uniformScale = min(scaleX, scaleY)
            val offsetX = (outWidth - canvasWidth * uniformScale) / 2f
            val offsetY = (outHeight - canvasHeight * uniformScale) / 2f

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = strokeColorInt
                style = Paint.Style.STROKE
                strokeWidth = strokeWidthPx
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }

            strokes.forEach { pts ->
                if (pts.size == 1) {
                    val p = pts.first()
                    canvas.drawCircle(
                        offsetX + p.x * uniformScale,
                        offsetY + p.y * uniformScale,
                        strokeWidthPx / 2f,
                        Paint(paint).apply { style = Paint.Style.FILL }
                    )
                } else if (pts.size > 1) {
                    val path = Path()
                    val p0 = pts.first()
                    var prevX = offsetX + p0.x * uniformScale
                    var prevY = offsetY + p0.y * uniformScale
                    path.moveTo(prevX, prevY)
                    for (i in 1 until pts.size) {
                        val curX = offsetX + pts[i].x * uniformScale
                        val curY = offsetY + pts[i].y * uniformScale
                        val midX = (prevX + curX) / 2f
                        val midY = (prevY + curY) / 2f
                        path.quadTo(prevX, prevY, midX, midY)
                        prevX = curX
                        prevY = curY
                    }
                    path.lineTo(prevX, prevY)
                    canvas.drawPath(path, paint)
                }
            }

            val outFile = File(brandingDir(context), "${prefix}_${System.currentTimeMillis()}.png")
            FileOutputStream(outFile).use { fos ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
                fos.flush()
            }
            bmp.recycle()
            val resultUri = Uri.fromFile(outFile).toString()
            clearCacheFor(resultUri)
            resultUri
        }
    }

    fun decodeDownsampledBitmap(
        context: Context,
        uriOrPath: String?,
        maxWidth: Int,
        maxHeight: Int
    ): Bitmap? {
        if (uriOrPath.isNullOrBlank()) return null
        val key = "$uriOrPath|$maxWidth|$maxHeight"
        bitmapCache.get(key)?.takeIf { !it.isRecycled }?.let { return it }

        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            openStreamForUriOrPath(context, uriOrPath)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            } ?: return null

            var sampleSize = 1
            while ((bounds.outWidth / sampleSize) > maxWidth * 2 ||
                (bounds.outHeight / sampleSize) > maxHeight * 2
            ) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = openStreamForUriOrPath(context, uriOrPath)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            } ?: return null

            val fitted = scaleBitmapToFit(decoded, maxWidth, maxHeight)
            if (fitted !== decoded) decoded.recycle()
            bitmapCache.put(key, fitted)
            fitted
        }.getOrNull()
    }

    private fun openStreamForUriOrPath(context: Context, uriOrPath: String) = runCatching {
        val parsed = Uri.parse(uriOrPath)
        when (parsed.scheme) {
            null, "" -> File(uriOrPath).takeIf { it.exists() }?.inputStream()
            "file" -> parsed.path?.let { File(it) }?.takeIf { it.exists() }?.inputStream()
            else -> context.contentResolver.openInputStream(parsed)
        }
    }.getOrNull()

    private fun scaleBitmapToFit(source: Bitmap, maxW: Int, maxH: Int): Bitmap {
        if (source.width <= maxW && source.height <= maxH) return source
        val ratio = min(
            maxW.toFloat() / max(source.width, 1),
            maxH.toFloat() / max(source.height, 1)
        )
        val w = (source.width * ratio).toInt().coerceAtLeast(1)
        val h = (source.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, w, h, true)
    }
}
