package top.apip.spotisync

import com.github.kiulian.downloader.YoutubeDownloader
import com.github.kiulian.downloader.downloader.YoutubeProgressCallback
import com.github.kiulian.downloader.downloader.request.RequestSearchResult
import com.github.kiulian.downloader.downloader.request.RequestVideoFileDownload
import com.github.kiulian.downloader.downloader.request.RequestVideoInfo
import com.github.kiulian.downloader.model.search.field.FormatField
import com.github.kiulian.downloader.model.search.field.TypeField
import com.sun.net.httpserver.HttpServer
import org.apache.commons.lang3.StringUtils
import se.michaelthelin.spotify.SpotifyApi
import se.michaelthelin.spotify.model_objects.specification.SavedTrack
import java.awt.Desktop
import java.io.File
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.Executors
import kotlin.system.exitProcess


val executor = Executors.newSingleThreadExecutor()
var done = false

val downloader = YoutubeDownloader()

fun main() {
    val downloaderExecutor = Executors.newFixedThreadPool(10) // 5 Downloads at the same time

    val config = downloader.config
    config.executorService = downloaderExecutor

    val spotifyApi = SpotifyApi.Builder()
        .setClientId("c4671044d6474fa0b20cd7be53794206")
        .setClientSecret("501ceb432c5f4a73999a429dc5b82ef1")
        .setRedirectUri(URI.create("http://localhost:6969/"))
        .build()

    val uri = spotifyApi.authorizationCodeUri()
        .scope("user-library-read")
        .build()
        .execute()

    Desktop.getDesktop().browse(uri)

    val server = HttpServer.create(InetSocketAddress("localhost", 6969), 0)
    server.createContext("/") { exchange ->
        val code = exchange.requestURI.query.split("=")[1]
        // Respond with javascript of window close
        val responseBody = """
            <script>
                window.close();
            </script>
        """.trimIndent()
        exchange.sendResponseHeaders(200, responseBody.length.toLong())
        exchange.responseBody.use { it.write(responseBody.toByteArray()) }
        println("Code: $code")
        spotifyApi.accessToken = spotifyApi.authorizationCode(code).build().execute().accessToken
        done = true
    }
    server.executor = executor
    server.start()

    print("Waiting for code")
    while (!done) {
        print(".") // Waiting for code
        Thread.sleep(1000)
    }

    var iter = 0
    var totalSize = 50

    val inMemorySongs = mutableListOf<SavedTrack>()

    while (totalSize == 50) {
        val req = spotifyApi.usersSavedTracks
            .limit(50)
            .offset(iter * 50)
            .build()

        val album = req.execute()
        val items = album.items
        items.forEach { track ->
            inMemorySongs.add(track)
        }
        totalSize = album.items.size
        iter++
    }
    inMemorySongs.sortByDescending { it.addedAt }

    val downloadDir = File("downloaded")
    if (!downloadDir.exists()) downloadDir.mkdir()
    if (downloadDir.isFile) {
        downloadDir.delete()
        downloadDir.mkdir()
    }

    val downloadedFilesWithoutExtension = downloadDir.listFiles()
        ?.map { it.name }
        ?.map {
            it.split(".")
                .dropLast(1)
                .joinToString(".")
        }

    if (downloadedFilesWithoutExtension == null) {
        inMemorySongs.forEach {
            downloadAudio("${clean(it.track.name)} - ${clean(it.track.artists[0].name)}")
        }
    } else {
        // TODO: Change this method to something else that's more efficient
        val toDownload = inMemorySongs.filter {
            fil1 ->
            downloadedFilesWithoutExtension.none { fil2 ->
                StringUtils.getJaroWinklerDistance(
                    fil2,
                    "${clean(fil1.track.name)} - ${clean(fil1.track.artists[0].name)}"
                ) > 0.8
            }
        }

        if (toDownload.isEmpty()) {
            println("There is no any new songs detected, you're currently up to date!")
            exitProcess(0)
        }
        println("New songs detected! Here is the list of them: ")
        println(toDownload.joinToString("\n"))
        println("Downloading them now...")
        toDownload.forEach {
            downloadAudio("${it.track.name} - ${it.track.artists[0].name}")
        }
    }
}

fun downloadAudio(filt: String) {
    println("Starting download for $filt")
    val searchReq = RequestSearchResult(filt)
        .filter(
            TypeField.VIDEO,
            FormatField.HD,
        )
    val response = downloader.search(searchReq).data() ?: return println("No results found for $filt")
    val it = response.videos().first()

    val vidInfo = downloader.getVideoInfo(RequestVideoInfo(it.videoId())).data()
        ?: return println("No results found for $filt")

    downloader.downloadVideoFile(RequestVideoFileDownload(vidInfo.bestAudioFormat())
        .renameTo(filt)
        .saveTo(File("downloaded"))
        .callback(object : YoutubeProgressCallback<File?> {
            override fun onDownloading(progress: Int) {
                // Do Nothing
            }

            override fun onFinished(data: File?) {
                println("Downloaded $filt")
            }

            override fun onError(throwable: Throwable) {
                println("Error: " + throwable.localizedMessage)
            }
        })
        .async())
    println("Downloaded $filt, saved to downloaded/$filt.${vidInfo.bestAudioFormat().extension().value()}")
}

fun clean(str: String): String {
    // Remove all special characters excluding other unicode like japanese thing
    return str.replace(Regex("[^/[一-龠]+|[ぁ-ゔ][ァ-ヴー][a-zA-Z0-9][ａ-ｚＡ-Ｚ０-９][々〆〤ヶ]u\n ]"), "")
}