package de.quati.ogen.plugin.intern.oas.load

import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.readText

/** Loads the text of a document by its absolute URI. */
internal fun interface ResourceLoader {
    fun load(uri: URI): Resource

    sealed interface Resource {
        /** [source] names the document in messages, e.g. its file path. */
        data class Text(val text: String, val source: String) : Resource
        data class Failure(val reason: String) : Resource
    }

    companion object {
        /**
         * Files, and documents over HTTP(S) if their URI starts with one of [httpAllowlist]; with an empty
         * allowlist, which is the default, remote references are not loaded.
         */
        fun default(httpAllowlist: List<String> = emptyList()): ResourceLoader {
            val http by lazy { HttpResourceLoader() }
            return ResourceLoader { uri ->
                when (uri.scheme?.lowercase()) {
                    "file" -> FileResourceLoader.load(uri)
                    "http", "https" -> if (httpAllowlist.any { uri.toString().startsWith(it) })
                        http.load(uri)
                    else
                        Resource.Failure("remote references are not loaded unless their URI is allowed")
                    else -> Resource.Failure("the URI scheme '${uri.scheme}' is not supported")
                }
            }
        }
    }
}

internal object FileResourceLoader : ResourceLoader {
    override fun load(uri: URI): ResourceLoader.Resource {
        val path = try {
            Path.of(uri)
        } catch (e: IllegalArgumentException) {
            return ResourceLoader.Resource.Failure("'$uri' is not a file: ${e.message}")
        }
        return try {
            ResourceLoader.Resource.Text(path.readText(), path.toString())
        } catch (_: NoSuchFileException) {
            ResourceLoader.Resource.Failure("the file '$path' does not exist")
        } catch (e: IOException) {
            ResourceLoader.Resource.Failure("cannot read '$path': ${e.message ?: e::class.simpleName}")
        }
    }
}

internal class HttpResourceLoader(
    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(10))
        .build(),
) : ResourceLoader {
    override fun load(uri: URI): ResourceLoader.Resource = try {
        val request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).GET().build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() in 200..299)
            ResourceLoader.Resource.Text(response.body(), uri.toString())
        else
            ResourceLoader.Resource.Failure("'$uri' answered with HTTP ${response.statusCode()}")
    } catch (e: IOException) {
        ResourceLoader.Resource.Failure("cannot load '$uri': ${e.message ?: e::class.simpleName}")
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        ResourceLoader.Resource.Failure("loading '$uri' was interrupted")
    }
}
