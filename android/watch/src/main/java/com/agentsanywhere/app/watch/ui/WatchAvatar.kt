package com.agentsanywhere.app.watch.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agentsanywhere.app.ui.designsystem.LocalAAColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * The account picture.
 *
 * `/auth/me` returns `avatar` as whatever the phone client uploaded, which is a
 * `data:image/...;base64,...` URL; an account that never set one returns null, and a hand-made one
 * could be a plain http(s) URL. Coil is deliberately not on the watch classpath - it is most of a
 * megabyte for a 24dp circle - so both shapes are decoded here instead: base64 straight from
 * memory, http through OkHttp on the IO dispatcher.
 *
 * Decoding happens in [produceState], so it runs off the composition and is redone only when the
 * source string actually changes.
 */
@Composable
internal fun WatchAvatar(
    source: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    fallback: String = "",
) {
    val colors = LocalAAColors.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, source) {
        value = source?.takeIf(String::isNotBlank)?.let { decodeAvatar(it) }
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.raisedSurface),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            // A letter rather than an empty ring: the row keeps its shape while the picture is
            // still being fetched, and an account with no avatar still gets an identity mark.
            Text(
                text = fallback.trim().take(1).uppercase(),
                color = colors.faint,
                fontSize = (size.value * 0.5f).sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private val avatarHttpClient by lazy { OkHttpClient() }

private suspend fun decodeAvatar(source: String): ImageBitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val bytes = when {
            source.startsWith("data:", ignoreCase = true) -> {
                val comma = source.indexOf(',')
                if (comma < 0) {
                    null
                } else {
                    Base64.decode(source.substring(comma + 1), Base64.DEFAULT)
                }
            }

            source.startsWith("http", ignoreCase = true) -> {
                val request = Request.Builder().url(source).build()
                avatarHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) response.body?.bytes() else null
                }
            }

            else -> null
        }
        bytes?.takeIf { it.isNotEmpty() }?.let { data ->
            BitmapFactory.decodeByteArray(data, 0, data.size)?.asImageBitmap()
        }
    }.getOrNull()
}
