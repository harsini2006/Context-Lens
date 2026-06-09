package com.mpowernet.shesafe

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpowernet.shesafe.data.entity.ConsentLog
import com.mpowernet.shesafe.data.entity.VaultItem
import com.mpowernet.shesafe.security.VaultAuthManager
import com.mpowernet.shesafe.security.ZkcpEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SheSafeTheme {
                MainDashboardScreen(this)
            }
        }
    }
}

// Global cyber-security color palette
val CyberDark = Color(0xFF0B0F19)       // Midnight blue/black background
val CyberCard = Color(0xFF161E30)       // Deep slate-navy card background
val CyberGreen = Color(0xFF00E676)      // Neon mint green for active status
val CyberBlue = Color(0xFF00B0FF)       // Electric cyan for primary actions
val CyberRed = Color(0xFFFF1744)        // Neon coral red for alert/panic
val CyberAmber = Color(0xFFFFB300)      // Amber yellow for warnings
val CyberTextPrimary = Color(0xFFECEFF1)// Soft clean white
val CyberTextSecondary = Color(0xFF90A4AE)// Muted gray-blue

@Composable
fun SheSafeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = CyberBlue,
            secondary = CyberGreen,
            background = CyberDark,
            surface = CyberCard,
            onBackground = CyberTextPrimary,
            onSurface = CyberTextPrimary
        ),
        content = content
    )
}

