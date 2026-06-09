package com.mpowernet.shesafe

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpowernet.shesafe.data.entity.ConsentLog
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
    var consentLogs by remember { mutableStateOf(emptyList<ConsentLog>()) }

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

    // Initialize state
    LaunchedEffect(Unit) {
        checkServiceStatus()
        loadLogs()
        val prefs = context.getSharedPreferences("shesafe_prefs", Context.MODE_PRIVATE)
        isTtsEnabled = prefs.getBoolean("tts_enabled", false)
    }

    // Track return to app to refresh status
    DisposableEffect(Unit) {
        onDispose { }
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

            // Setting Panel Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
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
                        LogItemRow(log)
                    }
                }
            }
        }
    }
}

@Composable
fun LogItemRow(log: ConsentLog) {
    val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
    val dateString = sdf.format(Date(log.timestamp))

    val decisionColor = when (log.decision) {
        "ALLOWED" -> Color(0xFF2E7D32)
        "BLOCKED" -> Color(0xFFC62828)
        else -> Color.Gray
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
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
                    text = dateString,
                    fontSize = 10.sp,
                    color = Color.LightGray
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
