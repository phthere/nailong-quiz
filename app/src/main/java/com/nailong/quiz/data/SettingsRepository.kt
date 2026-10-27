package com.nailong.quiz.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

enum class ThemeBg(val key: String, val zh: String) {
    DEEP_SEA("deep_sea", "深海奶龙"),
    CLOUD("cloud", "云端奶龙"),
    SKY_MIRROR("sky_mirror", "天空之镜");

    companion object {
        fun fromKey(k: String) = entries.firstOrNull { it.key == k } ?: CLOUD
    }
}

enum class DarkMode(val zh: String) { SYSTEM("跟随系统"), LIGHT("浅色"), DARK("深色") }

data class AppSettings(
    val selectedBank: Int = BuiltInBank.DEFAULT.id,
    val themeBg: ThemeBg = ThemeBg.CLOUD,
    val darkMode: DarkMode = DarkMode.SYSTEM,
    val bgBlur: Float = 0f,            // 0..1 模糊程度
    val scrimAlpha: Float = 0.35f,     // 遮罩深浅 0..0.8
    val bgHomeOnly: Boolean = true,    // 仅首页显示背景
    val fontScale: Float = 1.0f,       // 0.85/1.0/1.15/1.3
    val immediateFeedback: Boolean = true,
    val shuffleOptions: Boolean = false,
    val sound: Boolean = false,
    val vibrate: Boolean = true,
    val encouragement: Boolean = true,
    val masteryThreshold: Int = 2,     // 连续答对 N 次标记已掌握
    val examDurationMin: Int = 60,
    val examQuestionCount: Int = 50,
    val dailyGoal: Int = 30
)

class SettingsRepository(private val context: Context) {

    private object K {
        val SELECTED_BANK = intPreferencesKey("selected_bank")
        val THEME_BG = stringPreferencesKey("theme_bg")
        val DARK_MODE = stringPreferencesKey("dark_mode")
        val BG_BLUR = floatPreferencesKey("bg_blur")
        val SCRIM = floatPreferencesKey("scrim_alpha")
        val BG_HOME_ONLY = booleanPreferencesKey("bg_home_only")
        val FONT_SCALE = floatPreferencesKey("font_scale")
        val IMMEDIATE = booleanPreferencesKey("immediate_feedback")
        val SHUFFLE_OPTS = booleanPreferencesKey("shuffle_options")
        val SOUND = booleanPreferencesKey("sound")
        val VIBRATE = booleanPreferencesKey("vibrate")
        val ENCOURAGE = booleanPreferencesKey("encouragement")
        val MASTERY = intPreferencesKey("mastery_threshold")
        val EXAM_DUR = intPreferencesKey("exam_duration_min")
        val EXAM_COUNT = intPreferencesKey("exam_question_count")
        val DAILY_GOAL = intPreferencesKey("daily_goal")
        val SEQ_INDEX = intPreferencesKey("seq_index")
    }

    val seqIndex: Flow<Int> = context.dataStore.data.map { p ->
        val bankId = p[K.SELECTED_BANK] ?: BuiltInBank.DEFAULT.id
        p[intPreferencesKey("seq_index_$bankId")] ?: 0
    }

    suspend fun saveSeqIndex(index: Int, bankId: Int) {
        context.dataStore.edit { it[intPreferencesKey("seq_index_$bankId")] = index }
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            selectedBank = (p[K.SELECTED_BANK] ?: BuiltInBank.DEFAULT.id),
            themeBg = ThemeBg.fromKey(p[K.THEME_BG] ?: ThemeBg.CLOUD.key),
            darkMode = runCatching { DarkMode.valueOf(p[K.DARK_MODE] ?: "SYSTEM") }.getOrDefault(DarkMode.SYSTEM),
            bgBlur = p[K.BG_BLUR] ?: 0f,
            scrimAlpha = p[K.SCRIM] ?: 0.35f,
            bgHomeOnly = p[K.BG_HOME_ONLY] ?: true,
            fontScale = p[K.FONT_SCALE] ?: 1.0f,
            immediateFeedback = p[K.IMMEDIATE] ?: true,
            shuffleOptions = p[K.SHUFFLE_OPTS] ?: false,
            sound = p[K.SOUND] ?: false,
            vibrate = p[K.VIBRATE] ?: true,
            encouragement = p[K.ENCOURAGE] ?: true,
            masteryThreshold = p[K.MASTERY] ?: 2,
            examDurationMin = p[K.EXAM_DUR] ?: 60,
            examQuestionCount = p[K.EXAM_COUNT] ?: 50,
            dailyGoal = p[K.DAILY_GOAL] ?: 30
        )
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { p ->
            val cur = AppSettings(
                selectedBank = (p[K.SELECTED_BANK] ?: BuiltInBank.DEFAULT.id),
            themeBg = ThemeBg.fromKey(p[K.THEME_BG] ?: ThemeBg.CLOUD.key),
                darkMode = runCatching { DarkMode.valueOf(p[K.DARK_MODE] ?: "SYSTEM") }.getOrDefault(DarkMode.SYSTEM),
                bgBlur = p[K.BG_BLUR] ?: 0f,
                scrimAlpha = p[K.SCRIM] ?: 0.35f,
                bgHomeOnly = p[K.BG_HOME_ONLY] ?: true,
                fontScale = p[K.FONT_SCALE] ?: 1.0f,
                immediateFeedback = p[K.IMMEDIATE] ?: true,
                shuffleOptions = p[K.SHUFFLE_OPTS] ?: false,
                sound = p[K.SOUND] ?: false,
                vibrate = p[K.VIBRATE] ?: true,
                encouragement = p[K.ENCOURAGE] ?: true,
                masteryThreshold = p[K.MASTERY] ?: 2,
                examDurationMin = p[K.EXAM_DUR] ?: 60,
                examQuestionCount = p[K.EXAM_COUNT] ?: 50,
                dailyGoal = p[K.DAILY_GOAL] ?: 30
            )
            val n = transform(cur)
            p[K.SELECTED_BANK] = n.selectedBank
            p[K.THEME_BG] = n.themeBg.key
            p[K.DARK_MODE] = n.darkMode.name
            p[K.BG_BLUR] = n.bgBlur
            p[K.SCRIM] = n.scrimAlpha
            p[K.BG_HOME_ONLY] = n.bgHomeOnly
            p[K.FONT_SCALE] = n.fontScale
            p[K.IMMEDIATE] = n.immediateFeedback
            p[K.SHUFFLE_OPTS] = n.shuffleOptions
            p[K.SOUND] = n.sound
            p[K.VIBRATE] = n.vibrate
            p[K.ENCOURAGE] = n.encouragement
            p[K.MASTERY] = n.masteryThreshold
            p[K.EXAM_DUR] = n.examDurationMin
            p[K.EXAM_COUNT] = n.examQuestionCount
            p[K.DAILY_GOAL] = n.dailyGoal
        }
    }
}
