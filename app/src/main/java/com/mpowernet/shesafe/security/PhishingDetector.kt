package com.mpowernet.shesafe.security

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.sqrt

object PhishingDetector {

    private const val TAG = "PhishingDetector"
    private const val MODEL_PATH = "phishing_model.tflite"
    
    private var interpreter: Interpreter? = null
    private var isModelLoaded = false

    // Pre-seeded Phishing Corpus (Malware overlays, social engineering popup texts)
    private val phishingCorpus = listOf(
        "critical system security alert threat detected verify identity bank credentials password pin",
        "verify debit card banking account security code update warning pay now",
        "malware attack compromise immediate action required contact support now details credentials login",
        "unauthorized transaction notification approve transfer verification required instantly"
    )

    // Pre-seeded System Dialog Corpus (Official, safe Android dialog templates)
    private val systemCorpus = listOf(
        "allow shesafe to access this device's location contacts media photo",
        "shesafe needs permission to access files camera microphone on this device",
        "grant permission package installer settings android control access"
    )

    fun init(context: Context) {
        try {
            val buffer = loadModelFile(context, MODEL_PATH)
            interpreter = Interpreter(buffer)
            isModelLoaded = true
            Log.d(TAG, "TFLite Phishing Detector Model loaded successfully.")
        } catch (e: Exception) {
            Log.w(TAG, "TFLite model not found or failed to load. Falling back to layout TF-IDF Cosine Similarity engine. Details: ${e.message}")
            isModelLoaded = false
        }
    }

    /**
     * Evaluates a node tree for spoofed templates or overlay phishing signs.
     * Combines TFLite visual/text structural inference (when available) with deep NLP TF-IDF cosine similarity.
     */
    fun analyzeScreen(rootNode: AccessibilityNodeInfo?): AnalysisResult {
        if (rootNode == null) return AnalysisResult(isPhishing = false, riskScore = 0f, reason = "Empty screen context")

        val textList = mutableListOf<String>()
        val inputFieldCount = scanNodeHierarchy(rootNode, textList)
        
        val screenText = textList.joinToString(" ").lowercase()
        val packageName = rootNode.packageName?.toString() ?: ""
        
        val isSystemPackage = packageName.contains("android.permissioncontroller") || 
                            packageName.contains("packageinstaller") || 
                            packageName.contains("settings")

        var riskScore = 0.0f
        val riskIndicators = mutableListOf<String>()

        // 1. NLP TF-IDF Cosine Similarity Analysis
        if (screenText.isNotBlank() && !isSystemPackage) {
            val phishingSimilarity = calculateMaxCosineSimilarity(screenText, phishingCorpus)
            val systemSimilarity = calculateMaxCosineSimilarity(screenText, systemCorpus)

            Log.d(TAG, "TF-IDF Similarities: Phishing=$phishingSimilarity, System=$systemSimilarity")

            if (phishingSimilarity > 0.35f && phishingSimilarity > systemSimilarity) {
                riskScore += phishingSimilarity.coerceAtMost(0.6f)
                riskIndicators.add("TF-IDF match on phishing templates (Similarity: ${"%.2f".format(phishingSimilarity)})")
            }
        }

        // 2. Extra Structural Heuristics & Layout Text Scan
        if (!isSystemPackage) {
            val permissionKeywords = listOf("allow", "deny", "permission", "access", "grant", "camera", "microphone", "location", "contacts")
            var permissionMatchCount = 0
            for (keyword in permissionKeywords) {
                if (screenText.contains(keyword)) {
                    permissionMatchCount++
                }
            }

            if (permissionMatchCount >= 2 && screenText.contains("allow")) {
                riskScore += 0.3f
                riskIndicators.add("Spoofed permission layout nodes detected")
            }

            if (inputFieldCount >= 1 && (screenText.contains("password") || screenText.contains("credentials") || screenText.contains("pin"))) {
                riskScore += 0.2f
                riskIndicators.add("Credential entry fields matching phishing collection layout")
            }
        }

        // 3. TFLite model inference (hybrid classification fallback)
        if (isModelLoaded && interpreter != null) {
            try {
                val inputVal = FloatArray(128)
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

    private fun scanNodeHierarchy(node: AccessibilityNodeInfo, textList: MutableList<String>): Int {
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

    /**
     * Tokenizes a text block and calculates TF-IDF vectors to compute Cosine Similarity.
     */
    private fun calculateMaxCosineSimilarity(input: String, corpus: List<String>): Float {
        var maxSim = 0.0f
        val inputTokens = input.lowercase().split(Regex("[^a-zA-Z0-9]+")).filter { it.isNotBlank() }
        
        if (inputTokens.isEmpty()) return 0.0f

        for (doc in corpus) {
            val docTokens = doc.lowercase().split(Regex("[^a-zA-Z0-9]+")).filter { it.isNotBlank() }
            val unionVocab = (inputTokens + docTokens).distinct()

            val inputVec = DoubleArray(unionVocab.size)
            val docVec = DoubleArray(unionVocab.size)

            for (i in unionVocab.indices) {
                val term = unionVocab[i]
                
                // Simple Term Frequency (TF)
                val tfInput = inputTokens.count { it == term }.toDouble() / inputTokens.size
                val tfDoc = docTokens.count { it == term }.toDouble() / docTokens.size

                // Inverse Document Frequency (IDF) - computed over input + doc
                val docWithTerm = (if (inputTokens.contains(term)) 1 else 0) + (if (docTokens.contains(term)) 1 else 0)
                val idf = Math.log(3.0 / (1.0 + docWithTerm))

                inputVec[i] = tfInput * idf
                docVec[i] = tfDoc * idf
            }

            // Cosine Similarity calculation
            var dotProduct = 0.0
            var normInput = 0.0
            var normDoc = 0.0
            for (i in unionVocab.indices) {
                dotProduct += inputVec[i] * docVec[i]
                normInput += inputVec[i] * inputVec[i]
                normDoc += docVec[i] * docVec[i]
            }

            val sim = if (normInput > 0.0 && normDoc > 0.0) {
                dotProduct / (sqrt(normInput) * sqrt(normDoc))
            } else {
                0.0
            }
            if (sim > maxSim) {
                maxSim = sim.toFloat()
            }
        }
        return maxSim
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
