package com.example

import android.util.Log
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

class WhiteboardServer {

    private var server: ApplicationEngine? = null
    
    // Manage all current strokes for new clients
    private val allStrokes = mutableListOf<Stroke>()
    private var currentStrokePoints = mutableListOf<StrokePoint>()
    private var currentColor = "#000000"
    private var currentWidth = 8f

    // Use a shared flow to broadcast to all connected WebSocket clients
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val messages = _messages.asSharedFlow()

    fun start() {
        if (server != null) return
        
        server = embeddedServer(CIO, port = 8080) {
            install(WebSockets) {
                maxFrameSize = Long.MAX_VALUE
                masking = false
            }
            install(CORS) {
                anyHost()
            }
            routing {
                get("/") {
                    call.respondText(getHtml(), ContentType.Text.Html)
                }
                
                webSocket("/ws") {
                    // When a new client connects, send them the existing strokes
                    try {
                        val syncMessage = WsMessage(type = "sync", strokes = allStrokes.toList())
                        send(Frame.Text(Json.encodeToString(syncMessage)))
                        
                        // Listen for broadcasts and send them to this client
                        launch {
                            messages.collect { msg ->
                                send(Frame.Text(msg))
                            }
                        }
                        
                        // Keep connection alive
                        for (frame in incoming) {
                            // (We only send data from mobile to laptop, but we can handle ping/pong here or just ignore)
                        }
                    } catch (e: Exception) {
                        Log.e("WhiteboardServer", "WebSocket error: ${e.localizedMessage}")
                    }
                }
            }
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                server?.start(wait = true)
            } catch (e: Exception) {
                Log.e("WhiteboardServer", "Server start error: ${e.localizedMessage}")
            }
        }
    }

    fun stop() {
        server?.stop(1000L, 2000L)
        server = null
    }

    suspend fun clear() {
        allStrokes.clear()
        currentStrokePoints.clear()
        val msg = Json.encodeToString(WsMessage(type = "clear"))
        _messages.emit(msg)
    }

    suspend fun startStroke(x: Float, y: Float, color: String, width: Float) {
        currentColor = color
        currentWidth = width
        currentStrokePoints = mutableListOf()
        currentStrokePoints.add(StrokePoint(x, y))
        
        val msg = Json.encodeToString(WsMessage(type = "start", x = x, y = y, color = color, width = width))
        _messages.emit(msg)
    }

    suspend fun drawStroke(x: Float, y: Float) {
        currentStrokePoints.add(StrokePoint(x, y))
        val msg = Json.encodeToString(WsMessage(type = "draw", x = x, y = y))
        _messages.emit(msg)
    }

    suspend fun endStroke() {
        if (currentStrokePoints.isNotEmpty()) {
            allStrokes.add(Stroke(currentColor, currentWidth, currentStrokePoints.toList()))
        }
        val msg = Json.encodeToString(WsMessage(type = "end"))
        _messages.emit(msg)
    }

    companion object {
        fun getLocalIpAddress(): String? {
            try {
                val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
                for (intf in interfaces) {
                    val addrs = Collections.list(intf.inetAddresses)
                    for (addr in addrs) {
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            return addr.hostAddress
                        }
                    }
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
            return null
        }
    }

    private fun getHtml() = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Digital Pad</title>
            <style>
                body, html {
                    margin: 0;
                    padding: 0;
                    width: 100%;
                    height: 100%;
                    background: #fcfcfc;
                    overflow: hidden;
                    font-family: sans-serif;
                }
                canvas {
                    display: block;
                    width: 100%;
                    height: 100%;
                }
                #status {
                    position: absolute;
                    top: 10px;
                    left: 10px;
                    background: rgba(0,0,0,0.7);
                    color: white;
                    padding: 8px 12px;
                    border-radius: 8px;
                    font-size: 14px;
                    pointer-events: none;
                    transition: opacity 0.3s;
                }
            </style>
        </head>
        <body>
            <div id="status">Connecting...</div>
            <canvas id="canvas"></canvas>

            <script>
                const canvas = document.getElementById('canvas');
                const ctx = canvas.getContext('2d');
                const status = document.getElementById('status');

                function resizeCanvas() {
                    canvas.width = window.innerWidth;
                    canvas.height = window.innerHeight;
                    // Note: We might want to handle resizing gracefully, 
                    // but for a simple whiteboard, we use proportional scaling
                    // based on a fixed coordinate system (e.g., 1000x1000) or
                    // just standard aspect ratio.
                    // For now, let's assume the mobile app sends normalized coordinates (0.0 to 1.0)
                    // If it sends absolute, it won't scale. We should scale!
                    // Assuming mobile sends coordinates 0.0 to 1.0.
                }

                window.addEventListener('resize', resizeCanvas);
                resizeCanvas();

                const host = window.location.host;
                const ws = new WebSocket(`ws://${"$"}{host}/ws`);

                let currentPath = [];

                ws.onopen = () => {
                    status.textContent = 'Connected. Waiting for drawing...';
                    setTimeout(() => status.style.opacity = '0', 2000);
                };

                ws.onclose = () => {
                    status.textContent = 'Disconnected. Refresh to reconnect.';
                    status.style.opacity = '1';
                };

                function drawStroke(stroke) {
                    if (stroke.points.length === 0) return;
                    ctx.beginPath();
                    ctx.strokeStyle = stroke.color;
                    ctx.lineWidth = stroke.width;
                    ctx.lineCap = 'round';
                    ctx.lineJoin = 'round';
                    
                    const p0 = stroke.points[0];
                    ctx.moveTo(p0.x * canvas.width, p0.y * canvas.height);
                    for (let i = 1; i < stroke.points.length; i++) {
                        const p = stroke.points[i];
                        ctx.lineTo(p.x * canvas.width, p.y * canvas.height);
                    }
                    ctx.stroke();
                }

                ws.onmessage = (event) => {
                    const data = JSON.parse(event.data);
                    
                    if (data.type === 'sync') {
                        ctx.clearRect(0, 0, canvas.width, canvas.height);
                        data.strokes.forEach(drawStroke);
                    } else if (data.type === 'start') {
                        ctx.beginPath();
                        ctx.strokeStyle = data.color || '#000000';
                        ctx.lineWidth = data.width || 8;
                        ctx.lineCap = 'round';
                        ctx.lineJoin = 'round';
                        if (data.x !== undefined && data.y !== undefined) {
                            ctx.moveTo(data.x * canvas.width, data.y * canvas.height);
                        }
                    } else if (data.type === 'draw') {
                        if (data.x !== undefined && data.y !== undefined) {
                            ctx.lineTo(data.x * canvas.width, data.y * canvas.height);
                            ctx.stroke();
                        }
                    } else if (data.type === 'end') {
                        // Drawing ended
                    } else if (data.type === 'clear') {
                        ctx.clearRect(0, 0, canvas.width, canvas.height);
                    }
                };
            </script>
        </body>
        </html>
    """.trimIndent()
}
