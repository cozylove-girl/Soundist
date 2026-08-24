package com.soundist.feature.listening

import java.security.MessageDigest

/** 从显示名推导真实音频扩展名（保留原扩展、小写；无扩展或异常回退 ogg）。 */
fun audioFileExtension(displayName: String): String {
    val raw = displayName.substringAfterLast('.', "").trim()
    return if (raw.isNotEmpty() && raw.length <= 5 && raw.all { it.isLetterOrDigit() }) raw.lowercase() else "ogg"
}

/** SHA-256 十六进制摘要（小写 64 位）。 */
fun sha256Hex(data: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
