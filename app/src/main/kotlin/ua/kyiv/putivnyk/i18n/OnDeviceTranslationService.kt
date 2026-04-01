package ua.kyiv.putivnyk.i18n

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class OnDeviceTranslationService @Inject constructor() : TranslationService {

    private val translators = ConcurrentHashMap<String, Translator>()

    override suspend fun translateText(
        text: String,
        sourceLanguageIso: String,
        targetLanguageIso: String
    ): String {
        val source = resolveLanguageTag(sourceLanguageIso, isSource = true)
            ?: return text
        val target = resolveLanguageTag(targetLanguageIso, isSource = false)
            ?: return text

        if (source == target) return text

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(source)
            .setTargetLanguage(target)
            .build()

        val translator = getOrCreateTranslator(source, target, options)

        translator.downloadModelIfNeededAwait()
        return translator.translateAwait(text)
    }

    override fun isSupportedByMlKit(isoCode: String): Boolean {
        return try {
            val lang = resolveLanguageTag(isoCode, isSource = false) ?: return false
            TranslateLanguage.getAllLanguages().contains(lang)
        } catch (_: Throwable) {
            false
        }
    }

    override suspend fun downloadModel(
        sourceLanguageIso: String,
        targetLanguageIso: String,
        timeoutMs: Long
    ): Boolean {
        val source = resolveLanguageTag(sourceLanguageIso, isSource = true) ?: return false
        val target = resolveLanguageTag(targetLanguageIso, isSource = false) ?: return false
        if (source == target) return true

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(source)
            .setTargetLanguage(target)
            .build()

        val translator = getOrCreateTranslator(source, target, options)
        return try {
            withTimeout(timeoutMs) {
                translator.downloadModelIfNeededAwait()
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    override suspend fun deleteDownloadedModels(): Int {
        translators.values.forEach { translator -> runCatching { translator.close() } }
        translators.clear()

        val manager = RemoteModelManager.getInstance()
        val models = manager.getDownloadedModelsAwait()
            .filterIsInstance<TranslateRemoteModel>()

        models.forEach { model ->
            runCatching { manager.deleteDownloadedModelAwait(model) }
        }
        return models.size
    }

    private fun getOrCreateTranslator(
        source: String,
        target: String,
        options: TranslatorOptions
    ): Translator {
        val key = "$source->$target"
        return translators.getOrPut(key) {
            Translation.getClient(options)
        }
    }

    private fun resolveLanguageTag(isoCode: String, isSource: Boolean): String? {
        val normalized = isoCode.lowercase()
        val direct = TranslateLanguage.fromLanguageTag(normalized)
        if (direct != null) return direct

        return if (isSource) {
            TranslateLanguage.fromLanguageTag("en")
        } else {
            null
        }
    }

    private suspend fun Translator.downloadModelIfNeededAwait() =
        suspendCancellableCoroutine<Unit> { continuation ->
            val conditions = DownloadConditions.Builder().build()

            val task = downloadModelIfNeeded(conditions)
            task.addOnSuccessListener { if (continuation.isActive) continuation.resume(Unit) }
                .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
        }

    private suspend fun Translator.translateAwait(text: String): String =
        suspendCancellableCoroutine { continuation ->
            translate(text)
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.resumeWithException(it) }
        }

    private suspend fun RemoteModelManager.getDownloadedModelsAwait() =
        suspendCancellableCoroutine<Set<com.google.mlkit.common.model.RemoteModel>> { continuation ->
            getDownloadedModels(TranslateRemoteModel::class.java)
                .addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
                .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
        }

    private suspend fun RemoteModelManager.deleteDownloadedModelAwait(model: TranslateRemoteModel) =
        suspendCancellableCoroutine<Unit> { continuation ->
            deleteDownloadedModel(model)
                .addOnSuccessListener { if (continuation.isActive) continuation.resume(Unit) }
                .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
        }
}
