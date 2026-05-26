package com.surprisekhoza.sktunnel.core

data class ProxyConfig(
    val host: String,
    val port: Int,
    val user: String?,
    val protocol: String = "vmess" // default
)

object ProxyParser {

    /**
     * Parses the format: ip:port@user
     * Example: 192.168.1.1:8080@myusername
     * Returns null if format is invalid.
     */
    fun parse(input: String, protocol: String = "vmess"): ProxyConfig? {
        // Remove whitespace
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        // Split at @ to get user
        val parts = trimmed.split("@", limit = 2)
        val hostPort = parts[0]
        val user = if (parts.size == 2) parts[1] else null

        // Split host and port
        val hostPortParts = hostPort.split(":", limit = 2)
        if (hostPortParts.size != 2) return null

        val host = hostPortParts[0]
        val port = hostPortParts[1].toIntOrNull() ?: return null

        if (host.isEmpty() || port !in 1..65535) return null

        return ProxyConfig(
            host = host,
            port = port,
            user = user,
            protocol = protocol
        )
    }
}