@Composable
fun MainDashboardScreen(context: Context) {
    val coroutineScope = rememberCoroutineScope()
    val database = (context.applicationContext as SheSafeApplication).database
    
    var isAccessibilityEnabled by remember { mutableStateOf(false) }
    var isTtsEnabled by remember { mutableStateOf(false) }
    var isStealthEnabled by remember { mutableStateOf(false) }
    var selectedSeal by remember { mutableStateOf("🌻") }
    var consentLogs by remember { mutableStateOf(emptyList<ConsentLog>()) }

    // Vault Dialog states
    var showPinSetup by remember { mutableStateOf(false) }
    var showPinPrompt by remember { mutableStateOf(false) }
    var showVaultContent by remember { mutableStateOf(false) }
    var showZkcpProof by remember { mutableStateOf<String?>(null) }
    var isDecoyMode by remember { mutableStateOf(false) }
    var vaultItems by remember { mutableStateOf(emptyList<VaultItem>()) }

    // Check system security and integrity status dynamically
    var isDbValid by remember { mutableStateOf(SheSafeApplication.isDatabaseIntegrityValid) }
    var isPlayValid by remember { mutableStateOf(SheSafeApplication.isPlayIntegrityValid) }
    val isSystemSecure = isDbValid && isPlayValid

    val trustSeals = listOf("🌻", "❤️", "⭐", "🌈", "🍀")

    // Check accessibility service status
    fun checkServiceStatus() {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
        isAccessibilityEnabled = enabledServices.any { 
            it.resolveInfo.serviceInfo.packageName == context.packageName 
        }
    }

    // Load logs from DB
    fun loadLogs() {
        coroutineScope.launch(Dispatchers.IO) {
            val logs = database.consentLogDao().getAllLogs()
            withContext(Dispatchers.Main) {
                consentLogs = logs
            }
        }
    }

    // Load Vault Items from Room (secured by SQLCipher)
    fun loadVaultItems() {
        coroutineScope.launch(Dispatchers.IO) {
            var items = if (isDecoyMode) {
                database.vaultItemDao().getDecoyItems()
            } else {
                database.vaultItemDao().getRealItems()
            }
            if (isDecoyMode && items.isEmpty()) {
                val defaultDecoy = listOf(
                    VaultItem(title = "Safe Contact", content = "+91 9876543210 (Mother)", timestamp = System.currentTimeMillis(), isDecoy = true),
                    VaultItem(title = "Shopping Memo", content = "Buy vegetables and milk.", timestamp = System.currentTimeMillis(), isDecoy = true)
                )
                for (decoyItem in defaultDecoy) {
                    database.vaultItemDao().insertItem(decoyItem)
                }
                items = database.vaultItemDao().getDecoyItems()
            }
            withContext(Dispatchers.Main) {
                vaultItems = items
            }
        }
    }

    // Toggle default/stealth launcher activity aliases
    fun toggleLauncherStealth(stealth: Boolean) {
        val pm = context.packageManager
        val defaultComponent = ComponentName(context, "com.mpowernet.shesafe.MainActivityDefault")
        val stealthComponent = ComponentName(context, "com.mpowernet.shesafe.MainActivityStealth")

        if (stealth) {
            pm.setComponentEnabledSetting(
                defaultComponent,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            pm.setComponentEnabledSetting(
                stealthComponent,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        } else {
            pm.setComponentEnabledSetting(
                stealthComponent,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            pm.setComponentEnabledSetting(
                defaultComponent,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        }
    }

    // Initialize state and start background polling for integrity results
    LaunchedEffect(Unit) {
        checkServiceStatus()
        loadLogs()
        val prefs = context.getSharedPreferences("shesafe_prefs", Context.MODE_PRIVATE)
        isTtsEnabled = prefs.getBoolean("tts_enabled", false)
        selectedSeal = prefs.getString("personal_trust_seal", "🌻") ?: "🌻"
        isStealthEnabled = prefs.getBoolean("stealth_enabled", false)
        
        launch(Dispatchers.Default) {
            while (true) {
                val currentDbValid = SheSafeApplication.isDatabaseIntegrityValid
                val currentPlayValid = SheSafeApplication.isPlayIntegrityValid
                if (isDbValid != currentDbValid || isPlayValid != currentPlayValid) {
                    withContext(Dispatchers.Main) {
                        isDbValid = currentDbValid
                        isPlayValid = currentPlayValid
                    }
                }
                kotlinx.coroutines.delay(250)
            }
        }
    }

    val gradientBrush = Brush.verticalGradient(
        colors = listOf(CyberDark, Color(0xFF13182B))
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBrush)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Premium Brand Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "🛡️ SHESAFE",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = CyberBlue,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = " | ",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = CyberTextSecondary
                )
                Text(
                    text = "CONTEXTLENS",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = CyberTextPrimary,
                    letterSpacing = 1.sp
                )
            }

            // 1. System Integrity Warning Banner (LOUD alerts if tampered)
            if (!isSystemSecure) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CyberRed, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberRed.copy(alpha = 0.12f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(text = "⚠️", fontSize = 28.sp)
                        Column {
                            Text(
                                text = "SYSTEM INTEGRITY BREACHED",
                                fontWeight = FontWeight.Bold,
                                color = CyberRed,
                                fontSize = 14.sp
                            )
                            Text(
                                text = if (!isDbValid) {
                                    "Database verification check failed. Tampering detected."
                                } else {
                                    "App package signature verification failed (Sideload detected)."
                                },
                                color = CyberTextPrimary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // 2. Interceptor Status Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp, 
                        if (isAccessibilityEnabled) CyberGreen else CyberAmber, 
                        RoundedCornerShape(20.dp)
                    ),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CyberCard)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(if (isAccessibilityEnabled) CyberGreen else CyberAmber, RoundedCornerShape(4.dp))
                        )
                        Text(
                            text = if (isAccessibilityEnabled) "INTERCEPTOR PROTECTION ACTIVE" else "INTERCEPTOR INACTIVE",
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isAccessibilityEnabled) CyberGreen else CyberAmber,
                            fontSize = 14.sp,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Text(
                        text = if (isAccessibilityEnabled) {
                            "ContextLens is actively shielding your system permission prompts from tapjacking, overlays, and spoofing attacks."
                        } else {
                            "Enable the accessibility shield to analyze permission requests and protect your system configuration."
                        },
                        fontSize = 12.sp,
                        color = CyberTextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp
                    )

                    Button(
                        onClick = {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isAccessibilityEnabled) CyberGreen.copy(alpha = 0.15f) else CyberAmber,
                            contentColor = if (isAccessibilityEnabled) CyberGreen else CyberDark
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .border(
                                1.dp, 
                                if (isAccessibilityEnabled) CyberGreen else Color.Transparent, 
                                RoundedCornerShape(24.dp)
                            )
                    ) {
                        Text(
                            text = if (isAccessibilityEnabled) "CONFIGURE INTERCEPTOR" else "ACTIVATE INTERCEPTOR SHIELD",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // 3. Settings Control Panel
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF23304A), RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CyberCard)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp), 
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Voice Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "🗣️ Voice Assistant Mode",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = CyberTextPrimary
                            )
                            Text(
                                text = "Reads aloud permission warnings dynamically.",
                                fontSize = 11.sp,
                                color = CyberTextSecondary
                            )
                        }
                        Switch(
                            checked = isTtsEnabled,
                            onCheckedChange = { checked ->
                                isTtsEnabled = checked
                                context.getSharedPreferences("shesafe_prefs", Context.MODE_PRIVATE)
                                    .edit()
                                    .putBoolean("tts_enabled", checked)
                                    .apply()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = CyberDark,
                                checkedTrackColor = CyberBlue,
                                uncheckedThumbColor = CyberTextSecondary,
                                uncheckedTrackColor = Color(0xFF23304A)
                            )
                        )
                    }

                    HorizontalDivider(color = Color(0xFF23304A), thickness = 0.8.dp)

                    // Stealth Icon Cloaking Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "🔍 Stealth Icon Cloaking",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = CyberTextPrimary
                            )
                            Text(
                                text = "Masks SheSafe behind a secondary calculator icon.",
                                fontSize = 11.sp,
                                color = CyberTextSecondary
                            )
                        }
                        Switch(
                            checked = isStealthEnabled,
                            onCheckedChange = { checked ->
                                isStealthEnabled = checked
                                context.getSharedPreferences("shesafe_prefs", Context.MODE_PRIVATE)
                                    .edit()
                                    .putBoolean("stealth_enabled", checked)
                                    .apply()
                                toggleLauncherStealth(checked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = CyberDark,
                                checkedTrackColor = CyberBlue,
                                uncheckedThumbColor = CyberTextSecondary,
                                uncheckedTrackColor = Color(0xFF23304A)
                            )
                        )
                    }
                }
            }

            // 4. Secure Action Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Private Vault Button
                Button(
                    onClick = {
                        if (VaultAuthManager.isPinSetup(context)) {
                            showPinPrompt = true
                        } else {
                            showPinSetup = true
                        }
                    },
                    modifier = Modifier
                        .weight(1.2f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberBlue),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("🔑 SECURE VAULT", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                // Panic Button
                OutlinedButton(
                    onClick = {
                        VaultAuthManager.executePanicWipe(context)
                        (context as? ComponentActivity)?.finishAffinity()
                        System.exit(0)
                    },
                    modifier = Modifier
                        .weight(0.8f)
                        .height(48.dp)
                        .border(1.2.dp, CyberRed, RoundedCornerShape(24.dp)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = CyberRed.copy(alpha = 0.08f),
                        contentColor = CyberRed
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("🚨 PANIC WIPE", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }

            // 5. Personal Trust Seal Selector
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF23304A), RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CyberCard)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column {
                        Text(
                            text = "🌻 Anti-Spoof Personal Trust Seal",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = CyberTextPrimary
                        )
                        Text(
                            text = "Genuine warning prompts display your chosen seal below.",
                            fontSize = 11.sp,
                            color = CyberTextSecondary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        trustSeals.forEach { seal ->
                            val isSelected = seal == selectedSeal
                            val borderCol = if (isSelected) CyberBlue else Color.Transparent
                            val backCol = if (isSelected) CyberBlue.copy(alpha = 0.15f) else Color(0xFF1F2B45)
                            
                            OutlinedButton(
                                onClick = {
                                    selectedSeal = seal
                                    context.getSharedPreferences("shesafe_prefs", Context.MODE_PRIVATE)
                                        .edit()
                                        .putString("personal_trust_seal", seal)
                                        .apply()
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = backCol),
                                modifier = Modifier
                                    .size(48.dp)
                                    .border(1.5.dp, borderCol, RoundedCornerShape(12.dp)),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(text = seal, fontSize = 20.sp)
                            }
                        }
                    }
                }
            }

            // 6. Local Safety History Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Local Audit History",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = CyberTextPrimary,
                    letterSpacing = 0.5.sp
                )
                if (consentLogs.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                database.consentLogDao().clearLogs()
                                loadLogs()
                            }
                        }
                    ) {
                        Text("Purge Logs", color = CyberRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            // 7. Consent Logs List
            if (consentLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(CyberCard, RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFF23304A), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No permission intercept logs recorded.",
                        color = CyberTextSecondary,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(consentLogs) { log ->
                        LogItemRow(log, onClick = {
                            coroutineScope.launch(Dispatchers.Default) {
                                val zkcpReceipt = ZkcpEngine.generateZkcpProof(log)
                                withContext(Dispatchers.Main) {
                                    showZkcpProof = zkcpReceipt
                                }
                            }
                        })
                    }
                }
            }
        }

        // --- SECTION MODALS (PIN setups, Vault, and proofs) ---
        if (showPinSetup) {
            PinSetupDialog(
                onConfirm = { real, duress ->
                    VaultAuthManager.setupPins(context, real, duress)
                    showPinSetup = false
                    showPinPrompt = true
                },
                onDismiss = { showPinSetup = false }
            )
        }

        if (showPinPrompt) {
            PinPromptDialog(
                onConfirm = { pin ->
                    showPinPrompt = false
                    val result = VaultAuthManager.authenticate(context, pin)
                    when (result) {
                        VaultAuthManager.AuthResult.REAL -> {
                            isDecoyMode = false
                            loadVaultItems()
                            showVaultContent = true
                        }
                        VaultAuthManager.AuthResult.DURESS -> {
                            isDecoyMode = true
                            loadVaultItems()
                            showVaultContent = true
                        }
                        VaultAuthManager.AuthResult.FAILED -> {
                            // PIN mismatch
                        }
                    }
                },
                onDismiss = { showPinPrompt = false }
            )
        }

        if (showVaultContent) {
            VaultContentDialog(
                items = vaultItems,
                isDecoy = isDecoyMode,
                onAddItem = { title, content ->
                    coroutineScope.launch(Dispatchers.IO) {
                        database.vaultItemDao().insertItem(
                            VaultItem(
                                title = title,
                                content = content,
                                timestamp = System.currentTimeMillis(),
                                isDecoy = isDecoyMode
                            )
                        )
                        loadVaultItems()
                    }
                },
                onDismiss = { showVaultContent = false }
            )
        }

        if (showZkcpProof != null) {
            ZkcpProofDialog(
                zkcpJson = showZkcpProof!!,
                onDismiss = { showZkcpProof = null }
            )
        }
    }
}

