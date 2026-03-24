package io.github.sslpinning.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.github.sslpinning.api.SslPinningClient
import io.github.sslpinning.api.SslPinningConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import android.util.Log

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

private sealed class InitState {
    object Loading : InitState()
    data class Error(val error: Throwable) : InitState()
    data class Ready(val client: SslPinningClient) : InitState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RootScreen(config: SslPinningConfig) {
    var initState by remember { mutableStateOf<InitState>(InitState.Loading) }

    LaunchedEffect(config) {
        val httpClient = OkHttpClient()

        val result = SslPinningClient.initialize(
            config = config,
            httpClient = httpClient,
        )

        initState = result.fold(
            onSuccess = { client -> InitState.Ready(client) },
            onFailure = { throwable -> InitState.Error(throwable) },
        )
    }

    when (val state = initState) {
        is InitState.Loading -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("SSL Pinning sample") }
                    )
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .padding(paddingValues)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Initializing SSL pinning…")
                }
            }
        }

        is InitState.Error -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("SSL Pinning sample") }
                    )
                }
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .padding(paddingValues)
                        .padding(16.dp)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Initialization failed",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFFC62828)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = (state.error.message ?: state.error.toString()),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        is InitState.Ready -> {
            SslPinningScreen(sslPinningClient = state.client)
        }
    }
}

private enum class RequestStatus {
    IDLE, SUCCESS, ERROR
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SslPinningScreen(sslPinningClient: SslPinningClient) {
    var sslEnabled by remember { mutableStateOf(false) }

    var googleUrl by remember {
        mutableStateOf(TextFieldValue("https://www.google.com"))
    }

    var googleStatus by remember { mutableStateOf(RequestStatus.IDLE) }

    var lastMessage by remember { mutableStateOf("No requests yet") }

    val scope = rememberCoroutineScope()

    val plainClient = remember(sslPinningClient) { sslPinningClient.createPlainClient() }
    val pinnedClient = remember(sslPinningClient) { sslPinningClient.createPinnedClient() }

    fun colorForStatus(status: RequestStatus): Color =
        when (status) {
            RequestStatus.IDLE -> Color.Gray
            RequestStatus.SUCCESS -> Color(0xFF2E7D32)
            RequestStatus.ERROR -> Color(0xFFC62828)
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SSL Pinning sample") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "SSL pinning: " + if (sslEnabled) "ON" else "OFF",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { sslEnabled = !sslEnabled }
            ) {
                Text("Toggle SSL pinning")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        suspend fun checkUrl(
                            url: String,
                            onResult: (RequestStatus) -> Unit
                        ) {
                            val status = runCatching {
                                val request = Request.Builder()
                                    .url(url)
                                    .build()

                                val client = if (sslEnabled) pinnedClient else plainClient

                                Log.d("SSL::client", "client - ${client}, sslEnabled - $sslEnabled")

                                client.newCall(request).execute().use { response ->
                                    if (response.isSuccessful) {
                                        RequestStatus.SUCCESS
                                    } else {
                                        RequestStatus.ERROR
                                    }
                                }
                            }.getOrElse {
                                RequestStatus.ERROR
                            }

                            withContext(Dispatchers.Main) {
                                onResult(status)
                            }
                        }

                        withContext(Dispatchers.Main) {
                            googleStatus = RequestStatus.IDLE
                            lastMessage = "Running requests with " +
                                    if (sslEnabled) "PINNED" else "PLAIN" +
                                            " client..."
                        }

                        checkUrl(googleUrl.text) { googleStatus = it }

                        withContext(Dispatchers.Main) {
                            lastMessage = "Done"
                        }
                    }
                }
            ) {
                Text("Test HTTPS request")
            }

            Spacer(modifier = Modifier.height(24.dp))

            UrlRow(
                value = googleUrl,
                onValueChange = { googleUrl = it },
                status = googleStatus,
                colorForStatus = ::colorForStatus
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = lastMessage,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun UrlRow(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    status: RequestStatus,
    colorForStatus: (RequestStatus) -> Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            modifier = Modifier.weight(1f),
            value = value,
            onValueChange = onValueChange,
            label = { Text("Your URL") },
            singleLine = true
        )

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .size(16.dp)
                .background(
                    color = colorForStatus(status),
                    shape = MaterialTheme.shapes.small
                )
        )
    }
}