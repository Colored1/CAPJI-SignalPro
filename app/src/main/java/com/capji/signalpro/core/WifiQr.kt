package com.capji.signalpro.core

enum class WifiSecurity(val qrToken: String) {
    WPA("WPA"),
    WPA3("SAE"),
    WEP("WEP"),
    OPEN("nopass"),
}

data class WifiCredentials(
    val ssid: String,
    val password: String,
    val security: WifiSecurity,
    val hidden: Boolean = false,
)

/** The `WIFI:T:...;S:...;P:...;;` payload understood by phone cameras. */
object WifiQr {
    private const val SPECIAL = "\\;,:\""

    fun escape(value: String): String = buildString {
        value.forEach { char ->
            if (char in SPECIAL) append('\\')
            append(char)
        }
    }

    fun build(credentials: WifiCredentials): String = buildString {
        append("WIFI:T:").append(credentials.security.qrToken)
        append(";S:").append(escape(credentials.ssid)).append(';')
        if (credentials.security != WifiSecurity.OPEN) {
            append("P:").append(escape(credentials.password)).append(';')
        }
        if (credentials.hidden) append("H:true;")
        append(';')
    }

    fun parse(raw: String): WifiCredentials? {
        val text = raw.trim()
        if (!text.startsWith("WIFI:", ignoreCase = true)) return null
        val fields = mutableMapOf<String, String>()
        val current = StringBuilder()
        var key: String? = null
        var index = 5
        while (index < text.length) {
            val char = text[index]
            when {
                char == '\\' && index + 1 < text.length -> {
                    current.append(text[index + 1])
                    index++
                }
                char == ':' && key == null -> {
                    key = current.toString().uppercase()
                    current.clear()
                }
                char == ';' -> {
                    key?.let { fields[it] = current.toString() }
                    key = null
                    current.clear()
                }
                else -> current.append(char)
            }
            index++
        }
        key?.let { fields[it] = current.toString() }

        val ssid = fields["S"].orEmpty()
        if (ssid.isEmpty()) return null
        val password = fields["P"].orEmpty()
        val type = fields["T"].orEmpty().uppercase()
        val security = when {
            type == "SAE" || type == "WPA3" -> WifiSecurity.WPA3
            type == "WEP" -> WifiSecurity.WEP
            type == "NOPASS" -> WifiSecurity.OPEN
            type.isEmpty() && password.isEmpty() -> WifiSecurity.OPEN
            else -> WifiSecurity.WPA
        }
        return WifiCredentials(ssid, password, security, fields["H"].equals("true", ignoreCase = true))
    }

    /** Returns false when a router would reject the password for this security type. */
    fun isValidPassword(password: String, security: WifiSecurity): Boolean = when (security) {
        WifiSecurity.OPEN -> true
        WifiSecurity.WEP -> password.length in setOf(5, 10, 13, 26)
        WifiSecurity.WPA -> password.length in 8..63 ||
            (password.length == 64 && password.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' })
        WifiSecurity.WPA3 -> password.length in 8..63
    }
}
