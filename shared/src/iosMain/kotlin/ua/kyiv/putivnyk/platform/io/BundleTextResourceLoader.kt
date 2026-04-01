package ua.kyiv.putivnyk.platform.io

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSBundle
import platform.posix.FILE
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.rewind
import platform.posix.SEEK_END

@OptIn(ExperimentalForeignApi::class)
class BundleTextResourceLoader(
    private val bundle: NSBundle = NSBundle.mainBundle
) : TextResourceLoader {

    override fun loadText(path: String): String? {
        val normalized = path.trimStart('/')
        val extension = normalized.substringAfterLast('.', missingDelimiterValue = "")
        val fileName = normalized.substringBeforeLast('.', missingDelimiterValue = normalized)
        val directory = fileName.substringBeforeLast('/', missingDelimiterValue = "")
        val resourceName = fileName.substringAfterLast('/')
        val resourcePath = bundle.pathForResource(
            name = resourceName,
            ofType = extension.ifBlank { null },
            inDirectory = directory.ifBlank { null }
        ) ?: return null

        return readTextFile(resourcePath)
    }

    private fun readTextFile(path: String): String? {
        val file = fopen(path, "rb") ?: return null
        return try {
            val size = fileSize(file)
            if (size <= 0) return ""
            val bytes = ByteArray(size)
            val read = bytes.usePinned { pinned ->
                fread(pinned.addressOf(0), 1u, size.toULong(), file).toInt()
            }
            if (read <= 0) null else bytes.decodeToString(endIndex = read)
        } finally {
            fclose(file)
        }
    }

    private fun fileSize(file: CPointer<FILE>): Int {
        fseek(file, 0, SEEK_END)
        val size = ftell(file).toInt()
        rewind(file)
        return size
    }
}