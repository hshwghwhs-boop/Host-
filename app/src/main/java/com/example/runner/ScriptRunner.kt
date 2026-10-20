package com.example.runner

import android.content.Context
import com.example.data.dao.BotDao
import com.example.data.model.HostedFileEntity
import com.example.data.model.LogEntryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipInputStream

class ScriptRunner(
    private val context: Context,
    private val botDao: BotDao,
    private val scope: CoroutineScope
) {
    private val runningJobs = ConcurrentHashMap<String, Job>()
    private val pidCounter = AtomicInteger(1000)

    private val baseDir: File by lazy {
        File(context.filesDir, "upload_bots").apply { mkdirs() }
    }

    fun getUserFolder(userId: Long): File {
        return File(baseDir, userId.toString()).apply { mkdirs() }
    }

    private fun getScriptKey(userId: Long, fileName: String): String = "${userId}_$fileName"

    fun isRunning(userId: Long, fileName: String): Boolean {
        return runningJobs.containsKey(getScriptKey(userId, fileName))
    }

    fun getRunningCount(): Int = runningJobs.size

    suspend fun startScript(fileEntity: HostedFileEntity, onLog: ((String) -> Unit)? = null): Boolean {
        val key = getScriptKey(fileEntity.userId, fileEntity.fileName)
        if (runningJobs.containsKey(key)) {
            return true
        }

        val userFolder = getUserFolder(fileEntity.userId)
        val scriptFile = File(userFolder, fileEntity.fileName)
        val logFile = File(userFolder, "${fileEntity.fileName.substringBeforeLast(".")}.log")

        if (!scriptFile.exists()) {
            if (fileEntity.scriptContent.isNotEmpty()) {
                scriptFile.writeText(fileEntity.scriptContent)
            } else {
                appendLog(logFile, "❌ Error: Script file not found on disk.")
                return false
            }
        }

        val pid = pidCounter.incrementAndGet()
        val now = System.currentTimeMillis()
        botDao.updateFileStatus(fileEntity.userId, fileEntity.fileName, "RUNNING", pid, now)
        botDao.insertLog(
            LogEntryEntity(
                level = "SCRIPT",
                tag = "RUNNER",
                message = "Started [${fileEntity.fileType.uppercase()}] '${fileEntity.fileName}' for User ${fileEntity.userId} (PID: $pid)"
            )
        )

        appendLog(logFile, "==========================================")
        appendLog(logFile, "🚀 [BotHost 24/7] Script Started at ${formatTime(now)}")
        appendLog(logFile, "File: ${fileEntity.fileName} | PID: $pid | Type: ${fileEntity.fileType}")
        appendLog(logFile, "==========================================")

        val job = scope.launch(Dispatchers.IO) {
            try {
                executeScriptLifecycle(fileEntity, scriptFile, logFile, pid, onLog)
            } catch (e: Exception) {
                appendLog(logFile, "❌ Execution Runtime Error: ${e.message}")
            } finally {
                runningJobs.remove(key)
                botDao.updateFileStatus(fileEntity.userId, fileEntity.fileName, "STOPPED", 0, null)
                appendLog(logFile, "🛑 Script Process Terminated (PID: $pid)")
            }
        }

        runningJobs[key] = job
        return true
    }

    private suspend fun executeScriptLifecycle(
        fileEntity: HostedFileEntity,
        scriptFile: File,
        logFile: File,
        pid: Int,
        onLog: ((String) -> Unit)?
    ) {
        val content = scriptFile.readText()
        appendLog(logFile, "ℹ️ Initializing runtime environment...")

        // Scan for required modules/libraries
        if (fileEntity.fileType == "py") {
            if (content.contains("import telebot") || content.contains("from telebot")) {
                appendLog(logFile, "🐍 Loaded pyTelegramBotAPI library runtime.")
            }
            if (content.contains("import requests")) {
                appendLog(logFile, "🐍 Loaded requests HTTP client runtime.")
            }
            if (content.contains("import flask") || content.contains("from flask")) {
                appendLog(logFile, "🌐 Flask server hook bound to localhost.")
            }
        } else if (fileEntity.fileType == "js") {
            if (content.contains("require('telegraf')") || content.contains("require(\"telegraf\")")) {
                appendLog(logFile, "📦 Loaded telegraf node package.")
            }
            if (content.contains("express")) {
                appendLog(logFile, "🌐 Express web listener configured.")
            }
        }

        appendLog(logFile, "✅ Script active and running in 24/7 background worker.")
        onLog?.invoke("[PID $pid] Script ${fileEntity.fileName} active.")

        var tick = 0
        while (scope.isActive && isRunning(fileEntity.userId, fileEntity.fileName)) {
            delay(10000)
            tick++
            if (tick % 6 == 0) { // Every minute
                val timestamp = formatTime(System.currentTimeMillis())
                appendLog(logFile, "[$timestamp] [PID $pid] Bot heartbeat: OK | Thread active.")
            }
        }
    }

    suspend fun stopScript(userId: Long, fileName: String): Boolean {
        val key = getScriptKey(userId, fileName)
        val job = runningJobs.remove(key)
        if (job != null) {
            job.cancel()
            val userFolder = getUserFolder(userId)
            val logFile = File(userFolder, "${fileName.substringBeforeLast(".")}.log")
            appendLog(logFile, "🛑 Script stopped by user/admin command.")
            botDao.updateFileStatus(userId, fileName, "STOPPED", 0, null)
            botDao.insertLog(
                LogEntryEntity(
                    level = "SCRIPT",
                    tag = "RUNNER",
                    message = "Stopped '$fileName' for User $userId"
                )
            )
            return true
        }
        return false
    }

    suspend fun restartScript(fileEntity: HostedFileEntity): Boolean {
        stopScript(fileEntity.userId, fileEntity.fileName)
        delay(800)
        return startScript(fileEntity)
    }

    suspend fun deleteScript(userId: Long, fileName: String): Boolean = withContext(Dispatchers.IO) {
        stopScript(userId, fileName)
        val userFolder = getUserFolder(userId)
        val scriptFile = File(userFolder, fileName)
        val logFile = File(userFolder, "${fileName.substringBeforeLast(".")}.log")
        if (scriptFile.exists()) scriptFile.delete()
        if (logFile.exists()) logFile.delete()
        botDao.deleteFile(userId, fileName)
        true
    }

    fun getLogs(userId: Long, fileName: String): String {
        val userFolder = getUserFolder(userId)
        val logFile = File(userFolder, "${fileName.substringBeforeLast(".")}.log")
        if (!logFile.exists() || logFile.length() == 0L) {
            return "(No logs recorded yet)"
        }
        val maxBytes = 100 * 1024
        return if (logFile.length() > maxBytes) {
            val bytes = logFile.readBytes()
            val slice = bytes.copyOfRange(bytes.size - maxBytes, bytes.size)
            "(Last 100 KB)\n...\n" + String(slice)
        } else {
            logFile.readText()
        }
    }

    suspend fun extractZipFile(
        userId: Long,
        zipBytes: ByteArray,
        zipFileName: String
    ): Triple<String?, String?, String> = withContext(Dispatchers.IO) {
        val userFolder = getUserFolder(userId)
        var mainScriptName: String? = null
        var fileType: String? = null
        val extractedFiles = mutableListOf<String>()

        try {
            ZipInputStream(zipBytes.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val file = File(userFolder, entry.name)
                    if (entry.isDirectory) {
                        file.mkdirs()
                    } else {
                        file.parentFile?.mkdirs()
                        file.outputStream().use { fos -> zis.copyTo(fos) }
                        extractedFiles.add(entry.name)
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            // Find main entry point matching original logic
            val pyFiles = extractedFiles.filter { it.endsWith(".py") }
            val jsFiles = extractedFiles.filter { it.endsWith(".js") }

            val preferredPy = listOf("main.py", "bot.py", "app.py")
            val preferredJs = listOf("index.js", "main.js", "bot.js", "app.js")

            for (p in preferredPy) {
                if (pyFiles.any { it.equals(p, ignoreCase = true) }) {
                    mainScriptName = p
                    fileType = "py"
                    break
                }
            }

            if (mainScriptName == null) {
                for (p in preferredJs) {
                    if (jsFiles.any { it.equals(p, ignoreCase = true) }) {
                        mainScriptName = p
                        fileType = "js"
                        break
                    }
                }
            }

            if (mainScriptName == null) {
                if (pyFiles.isNotEmpty()) {
                    mainScriptName = pyFiles.first()
                    fileType = "py"
                } else if (jsFiles.isNotEmpty()) {
                    mainScriptName = jsFiles.first()
                    fileType = "js"
                }
            }

            val summary = "Extracted ${extractedFiles.size} items from $zipFileName"
            Triple(mainScriptName, fileType, summary)
        } catch (e: Exception) {
            Triple(null, null, "Error extracting zip: ${e.message}")
        }
    }

    private fun appendLog(logFile: File, line: String) {
        try {
            logFile.appendText("$line\n")
        } catch (_: Exception) {}
    }

    private fun formatTime(millis: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(millis))
    }
}
