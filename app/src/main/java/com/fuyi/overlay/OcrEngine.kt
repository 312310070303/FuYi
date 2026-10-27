package com.fuyi.overlay

import android.graphics.Bitmap
import android.graphics.RectF
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions

data class TextBlock(val rect: RectF, val text: String)

class OcrEngine {

    private val recognizer =
        TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())

    fun recognize(
        bitmap: Bitmap,
        onResult: (List<TextBlock>) -> Unit,
        onError: (String) -> Unit = {}
    ) {
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { text ->
                val blocks = ArrayList<TextBlock>()
                for (block in text.textBlocks) {
                    val box = block.boundingBox ?: continue
                    val content = block.lines.joinToString("\n") { it.text }
                    if (content.isBlank()) continue
                    blocks.add(TextBlock(RectF(box), content))
                }
                onResult(blocks)
            }
            .addOnFailureListener { error ->
                onError(error.message ?: "文字识别失败")
            }
    }

    fun close() {
        try {
            recognizer.close()
        } catch (_: Throwable) {
        }
    }

    companion object {
        /** 去掉日文/汉字之间被识别出来的空格，保留换行。 */
        fun clean(text: String): String {
            val cjk = "\\u3000-\\u30ff\\u3400-\\u9fff\\uff00-\\uffef"
            return text
                .replace(Regex("(?<=[$cjk])[ \\t]+(?=[$cjk])"), "")
                .trim()
        }
    }
}
