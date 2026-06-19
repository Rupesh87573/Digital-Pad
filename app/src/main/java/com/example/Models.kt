package com.example

import kotlinx.serialization.Serializable

@Serializable
data class StrokePoint(val x: Float, val y: Float)

@Serializable
data class Stroke(
    val color: String,
    val width: Float,
    val points: List<StrokePoint>
)

@Serializable
data class WsMessage(
    val type: String, // "sync", "start", "draw", "end", "clear"
    val color: String? = null,
    val width: Float? = null,
    val x: Float? = null,
    val y: Float? = null,
    val strokes: List<Stroke>? = null // only for "sync"
)
