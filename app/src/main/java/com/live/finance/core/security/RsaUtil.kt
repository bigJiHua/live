package com.live.finance.core.security

import android.util.Base64
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

/**
 * RSA 字符级加密（安全键盘专用），严格对齐 web `utils/request/handshake.js` 下发的公钥与
 * 后端 `common/utils/rsaKeys.js` 的解密口径：
 *  - 公钥为 SPKI PEM（`-----BEGIN PUBLIC KEY-----`），与 web 握手 `rsaPublicKey` 同源。
 *  - 逐字符 RSA/ECB/PKCS1Padding 加密，结果标准 base64（无换行）。
 *  - 后端用 `crypto.privateDecrypt({ padding: RSA_PKCS1_PADDING }, base64)` 解密拼接。
 *
 * Android 自带 `java.security`/`javax.crypto`，无需三方库。
 */
object RsaUtil {

    /** 用 SPKI PEM 公钥逐字符加密，返回 base64 密文。 */
    fun encryptChar(plain: String, publicKeyPem: String): String {
        val der = pemToDer(publicKeyPem)
        val pub = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(der))
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, pub)
        val enc = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(enc, Base64.NO_WRAP)
    }

    private fun pemToDer(pem: String): ByteArray {
        val b64 = pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\n", "")
            .replace("\r", "")
            .trim()
        return Base64.decode(b64, Base64.NO_WRAP)
    }
}
