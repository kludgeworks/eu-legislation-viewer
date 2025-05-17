package com.kludgeworks.eu.legislation.shell.service

import arrow.core.raise.either
import arrow.core.raise.ensure
import com.kludgeworks.eu.legislation.shell.util.XmlFormatter
import com.kludgeworks.eu.legislation.util.DomainError
import com.kludgeworks.eu.legislation.util.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service
import java.io.FileOutputStream
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.ZipInputStream

@Service
class DownloadService(
    private val xmlFormatter: XmlFormatter,
    private val ktorClient: KtorClient
) {

suspend fun downloadContent(url: String, output: Path): Result<Path> = either {
        val downloaded = if (url.startsWith("file:")) {
            copyLocal(url, output).bind()
        } else {
            ktorClient.download(url, output).bind()
        }
        postProcess(downloaded).bind()
    }

    private suspend fun copyLocal(url: String, output: Path): Result<Path> = either {
        withContext(Dispatchers.IO) {
            Files.createDirectories(output.parent)
            Files.copy(Paths.get(URI(url)), output)
        }
        output
    }

    private suspend fun postProcess(path: Path): Result<Path> = either {
        when {
            path.toString().endsWith(".zip", true) ->
                unzip(path, path.parent).bind()
            path.toString().endsWith(".xml", true) -> {
                ensure(xmlFormatter.formatXmlFile(path.toFile())) {
                    DomainError.UnexpectedError("XML formatting failed for $path")
                }
                path
            }
            else -> path
        }
    }

    private suspend fun unzip(zipFile: Path, outputDir: Path): Result<Path> = either {
        try {
            val destDir = outputDir.resolve(zipFile.fileName.toString().removeSuffix(".zip"))
            withContext(Dispatchers.IO) {
                Files.createDirectories(destDir)
                ZipInputStream(Files.newInputStream(zipFile)).use { zis ->
                    generateSequence { zis.nextEntry }.forEach { entry ->
                        val outPath = destDir.resolve(entry.name)
                        if (entry.isDirectory) {
                            Files.createDirectories(outPath)
                        } else {
                            Files.createDirectories(outPath.parent)
                            FileOutputStream(outPath.toFile()).use { out -> zis.copyTo(out) }
                        }
                        zis.closeEntry()
                    }
                }
            }
            destDir
        } catch (e: IOException) {
            raise(DomainError.UnexpectedError("Failed to unzip: ${e.message}", e))
        }
    }
}
