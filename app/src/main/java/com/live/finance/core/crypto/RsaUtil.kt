package com.live.finance.core.crypto

import android.util.Base64
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

/**
 * RSA 公钥加密 —— 对应 web 安全键盘的「字符级加密」（jsencrypt / RSA PKCS#1 v1.5）。
 * 公钥来自握手接口返回的 rsaPublicKey（X.509 SubjectPublicKeyInfo，base64/PEM）。
 */
object RsaUtil {

    fun parsePublicKey(base64OrPem: String): PublicKey {
        val body = base64OrPem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\\s".toRegex(), "")
        val der = Base64.decode(body, Base64.NO_WRAP)
        return KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(der))
    }

    /** 单字符加密（安全键盘逐位上传），返回 base64。 */
    fun encryptChar(ch: Char, publicKey: PublicKey): String {
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        return Base64.encodeToString(cipher.doFinal(ch.toString().toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    fun encryptText(text: String, publicKey: PublicKey): String {
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        return Base64.encodeToString(cipher.doFinal(text.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }
}
