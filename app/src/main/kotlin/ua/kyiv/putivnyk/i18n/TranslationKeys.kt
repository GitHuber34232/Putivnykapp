package ua.kyiv.putivnyk.i18n

import java.security.MessageDigest

/** Cache key of a runtime-translated text; shared by the UI, notifications and the audio guide. */
fun dynamicTranslationKey(text: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
    val hex = digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    return "dyn.$hex"
}
