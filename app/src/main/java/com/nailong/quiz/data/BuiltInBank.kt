package com.nailong.quiz.data

/** Each bank uses its own database, including progress, favorites and exams. */
enum class BuiltInBank(val id: Int, val title: String, val assetFile: String, val count: Int) {
    ORIGINAL(1, "湘潭大学原题库", "questions.json", 1038),
    DANGZHANG(2, "党章题库", "questions_dangzhang.json", 357),
    DANGSHI(3, "党史题库", "questions_dangshi.json", 728);

    val databaseName: String get() = "nailong_builtin_bank_${id}.db"

    companion object {
        val DEFAULT = DANGZHANG
        fun fromId(id: Int): BuiltInBank = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
