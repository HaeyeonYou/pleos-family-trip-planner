package io.github.haeyeonyou.familytrip.grow

import android.content.Context

/** 이미 축하(정차 중 시트 또는 주행 중 음성)한 캐릭터 탄생을 기기에 기억한다. 서버에는 올리지 않는다. */
class BirthStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("grow", Context.MODE_PRIVATE)

    fun celebrated(): Set<String> = prefs.getStringSet(KEY, emptySet()).orEmpty()

    fun markCelebrated(kinds: Collection<JellyKind>) {
        if (kinds.isEmpty()) return
        prefs.edit().putStringSet(KEY, celebrated() + kinds.map { it.name }).apply()
    }

    fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val KEY = "celebrated_births"
    }
}
