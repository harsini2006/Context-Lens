package com.mpowernet.shesafe.security

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

object PhishingDetector {

    private const val TAG = "PhishingDetector"
    private const val MODEL_PATH = "phishing_model.tflite"
    
    private var interpreter: Interpreter? = null
    private var isModelLoaded = false

    fun init(context: Context) {
        try {
            val buffer = loadModelFile(context, MODEL_PATH)
            interpreter = Interpreter(buffer)
            isModelLoaded = true
            Log.d(TAG, "TFLite Phishing Detector Model loaded successfully.")
        } catch (e: Exception) {
            Log.w(TAG, "TFLite model not found or failed to load. Falling back to layout heuristic engine. Details: ${e.message}")
            isModelLoaded = false
        }
    }

    /**
     * Evaluates a node tree for spoofed permission templates or overlay phishing signs.
     * Combines TFLite visual/text structural inference (when available) with deep layout heuristics.
     */
    fun analyzeScreen(rootNode: AccessibilityNodeInfo?): AnalysisResult {
        if (rootNode == null) return AnalysisResult(isPhishing = false, riskScore = 0f, reason = "Empty screen context")

        // 1. Structural Heuristics & Layout Text Scan
        val textList = mutableListOf<String>()
        val inputFieldCount = scanNodeHierarchy(rootNode, textList)
        
        val screenText = textList.joinToString(" ").lowercase()
        var riskScore = 0.0f
        val riskIndicators = mutableListOf<String>()

        // Indicator 1: Sensitive permission-related words in a non-system package context
        val packageName = rootNode.packageName?.toString() ?: ""
        val isSystemPackage = packageName.contains("android.permissioncontroller") || 
                            packageName.contains("packageinstaller") || 
                            packageName.contains("settings")

        if (!isSystemPackage) {
            val permissionKeywords = listOf("allow", "deny", "permission", "access", "grant", "camera", "microphone", "location", "contacts")
            val dangerAlertKeywords = listOf("critical", "alert", "threat", "compromised", "verify identity", "bank details", "password required")
            
            var permissionMatchCount = 0
            for (keyword in permissionKeywords) {
                if (screenText.contains(keyword)) {
                    permissionMatchCount++
                }
            }

            var dangerMatchCount = 0
            for (keyword in dangerAlertKeywords) {
                if (screenText.contains(keyword)) {
                    dangerMatchCount++
                }
            }

            if (permissionMatchCount >= 2 && screenText.contains("allow")) {
                riskScore += 0.4f
                riskIndicators.add("Spoofed permission layout nodes detected")
            }

            if (dangerMatchCount >= 1) {
                riskScore += 0.3f
                riskIndicators.add("Coercive warning content detected")
            }

            // Indicator 2: High input density combined with permission terms (typical login/phishing forms)
            if (inputFieldCount >= 1 && (screenText.contains("password") || screenText.contains("credentials") || screenText.contains("pin"))) {
                riskScore += 0.2f
                riskIndicators.add("Login field matching credentials harvest pattern")
            }
        }

        // 2. TFLite model inference (hybrid classification fallback)
        if (isModelLoaded && interpreter != null) {
            try {
                // Prepare input: simple normalized text vector (mock size 128)
                val inputVal = FloatArray(128)
                // Convert screenText hash properties into normalized vector weights
                for (i in 0 until 128) {
                    inputVal[i] = if (screenText.hashCode() % (i + 1) == 0) 1.0f else 0.0f
                }
                val outputVal = Array(1) { FloatArray(1) }
                
                interpreter?.run(inputVal, outputVal)
                val modelPrediction = outputVal[0][0]
                
                if (modelPrediction > 0.5f) {
                    riskScore += (modelPrediction * 0.4f)
                    riskIndicators.add("TFLite classifier flag (Confidence: ${"%.2f".format(modelPrediction)})")
                }
            } catch (e: Exception) {
                Log.e(TAG, "TFLite inference failed, relying purely on layout heuristics.", e)
            }
        }

        val isPhishing = riskScore >= 0.5f
        val reason = if (isPhishing) {
            riskIndicators.joinToString(" & ")
        } else {
            "Normal interface structure"
        }

        return AnalysisResult(isPhishing, riskScore.coerceIn(0f, 1f), reason)
    }

    private fun scanNodeHierarchy(node: AccessibilityNodeInfo, textList: java.util.ArrayList<String>): Int {
        var inputCount = 0
        
        node.text?.let {
            if (it.isNotBlank()) textList.add(it.toString())
        }
        node.contentDescription?.let {
            if (it.isNotBlank()) textList.add(it.toString())
        }

        if (node.className == "android.widget.EditText" || node.className == "android.widget.AutoCompleteTextView") {
            inputCount++
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                inputCount += scanNodeHierarchy(child, textList)
            }
        }
        return inputCount
    }

    private fun loadModelFile(context: Context, modelPath: String): MappedByteBuffer {
        val fileDescriptor: AssetFileDescriptor = context.assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel: FileChannel = inputStream.channel
        val startOffset: Long = fileDescriptor.startOffset
        val declaredLength: Long = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    data class AnalysisResult(
        val isPhishing: Boolean,
        val riskScore: Float,
        val reason: String
    )
}
