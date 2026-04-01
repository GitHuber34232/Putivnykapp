package ua.kyiv.putivnyk.platform.io

interface TextResourceLoader {
    fun loadText(path: String): String?
}