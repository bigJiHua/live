package com.live.finance.core.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

/**
 * AES-256-CBC / PKCS7 —— 与 web `utils/aes.js`（CryptoJS）及后端 `authSecurityData.js` 逐字节对齐。
 *
 * 线格式： [ ivHex(32 小写 hex 字符) ][ base64(密文) ]
 *  - key：64 位 hex 字符串 → 32 字节（CryptoJS.enc.Hex.parse(key)）
 *  - iv：每次随机 16 字节
 *  - CryptoJS.AES 在显式给 key+iv 时，输出为纯 base64(密文)，无 OpenSSL salt 头 → 对应 Cipher AES/CBC/PKCS5Padding
 */
object AesCbc {

    /** key: 64 位 hex；plain: UTF-8 明文。返回 ivHex + base64(密文)。 */
    fun encrypt(plain: String, keyHex: String): String {
        val key = hexToBytes(keyHex)
        val iv = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        val cipherBytes = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return bytesToHex(iv) + Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
    }

    /** 解密 [ ivHex(32) + base64(密文) ] → UTF-8 明文（用于极少数回传加密场景；当前响应为明文，保留备用）。 */
    fun decrypt(payload: String, keyHex: String): String {
        val iv = hexToBytes(payload.substring(0, 32))
        val cipherBytes = Base64.decode(payload.substring(32), Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(hexToBytes(keyHex), "AES"), IvParameterSpec(iv))
        return String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
    }

    fun sha256Hex(input: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        return bytesToHex(md.digest(input.toByteArray(Charsets.UTF_8)))
    }

    private fun hexToBytes(hex: String): ByteArray {
        val out = ByteArray(hex.length / 2)
        for (i in out.indices) {
            out[i] = ((Character.digit(hex[i * 2], 16) shl 4) + Character.digit(hex[i * 2 + 1], 16)).toByte()
        }
        return out
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(Character.forDigit((b.toInt() shr 4) and 0xF, 16))
            sb.append(Character.forDigit(b.toInt() and 0xF, 16))
        }
        return sb.toString()
    }
}
