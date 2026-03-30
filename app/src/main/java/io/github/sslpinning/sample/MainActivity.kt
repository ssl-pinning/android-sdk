package io.github.sslpinning.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.sslpinning.api.SslPinningClient
import io.github.sslpinning.api.SslPinningConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = SslPinningConfig(
            endpointUrl = BuildConfig.SSL_PINNING_ENDPOINT,
            signingKeyBase64 = BuildConfig.SSL_PINNING_SIGNING_KEY_B64,
        )
        setContent {
            RootScreen(config = config)
        }
    }
}

// MARK: - State

private sealed class InitState {
    object Loading : InitState()
    data class Error(val error: Throwable) : InitState()
    data class Ready(val client: SslPinningClient) : InitState()
}

private sealed class RequestStatus {
    object Idle : RequestStatus()
    object InFlight : RequestStatus()
    data class Success(val message: String) : RequestStatus()
    data class Failure(val message: String) : RequestStatus()
}

private data class RequestLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Date = Date(),
    val usePinned: Boolean,
    val url: String,
    val statusCode: Int?,
    val headers: List<Pair<String, String>>,
    val error: String?,
) {
    val sessionLabel: String get() = if (usePinned) "Pinned" else "Plain"
}

// MARK: - RootScreen

@Composable
private fun RootScreen(config: SslPinningConfig) {
    var initState by remember { mutableStateOf<InitState>(InitState.Loading) }

    LaunchedEffect(config) {
        val result = SslPinningClient.initialize(
            config = config,
            httpClient = OkHttpClient(),
        )
        initState = result.fold(
            onSuccess = { InitState.Ready(it) },
            onFailure = { InitState.Error(it) },
        )
    }

    when (val state = initState) {
        is InitState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = "Initializing SSL Pinning…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        is InitState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color(0xFFC62828),
                        modifier = Modifier.size(48.dp)
                    )
                    Text("Initialization Failed", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = state.error.message ?: state.error.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        is InitState.Ready -> SslPinningScreen(sslPinningClient = state.client)
    }
}

// MARK: - SslPinningScreen

@Composable
private fun SslPinningScreen(sslPinningClient: SslPinningClient) {
    var usePinned by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf("https://www.google.com") }
    var requestStatus by remember { mutableStateOf<RequestStatus>(RequestStatus.Idle) }
    var log by remember { mutableStateOf(listOf<RequestLogEntry>()) }
    val scope = rememberCoroutineScope()

    val plainClient = remember(sslPinningClient) { sslPinningClient.createPlainClient() }
    val pinnedClient = remember(sslPinningClient) { sslPinningClient.createPinnedClient() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "SSL Pinning",
            style = MaterialTheme.typography.headlineLarge
        )

        // Toggle pinning mode
        Button(
            onClick = { usePinned = !usePinned },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (usePinned) Color(0xFF2E7D32) else Color.Gray
            )
        ) {
            Icon(
                imageVector = if (usePinned) Icons.Default.Lock else Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(if (usePinned) "Pinned Session" else "Plain Session")
        }

        // URL input
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
            Text(
                text = "URL",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                placeholder = { Text("https://…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Send Request button
        Button(
            onClick = {
                scope.launch {
                    val pinned = usePinned
                    val client = if (pinned) pinnedClient else plainClient
                    requestStatus = RequestStatus.InFlight
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            val request = Request.Builder().url(urlInput).build()
                            client.newCall(request).execute().use { response ->
                                val statusCode = response.code
                                val headers = response.headers
                                    .map { (k, v) -> k to v }
                                    .sortedBy { it.first }
                                Triple(statusCode, headers, null as String?)
                            }
                        }.getOrElse { e ->
                            Triple(null, emptyList(), e.message ?: e.toString())
                        }
                    }
                    val (statusCode, headers, error) = result
                    log = log + RequestLogEntry(
                        usePinned = pinned,
                        url = urlInput,
                        statusCode = statusCode,
                        headers = headers,
                        error = error,
                    )
                    requestStatus = if (error == null) {
                        RequestStatus.Success("HTTP ${statusCode ?: "?"} — ${if (pinned) "Pinned" else "Plain"}")
                    } else {
                        RequestStatus.Failure(error)
                    }
                }
            },
            enabled = requestStatus !is RequestStatus.InFlight
        ) {
            Text("Send Request")
        }

        // Status indicator
        Box(
            modifier = Modifier
                .defaultMinSize(minHeight = 24.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            when (val s = requestStatus) {
                is RequestStatus.Idle -> Canvas(modifier = Modifier.size(16.dp)) {
                    drawCircle(color = Color.Gray.copy(alpha = 0.4f))
                }
                is RequestStatus.InFlight -> CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
                is RequestStatus.Success -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Canvas(modifier = Modifier.size(12.dp)) {
                        drawCircle(color = Color(0xFF2E7D32))
                    }
                    Text(
                        text = s.message,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is RequestStatus.Failure -> Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Canvas(modifier = Modifier.size(12.dp)) {
                        drawCircle(color = Color(0xFFC62828))
                    }
                    Text(
                        text = s.message.split("\n")[0],
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFC62828),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))

        if (log.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No requests yet",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(log.reversed(), key = { it.id }) { entry ->
                    RequestLogRow(entry = entry)
                }
            }
        }
    }
}

// MARK: - RequestLogRow


@Composable
private fun RequestLogRow(entry: RequestLogEntry) {
    var expanded by remember { mutableStateOf(false) }
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Canvas(modifier = Modifier.size(8.dp)) {
                drawCircle(color = if (entry.error == null) Color(0xFF2E7D32) else Color(0xFFC62828))
            }
            Text(
                text = timeFormatter.format(entry.timestamp),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                imageVector = if (entry.usePinned) Icons.Default.Lock else Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = if (entry.usePinned) Color(0xFF2E7D32) else Color.Gray
            )
            Text(
                text = entry.sessionLabel,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = if (entry.usePinned) Color(0xFF2E7D32) else Color.Gray
            )
            if (entry.statusCode != null) {
                Text(
                    text = "HTTP ${entry.statusCode}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace
                )
            } else if (entry.error != null) {
                Text(
                    text = "ERROR",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFC62828)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                DebugRow("url", entry.url)
                DebugRow("session", entry.sessionLabel)
                if (entry.error != null) {
                    DebugRow("error", entry.error, valueColor = Color(0xFFC62828))
                } else {
                    entry.headers.forEach { (key, value) ->
                        DebugRow(key, value)
                    }
                }
            }
        }
    }
}

@Composable
private fun DebugRow(key: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "$key:",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = valueColor,
            maxLines = 4
        )
    }
}
