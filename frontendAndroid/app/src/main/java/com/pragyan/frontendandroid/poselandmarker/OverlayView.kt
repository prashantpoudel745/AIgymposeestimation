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

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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

    private lateinit var textPaint: Paint

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

        // to show status
        textPaint = Paint()
        textPaint.color = Color.WHITE
        textPaint.textSize = 60f
        textPaint.style = Paint.Style.FILL
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
        canvas.drawText(
            "Angle: ${angle ?: "--"}",
            50f,
            80f,
            textPaint
        )

        canvas.drawText(
            "Stage: $stage",
            50f,
            150f,
            textPaint
        )

        canvas.drawText(
            "Reps: ${postureAnalyzer.counter}",
            50f,
            220f,
            textPaint
        )

        canvas.drawText(
            "Form: $form",
            50f,
            290f,
            textPaint
        )

        if (warning.isNotEmpty()) {
            canvas.drawText(
                "Warning: $warning",
                50f,
                360f,
                textPaint
            )
        }
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

    companion object {
        private const val LANDMARK_STROKE_WIDTH = 8F
    }
}