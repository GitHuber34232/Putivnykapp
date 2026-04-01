package ua.kyiv.putivnyk.platform.io

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidAssetTextResourceLoader @Inject constructor(
    @param:ApplicationContext private val context: Context
) : TextResourceLoader {
    override fun loadText(path: String): String? = runCatching {
        context.assets.open(path).bufferedReader().use { it.readText() }
    }.getOrNull()
}