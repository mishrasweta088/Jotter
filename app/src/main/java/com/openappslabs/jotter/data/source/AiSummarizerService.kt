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
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class BenchmarkResult(
    val actionType: String,
    val latencyMs: Long,
    val tokensPerSec: Double,
    val heapBeforeMb: Long,
    val heapAfterMb: Long,
    val timestamp: Long = System.currentTimeMillis()
)

@Singleton
class AiSummarizerService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "AiSummarizerService"
    private val modelPath = "/data/local/tmp/llm/model.task"
    private var llmInference: LlmInference? = null

    private fun getHeapUsageMb(): Long {
        val runtime = Runtime.getRuntime()
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
    }

    private fun initializeInference() {
        if (llmInference == null) {
            Log.d(TAG, "Initializing offline LLM with model path: $modelPath")
            val modelFile = File(modelPath)
            if (modelFile.exists()) {
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelPath)
                    .build()

                llmInference = LlmInference.createFromOptions(context, options)
                Log.d(TAG, "Offline LLM initialized successfully with model path: $modelPath")
            }
        }
    }

    suspend fun summarizeWithBenchmark(noteText: String): Pair<String, BenchmarkResult> = withContext(Dispatchers.IO) {
        val heapBefore = getHeapUsageMb()
        val startTime = System.currentTimeMillis()
        
        val result = summarize(noteText)
        
        val endTime = System.currentTimeMillis()
        val heapAfter = getHeapUsageMb()
        
        val latency = endTime - startTime
        val estimatedTokens = result.length / 4.0
        val tps = if (latency > 0) (estimatedTokens / (latency / 1000.0)) else 0.0
        
        val benchmark = BenchmarkResult(
            actionType = "Summarize",
            latencyMs = latency,
            tokensPerSec = tps,
            heapBeforeMb = heapBefore,
            heapAfterMb = heapAfter
        )
        
        Pair(result, benchmark)
    }

    suspend fun rewriteWithBenchmark(noteText: String, style: String): Pair<String, BenchmarkResult> = withContext(Dispatchers.IO) {
        val heapBefore = getHeapUsageMb()
        val startTime = System.currentTimeMillis()
        
        val result = rewrite(noteText, style)
        
        val endTime = System.currentTimeMillis()
        val heapAfter = getHeapUsageMb()
        
        val latency = endTime - startTime
        val estimatedTokens = result.length / 4.0
        val tps = if (latency > 0) (estimatedTokens / (latency / 1000.0)) else 0.0
        
        val benchmark = BenchmarkResult(
            actionType = "Rewrite ($style)",
            latencyMs = latency,
            tokensPerSec = tps,
            heapBeforeMb = heapBefore,
            heapAfterMb = heapAfter
        )
        
        Pair(result, benchmark)
    }

    private suspend fun summarize(noteText: String): String {
        initializeInference()
        if (llmInference == null) {
            return generatePlaceholderSummary(noteText)
        }
        
        val prompt = """
            Summarize the text below into exactly 3 short, direct bullet points.
            Rules:
            - Exactly 3 bullets starting with "- ".
            - Each bullet: 8 to 14 words long.
            - Simple, plain language. No meta-talk like "Here is a summary".
            - Ignore checklist labels or section headers.
            
            Text:
            $noteText
        """.trimIndent()

        return try {
            Log.d(TAG, "Offline LLM used for summarize with model path: $modelPath")
            val response = llmInference!!.generateResponse(prompt)
            val result = normalizeSummaryOutput(response, noteText)
            result
        } catch (e: Exception) {
            Log.e(TAG, "Summarize failed: ${e.message}")
            generatePlaceholderSummary(noteText)
        }
    }

    private fun normalizeSummaryOutput(raw: String, originalText: String): String {
        val metaJunk = listOf("here is", "here's", "sure", "summary", "this note", "bullet points", "below is", "distilled")
        val labels = listOf("offline setup", "summarizer", "tone changer", "error handling", "benchmarking", "latency", "tokens", "memory")

        val cleanRaw = raw
            .replace("\\n", "\n")
            .replace("\\\"", "\"")
            .trim()

        val lines = cleanRaw.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { it.replace(Regex("[*_~#]"), "").replace(Regex("^[-*•\\d+.)\\s]+"), "").trim() }
            .filter { line ->
                val lower = line.lowercase()
                line.length > 5 &&
                        metaJunk.none { lower.startsWith(it) } &&
                        !lower.endsWith(":") &&
                        labels.none { lower.startsWith(it) && line.length < it.length + 5 }
            }

        var points = lines.toMutableList()

        if (points.size < 3 && points.isNotEmpty()) {
            points = points.flatMap { it.split(Regex("(?<=[.!?])\\s+")) }
                .map { it.trim().replace(Regex("^[-*•\\d+.)\\s]+"), "").trim() }
                .filter { it.length > 10 }
                .toMutableList()
        }

        if (points.isEmpty()) {
            points.addAll(
                originalText.lines()
                    .map { it.trim() }
                    .filter { it.length > 15 && !it.endsWith(":") }
                    .take(3)
            )
        }

        val final = points.take(3).map { s ->
            val words = s.split(Regex("\\s+"))
            if (words.size > 14) words.take(14).joinToString(" ") else s
        }.toMutableList()

        while (final.size < 3) {
            if (final.isNotEmpty()) final.add(final.last())
            else final.add("Review note for key information")
        }

        return final.take(3).joinToString("\n") { "- ${it.trimEnd('.', ' ')}" }
    }

    private suspend fun rewrite(noteText: String, style: String): String {
        initializeInference()
        if (llmInference == null) {
            return generatePlaceholderRewrite(noteText)
        }
        
        val prompt = """
            Rewrite the following note in a $style tone.

            Rules:
            - Preserve the original meaning exactly.
            - Do not add new ideas, headings, explanations, or examples.
            - Do not summarize.
            - Do not expand the content.
            - Keep the output close to the original length.
            - Return only the rewritten note text.
            - Remove markdown symbols, bullet decoration, and quotes.
            - Keep line breaks only where natural.

            Text:
            $noteText
        """.trimIndent()

        return try {
            Log.d(TAG, "Offline LLM used for rewrite with model path: $modelPath")
            val response = llmInference!!.generateResponse(prompt)
            val result = normalizeRewriteOutput(response, noteText)
            result
        } catch (e: Exception) {
            Log.e(TAG, "Rewrite failed: ${e.message}")
            generatePlaceholderRewrite(noteText)
        }
    }

    private fun normalizeRewriteOutput(raw: String, originalText: String): String {
        val metaJunk = listOf(
            "here is", "here's", "improved version", "rewritten", "below is",
            "professional style", "academic style", "style rewrite",
            "polished version", "rewriting:", "rewritten version:"
        )

        var clean = raw.trim()
        if (clean.startsWith("\"") && clean.endsWith("\"")) {
            clean = clean.substring(1, clean.length - 1).trim()
        }

        clean = clean
            .replace("\\n", "\n")
            .replace("\\r", "")
            .replace("\\\"", "\"")
            .replace(Regex("[*#~]"), "")
            .trim()

        val lines = clean.lines()
            .map { it.trimEnd() }
            .filterIndexed { index, line ->
                val lower = line.lowercase().trim()
                val isMeta = index < 3 && metaJunk.any { lower.startsWith(it) }
                !isMeta
            }

        val result = lines.joinToString("\n").trim()

        return result
    }

    private suspend fun generatePlaceholderSummary(content: String): String {
        delay(1000)
        val lines = content.lines()
            .map { it.trim().replace(Regex("^[-*•\\d+.)\\s]+"), "").trim() }
            .filter { it.isNotBlank() }
            .take(3)
        val result = lines.toMutableList()
        while (result.size < 3) {
            result.add("Key point ${result.size + 1} from note")
        }
        return result.joinToString("\n") { "- $it" }
    }

    private suspend fun generatePlaceholderRewrite(content: String): String {
        delay(1000)
        val cleaned = content.replace(Regex("[*#~]"), "").trim()
        return cleaned
    }
}
