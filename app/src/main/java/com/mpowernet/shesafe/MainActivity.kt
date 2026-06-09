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

@Composable
fun SheSafeTheme(content: @compose:Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF00796B),
            secondary = Color(0xFF00ACC1),
            background = Color(0xFFF4F9F9)
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

    // Check system security and integrity status
    val isDbValid = SheSafeApplication.isDatabaseIntegrityValid
    val isPlayValid = SheSafeApplication.isPlayIntegrityValid
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

    // Initialize state
    LaunchedEffect(Unit) {
        checkServiceStatus()
        loadLogs()
        val prefs = context.getSharedPreferences("shesafe_prefs", Context.MODE_PRIVATE)
        isTtsEnabled = prefs.getBoolean("tts_enabled", false)
        selectedSeal = prefs.getString("personal_trust_seal", "🌻") ?: "🌻"
        isStealthEnabled = prefs.getBoolean("stealth_enabled", false)
    }

    val gradientBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFE0F7FA), // Soft Light Blue
            Color(0xFFF4F9F9)
        )
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
            // Header
            Text(
                text = "SheSafe ContextLens",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF00796B),
                modifier = Modifier.padding(top = 16.dp)
            )

            Text(
                text = "Safe digital onboarding companion",
                fontSize = 14.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )

            // 1. System Integrity Warning Banner (LOUD alerts if tampered)
            if (!isSystemSecure) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "⚠ SYSTEM SECURITY INTEGRITY BREACH!",
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = if (!isDbValid) {
                                "Database file signature check failed. Integrity compromised."
                            } else {
                                "Application package verification failed (Sideload detected)."
                            },
                            color = Color.White,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Service Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isAccessibilityEnabled) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (isAccessibilityEnabled) "✓ SHESAFE INTERCEPTOR ACTIVE" else "⚠ SHESAFE INTERCEPTOR INACTIVE",
                        fontWeight = FontWeight.Bold,
                        color = if (isAccessibilityEnabled) Color(0xFF2E7D32) else Color(0xFFC62828),
                        fontSize = 15.sp
                    )

                    Text(
                        text = if (isAccessibilityEnabled) {
                            "We are protecting your digital permissions in real-time."
                        } else {
                            "Please activate ContextLens to protect your permissions."
                        },
                        fontSize = 13.sp,
                        color = Color.DarkGray,
                        textAlign = TextAlign.Center
                    )

                    Button(
                        onClick = {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isAccessibilityEnabled) Color(0xFF2E7D32) else Color(0xFFC62828)
                        )
                    ) {
                        Text(
                            text = if (isAccessibilityEnabled) "MANAGE SETTINGS" else "ACTIVATE INTERCEPTOR",
                            color = Color.White
                        )
                    }
                }
            }

            // Setting Panel Card (Voice toggle & Stealth Mode)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Voice Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Voice Assistant Mode",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Read permission safety card aloud",
                                fontSize = 12.sp,
                                color = Color.Gray
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
                                checkedThumbColor = Color(0xFF00796B),
                                checkedTrackColor = Color(0xFFB2DFDB)
                            )
                        )
                    }

                    Divider()

                    // Stealth Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Stealth Icon Cloaking",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Swaps SheSafe icon for a Calculator",
                                fontSize = 12.sp,
                                color = Color.Gray
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
                                checkedThumbColor = Color(0xFF00796B),
                                checkedTrackColor = Color(0xFFB2DFDB)
                            )
                        )
                    }
                }
            }

            // Private Vault & Panic Trigger
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Open Vault
                Button(
                    onClick = {
                        if (VaultAuthManager.isPinSetup(context)) {
                            showPinPrompt = true
                        } else {
                            showPinSetup = true
                        }
                    },
                    modifier = Modifier.weight(1.2f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00796B))
                ) {
                    Text("PRIVATE VAULT")
                }

                // Panic button
                Button(
                    onClick = {
                        VaultAuthManager.executePanicWipe(context)
                        // Terminate process immediately after wipe
                        (context as? ComponentActivity)?.finishAffinity()
                        System.exit(0)
                    },
                    modifier = Modifier.weight(0.8f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Text("PANIC WIPE")
                }
            }

            // Personal Trust Seal Selector Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Personal Trust Seal",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Select a secret symbol. Genuine cards will show this.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        trustSeals.forEach { seal ->
                            val isSelected = seal == selectedSeal
                            val borderCol = if (isSelected) Color(0xFF00796B) else Color.Transparent
                            val backCol = if (isSelected) Color(0xFFE0F7FA) else Color.Transparent
                            
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
                                    .size(54.dp)
                                    .border(2.dp, borderCol, RoundedCornerShape(12.dp)),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(text = seal, fontSize = 24.sp)
                            }
                        }
                    }
                }
            }

            // Consent Logs Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Local Safety History",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.DarkGray
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
                        Text("Clear History", color = Color(0xFFC62828))
                    }
                }
            }

            // Consent Logs List
            if (consentLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No recorded permission alerts yet.",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(consentLogs) { log ->
                        LogItemRow(log, onClick = {
                            // Generate Zero-Knowledge Consent Proof (ZKCP) on click
                            val zkcpReceipt = ZkcpEngine.generateZkcpProof(log)
                            showZkcpProof = zkcpReceipt
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
                            // Authentication failed warning
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

    val decisionColor = when (log.decision) {
        "ALLOWED" -> Color(0xFF2E7D32)
        "BLOCKED" -> Color(0xFFC62828)
        else -> Color.Gray
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
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
                    text = log.appPackage.substringAfterLast("."),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = log.permissionRequested.substringAfterLast("."),
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Text(
                    text = "$dateString • Click to view ZKCP Proof",
                    fontSize = 10.sp,
                    color = Color.Gray
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Box(
                    modifier = Modifier
                        .background(decisionColor.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = log.decision,
                        color = decisionColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

// Dialog Composable Helpers
@Composable
fun PinSetupDialog(
    onConfirm: (real: String, duress: String) -> Unit,
    onDismiss: () -> Unit
) {
    var realPin by remember { mutableStateOf("") }
    var duressPin by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configure Vault PINs") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Set a 4-digit PIN to access private history.", fontSize = 13.sp)
                OutlinedTextField(
                    value = realPin,
                    onValueChange = { if (it.length <= 4) realPin = it },
                    label = { Text("Main PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = PasswordVisualTransformation()
                )
                Text("Set a different PIN to trigger decoy mode under duress.", fontSize = 13.sp)
                OutlinedTextField(
                    value = duressPin,
                    onValueChange = { if (it.length <= 4) duressPin = it },
                    label = { Text("Duress PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = PasswordVisualTransformation()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (realPin.length == 4 && duressPin.length == 4 && realPin != duressPin) onConfirm(realPin, duressPin) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00796B))
            ) {
                Text("SAVE PINS")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL") }
        }
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
        title = { Text("Enter Vault PIN") },
        text = {
            OutlinedTextField(
                value = enteredPin,
                onValueChange = { if (it.length <= 4) enteredPin = it },
                label = { Text("4-Digit PIN") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                visualTransformation = PasswordVisualTransformation()
            )
        },
        confirmButton = {
            Button(
                onClick = { if (enteredPin.length == 4) onConfirm(enteredPin) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00796B))
            ) {
                Text("UNLOCK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL") }
        }
    )
}

@Composable
fun ZkcpProofDialog(
    zkcpJson: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Zero-Knowledge Consent Proof") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("DPDP Act 2025 compliant local verifiable consent receipt:", fontSize = 12.sp, color = Color.Gray)
                Card(
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFECEFF1))
                ) {
                    Box(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = zkcpJson,
                            fontSize = 10.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00796B))) {
                Text("OK")
            }
        }
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
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (isDecoy) "Decoy Private Vault" else "Authentic Private Vault")
                if (!isDecoy) {
                    IconButton(onClick = { showAddDialog = true }) {
                        Text("+", fontSize = 24.sp, color = Color(0xFF00796B))
                    }
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().height(260.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (items.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No private records saved.", color = Color.Gray)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(items) { item ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF4F9F9))
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(item.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(item.content, fontSize = 12.sp, color = Color.DarkGray)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00796B))) {
                Text("CLOSE")
            }
        }
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
        title = { Text("Secure New Vault Item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") })
                OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text("Secret Detail / Contacts") })
            }
        },
        confirmButton = {
            Button(
                onClick = { if (title.isNotEmpty() && content.isNotEmpty()) onAdd(title, content) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00796B))
            ) {
                Text("ADD")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL") }
        }
    )
}

// Add scrolling import/modifier support
@Composable
fun rememberScrollState() = androidx.compose.foundation.rememberScrollState()
@Composable
fun Modifier.verticalScroll(state: androidx.compose.foundation.ScrollState) = androidx.compose.foundation.verticalScroll(state)
