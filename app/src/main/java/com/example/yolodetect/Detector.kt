package com.example.yolodetect

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min

private const val TAG = "Detector"

class Detector(private val context: Context) {
    private val inputSize = 640
    private val confThresh = 0.30f
    private val iouThresh = 0.50f

    private val tflite: Interpreter by lazy {
        val mm = loadModel("best_float32.tflite")
        val itp = Interpreter(mm)
        try { Log.i(TAG, "Output shape: [${itp.getOutputTensor(0).shape().joinToString()}]") }
        catch (_: Throwable) {}
        itp
    }


    fun detect(src: Bitmap): List<Detection> {
        val input = Array(1) { Array(inputSize) { Array(inputSize) { FloatArray(3) } } }
        for (y in 0 until inputSize) for (x in 0 until inputSize) {
            val p = src.getPixel(x, y)
            input[0][y][x][0] = ((p shr 16) and 0xFF) / 255f
            input[0][y][x][1] = ((p shr 8) and 0xFF) / 255f
            input[0][y][x][2] = (p and 0xFF) / 255f
        }

        // YOLOv8 TF export (simplified): (1, 5, 8400)  [cx, cy, w, h, score]
        val out = Array(1) { Array(5) { FloatArray(8400) } }
        tflite.run(input, out)

        val boxes = mutableListOf<Detection>()
        for (i in 0 until 8400) {
            val cx = out[0][0][i]; val cy = out[0][1][i]
            val w  = out[0][2][i]; val h  = out[0][3][i]
            val sc = out[0][4][i]
            if (sc < confThresh || w <= 0f || h <= 0f) continue
            val x1 = (cx - w/2f) * inputSize
            val y1 = (cy - h/2f) * inputSize
            val x2 = (cx + w/2f) * inputSize
            val y2 = (cy + h/2f) * inputSize
            boxes.add(Detection(x1, y1, x2, y2, sc))
        }
        val kept = nms(boxes, iouThresh)
        Log.d(TAG, "detections=${kept.size} (pre=${boxes.size})")
        return kept
    }

    private fun nms(dets: List<Detection>, iouThresh: Float): List<Detection> {
        val sorted = dets.sortedByDescending { it.score }.toMutableList()
        val keep = mutableListOf<Detection>()
        while (sorted.isNotEmpty()) {
            val a = sorted.removeAt(0)
            keep.add(a)
            val it = sorted.iterator()
            while (it.hasNext()) if (iou(a, it.next()) > iouThresh) it.remove()
        }
        return keep
    }

    private fun iou(a: Detection, b: Detection): Float {
        val x1 = max(a.x1, b.x1); val y1 = max(a.y1, b.y1)
        val x2 = min(a.x2, b.x2); val y2 = min(a.y2, b.y2)
        val inter = max(0f, x2 - x1) * max(0f, y2 - y1)
        val areaA = (a.x2 - a.x1) * (a.y2 - a.y1)
        val areaB = (b.x2 - b.x1) * (b.y2 - b.y1)
        val denom = areaA + areaB - inter
        return if (denom <= 0f) 0f else inter / denom
    }

    private fun loadModel(name: String): MappedByteBuffer {
        val afd = context.assets.openFd(name)
        FileInputStream(afd.fileDescriptor).channel.use { fc ->
            return fc.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
        }
    }
}
