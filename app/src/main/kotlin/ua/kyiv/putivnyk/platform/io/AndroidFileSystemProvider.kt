package ua.kyiv.putivnyk.platform.io

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidFileSystemProvider @Inject constructor(
    @param:ApplicationContext private val context: Context
) : FileSystemProvider {

    override fun ensureDirectory(path: String) {
        resolve(path).mkdirs()
    }

    override fun writeText(path: String, text: String) {
        resolve(path).apply {
            parentFile?.mkdirs()
            writeText(text)
        }
    }

    override fun readText(path: String): String? = runCatching { resolve(path).readText() }.getOrNull()

    override fun exists(path: String): Boolean = resolve(path).exists()

    override fun size(path: String): Long = resolve(path).length()

    override fun list(path: String): List<String> = resolve(path).listFiles()?.map { child ->
        if (path.isBlank()) child.name else "$path/${child.name}"
    }.orEmpty()

    override fun delete(path: String): Boolean = resolve(path).delete()

    private fun resolve(path: String): File = File(context.filesDir, path)
}