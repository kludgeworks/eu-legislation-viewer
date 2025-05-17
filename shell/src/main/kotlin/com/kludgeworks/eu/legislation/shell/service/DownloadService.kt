package com.kludgeworks.eu.legislation.shell.service

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import com.kludgeworks.eu.legislation.shell.util.XmlFormatter
import com.kludgeworks.eu.legislation.util.DomainError
import com.kludgeworks.eu.legislation.util.catchingUnexpectedSuspend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service
import java.io.FileOutputStream
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

    /**
     * Downloads content from a URL or local file and processes it based on file type
     * 
     * @param url The URL or file URI to download from
     * @param output The path to save the downloaded content to
     * @return The path to the processed content (may be different if zip file was unzipped)
     */
    context(Raise<DomainError>)
    suspend fun downloadContent(url: String, output: Path): Path {
        val downloaded = if (url.startsWith("file:")) {
            copyLocal(url, output)
        } else {
            ktorClient.download(url, output)
        }
        return postProcess(downloaded)
    }

    /**
     * Copies a local file to the output path
     */
    context(Raise<DomainError>)
    private suspend fun copyLocal(url: String, output: Path): Path = catchingUnexpectedSuspend("Failed to copy local file") {
        withContext(Dispatchers.IO) {
            Files.createDirectories(output.parent)
            Files.copy(Paths.get(URI(url)), output)
        }
        output
    }

    /**
     * Post-processes downloaded content based on file type
     */
    context(Raise<DomainError>)
    private suspend fun postProcess(path: Path): Path {
        return when {
            path.toString().endsWith(".zip", true) ->
                unzip(path, path.parent)
            path.toString().endsWith(".xml", true) -> {
                ensure(xmlFormatter.formatXmlFile(path.toFile())) {
                    DomainError.UnexpectedError("XML formatting failed for $path")
                }
                path
            }
            else -> path
        }
    }

    /**
     * Unzips a ZIP file to the specified output directory
     */
    context(Raise<DomainError>)
    private suspend fun unzip(zipFile: Path, outputDir: Path): Path = catchingUnexpectedSuspend("Failed to unzip file") {
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
    }
}
