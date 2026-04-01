package ua.kyiv.putivnyk.platform.io

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.posix.FILE
import platform.posix.closedir
import platform.posix.dirent
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.fwrite
import platform.posix.mkdir
import platform.posix.opendir
import platform.posix.readdir
import platform.posix.remove
import platform.posix.rewind
import platform.posix.stat
import platform.posix.SEEK_END

@OptIn(ExperimentalForeignApi::class)
class DocumentsFileSystemProvider : FileSystemProvider {
    private val rootPath: String = run {
        val baseDir = (NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).firstOrNull() as? String)
            ?: error("Unable to resolve iOS documents directory")
        val root = "$baseDir/PutivnykData"
        ensureDirectoryAbsolute(root)
        root
    }

    override fun ensureDirectory(path: String) {
        if (path.isBlank()) return
        ensureDirectoryAbsolute(resolve(path))
    }

    override fun writeText(path: String, text: String) {
        val filePath = resolve(path)
        filePath.substringBeforeLast('/', missingDelimiterValue = "")
            .takeIf { it.isNotBlank() }
            ?.let(::ensureDirectoryAbsolute)
        val file = fopen(filePath, "wb") ?: error("Unable to open file for writing: $filePath")
        try {
            val bytes = text.encodeToByteArray()
            bytes.usePinned { pinned ->
                fwrite(pinned.addressOf(0), 1u, bytes.size.toULong(), file)
            }
        } finally {
            fclose(file)
        }
    }

    override fun readText(path: String): String? {
        val file = fopen(resolve(path), "rb") ?: return null
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

    override fun exists(path: String): Boolean = memScoped {
        val info = alloc<stat>()
        platform.posix.stat(resolve(path), info.ptr) == 0
    }

    override fun size(path: String): Long {
        return memScoped {
            val info = alloc<stat>()
            if (platform.posix.stat(resolve(path), info.ptr) != 0) return@memScoped 0L
            info.st_size
        }
    }

    override fun list(path: String): List<String> {
        val basePath = resolve(path)
        val dir = opendir(basePath) ?: return emptyList()
        val results = mutableListOf<String>()
        try {
            while (true) {
                val entry = readdir(dir) ?: break
                val name = entry.pointed.d_name.toKString()
                if (name == "." || name == "..") continue
                results += if (path.isBlank()) name else "$path/$name"
            }
        } finally {
            closedir(dir)
        }
        return results
    }

    override fun delete(path: String): Boolean = remove(resolve(path)) == 0

    private fun resolve(path: String): String = if (path.startsWith('/')) path else "$rootPath/$path"

    private fun ensureDirectoryAbsolute(path: String) {
        var current = ""
        path.split('/').filter { it.isNotBlank() }.forEach { segment ->
            current += "/$segment"
            mkdir(current, 511u)
        }
    }

    private fun fileSize(file: CPointer<FILE>): Int {
        fseek(file, 0, SEEK_END)
        val size = ftell(file).toInt()
        rewind(file)
        return size
    }
}