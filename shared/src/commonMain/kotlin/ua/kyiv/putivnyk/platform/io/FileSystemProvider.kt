package ua.kyiv.putivnyk.platform.io

interface FileSystemProvider {
    fun ensureDirectory(path: String)
    fun writeText(path: String, text: String)
    fun readText(path: String): String?
    fun exists(path: String): Boolean
    fun size(path: String): Long
    fun list(path: String): List<String>
    fun delete(path: String): Boolean
}