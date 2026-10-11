package com.example.comicbooktts.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.Log
import kotlin.math.max
import kotlin.math.min

/**
 * Comic-specific image preprocessing using only Android native APIs
 * ([ColorMatrix], [Canvas], [Paint], [Bitmap]).
 *
 * Cleans up comic pages before OCR: screentone/halftone-heavy art, colored
 * speech bubbles and low-contrast lettering all confuse text engines, so the
 * image is normalized to high-contrast black-on-white text first.
 *
 * Three modes trade speed for accuracy:
 * - [PreprocessMode.FAST]: grayscale + simple threshold (~fastest)
 * - [PreprocessMode.BALANCED]: + contrast boost + adaptive threshold (recommended)
 * - [PreprocessMode.QUALITY]: + median denoise + sharpen (slowest, most accurate)
 */
class ComicImagePreprocessor {

    /**
     * Preprocessing intensity level.
     */
    enum class PreprocessMode {
        /** Grayscale + simple global threshold. */
        FAST,

        /** Grayscale + 1.5x contrast + 15x15 adaptive threshold. */
        BALANCED,

        /** BALANCED + 3x3 median denoise + 3x3 sharpen. */
        QUALITY
    }

    companion object {
        private const val TAG = "ComicImagePreprocessor"

        /** Contrast multiplier used by BALANCED and QUALITY modes. */
        private const val CONTRAST_FACTOR = 1.5f

        /** Adaptive threshold window size (square, in pixels). */
        private const val ADAPTIVE_WINDOW = 15

        /** Constant subtracted from the local mean in adaptive thresholding. */
        private const val ADAPTIVE_CONSTANT = 10

        /** Global threshold used by FAST mode. */
        private const val SIMPLE_THRESHOLD = 128

        /** 3x3 sharpen kernel: [0,-1,0; -1,5,-1; 0,-1,0]. */
        private val SHARPEN_KERNEL = intArrayOf(
            0, -1, 0,
            -1, 5, -1,
            0, -1, 0
        )
    }

    /**
     * Preprocesses a bitmap for OCR.
     *
     * The input bitmap is never modified or recycled; a new bitmap is always
     * returned. Intermediate bitmaps are recycled internally.
     *
     * @param bitmap Source image (any config).
     * @param mode Preprocessing intensity.
     * @return New preprocessed bitmap, or the original on unexpected failure.
     */
    fun preprocess(bitmap: Bitmap, mode: PreprocessMode): Bitmap {
        val start = System.currentTimeMillis()
        return try {
            var current = toGrayscale(bitmap)

            current = when (mode) {
                PreprocessMode.FAST -> {
                    val out = applySimpleThreshold(current)
                    current.recycle()
                    out
                }
                PreprocessMode.BALANCED -> {
                    var step = enhanceContrast(current, CONTRAST_FACTOR)
                    current.recycle()
                    current = step
                    step = applyAdaptiveThreshold(current)
                    current.recycle()
                    step
                }
                PreprocessMode.QUALITY -> {
                    var step = enhanceContrast(current, CONTRAST_FACTOR)
                    current.recycle()
                    current = step
                    step = applyAdaptiveThreshold(current)
                    current.recycle()
                    current = step
                    step = reduceNoise(current)
                    current.recycle()
                    current = step
                    step = sharpen(current)
                    current.recycle()
                    step
                }
            }

            Log.d(TAG, "preprocess($mode) ${bitmap.width}x${bitmap.height} " +
                "in ${System.currentTimeMillis() - start}ms")
            current
        } catch (e: Exception) {
            Log.w(TAG, "Preprocessing failed, returning original: ${e.message}")
            bitmap
        }
    }

    /**
     * Converts to grayscale via [ColorMatrix] saturation removal.
     */
    private fun toGrayscale(bitmap: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint()
        val colorMatrix = ColorMatrix()
        colorMatrix.setSaturation(0f)
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return out
    }

    /**
     * Boosts contrast with a scale + translate color matrix.
     *
     * @param factor Contrast multiplier (1.0 = unchanged).
     */
    private fun enhanceContrast(bitmap: Bitmap, factor: Float): Bitmap {
        val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint()
        val translate = (1.0f - factor) / 2.0f * 255f
        val matrix = ColorMatrix(
            floatArrayOf(
                factor, 0f, 0f, 0f, translate,
                0f, factor, 0f, 0f, translate,
                0f, 0f, factor, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return out
    }

    /**
     * Applies a single global threshold (FAST mode).
     */
    private fun applySimpleThreshold(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        for (i in pixels.indices) {
            val gray = pixels[i] and 0xFF
            val v = if (gray > SIMPLE_THRESHOLD) 255 else 0
            pixels[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * Adaptive (local-mean) thresholding using a summed-area table.
     *
     * Each pixel is compared against the mean of its 15x15 neighborhood
     * minus a constant, which handles uneven lighting and colored
     * backgrounds far better than a global threshold. The integral image
     * keeps the per-pixel window query O(1).
     */
    private fun applyAdaptiveThreshold(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val half = ADAPTIVE_WINDOW / 2

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // Summed-area table of grayscale values (use red channel; input is gray).
        val integral = LongArray((w + 1) * (h + 1))
        for (y in 0 until h) {
            var rowSum = 0L
            for (x in 0 until w) {
                rowSum += (pixels[y * w + x] and 0xFF).toLong()
                integral[(y + 1) * (w + 1) + (x + 1)] =
                    integral[y * (w + 1) + (x + 1)] + rowSum
            }
        }

        val out = IntArray(w * h)
        for (y in 0 until h) {
            val y0 = max(0, y - half)
            val y1 = min(h - 1, y + half)
            for (x in 0 until w) {
                val x0 = max(0, x - half)
                val x1 = min(w - 1, x + half)

                // Window sum from the integral image.
                val sum = integral[(y1 + 1) * (w + 1) + (x1 + 1)] -
                    integral[y0 * (w + 1) + (x1 + 1)] -
                    integral[(y1 + 1) * (w + 1) + x0] +
                    integral[y0 * (w + 1) + x0]
                val count = (x1 - x0 + 1) * (y1 - y0 + 1)
                val threshold = (sum / count).toInt() - ADAPTIVE_CONSTANT

                val gray = pixels[y * w + x] and 0xFF
                val v = if (gray > threshold) 255 else 0
                out[y * w + x] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
            }
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(out, 0, w, 0, 0, w, h)
        return result
    }

    /**
     * 3x3 median filter.
     *
     * Removes salt-and-pepper noise (e.g. halftone dots surviving the
     * threshold) while preserving text edges better than a mean filter.
     */
    private fun reduceNoise(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val out = pixels.clone()

        val window = IntArray(9)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                var k = 0
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        window[k++] = pixels[(y + dy) * w + (x + dx)] and 0xFF
                    }
                }
                window.sort()
                val median = window[4]
                val idx = y * w + x
                out[idx] = (0xFF shl 24) or (median shl 16) or (median shl 8) or median
            }
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(out, 0, w, 0, 0, w, h)
        return result
    }

    /**
     * Sharpens with a 3x3 convolution kernel [0,-1,0; -1,5,-1; 0,-1,0].
     *
     * Enhances text edges, helping OCR on slightly blurry scans.
     */
    private fun sharpen(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val out = pixels.clone()

        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                var acc = 0
                var k = 0
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        acc += (pixels[(y + dy) * w + (x + dx)] and 0xFF) * SHARPEN_KERNEL[k++]
                    }
                }
                val v = acc.coerceIn(0, 255)
                val idx = y * w + x
                out[idx] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
            }
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(out, 0, w, 0, 0, w, h)
        return result
    }
}
