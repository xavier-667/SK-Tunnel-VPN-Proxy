package com.surprisekhoza.sktunnel.core

import android.content.Context
import android.content.Intent
import com.surprisekhoza.sktunnel.VpnService
import moe.matsuri.lite.Libcore // sing-box AAR
import org.json.JSONArray
import org.json.JSONObject

object VpnConnection {

    // All supported sing-box outbound protocols
    val PROTOCOLS = listOf(
        "direct",        // no encryption – just bypass
        "block",         // block all traffic (useful for testing)
        "socks",         // SOCKS5 proxy
        "http",          // HTTP proxy
        "shadowsocks",   // Shadowsocks
        "vmess",         // VMess
        "vless",         // VLESS
        "trojan",        // Trojan
        "hysteria",      // Hysteria
        "hysteria2",     // Hysteria2
        "wireguard",     // WireGuard
        "tuic",          // TUIC
        "ssh",           // SSH (via sing-box)
        "shadowtls"      // ShadowTLS
    )

    private var isRunning = false

    /**
     * Build a full sing‑box JSON config from the proxy details.
     *
     * @param proxy    Parsed ProxyConfig (host, port, user)
     * @param protocol The chosen sing‑box outbound type
     * @return Sing‑box config as a JSON string
     */
    fun buildConfig(proxy: ProxyConfig, protocol: String): String {
        val outbound = JSONObject()
        outbound.put("tag", "proxy")
        outbound.put("type", protocol)

        // The user string from ip:port@user – for complex protocols, we expect
        // multiple colon-separated values. See each protocol for the exact format.
        val userFields = proxy.user?.split(":") ?: emptyList()

        when (protocol) {
            "direct" -> {
                // No extra config needed; traffic goes out directly
            }
            "block" -> {
                // Drops all packets; no server needed, ignore host/port
            }
            "socks" -> {
                outbound.put("server", proxy.host)
                outbound.put("server_port", proxy.port)
                if (proxy.user != null) {
                    // Format: username:password
                    outbound.put("username", userFields.getOrElse(0) { "" })
                    outbound.put("password", userFields.getOrElse(1) { "" })
                }
            }
            "http" -> {
                outbound.put("server", proxy.host)
                outbound.put("server_port", proxy.port)
                if (proxy.user != null) {
                    outbound.put("username", userFields.getOrElse(0) { "" })
                    outbound.put("password", userFields.getOrElse(1) { "" })
                }
            }
            "shadowsocks" -> {
                outbound.put("server", proxy.host)
                outbound.put("server_port", proxy.port)
                // Format: method:password  (e.g. aes-256-gcm:mysecret)
                outbound.put("method", userFields.getOrElse(0) { "aes-256-gcm" })
                outbound.put("password", userFields.getOrElse(1) { "password" })
            }
            "vmess" -> {
                outbound.put("server", proxy.host)
                outbound.put("server_port", proxy.port)
                // Format: uuid (user ID from VMess)
                outbound.put("uuid", proxy.user ?: "00000000-0000-0000-0000-000000000000")
                outbound.put("security", "auto")
                outbound.put("alter_id", 0)
            }
            "vless" -> {
                outbound.put("server", proxy.host)
                outbound.put("port", proxy.port)
                // Format: uuid
                outbound.put("uuid", proxy.user ?: "00000000-0000-0000-0000-000000000000")
                outbound.put("flow", "xtls-rprx-vision")
                // VLESS almost always uses TLS; we enable it with server_name = host
                outbound.put("tls", JSONObject().apply {
                    put("enabled", true)
                    put("server_name", proxy.host)
                })
            }
            "trojan" -> {
                outbound.put("server", proxy.host)
                outbound.put("server_port", proxy.port)
                // Format: password
                outbound.put("password", proxy.user ?: "password")
                outbound.put("tls", JSONObject().apply {
                    put("enabled", true)
                    put("server_name", proxy.host)
                })
            }
            "hysteria" -> {
                outbound.put("server", proxy.host)
                outbound.put("server_port", proxy.port)
                // Format: auth_str (base64 authentication string)
                outbound.put("auth_str", proxy.user ?: "")
                // Hysteria always uses QUIC/TLS
                outbound.put("tls", JSONObject().apply {
                    put("enabled", true)
                    put("server_name", proxy.host)
                    put("insecure", true)  // often needed for self-signed certs
                })
                // Default up/down speeds (adjust as needed)
                outbound.put("up_mbps", 100)
                outbound.put("down_mbps", 100)
            }
            "hysteria2" -> {
                outbound.put("server", proxy.host)
                outbound.put("server_port", proxy.port)
                // Format: password
                outbound.put("password", proxy.user ?: "password")
                outbound.put("tls", JSONObject().apply {
                    put("enabled", true)
                    put("server_name", proxy.host)
                    put("insecure", true)
                })
                outbound.put("up_mbps", 100)
                outbound.put("down_mbps", 100)
            }
            "wireguard" -> {
                outbound.put("server", proxy.host)
                outbound.put("server_port", proxy.port)
                // WireGuard needs a lot of fields; we expect the user to supply
                // the private key in the user string: private_key
                outbound.put("private_key", proxy.user ?: "")
                // For simplicity, we'll set a single peer with allowed IPs 0.0.0.0/0
                // In practice, you'd need a full config, but sing-box can handle it.
                val peer = JSONObject()
                peer.put("endpoint", "${proxy.host}:${proxy.port}")
                peer.put("public_key", "")  // you'd need to provide this – we leave blank
                peer.put("pre_shared_key", "")
                peer.put("allowed_ips", JSONArray().put("0.0.0.0/0"))
                outbound.put("peers", JSONArray().put(peer))
            }
            "tuic" -> {
                outbound.put("server", proxy.host)
                outbound.put("server_port", proxy.port)
                // Format: uuid:password
                outbound.put("uuid", userFields.getOrElse(0) { "00000000-0000-0000-0000-000000000000" })
                outbound.put("password", userFields.getOrElse(1) { "password" })
                outbound.put("congestion_control", "bbr")
                outbound.put("tls", JSONObject().apply {
                    put("enabled", true)
                    put("server_name", proxy.host)
                    put("insecure", true)
                })
            }
            "ssh" -> {
                outbound.put("server", proxy.host)
                outbound.put("server_port", proxy.port)
                // Format: user:password (or user:private_key_path)
                outbound.put("user", userFields.getOrElse(0) { "root" })
                outbound.put("password", userFields.getOrElse(1) { "" })
                // SSH often needs host key verification disabled for simplicity
                outbound.put("host_key_algorithms", JSONArray().put("ssh-rsa"))
                outbound.put("host_key", JSONArray().put(""))
            }
            "shadowtls" -> {
                // ShadowTLS usually wraps another outbound; here we'll set it as a direct
                // outbound to the server with TLS fingerprint.
                outbound.put("server", proxy.host)
                outbound.put("server_port", proxy.port)
                // Format: password
                outbound.put("password", proxy.user ?: "password")
                outbound.put("version", 2)  // ShadowTLS v2
                outbound.put("tls", JSONObject().apply {
                    put("enabled", true)
                    put("server_name", proxy.host)
                    put("insecure", true)
                })
            }
        }

        // Build the full sing-box configuration
        val config = JSONObject()
        config.put("log", JSONObject().apply { put("level", "warn") })

        // TUN inbound – no root required
        val tunInbound = JSONObject().apply {
            put("type", "tun")
            put("tag", "tun")
            put("address", "10.0.0.2/30")    // VPN client IP
            put("mtu", 1500)
            put("auto_route", true)           // redirect all traffic
            put("strict_route", true)
        }
        config.put("inbounds", JSONArray().put(tunInbound))

        // Outbounds array – add the main proxy and a direct outbound for fallback
        val outboundsArray = JSONArray()
        outboundsArray.put(outbound)
        // Add a direct outbound as fallback (optional)
        outboundsArray.put(JSONObject().apply {
            put("type", "direct")
            put("tag", "direct")
        })
        config.put("outbounds", outboundsArray)

        // Simple routing rule: everything goes through the proxy
        val rule = JSONObject().apply {
            put("inbound", JSONArray().put("tun"))
            put("outbound", "proxy")
        }
        config.put("route", JSONObject().apply {
            put("rules", JSONArray().put(rule))
        })

        return config.toString()
    }

    fun start(context: Context, configJson: String) {
        if (isRunning) return
        val intent = Intent(context, VpnService::class.java)
        intent.putExtra("config", configJson)
        context.startService(intent)
        isRunning = true
    }

    fun stop(context: Context) {
        val intent = Intent(context, VpnService::class.java)
        context.stopService(intent)
        isRunning = false
    }

    fun isActive(): Boolean = isRunning
}