@Composable
fun LogItemRow(log: ConsentLog, onClick: () -> Unit) {
    val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
    val dateString = sdf.format(Date(log.timestamp))
    val isDecisionAllowed = log.decision == "ALLOWED"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .border(0.8.dp, Color(0xFF23304A), RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CyberCard)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = log.appPackage.substringAfterLast(".").uppercase(Locale.ROOT),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = CyberBlue
                )
                Text(
                    text = log.permissionRequested.substringAfterLast("."),
                    fontSize = 12.sp,
                    color = CyberTextPrimary
                )
                Text(
                    text = "$dateString • Verify Proof",
                    fontSize = 10.sp,
                    color = CyberTextSecondary
                )
            }

            Box(
                modifier = Modifier
                    .background(
                        if (isDecisionAllowed) CyberGreen.copy(alpha = 0.12f) else CyberRed.copy(alpha = 0.12f),
                        RoundedCornerShape(8.dp)
                    )
                    .border(
                        1.dp,
                        if (isDecisionAllowed) CyberGreen.copy(alpha = 0.5f) else CyberRed.copy(alpha = 0.5f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = log.decision,
                    color = if (isDecisionAllowed) CyberGreen else CyberRed,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@Composable
fun PinSetupDialog(
    onConfirm: (real: String, duress: String) -> Unit,
    onDismiss: () -> Unit
) {
    var realPin by remember { mutableStateOf("") }
    var duressPin by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                "Configure Vault Access", 
                fontWeight = FontWeight.Bold,
                color = CyberBlue,
                fontSize = 18.sp
            ) 
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Set a 4-digit master PIN for legitimate access to private history and items:", 
                    fontSize = 12.sp, 
                    color = CyberTextPrimary
                )
                OutlinedTextField(
                    value = realPin,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) realPin = it },
                    label = { Text("Master PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberBlue,
                        unfocusedBorderColor = Color(0xFF23304A),
                        focusedLabelColor = CyberBlue
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Set a secondary Duress PIN to trigger decoy mode during forced inspections:", 
                    fontSize = 12.sp, 
                    color = CyberTextPrimary
                )
                OutlinedTextField(
                    value = duressPin,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) duressPin = it },
                    label = { Text("Duress Decoy PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberBlue,
                        unfocusedBorderColor = Color(0xFF23304A),
                        focusedLabelColor = CyberBlue
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (realPin.length == 4 && duressPin.length == 4 && realPin != duressPin) onConfirm(realPin, duressPin) },
                colors = ButtonDefaults.buttonColors(containerColor = CyberBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("SAVE SECURE CONFIG", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = CyberDark)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { 
                Text("CANCEL", color = CyberTextSecondary) 
            }
        },
        containerColor = CyberCard,
        modifier = Modifier.border(1.dp, Color(0xFF23304A), RoundedCornerShape(28.dp))
    )
}

