package com.example.wa

import android.content.Context
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.net.LocalSocketAddress
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * HTTP/JSON client for the CoS brain over a UNIX domain socket.
 *
 * Android's loopback TCP port is closed by default (Stage 3): the brain binds
 * `@brain`/a filesystem socket and the engine reaches it via `LocalSocket`.
 * The daemon enforces `Authorization: Bearer <token>`, mirrored here.
 */
object BrainClient {

    /** Send an RPC to the brain over its UNIX socket. Returns the raw body. */
    fun call(context: Context, jsonBody: String): String {
        val sock = BrainService.sockPath(context)
        val token = BrainService.getToken(context)
        return callRaw(sock, token, jsonBody)
    }

    /** Low-level POST over a filesystem UNIX socket. */
    fun callRaw(sockPath: String, token: String, jsonBody: String): String {
        val socket = LocalSocket()
        socket.connect(LocalSocketAddress(sockPath, LocalSocketAddress.Namespace.FILESYSTEM))
        try {
            val bodyBytes = jsonBody.toByteArray(Charsets.UTF_8)
            val request =
                "POST / HTTP/1.1\r\n" +
                    "Host: localhost\r\n" +
                    "Authorization: Bearer $token\r\n" +
                    "Content-Type: application/json\r\n" +
                    "Content-Length: ${bodyBytes.size}\r\n" +
                    "Connection: close\r\n\r\n"
            val out = socket.outputStream
            out.write(request.toByteArray(Charsets.UTF_8))
            out.write(bodyBytes)
            out.flush()

            return readBody(socket)
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    /** GET /ping over the socket (health check). */
    fun pingRaw(sockPath: String, token: String): Boolean {
        val socket = LocalSocket()
        socket.connect(LocalSocketAddress(sockPath, LocalSocketAddress.Namespace.FILESYSTEM))
        try {
            val request =
                "GET /ping HTTP/1.1\r\n" +
                    "Host: localhost\r\n" +
                    "Authorization: Bearer $token\r\n" +
                    "Connection: close\r\n\r\n"
            socket.outputStream.write(request.toByteArray(Charsets.UTF_8))
            socket.outputStream.flush()
            return readBody(socket).contains("\"pong\"")
        } catch (_: Exception) {
            return false
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun readBody(socket: LocalSocket): String {
        val buf = ByteArrayOutputStream()
        val tmp = ByteArray(4096)
        val input = socket.inputStream
        while (true) {
            val n = input.read(tmp)
            if (n <= 0) break
            buf.write(tmp, 0, n)
        }
        // Strip the HTTP status line + headers; return the body.
        val raw = buf.toString(Charsets.UTF_8)
        val split = raw.split("\r\n\r\n")
        return if (split.size >= 2) split[1] else raw
    }

    /** Health check: ping the brain, return true if it answers. */
    fun ping(context: Context): Boolean {
        return try {
            pingRaw(BrainService.sockPath(context), BrainService.getToken(context))
        } catch (_: Exception) {
            false
        }
    }
}
