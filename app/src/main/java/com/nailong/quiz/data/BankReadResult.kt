package com.nailong.quiz.data

enum class ImportFormat { PDF, DOCX, TXT }

data class BankReadResult(
    val fileName: String,
    val sha256: String,
    val pages: List<PdfTextPage>,
    val format: ImportFormat = ImportFormat.PDF,
    val warnings: List<String> = emptyList()
)
