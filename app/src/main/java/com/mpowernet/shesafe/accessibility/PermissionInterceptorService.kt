package com.mpowernet.shesafe.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.mpowernet.shesafe.SheSafeApplication
import com.mpowernet.shesafe.data.AppDatabase
import com.mpowernet.shesafe.data.entity.ConsentLog
import com.mpowernet.shesafe.data.entity.PermissionRule
import kotlinx.coroutines.*
import java.util.Locale

class PermissionInterceptorService : AccessibilityService(), TextToSpeech.OnInitListener {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var windowManager: WindowManager? = null
    private var overlayLayout: FrameLayout? = null
    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false
    private var isTtsEnabled = false

    private lateinit var database: AppDatabase
    private var activePermissionDialog: String? = null
    private var lastTargetPackage: String = "Unknown Application"

    companion object {
        private const val TAG = "SheSafeInterceptor"
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        tts = TextToSpeech(this, this)
        database = (application as SheSafeApplication).database
        
        // Initialize TFLite phishing detector
        com.mpowernet.shesafe.security.PhishingDetector.init(applicationContext)

        // Read TTS preference
        val prefs = getSharedPreferences("shesafe_prefs", Context.MODE_PRIVATE)
        isTtsEnabled = prefs.getBoolean("tts_enabled", false)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                isTtsInitialized = true
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val pkgName = event.packageName?.toString() ?: ""
                val className = event.className?.toString() ?: ""

                // Detect system permission dialog
                if (pkgName.contains("permissioncontroller") || pkgName.contains("packageinstaller")) {
                    // Give the system a brief moment to render nodes
                    serviceScope.launch {
                        delay(250)
                        inspectActiveWindow()
                    }
                } else {
                    // Update the last active application package
                    if (pkgName.isNotEmpty() && !pkgName.contains("shesafe") && 
                        !pkgName.contains("android") && !pkgName.contains("launcher")) {
                        lastTargetPackage = pkgName
                    }

                    // Check for spoofed overlays or phishing on external screens
                    serviceScope.launch {
                        delay(150)
                        val rootNode = rootInActiveWindow
                        val phishingResult = withContext(Dispatchers.Default) {
                            com.mpowernet.shesafe.security.PhishingDetector.analyzeScreen(rootNode)
                        }
                        if (phishingResult.isPhishing) {
                            val phishingRule = PermissionRule(
                                permissionId = "PHISHING_ALERT",
                                systemLabel = "PHISHING ATTEMPT",
                                riskLevel = "HIGH",
                                explanationEnglish = "SUSPICIOUS POPUP DETECTED: ${phishingResult.reason}.",
                                explanationHindi = "संदेहास्पद पॉपअप चेतावनी: ${phishingResult.reason}.",
                                allowedPackagesCSV = ""
                            )
                            showVisualConsequenceOverlay(phishingRule)
                        }
                    }
                }
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                val pkgName = event.packageName?.toString() ?: ""
                if (pkgName.contains("permissioncontroller") || pkgName.contains("packageinstaller")) {
                    serviceScope.launch {
                        inspectActiveWindow()
                    }
                }
            }
        }
    }

    private suspend fun inspectActiveWindow() {
        val rootNode = rootInActiveWindow ?: return
        val texts = withContext(Dispatchers.Default) {
            val list = mutableListOf<String>()
            extractTextsFromNode(rootNode, list)
            list
        }
        
        // Scan for permissions
        var detectedPermission: String? = null
        for (text in texts) {
            val lowerText = text.lowercase()
            when {
                lowerText.contains("location") || lowerText.contains("स्थान") -> {
                    detectedPermission = "android.permission.ACCESS_FINE_LOCATION"
                }
                lowerText.contains("camera") || lowerText.contains("कैमरा") -> {
                    detectedPermission = "android.permission.CAMERA"
                }
                lowerText.contains("record audio") || lowerText.contains("microphone") || lowerText.contains("माइक") -> {
                    detectedPermission = "android.permission.RECORD_AUDIO"
                }
                lowerText.contains("contacts") || lowerText.contains("संपर्क") -> {
                    detectedPermission = "android.permission.READ_CONTACTS"
                }
                lowerText.contains("photos") || lowerText.contains("media") || lowerText.contains("फ़ोटो") -> {
                    detectedPermission = "android.permission.READ_MEDIA_IMAGES"
                }
            }
            if (detectedPermission != null) break
        }

        if (detectedPermission != null && activePermissionDialog != detectedPermission) {
            activePermissionDialog = detectedPermission

            // Check session velocity anomaly (Permission Escalation Protection)
            val isAnomaly = com.mpowernet.shesafe.security.BehavioralPatternEngine.recordRequestAndCheckAnomaly(lastTargetPackage)
            
            if (isAnomaly) {
                val anomalyRule = PermissionRule(
                    permissionId = detectedPermission,
                    systemLabel = "ESCALATION DETECTED",
                    riskLevel = "HIGH",
                    explanationEnglish = "SUSPICIOUS VELOCITY: App requested too many permissions rapidly.",
                    explanationHindi = "संदेहास्पद गतिविधि: ऐप ने बहुत तेज़ी से अनुमति मांगी।",
                    allowedPackagesCSV = ""
                )
                showVisualConsequenceOverlay(anomalyRule)
            } else {
                val rule = withContext(Dispatchers.IO) {
                    database.permissionRuleDao().getRuleForPermission(detectedPermission)
                }
                if (rule != null) {
                    showVisualConsequenceOverlay(rule)
                }
            }
        }
    }

    private fun extractTextsFromNode(node: AccessibilityNodeInfo?, texts: MutableList<String>) {
        if (node == null) return
        val text = node.text
        if (!text.isNullOrEmpty()) {
            texts.add(text.toString())
        }
        for (i in 0 until node.childCount) {
            extractTextsFromNode(node.getChild(i), texts)
        }
    }

    private fun showVisualConsequenceOverlay(rule: PermissionRule) {
        if (overlayLayout != null) {
            removeOverlay()
        }

        // Check if application integrity is compromised
        val isIntegrityFailed = !SheSafeApplication.isDatabaseIntegrityValid || !SheSafeApplication.isPlayIntegrityValid

        // Determine actual risk mapping dynamically based on requesting package
        val allowedApps = rule.allowedPackagesCSV.split(",")
        val isExpected = allowedApps.any { lastTargetPackage.contains(it) }
        val adjustedRisk = if (isIntegrityFailed) "HIGH" else if (isExpected) "LOW" else rule.riskLevel

        // Fetch selected trust seal from SharedPreferences
        val prefs = getSharedPreferences("shesafe_prefs", Context.MODE_PRIVATE)
        val selectedSeal = prefs.getString("personal_trust_seal", "🌻") ?: "🌻"

        overlayLayout = FrameLayout(this).apply {
            // Supply window bindings so ComposeView has lifecycle awareness
            val lifecycleOwner = CustomLifecycleOwner()
            lifecycleOwner.start()
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(CustomViewModelStoreOwner())
            setViewTreeSavedStateRegistryOwner(CustomSavedStateRegistryOwner(this))
        }

        val composeView = ComposeView(this).apply {
            // Enable tapjacking protection on the root Compose overlay view
            filterTouchesWhenObscured = true
            
            setContent {
                ConsequenceCardOverlay(
                    rule = rule,
                    assignedRisk = adjustedRisk,
                    targetPackage = lastTargetPackage,
                    trustSeal = selectedSeal,
                    isIntegrityFailed = isIntegrityFailed,
                    onDismiss = { decision ->
                        serviceScope.launch {
                            val signatureResult = withContext(Dispatchers.Default) {
                                com.mpowernet.shesafe.security.ZkcpEngine.signConsent(
                                    packageName = lastTargetPackage,
                                    permission = rule.permissionId,
                                    decision = decision
                                )
                            }
                            withContext(Dispatchers.IO) {
                                database.consentLogDao().insertLog(
                                    ConsentLog(
                                        timestamp = System.currentTimeMillis(),
                                        appPackage = lastTargetPackage,
                                        permissionRequested = rule.permissionId,
                                        riskAssigned = adjustedRisk,
                                        decision = decision,
                                        zkcpSignature = signatureResult.signature,
                                        zkcpSalt = signatureResult.salt
                                    )
                                )
                            }
                            // Re-calculate the trusted database file hash after modifications
                            (application as SheSafeApplication).updateDatabaseHash()
                            removeOverlay()
                        }
                    }
                )
            }
        }

        overlayLayout?.addView(composeView)

        val params = WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or 
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.CENTER
        }

        // Apply tapjacking split touch flags
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_SPLIT_TOUCH
        }

        windowManager?.addView(overlayLayout, params)

        // Read out loud if TTS is enabled and initialized
        if (isTtsEnabled && isTtsInitialized) {
            val speakText = if (isIntegrityFailed) {
                "SECURITY WARNING. SYSTEM TAMPERING DETECTED. DO NOT TRUST THIS SCREEN."
            } else if (adjustedRisk == "HIGH") {
                "DANGER. ${rule.systemLabel} REQUEST DETECTED. ${rule.explanationEnglish}"
            } else {
                "${rule.systemLabel} Request. ${rule.explanationEnglish}"
            }
            tts?.speak(speakText, TextToSpeech.QUEUE_FLUSH, null, "SheSafeTTS")
        }
    }

    private fun removeOverlay() {
        if (overlayLayout != null) {
            try {
                windowManager?.removeView(overlayLayout)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing overlay", e)
            }
            overlayLayout = null
            activePermissionDialog = null
        }
    }

    override fun onInterrupt() {
        removeOverlay()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        tts?.stop()
        tts?.shutdown()
        removeOverlay()
    }
}

