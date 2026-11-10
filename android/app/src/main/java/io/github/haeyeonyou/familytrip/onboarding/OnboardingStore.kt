package io.github.haeyeonyou.familytrip.onboarding

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 온보딩 완료 여부와 O03 선택 항목을 차에 저장한다 (SharedPreferences "onboarding", 서버로 보내지 않음).
 * 예전의 `AccountStore.skipped`(실행 중 메모리 값)와 달리 `나중에 하기`도 다음 실행까지 남는다 (QO7).
 */
class OnboardingStore(context: Context) {
    private val prefs = context.getSharedPreferences("onboarding", Context.MODE_PRIVATE)

    private val _choices = MutableStateFlow(readChoices())
    val choices: StateFlow<LinkChoices> = _choices.asStateFlow()

    val done: Boolean get() = prefs.getBoolean(KEY_DONE, false)

    /** O06 `홈으로`나 `나중에 하기`를 눌렀다. */
    fun markDone() = prefs.edit().putBoolean(KEY_DONE, true).apply()

    fun saveChoices(choices: LinkChoices) {
        prefs.edit().putBoolean(KEY_CLIMATE, choices.climate).putBoolean(KEY_VOICE, choices.voice).apply()
        _choices.value = choices
    }

    /** 데이터 전체 삭제: 다음 실행에 온보딩부터 다시. */
    fun clear() {
        prefs.edit().clear().apply()
        _choices.value = LinkChoices()
    }

    private fun readChoices() = LinkChoices(
        climate = prefs.getBoolean(KEY_CLIMATE, true),
        voice = prefs.getBoolean(KEY_VOICE, true),
    )

    private companion object {
        const val KEY_DONE = "done"
        const val KEY_CLIMATE = "climate"
        const val KEY_VOICE = "voice"
    }
}

/** O03에서 음성을 끄면 마이크 버튼만 숨긴다 (QO6). 음성 버튼을 그리는 곳이 읽는다. */
val LocalVoiceEnabled = staticCompositionLocalOf { true }
