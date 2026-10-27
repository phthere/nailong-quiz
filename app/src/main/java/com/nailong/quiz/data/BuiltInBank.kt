package com.nailong.quiz.data

/** Each bank uses its own database, including progress, favorites and exams. */
interface QuizBank {
    val id: Int
    val title: String
    val count: Int
    val databaseName: String
}

enum class BuiltInBank(override val id: Int, override val title: String, val assetFile: String, override val count: Int) : QuizBank {
    ORIGINAL(1, "湘潭大学原题库", "questions.json", 1038),
    DANGZHANG(2, "党章题库", "questions_dangzhang.json", 357),
    DANGSHI(3, "党史题库", "questions_dangshi.json", 728);

    override val databaseName: String get() = "nailong_builtin_bank_${id}.db"

    companion object {
        val DEFAULT = DANGZHANG
        fun fromId(id: Int): BuiltInBank = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