@Composable
fun ConsequenceCardOverlay(
    rule: PermissionRule,
    assignedRisk: String,
    targetPackage: String,
    trustSeal: String,
    isIntegrityFailed: Boolean,
    onDismiss: (String) -> Unit
) {
    var useHindi by remember { mutableStateOf(false) }
    
    val riskColor = when (assignedRisk) {
        "HIGH" -> Color(0xFFFF1744)   // Neon coral red
        "MEDIUM" -> Color(0xFFFFB300) // Amber yellow
        else -> Color(0xFF00E676)     // Neon green
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE60B0F19)), // Deep translucent midnight dark dim
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .border(2.dp, riskColor, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161E30))
        ) {
            Box(
                modifier = Modifier
                    .background(Color(0xFF161E30))
                    .padding(24.dp)
            ) {
                // Trust Seal Floating indicator in top-right corner to prevent overlays
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(Color(0xFF0B0F19), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF00B0FF), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = trustSeal,
                        fontSize = 16.sp
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Risk Badge
                    Box(
                        modifier = Modifier
                            .background(riskColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                            .border(1.2.dp, riskColor, RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (isIntegrityFailed) {
                                "⚠️ CHEATING / छेड़छाड़"
                            } else if (assignedRisk == "HIGH") {
                                "⚠️ CRITICAL / खतरा"
                            } else {
                                "🛡️ CAUTION / चेतावनी"
                            },
                            color = riskColor,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            letterSpacing = 0.5.sp
                        )
                    }

                    // App Name Context
                    Text(
                        text = if (useHindi) {
                            "एप्लिकेशन \"${targetPackage.substringAfterLast(".")}\" अनुमति मांग रहा है:"
                        } else {
                            "APPLICATION \"${targetPackage.substringAfterLast(".")}\" IS REQUESTING:"
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF90A4AE),
                        textAlign = TextAlign.Center
                    )

                    // Permission Title
                    Text(
                        text = if (isIntegrityFailed) "INTEGRITY BREACH" else rule.systemLabel.uppercase(),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isIntegrityFailed) Color(0xFFFF1744) else Color(0xFFECEFF1),
                        textAlign = TextAlign.Center,
                        letterSpacing = 0.5.sp
                    )

                    // Explanation Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF23304A), RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0F19))
                    ) {
                        Box(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = if (isIntegrityFailed) {
                                    if (useHindi) {
                                        "सुरक्षा चेतावनी: ऐप का सत्यापन विफल हो गया। छेड़छाड़ हुई है।"
                                    } else {
                                        "SECURITY WARNING: SYSTEM INTEGRITY VERIFICATION FAILED. THE DEVICE OR APP MAY BE COMPROMISED."
                                    }
                                } else {
                                    if (useHindi) rule.explanationHindi else rule.explanationEnglish
                                },
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFECEFF1),
                                textAlign = TextAlign.Center,
                                lineHeight = 22.sp
                            )
                        }
                    }

                    // Recommendation Action
                    val recommendationText = if (isIntegrityFailed || assignedRisk == "HIGH") {
                        if (useHindi) "सलाह: अनुमति ब्लॉक करें (BLOCK)" else "RECOMMENDED ACTION: BLOCK"
                    } else {
                        if (useHindi) "सलाह: अनुमति प्रदान करें (ALLOW)" else "RECOMMENDED ACTION: ALLOW"
                    }
                    Text(
                        text = recommendationText,
                        color = riskColor,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Buttons Layout
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Translation switch button
                        OutlinedButton(
                            onClick = { useHindi = !useHindi },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .border(1.dp, Color(0xFF00B0FF), RoundedCornerShape(22.dp)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color(0xFF00B0FF).copy(alpha = 0.08f),
                                contentColor = Color(0xFF00B0FF)
                            ),
                            shape = RoundedCornerShape(22.dp)
                        ) {
                            Text(text = if (useHindi) "English" else "हिंदी", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        // Close / Action Button
                        Button(
                            onClick = { onDismiss("DISMISSED") },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF23304A)),
                            shape = RoundedCornerShape(22.dp)
                        ) {
                            Text(text = if (useHindi) "बंद करें" else "CLOSE", color = Color(0xFFECEFF1), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    // Quick decisions logs
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { onDismiss("BLOCKED") },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF1744)),
                            shape = RoundedCornerShape(23.dp)
                        ) {
                            Text(text = if (useHindi) "ब्लॉक करें" else "BLOCK ACCESS", color = Color(0xFF0B0F19), fontWeight = FontWeight.Black, fontSize = 12.sp)
                        }
                        Button(
                            onClick = { onDismiss("ALLOWED") },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                            shape = RoundedCornerShape(23.dp)
                        ) {
                            Text(text = if (useHindi) "मंजूरी दें" else "ALLOW ACCESS", color = Color(0xFF0B0F19), fontWeight = FontWeight.Black, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
