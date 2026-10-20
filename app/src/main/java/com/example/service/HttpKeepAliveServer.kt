package com.example.service

import com.example.data.dao.BotDao
import com.example.data.model.LogEntryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket

class HttpKeepAliveServer(
    private val port: Int = 8080,
    private val botDao: BotDao,
    private val scope: CoroutineScope,
    private val statusProvider: () -> String
) {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    var isRunning: Boolean = false
        private set

    fun start() {
        if (isRunning) return
        serverJob = scope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(port)
                isRunning = true
                botDao.insertLog(
                    LogEntryEntity(
                        level = "HTTP",
                        tag = "SERVER",
                        message = "🌐 Web Dashboard & Keep-Alive Server active at http://0.0.0.0:$port"
                    )
                )

                while (isActive && !serverSocket!!.isClosed) {
                    try {
                        val clientSocket = serverSocket!!.accept()
                        launch(Dispatchers.IO) {
                            handleClient(clientSocket)
                        }
                    } catch (e: Exception) {
                        if (!isActive) break
                    }
                }
            } catch (e: Exception) {
                botDao.insertLog(
                    LogEntryEntity(
                        level = "ERROR",
                        tag = "HTTP",
                        message = "HTTP Server error: ${e.message}"
                    )
                )
            } finally {
                isRunning = false
            }
        }
    }

    private suspend fun handleClient(socket: Socket) = withContext(Dispatchers.IO) {
        try {
            socket.use { s ->
                val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                val out: OutputStream = s.getOutputStream()
                val requestLine = reader.readLine() ?: return@use

                val parts = requestLine.split(" ")
                val path = if (parts.size > 1) parts[1].substringBefore("?") else "/"

                val (statusCode, bodyBytes, contentType) = when {
                    path == "/ping" -> {
                        Triple("200 OK", "I'am Marco File Host".toByteArray(Charsets.UTF_8), "text/plain; charset=utf-8")
                    }
                    path == "/status" || path == "/api/status" -> {
                        Triple("200 OK", statusProvider().toByteArray(Charsets.UTF_8), "application/json; charset=utf-8")
                    }
                    path == "/style.css" -> {
                        val file = File("website/style.css")
                        if (file.exists()) {
                            Triple("200 OK", file.readBytes(), "text/css; charset=utf-8")
                        } else {
                            Triple("404 Not Found", "Not found".toByteArray(Charsets.UTF_8), "text/plain")
                        }
                    }
                    path == "/app.js" -> {
                        val file = File("website/app.js")
                        if (file.exists()) {
                            Triple("200 OK", file.readBytes(), "application/javascript; charset=utf-8")
                        } else {
                            Triple("404 Not Found", "Not found".toByteArray(Charsets.UTF_8), "text/plain")
                        }
                    }
                    path == "/" || path == "/index.html" -> {
                        val file = File("website/index.html")
                        if (file.exists()) {
                            Triple("200 OK", file.readBytes(), "text/html; charset=utf-8")
                        } else {
                            Triple("200 OK", "I'am Marco File Host".toByteArray(Charsets.UTF_8), "text/plain; charset=utf-8")
                        }
                    }
                    else -> {
                        Triple("200 OK", "I'am Marco File Host".toByteArray(Charsets.UTF_8), "text/plain; charset=utf-8")
                    }
                }

                val responseHeader = "HTTP/1.1 $statusCode\r\n" +
                        "Content-Type: $contentType\r\n" +
                        "Content-Length: ${bodyBytes.size}\r\n" +
                        "Connection: close\r\n" +
                        "Access-Control-Allow-Origin: *\r\n\r\n"

                out.write(responseHeader.toByteArray(Charsets.UTF_8))
                out.write(bodyBytes)
                out.flush()
            }
        } catch (_: Exception) {}
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverJob?.cancel()
    }
}
