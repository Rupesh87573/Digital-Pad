package com.example

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PathProperties(
    val color: Color = Color.Black,
    val strokeWidth: Float = 10f
)

data class DrawnPath(
    val path: Path,
    val properties: PathProperties
)

class WhiteboardViewModel : ViewModel() {

    private val server = WhiteboardServer()

    private val _paths = MutableStateFlow<List<DrawnPath>>(emptyList())
    val paths = _paths.asStateFlow()

    private val _currentPath = MutableStateFlow<DrawnPath?>(null)
    val currentPath = _currentPath.asStateFlow()

    private val _currentProperties = MutableStateFlow(PathProperties())
    val currentProperties = _currentProperties.asStateFlow()

    private val _ipAddress = MutableStateFlow<String?>(null)
    val ipAddress = _ipAddress.asStateFlow()

    init {
        server.start()
        _ipAddress.value = WhiteboardServer.getLocalIpAddress()
    }

    override fun onCleared() {
        super.onCleared()
        server.stop()
    }

    fun setColor(color: Color) {
        _currentProperties.value = _currentProperties.value.copy(color = color)
    }

    fun setStrokeWidth(width: Float) {
        _currentProperties.value = _currentProperties.value.copy(strokeWidth = width)
    }

    fun clearBoard() {
        _paths.value = emptyList()
        _currentPath.value = null
        viewModelScope.launch {
            server.clear()
        }
    }

    // Coordinates should come in as 0.0 .. 1.0 (normalized)
    // But for local drawing, we also need absolute pixels.
    // So we pass BOTH absolute (for local Path) and normalized (for Server).
    
    // Actually, local Path is easier to be built with absolute pixels.
    fun startStroke(absX: Float, absY: Float, normX: Float, normY: Float) {
        val path = Path().apply {
            moveTo(absX, absY)
        }
        val properties = _currentProperties.value
        _currentPath.value = DrawnPath(path, properties)
        
        viewModelScope.launch {
            val hexColor = String.format("#%06X", 0xFFFFFF and properties.color.value.toInt())
            server.startStroke(normX, normY, hexColor, properties.strokeWidth)
        }
    }

    fun drawStroke(absX: Float, absY: Float, normX: Float, normY: Float) {
        _currentPath.value?.path?.lineTo(absX, absY)
        // Force state update for UI to recompose
        _currentPath.value = _currentPath.value?.copy()

        viewModelScope.launch {
            server.drawStroke(normX, normY)
        }
    }

    fun endStroke() {
        _currentPath.value?.let { current ->
            _paths.value = _paths.value + current
        }
        _currentPath.value = null

        viewModelScope.launch {
            server.endStroke()
        }
    }
}
