package com.surprisekhoza.sktunnel

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.surprisekhoza.sktunnel.core.ProxyParser
import com.surprisekhoza.sktunnel.core.VpnConnection
import com.surprisekhoza.sktunnel.ui.theme.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val vpnPrepareLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Permission granted – now start the VPN
            startVpn()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SKTunnelTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = NpvDark
                ) {
                    MainScreen(
                        onStartVpn = { prepareVpn() },
                        onStopVpn = { stopVpn() }
                    )
                }
            }
        }
    }

    /**
     * Ask for VPN permission (required by Android before establishing VPN).
     */
    private fun prepareVpn() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            // User hasn't granted permission yet – show system dialog
            vpnPrepareLauncher.launch(intent)
        } else {
            // Already granted – start immediately
            startVpn()
        }
    }

    /**
     * Start the VPN with the current protocol and server input.
     */
    private fun startVpn() {
        // We'll trigger the actual start from the composable via a callback
        // This method is called after permission is OK.
        // We'll store the current state in a companion object or pass it.
        // For simplicity, we'll use a global variable (not ideal but works for now).
        val config = buildConfigFromState()
        if (config != null) {
            VpnConnection.start(this, config)
        }
    }

    private fun stopVpn() {
        VpnConnection.stop(this)
    }

    // Temporary state storage – the composable updates these
    companion object {
        var currentProtocol: String = "vmess"
        var currentServerInput: String = ""
    }

    private fun buildConfigFromState(): String? {
        val proxy = ProxyParser.parse(currentServerInput, currentProtocol) ?: return null
        return VpnConnection.buildConfig(proxy, currentProtocol)
    }
}

@Composable
fun MainScreen(
    onStartVpn: () -> Unit,
    onStopVpn: () -> Unit
) {
    // State
    var protocol by remember { mutableStateOf("vmess") }
    var serverInput by remember { mutableStateOf("") }
    var isConnected by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Keep the Activity's companion object in sync
    MainActivity.currentProtocol = protocol
    MainActivity.currentServerInput = serverInput

    // Check actual VPN state from the service
    isConnected = VpnConnection.isActive()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top App Bar
        Text(
            text = "SK Tunnel",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextWhite,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Protocol Selector Dropdown
        ProtocolDropdown(
            selectedProtocol = protocol,
            onProtocolSelected = { protocol = it }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Server Input
        ServerInputCard(
            input = serverInput,
            onInputChange = { serverInput = it },
            protocolHint = when (protocol) {
                "vmess" -> "e.g. server.com:443@uuid"
                "vless" -> "e.g. server.com:443@uuid"
                "trojan" -> "e.g. server.com:443@password"
                "shadowsocks" -> "e.g. server.com:8388@method:password"
                "socks" -> "e.g. proxy.com:1080@user:pass"
                "http" -> "e.g. proxy.com:8080@user:pass"
                "hysteria" -> "e.g. server.com:443@auth_string"
                "hysteria2" -> "e.g. server.com:443@password"
                "wireguard" -> "e.g. server.com:51820@privatekey"
                "tuic" -> "e.g. server.com:443@uuid:password"
                "ssh" -> "e.g. ssh.server.com:22@user:password"
                "shadowtls" -> "e.g. server.com:443@password"
                else -> "server:port@user"
            }
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Connection Panel (big circle button)
        ConnectionPanel(
            isConnected = isConnected,
            onToggle = {
                if (isConnected) {
                    coroutineScope.launch {
                        onStopVpn()
                        isConnected = false
                    }
                } else {
                    onStartVpn()
                    // The actual connection will be handled after permission;
                    // we can't set isConnected here yet, it'll update from the service.
                }
            }
        )

        Spacer(modifier = Modifier.weight(1f))

        // Status text
        Text(
            text = if (isConnected) "VPN Connected" else "Tap to Connect",
            color = if (isConnected) GreenAccent else TextGray,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 32.dp)
        )
    }
}

@Composable
fun ProtocolDropdown(
    selectedProtocol: String,
    onProtocolSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = NpvSurface,
                contentColor = TextWhite
            ),
            border = ButtonDefaults.outlinedButtonBorder.copy(
                brush = androidx.compose.ui.graphics.SolidColor(GreenAccent)
            )
        ) {
            Text(
                text = "Protocol: $selectedProtocol",
                modifier = Modifier.weight(1f),
                color = TextWhite
            )
            Text("▼", color = GreenAccent)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .background(NpvSurface)
        ) {
            VpnConnection.PROTOCOLS.forEach { protocol ->
                DropdownMenuItem(
                    text = { Text(protocol, color = TextWhite) },
                    onClick = {
                        onProtocolSelected(protocol)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun ServerInputCard(
    input: String,
    onInputChange: (String) -> Unit,
    protocolHint: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = NpvSurface)
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            placeholder = {
                Text(protocolHint, color = TextGray)
            },
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = GreenAccent,
                unfocusedBorderColor = TextGray,
                cursorColor = GreenAccent,
                textColor = TextWhite,
                containerColor = NpvSurface
            ),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
fun ConnectionPanel(
    isConnected: Boolean,
    onToggle: () -> Unit
) {
    val buttonColor = if (isConnected) GreenAccent else DisconnectedGray
    val buttonSize = 140.dp

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = onToggle,
            modifier = Modifier
                .size(buttonSize)
                .clip(CircleShape),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = buttonColor,
                contentColor = TextWhite
            )
        ) {
            // Icon inside the circle – just a simple power symbol or text
            Text(
                text = if (isConnected) "STOP" else "GO",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Live data (dummy for now)
        if (isConnected) {
            Text("↑ 0 B/s   ↓ 0 B/s", color = TextGray, fontSize = 14.sp)
        }
    }
}