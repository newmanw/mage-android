package mil.nga.giat.mage.ui.observation.edit.dialog

import android.graphics.Point
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput

enum class DrawerMotionEvent { IDLE, DOWN, UP, MOVE }

data class CanvasDrawState(
   val currentPosition: Offset = Offset.Unspecified,
   val event: DrawerMotionEvent = DrawerMotionEvent.IDLE
)

@Composable
fun CanvasPath(onDrawingEnd: (List<Point>) -> Unit) {
   var state by remember { mutableStateOf(CanvasDrawState()) }
   val brush = remember { SolidColor(Color.Black) }
   val path = remember { Path() }
   Canvas(
      modifier = Modifier
         .fillMaxSize()
         .pointerInput(Unit) {
            awaitEachGesture {
               val screenPoints = mutableListOf<Point>()
               awaitPointerEvent().changes
                  .first()
                  .also { changes ->
                     val position = changes.position
                     screenPoints.add(position.toPoint())
                     state = state.copy(
                        currentPosition = position,
                        event = DrawerMotionEvent.DOWN
                     )
                  }
               do {
                  val event: PointerEvent = awaitPointerEvent()
                  event.changes.forEach { changes ->
                     val position = changes.position
                     screenPoints.add(position.toPoint())
                     state = state.copy(
                        currentPosition = position,
                        event = DrawerMotionEvent.MOVE
                     )
                  }
               } while (event.changes.any { it.pressed })

               currentEvent.changes
                  .first()
                  .also { change ->
                     state = state.copy(
                        currentPosition = change.position,
                        event = DrawerMotionEvent.UP
                     )
                     screenPoints.add(change.position.toPoint())
                  }
               onDrawingEnd.invoke(screenPoints)
            }
         },
      onDraw = {
         when (state.event) {
            DrawerMotionEvent.IDLE -> Unit
            DrawerMotionEvent.UP, DrawerMotionEvent.MOVE -> path.lineTo(
               state.currentPosition.x,
               state.currentPosition.y
            )

            DrawerMotionEvent.DOWN -> path.moveTo(
               state.currentPosition.x,
               state.currentPosition.y
            )
         }
         drawPath(
            path = path,
            brush = brush,
            style = Stroke(width = 5f)
         )
      }
   )
}

@Composable
fun CanvasBox(
   onDraw: (Pair<Point?, Point?>) -> Unit
) {
   var state by remember { mutableStateOf(CanvasDrawState()) }
   val brush = remember { SolidColor(Color.Black) }
   var topLeft = remember { Offset(0f, 0f) }
   var size = remember { Size(0f, 0f) }

   Canvas(
      modifier = Modifier
         .fillMaxSize()
         .pointerInput(Unit) {
            awaitEachGesture {
               var start: Point?
               var end: Point?
               awaitPointerEvent().changes
                  .first()
                  .also { changes ->
                     start = changes.position.toPoint()
                     state = state.copy(
                        currentPosition = changes.position,
                        event = DrawerMotionEvent.DOWN
                     )
                  }
               do {
                  val event: PointerEvent = awaitPointerEvent()
                  event.changes.forEach { changes ->
                     val position = changes.position
                     state = state.copy(
                        currentPosition = position,
                        event = DrawerMotionEvent.MOVE
                     )
                  }
               } while (event.changes.any { it.pressed })

               currentEvent.changes
                  .first()
                  .also { change ->
                     state = state.copy(
                        currentPosition = change.position,
                        event = DrawerMotionEvent.UP
                     )

                     end = change.position.toPoint()
                  }

               onDraw.invoke(Pair(start, end))
            }
         },
      onDraw = {
         when (state.event) {
            DrawerMotionEvent.IDLE -> Unit
            DrawerMotionEvent.UP, DrawerMotionEvent.MOVE -> {
               size = Size(
                  width = state.currentPosition.x - topLeft.x,
                  height = state.currentPosition.y - topLeft.y
               )
            }

            DrawerMotionEvent.DOWN -> {
               topLeft = Offset(state.currentPosition.x, state.currentPosition.y)
            }
         }

         drawRect(
            topLeft = topLeft,
            size = size,
            brush = brush,
            style = Stroke(width = 5f)
         )
      }
   )
}