@Composable
fun PinPromptDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var enteredPin by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                "Secure Authenticator", 
                fontWeight = FontWeight.Bold,
                color = CyberBlue,
                fontSize = 18.sp
            ) 
        },
        text = {
            OutlinedTextField(
                value = enteredPin,
                onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) enteredPin = it },
                label = { Text("Enter 4-Digit PIN") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                visualTransformation = PasswordVisualTransformation(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyberBlue,
                    unfocusedBorderColor = Color(0xFF23304A),
                    focusedLabelColor = CyberBlue
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { if (enteredPin.length == 4) onConfirm(enteredPin) },
                colors = ButtonDefaults.buttonColors(containerColor = CyberBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("UNLOCK VAULT", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = CyberDark)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { 
                Text("CANCEL", color = CyberTextSecondary) 
            }
        },
        containerColor = CyberCard,
        modifier = Modifier.border(1.dp, Color(0xFF23304A), RoundedCornerShape(28.dp))
    )
}

@Composable
fun ZkcpProofDialog(
    zkcpJson: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                "Zero-Knowledge Proof Receipt", 
                fontWeight = FontWeight.Bold,
                color = CyberBlue,
                fontSize = 18.sp
            ) 
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "DPDP Act 2025 compliant cryptographic consent receipt (locally signed verification token):", 
                    fontSize = 12.sp, 
                    color = CyberTextSecondary
                )
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .border(1.dp, Color(0xFF23304A), RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberDark)
                ) {
                    Box(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = zkcpJson,
                            fontSize = 10.sp,
                            color = CyberGreen,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss, 
                colors = ButtonDefaults.buttonColors(containerColor = CyberBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("OK", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = CyberDark)
            }
        },
        containerColor = CyberCard,
        modifier = Modifier.border(1.dp, Color(0xFF23304A), RoundedCornerShape(28.dp))
    )
}

