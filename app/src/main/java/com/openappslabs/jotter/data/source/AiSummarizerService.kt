/*
 * Copyright (c) 2026 Open Apps Labs
 *
 * This file is part of Jotter
 *
 * Jotter is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * Jotter is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Jotter.
 * If not, see <https://www.gnu.org/licenses/>.
 */

package com.openappslabs.jotter.data.source

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiSummarizerService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val modelPath = "/data/local/tmp/llm/model.task"
    private var llmInference: LlmInference? = null

    private fun initializeInference() {
        if (llmInference == null) {
            val modelFile = File(modelPath)
            if (modelFile.exists()) {
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelPath)
                    .build()

                llmInference = LlmInference.createFromOptions(context, options)
            }
        }
    }

    suspend fun summarize(noteText: String): String = withContext(Dispatchers.IO) {
        try {
            initializeInference()

            if (llmInference == null) {
                android.util.Log.e("SUMMARY", "LlmInference is null. Falling back.")
                return@withContext generatePlaceholderSummary(noteText)
            }

            android.util.Log.d("SUMMARY", "Offline LLM used. model=$modelPath")

            val prompt = """
            You are an offline note summarizer.
            Summarize into EXACTLY 3 bullet points.
            Return only bullets.

            Note:
            $noteText
        """.trimIndent()

            val result = llmInference!!.generateResponse(prompt)

            if (result.isBlank()) {
                android.util.Log.e("SUMMARY", "Empty response. Falling back.")
                return@withContext generatePlaceholderSummary(noteText)
            }

            return@withContext result
        } catch (e: Exception) {
            android.util.Log.e("SUMMARY", "Error: ${e.message}", e)
            return@withContext generatePlaceholderSummary(noteText)
        }
    }

    suspend fun rewrite(noteText: String, style: String): String = withContext(Dispatchers.IO) {
        try {
            initializeInference()

            if (llmInference == null) {
                android.util.Log.e("REWRITE", "LlmInference is null. Falling back.")
                return@withContext generatePlaceholderRewrite(noteText, style)
            }

            val prompt = """
            You are a helpful assistant. Rewrite the following text in a $style tone.
            Keep the original meaning but change the style.
            Return only the rewritten text.

            Text:
            $noteText
        """.trimIndent()

            android.util.Log.d("REWRITE", "Offline LLM used. model=$modelPath style=$style")

            val result = llmInference!!
                .generateResponse(prompt)
                .replace("\\n", "\n")
                .replace("\\\"", "\"")

            if (result.isBlank()) {
                return@withContext generatePlaceholderRewrite(noteText, style)
            }

            return@withContext result
        } catch (e: Exception) {
            android.util.Log.e("REWRITE", "Error: ${e.message}", e)
            return@withContext generatePlaceholderRewrite(noteText, style)
        }
    }

    private suspend fun generatePlaceholderSummary(content: String): String {
        delay(1500) // Simulate processing time
        val lines = content.lines().map { it.trim() }.filter { it.isNotBlank() }.take(3)
        val paddedLines = lines.toMutableList()
        for (i in lines.size until 3) {
            paddedLines.add("Key point ${i + 1} from note")
        }
        return paddedLines.joinToString("\n") { "• $it" }
    }

    private suspend fun generatePlaceholderRewrite(content: String, style: String): String {
        delay(1500)
        return "[Placeholder $style Rewrite]:\n$content"
    }
}
