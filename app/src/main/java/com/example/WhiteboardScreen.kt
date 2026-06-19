package com.example

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun WhiteboardScreen(viewModel: WhiteboardViewModel = viewModel()) {
    val paths by viewModel.paths.collectAsState()
    val currentPath by viewModel.currentPath.collectAsState()
    val currentProperties by viewModel.currentProperties.collectAsState()
    val ipAddress by viewModel.ipAddress.collectAsState()

    val colors = listOf(
        Color.Black,
        Color(0xFFE53935), // Red
        Color(0xFF1E88E5), // Blue
        Color(0xFF43A047), // Green
        Color(0xFFFDD835), // Yellow
        Color.White        // Eraser basically
    )

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F5))) {
        
        // Drawing Canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val normX = offset.x / size.width
                            val normY = offset.y / size.height
                            viewModel.startStroke(offset.x, offset.y, normX, normY)
                        },
                        onDrag = { change, _ ->
                            val offset = change.position
                            val normX = offset.x / size.width
                            val normY = offset.y / size.height
                            viewModel.drawStroke(offset.x, offset.y, normX, normY)
                        },
                        onDragEnd = {
                            viewModel.endStroke()
                        },
                        onDragCancel = {
                            viewModel.endStroke()
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = { offset ->
                            val normX = offset.x / size.width
                            val normY = offset.y / size.height
                            viewModel.startStroke(offset.x, offset.y, normX, normY)
                            viewModel.drawStroke(offset.x, offset.y, normX, normY)
                            viewModel.endStroke()
                        }
                    )
                }
        ) {
            paths.forEach { drawnPath ->
                drawPath(
                    path = drawnPath.path,
                    color = drawnPath.properties.color,
                    style = Stroke(
                        width = drawnPath.properties.strokeWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
            
            currentPath?.let { drawnPath ->
                drawPath(
                    path = drawnPath.path,
                    color = drawnPath.properties.color,
                    style = Stroke(
                        width = drawnPath.properties.strokeWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }

        // Top Status Bar
        Card(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
                .statusBarsPadding(),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🌐 Connect via Laptop:",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (ipAddress != null) "http://$ipAddress:8080" else "Connecting...",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Bottom Toolbar
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .navigationBarsPadding(),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Colors
                colors.forEach { color ->
                    val isSelected = currentProperties.color == color
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 48.dp else 40.dp)
                            .clip(CircleShape)
                            .background(color)
                            .clickable {
                                viewModel.setColor(color)
                                // Standardize widths: eraser is thicker
                                if (color == Color.White) {
                                    viewModel.setStrokeWidth(30f)
                                } else {
                                    viewModel.setStrokeWidth(10f)
                                }
                            }
                    ) {
                        if (color == Color.White) {
                            // Eraser icon
                            Icon(
                                imageVector = Icons.Default.Edit, // Or a custom eraser icon
                                contentDescription = "Eraser",
                                modifier = Modifier.align(Alignment.Center).size(20.dp),
                                tint = Color.Gray
                            )
                        }
                        if (isSelected && color != Color.White) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(4.dp)
                                    .clip(CircleShape)
                                    .background(Color.Transparent)
                            ) {
                                // Inner dot or outline to show selection
                            }
                        }
                    }
                }
                
                Divider(
                    modifier = Modifier.height(30.dp).width(1.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                // Clear Button
                IconButton(
                    onClick = { viewModel.clearBoard() },
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.errorContainer, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear Board",
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}