@Composable
fun VaultContentDialog(
    items: List<VaultItem>,
    isDecoy: Boolean,
    onAddItem: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(
                modifier = Modifier.fillMaxWidth(), 
                horizontalArrangement = Arrangement.SpaceBetween, 
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isDecoy) "Decoy Safe Sandbox" else "Authentic Private Vault",
                    color = if (isDecoy) CyberAmber else CyberGreen,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp
                )
                if (!isDecoy) {
                    IconButton(onClick = { showAddDialog = true }) {
                        Text("+", fontSize = 28.sp, color = CyberBlue, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp), 
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (items.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No records locked in vault.", color = CyberTextSecondary)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(items) { item ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(0.8.dp, Color(0xFF23304A), RoundedCornerShape(12.dp)),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = CyberDark)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = item.title, 
                                        fontWeight = FontWeight.Bold, 
                                        fontSize = 14.sp,
                                        color = CyberBlue
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = item.content, 
                                        fontSize = 12.sp, 
                                        color = CyberTextPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss, 
                colors = ButtonDefaults.buttonColors(containerColor = CyberBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("CLOSE", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = CyberDark)
            }
        },
        containerColor = CyberCard,
        modifier = Modifier.border(1.dp, Color(0xFF23304A), RoundedCornerShape(28.dp))
    )

    if (showAddDialog) {
        AddVaultItemDialog(
            onAdd = { title, content ->
                onAddItem(title, content)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }
}

@Composable
fun AddVaultItemDialog(
    onAdd: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                "Secure New Entry", 
                fontWeight = FontWeight.Bold,
                color = CyberBlue,
                fontSize = 18.sp
            ) 
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title, 
                    onValueChange = { title = it }, 
                    label = { Text("Title / Header") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberBlue,
                        unfocusedBorderColor = Color(0xFF23304A),
                        focusedLabelColor = CyberBlue
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = content, 
                    onValueChange = { content = it }, 
                    label = { Text("Secret Details / Contacts") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberBlue,
                        unfocusedBorderColor = Color(0xFF23304A),
                        focusedLabelColor = CyberBlue
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (title.isNotEmpty() && content.isNotEmpty()) onAdd(title, content) },
                colors = ButtonDefaults.buttonColors(containerColor = CyberBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("LOCK FILE", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = CyberDark)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { 
                Text("CANCEL", color = CyberTextSecondary) 
            }
        },
        containerColor = CyberCard,
        modifier = Modifier.border(1.dp, Color(0xFF23304A), RoundedCornerShape(28.dp))
    )
}
