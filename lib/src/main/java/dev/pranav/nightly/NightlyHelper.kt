package dev.pranav.nightly

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

object NightlyHelper {

    val client = OkHttpClient()
    var repository = "Sketchware-Pro/Sketchware-Pro"
    var branch = "main"
    var artifact = "apk-minApi21-debug"
    var workflow = "android"

    @JvmStatic
    fun main(args: Array<String>) {
        println(downloadLatestArtifact("apk-minApi21-debug", File("build")))
    }
// git log -n 1 --pretty=format:"%h %B" > commit.txt
    fun getLatestCommit(): String {
        val req = Request.Builder()
            .url("https://nightly.link/$repository/workflows/$workflow/$branch/$artifact")
            .build()

        val res = client.newCall(req).execute()

        ZipInputStream(res.body!!.byteStream()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                if (entry.name == "commit") {
                    return zis.readBytes().decodeToString()
                }
            }
        }
        return ""
    }

    fun getLatestCommitSHA256(): String {
        return getLatestCommit().substringBefore(' ')
    }

    fun getLatestCommitMessage(): String {
        return getLatestCommit().substringAfter(' ')
    }

    fun downloadLatestArtifact(name: String, dir: File) {
        val req = Request.Builder()
            .url("https://nightly.link/$repository/workflows/$workflow/$branch/$name.zip")
            .build()

        val res = client.newCall(req).execute()

        val file = File(dir, getLatestCommitSHA256())
        file.mkdirs()

        println("Downloading $name to $file")


        res.body!!.byteStream().unzip(file)
    }


    private fun InputStream.unzip(targetDir: File) {
        ZipInputStream(this).use { zipIn ->
            var ze = zipIn.nextEntry
            while (ze != null) {
                val resolved = targetDir.resolve(ze.name).normalize()
                // Android 14+ prevents Zip Slip attacks: https://developer.android.com/about/versions/14/behavior-changes-14#zip-path-traversal
                if (!resolved.startsWith(targetDir)) {
                    // see: https://snyk.io/research/zip-slip-vulnerability
                    throw SecurityException("Entry with an illegal path: " + ze.name)
                }
                if (ze.isDirectory) {
                    resolved.mkdirs()
                } else {
                    resolved.parentFile?.mkdirs()
                    resolved.outputStream().use { output ->
                        zipIn.copyTo(output)
                    }
                }

                ze = zipIn.nextEntry
            }
        }

        close()
    }
}
