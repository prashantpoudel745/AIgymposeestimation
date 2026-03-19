package com.pragyan.frontendandroid.poselandmarker

/*
 * Copyright 2023 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.pragyan.frontendandroid.R
import com.pragyan.frontendandroid.domain.model.ExerciseType
import com.pragyan.frontendandroid.posture.PostureAnalyzer
import kotlin.math.max
import kotlin.math.min
import androidx.core.graphics.toColorInt

class OverlayView(context: Context?, attrs: AttributeSet?) :
    View(context, attrs) {

    private var results: PoseLandmarkerResult? = null
    private var pointPaint = Paint()
    private var linePaint = Paint()

    private var scaleFactor: Float = 1f
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1
    private var offsetX: Float = 0f
    private var offsetY: Float = 0f
    // for showing angle, warnings, rep counter, and form status
    private val postureAnalyzer = PostureAnalyzer(ExerciseType.BICEPS_CURL, "left")

    private var angle: Int? = null
    private var stage: String = ""
    private var form: String = ""
    private var warning: String = ""

    // Panel paints
    private lateinit var panelBgPaint: Paint
    private lateinit var panelBorderPaint: Paint
    private lateinit var rowBgPaint: Paint

    // Label paints
    private lateinit var labelPaint: Paint      // small muted category label
    private lateinit var valuePaint: Paint       // large value text
    private lateinit var accentGoodPaint: Paint  // green left-bar
    private lateinit var accentWarnPaint: Paint  // orange left-bar
    private lateinit var accentInfoPaint: Paint  // blue left-bar

    // Reusable rect
    private val rectF = RectF()

    // Layout constants (dp-independent; scale later if needed)
    private val MARGIN = 20f
    private val PANEL_WIDTH = 450f
    private val PANEL_RADIUS = 14f
    private val ROW_RADIUS = 8f
    private val ROW_GAP = 8f
    private val ACCENT_BAR = 5f
    private val LABEL_SIZE = 48f
    private val VALUE_SIZE = 48f
    private val SMALL_VALUE_SIZE = 28f

    init {
        initPaints()
    }

    fun clear() {
        results = null
        pointPaint.reset()
        linePaint.reset()
        invalidate()
        initPaints()
    }

    private fun initPaints() {
        linePaint.color =
            ContextCompat.getColor(context!!, R.color.teal_700)
        linePaint.strokeWidth = LANDMARK_STROKE_WIDTH
        linePaint.style = Paint.Style.STROKE

        pointPaint.color = Color.YELLOW
        pointPaint.strokeWidth = LANDMARK_STROKE_WIDTH
        pointPaint.style = Paint.Style.FILL


        // Panel background — semi-transparent black
        panelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(175, 0, 0, 0)
            style = Paint.Style.FILL
        }
        panelBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(40, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }

        // Row backgrounds
        rowBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        // Text paints
        labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = LABEL_SIZE
            style = Paint.Style.FILL
            isFakeBoldText = true
            letterSpacing = 0.12f
        }
        valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = VALUE_SIZE
            style = Paint.Style.FILL
            isFakeBoldText = true
        }

        // Accent bar paints
        accentGoodPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = "#27AE60".toColorInt()
            style = Paint.Style.FILL
        }
        accentWarnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = "#E67E22".toColorInt()
            style = Paint.Style.FILL
        }
        accentInfoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = "#2980B9".toColorInt()
            style = Paint.Style.FILL
        }
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        results?.let { poseLandmarkerResult ->
            for(landmark in poseLandmarkerResult.landmarks()) {
                for(normalizedLandmark in landmark) {
                    val visibility = normalizedLandmark.visibility().orElse(0f)
                    if (visibility > 0.5f) {
                        canvas.drawPoint(
                            (1f - normalizedLandmark.x()) * imageWidth * scaleFactor + offsetX, // Mirror the X coordinate
                            normalizedLandmark.y() * imageHeight * scaleFactor + offsetY,
                            pointPaint
                        )
                    }
                }

                PoseLandmarker.POSE_LANDMARKS.forEach { connection ->
                    // Get the starting and ending landmark of the connection
                    val startLm = poseLandmarkerResult.landmarks()[0][connection!!.start()]
                    val endLm = poseLandmarkerResult.landmarks()[0][connection.end()]

                    // Check if the starting and ending landmark is visible with confidence > 0.5
                    val startVisible = startLm.visibility().orElse(0f) > 0.5f
                    val endVisible = endLm.visibility().orElse(0f) > 0.5f

                    if (startVisible && endVisible) {

                        canvas.drawLine(
                            (1f - startLm.x()) * imageWidth * scaleFactor + offsetX,
                            startLm.y() * imageHeight * scaleFactor + offsetY,
                            (1f - endLm.x()) * imageWidth * scaleFactor + offsetX,
                            endLm.y() * imageHeight * scaleFactor + offsetY,
                            linePaint
                        )
                    }
                }
            }
        }

        // ---------- TEXT OVERLAY ----------
//        canvas.drawText(
//            "Angle: ${angle ?: "--"}",
//            50f,
//            80f,
//            textPaint
//        )
//
//        canvas.drawText(
//            "Stage: $stage",
//            50f,
//            150f,
//            textPaint
//        )
//
//        canvas.drawText(
//            "Reps: ${postureAnalyzer.counter}",
//            50f,
//            220f,
//            textPaint
//        )
//
//        canvas.drawText(
//            "Form: $form",
//            50f,
//            290f,
//            textPaint
//        )
//
//        if (warning.isNotEmpty()) {
//            canvas.drawText(
//                "Warning: $warning",
//                50f,
//                360f,
//                textPaint
//            )
//        }
        drawInfoPanel(canvas)
    }


    private fun drawInfoPanel(canvas: Canvas) {
        val panelX = MARGIN
        val panelY = MARGIN + 16f

        // Measure rows to compute total panel height
        val isFormGood = form.equals("good", ignoreCase = true)
        val hasWarning = warning.isNotEmpty()
        val rows = mutableListOf<PanelRow>()

        rows += PanelRow(
            label = "ANGLE",
            value = "${angle ?: "--"}°",
            sublabel = null,
            bgColor = Color.argb(200, 15, 50, 100),
            accentPaint = accentInfoPaint,
            textColor = Color.WHITE,
            labelColor = Color.WHITE,
            rowHeight = 88f
        )
        rows += PanelRow(
            label = "STAGE",
            value = "${stage.uppercase().ifEmpty { "--" }}",
            sublabel = null,
            bgColor = Color.argb(200, 15, 50, 100),
            accentPaint = accentInfoPaint,
            textColor = Color.WHITE,
            labelColor = Color.WHITE,
            rowHeight = 88f
        )
        rows += PanelRow(
            label = "REPS",
            value = "${postureAnalyzer.counter}",
            sublabel = null,
            bgColor = Color.argb(200, 15, 60, 35),
            accentPaint = accentGoodPaint,
            textColor = Color.WHITE,
            labelColor =Color.WHITE,
            rowHeight = 72f
        )
        rows += PanelRow(
            label = "FORM",
            value = if (isFormGood) "Good ✓" else "Fix form ✗",
            sublabel = null,
            bgColor = if (isFormGood) Color.argb(200, 15, 60, 35) else Color.argb(200, 80, 20, 20),
            accentPaint = if (isFormGood) accentGoodPaint else accentWarnPaint,
            textColor = if (isFormGood) "#2ECC71".toColorInt() else "#E74C3C".toColorInt(),
            labelColor = if (isFormGood) "#6FCF97".toColorInt() else "#F1948A".toColorInt(),
            rowHeight = 72f
        )
        if (hasWarning) {
            rows += PanelRow(
                label = "WARNING",
                value = null,
                sublabel =warning ,
                bgColor = Color.argb(210, 80, 40, 5),
                accentPaint = accentWarnPaint,
                textColor = "#F5A623".toColorInt(),
                labelColor = "#F39C12".toColorInt(),
                rowHeight = 140f
            )
        }

        val innerPad = 8f
        val totalHeight = innerPad + rows.sumOf { it.rowHeight.toDouble() }.toFloat() +
                (rows.size - 1) * ROW_GAP + innerPad

        // Clamp panel width so it never overflows screen
        val maxPanelWidth = width - MARGIN * 2
        val panelW = min(PANEL_WIDTH, maxPanelWidth)

        // Draw panel background
        rectF.set(panelX, panelY, panelX + panelW, panelY + totalHeight)
        canvas.drawRoundRect(rectF, PANEL_RADIUS, PANEL_RADIUS, panelBgPaint)
        canvas.drawRoundRect(rectF, PANEL_RADIUS, PANEL_RADIUS, panelBorderPaint)

        // Draw each row
        var rowY = panelY + innerPad
        for (row in rows) {
            drawPanelRow(canvas, row, panelX + innerPad, rowY, panelW - innerPad * 2)
            rowY += row.rowHeight + ROW_GAP
        }
    }

    private fun drawPanelRow(canvas: Canvas, row: PanelRow, x: Float, y: Float, w: Float) {
        val h = row.rowHeight

        // Row background
        rowBgPaint.color = row.bgColor
        rectF.set(x, y, x + w, y + h)
        canvas.drawRoundRect(rectF, ROW_RADIUS, ROW_RADIUS, rowBgPaint)

        // Left accent bar
        rectF.set(x, y + ROW_RADIUS / 2, x + ACCENT_BAR, y + h - ROW_RADIUS / 2)
        canvas.drawRoundRect(rectF, ACCENT_BAR / 2, ACCENT_BAR / 2, row.accentPaint)

        val textX = x + ACCENT_BAR + 12f

        // Label + Value in one line
        val baseY = y + h / 2 + LABEL_SIZE / 3

    // Draw label
        labelPaint.color = row.labelColor
        labelPaint.textSize = LABEL_SIZE
        canvas.drawText(row.label, textX, baseY, labelPaint)

        // Draw value right next to label
        row.value?.let {
            valuePaint.color = row.textColor
            valuePaint.textSize = VALUE_SIZE

            val labelWidth = labelPaint.measureText(row.label)
            val valueX = textX + labelWidth + 16f

            val clamped = clampText(it, w - (valueX - x) - 10f, valuePaint)
            canvas.drawText(clamped, valueX, baseY, valuePaint)
        }

        when {

            row.sublabel != null -> {
                // Warning text — wrap or truncate to fit width
                labelPaint.color = row.textColor
                labelPaint.textSize = SMALL_VALUE_SIZE + 2f
                val clamped = clampText(row.sublabel, w - ACCENT_BAR - 14f, labelPaint)
                canvas.drawText(clamped, textX, y + h - 14f, labelPaint)
            }
        }
    }

    /** Truncates text with ellipsis if it exceeds maxWidth for the given paint. */
    private fun clampText(text: String, maxWidth: Float, paint: Paint): String {
        if (maxWidth <= 0) return "…"
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 0 && paint.measureText(text.substring(0, end) + "…") > maxWidth) {
            end--
        }
        return text.substring(0, end) + "…"
    }

    fun setResults(
        poseLandmarkerResults: PoseLandmarkerResult,
        imageHeight: Int,
        imageWidth: Int,
        runningMode: RunningMode = RunningMode.IMAGE
    ) {
        results = poseLandmarkerResults

        this.imageHeight = imageHeight
        this.imageWidth = imageWidth

        if (poseLandmarkerResults.landmarks().isNotEmpty()) {

            val landmarks = poseLandmarkerResults.landmarks()[0]

            val (a, s, f) = postureAnalyzer.analyze(landmarks)

            angle = a
            stage = s
            form = f
            warning = postureAnalyzer.formIssues.joinToString(", ")
        }

        when (runningMode) {
            RunningMode.IMAGE,
            RunningMode.VIDEO -> {
                scaleFactor = min(width * 1f / imageWidth, height * 1f / imageHeight)
            }
            RunningMode.LIVE_STREAM -> {
                // PreviewView is in FILL_START mode. So we need to scale up the
                // landmarks to match with the size that the captured images will be
                // displayed.
                scaleFactor = max(width * 1f / imageWidth, height * 1f / imageHeight)
                offsetX = 0f  // FILL_START anchors to left
                offsetY = 0f  // FILL_START anchors to top
            }
        }

        invalidate()
    }

    // Data class for panel rows
    private data class PanelRow(
        val label: String,
        val value: String?,
        val sublabel: String?,
        val bgColor: Int,
        val accentPaint: Paint,
        val textColor: Int,
        val labelColor: Int,
        val rowHeight: Float
    )

    companion object {
        private const val LANDMARK_STROKE_WIDTH = 8F
    }
}