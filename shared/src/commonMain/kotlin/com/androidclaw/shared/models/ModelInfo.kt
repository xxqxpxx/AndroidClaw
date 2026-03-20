package com.androidclaw.shared.models

data class ModelInfo(
    val name: String,
    val fileName: String,
    val url: String,
    val sizeBytes: Long,
    val description: String
)

object WhisperModels {
    private const val HF_BASE = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main"

    val TINY_EN = ModelInfo(
        name = "tiny.en",
        fileName = "ggml-tiny.en.bin",
        url = "$HF_BASE/ggml-tiny.en.bin",
        sizeBytes = 77_691_713,
        description = "Tiny English-only model (~75MB). Fastest, lower accuracy."
    )

    val BASE_EN = ModelInfo(
        name = "base.en",
        fileName = "ggml-base.en.bin",
        url = "$HF_BASE/ggml-base.en.bin",
        sizeBytes = 147_951_465,
        description = "Base English-only model (~141MB). Good balance of speed and accuracy."
    )

    val BASE_EN_Q5 = ModelInfo(
        name = "base.en-q5_0",
        fileName = "ggml-base.en-q5_0.bin",
        url = "$HF_BASE/ggml-base.en-q5_0.bin",
        sizeBytes = 57_336_064,
        description = "Base English quantized model (~55MB). Smaller size, good accuracy."
    )

    val SMALL_EN = ModelInfo(
        name = "small.en",
        fileName = "ggml-small.en.bin",
        url = "$HF_BASE/ggml-small.en.bin",
        sizeBytes = 487_601_913,
        description = "Small English-only model (~465MB). Higher accuracy, slower."
    )

    val DEFAULT = BASE_EN_Q5

    val ALL = listOf(TINY_EN, BASE_EN_Q5, BASE_EN, SMALL_EN)
}
