package io.github.haeyeonyou.familytrip.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** 차에 로그인한 계정. 토큰은 이 차 전용 세션이라 로그아웃하면 서버에서 폐기된다. */
@Serializable
data class Account(val token: String, val user: AccountUser)

/**
 * 로그인 상태를 앱 내부 파일에 둔다 (다른 앱은 읽지 못함).
 * 로그인하지 않고도 코스 기능은 쓸 수 있고, 계정이 필요한 기능(폰 연동 등)만 로그인을 요구한다.
 */
class AccountStore(context: Context) {
    private val file = File(context.filesDir, "account.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val _account = MutableStateFlow(load())
    val account: StateFlow<Account?> = _account.asStateFlow()

    /** 이번 실행에서 로그인을 건너뛰었는지 (앱을 다시 켜면 다시 묻는다). */
    var skipped = false

    val token: String? get() = _account.value?.token

    fun save(account: Account) {
        file.writeText(json.encodeToString(account))
        _account.value = account
    }

    fun clear() {
        file.delete()
        _account.value = null
    }

    private fun load(): Account? = runCatching { json.decodeFromString<Account>(file.readText()) }.getOrNull()
}
