package io.github.haeyeonyou.familytrip.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.serializer
import retrofit2.HttpException
import java.io.File

/** [ServerSync.refresh] 결과: 반영을 마친 영역, 그리고 로그인이 풀렸는지. */
data class RefreshResult(val done: SyncChange, val unauthorized: Boolean = false)

/**
 * 차 앱 ↔ 서버 동기화의 입구. 방문 기록·수유·가족·프리셋을 각각의 규칙([VisitSync] 등)으로 맞춘다.
 *
 * - 로그인하지 않았으면 아무것도 하지 않는다 (기기에만 저장).
 * - [refresh]는 7초마다 도는 `/me/sync` 확인이 부른다: 표시가 바뀐 영역과, 못 올린 것이 남은 영역만 다룬다.
 * - 도착·수유·가족 정보 저장처럼 차에서 일어난 일은 바로 [kick]으로 올리고, 실패하면 [refresh]가 다시 올린다.
 * - 요청 실패는 로그만 남기고 앱을 멈추지 않는다.
 */
class ServerSync(
    context: Context,
    private val api: TripApi,
    private val account: AccountStore,
    private val visitStore: VisitStore,
    private val profileStore: ProfileStore,
    private val feedingStore: FeedingStore,
    private val scope: CoroutineScope,
) {
    private val dir = File(context.filesDir, "sync").apply { mkdirs() }
    private val owner = JsonFile(File(dir, "owner.json"), String.serializer()) { "" }

    private val visitState = JsonFile(File(dir, "visits_known.json"), knownVisitsSerializer) { emptyMap() }
    val family = FamilySync(
        TripApiFamilyRemote(api),
        profileStore,
        feedingStore.firstChild,
        JsonFile(File(dir, "family_snapshot.json"), serverChildrenSerializer) { emptyList() },
        childInterval = { id, minutes ->
            val r = feedingStore.loadFor(id)
            if (r.intervalMinutes != minutes) feedingStore.saveFor(id, r.copy(intervalMinutes = minutes))
        },
    )
    val visits = VisitSync(visitStore, TripApiVisitRemote(api), visitState)
    /** 자주 가는 케어 장소 (방문 기록에서 나온다, #184). 온보딩 O02가 [CarePlaceSync.cached]를 보여 준다. */
    val carePlaces = CarePlaceSync(
        visitStore,
        TripApiCarePlaceRemote(api),
        JsonFile(File(dir, "care_places_uploaded.json"), carePlaceListSerializer) { emptyList() },
        JsonFile(File(dir, "care_places.json"), carePlaceListSerializer) { emptyList() },
    )
    val feedings = FeedingSync(
        remote = TripApiFeedingRemote(api),
        queue = JsonFile(File(dir, "feeding_queue.json"), FeedingQueueData.serializer()) { FeedingQueueData() },
        routine = feedingStore,
        kind = { feedingKindOf(family.feedingTypeOf(feedingStore.selectedChildId.value)) },
        owner = { loginId() },
        childId = { feedingStore.selectedChildId.value },
    )
    val presetSync = PresetSync(
        TripApiPresetRemote(api),
        JsonFile(File(dir, "preset_outbox.json"), PresetOutbox.serializer()) { PresetOutbox() },
    )

    private val _allPresets = MutableStateFlow<List<PresetState>>(emptyList())
    private val _presets = MutableStateFlow<List<PresetState>>(emptyList())

    /** 가족 기본값과 아이마다의 프리셋 (QO5 아이별 케어 모드, #192). 온보딩 O05가 아이 카드마다 쓴다. */
    val allPresets: StateFlow<List<PresetState>> = _allPresets.asStateFlow()

    /** **지금 고른 아이**의 프리셋 슬롯 3개 (케어·공조가 쓴다). 로그인 전이거나 아직 못 받았으면 빈 목록. */
    val presets: StateFlow<List<PresetState>> = _presets.asStateFlow()

    /** 지금 고른 아이의 서버 id (모드 값의 주인). 서버와 아직 안 맞춘 아이면 null (가족 기본값). */
    fun presetChildId(): String? = presetChildId(profileStore.load()?.children.orEmpty(), feedingStore.selectedChildId.value)

    /** 받은 전체 목록에서 고른 아이 것을 [presets]로 낸다. 아이를 바꾸면 곧바로 부른다. */
    private fun publishPresets(all: List<PresetState> = _allPresets.value) {
        _allPresets.value = all
        _presets.value = presetsForChild(all, presetChildId())
    }

    private fun loginId(): String? = account.account.value?.user?.loginId
    private val loggedIn get() = account.token != null

    /** 차에서 일어난 변경을 바로 서버에 올리는 관찰을 시작한다. 앱이 켜져 있는 동안 계속된다. */
    fun start() {
        scope.launch { visitStore.revision.drop(1).collectLatest { kick { visits.reconcile() } } }
        scope.launch { account.account.collectLatest { if (it == null) publishPresets(emptyList()) } }
        // 아이 목록(서버 id)이 바뀌어도 고른 아이의 모드 값을 다시 고른다
        scope.launch { profileStore.profile.drop(1).collectLatest { publishPresets() } }
    }

    /** 방금 수유했다: 차에 이미 저장한 시각을 대기열에 넣고 바로 올린다. */
    fun onFed(fedAtMs: Long) {
        feedings.record(fedAtMs)
        kick { feedings.flush(); feedings.pull() }
    }

    /** 케어 대상 아이를 바꿨다: 그 아이의 모드 값으로 바로 바꾸고, 마지막 수유를 서버에서 받는다 (C07). */
    fun onChildSelected() {
        publishPresets()
        kick { feedings.flush(); feedings.pull() }
    }

    /** 차에서 고친 아이 정보·수유 간격을 서버에 올린다. */
    fun onFamilyEdited() = kick { family.reconcile() }

    /**
     * 데이터 전체 삭제 직전에 부른다: 서버와 맞춘 기록(방문·가족 스냅샷)과 못 올린 수유 대기열을 비운다.
     * 그러지 않으면 비운 차를 "차에서 지운 것"이나 올릴 것으로 오해할 수 있다. 서버 데이터는 건드리지 않는다.
     * 서버 데이터를 지우지 않았다면 다음 동기화 때 서버 값이 다시 내려온다.
     */
    fun forgetForWipe() {
        visits.forget()
        family.forget()
        carePlaces.forget()
        feedings.clearPending()
    }

    /** 서버의 프리셋을 지금 다시 받는다. */
    fun refreshPresets() = kick { publishPresets(presetSync.fetch()) }

    /**
     * 정차 중 고친 프리셋을 [childId] 아이(기본: 지금 고른 아이)의 슬롯에 서버로 저장한다.
     * 실패하면 대기열에 남아 다음 주기에 다시 올라가고 false를 돌려준다.
     */
    suspend fun savePresetEdit(slot: String, settings: ClimateSettings, childId: String? = presetChildId()): Boolean {
        if (!loggedIn) return false
        return try {
            publishPresets(presetSync.saveEdit(slot, childId, settings))
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "preset edit upload failed, will retry", e)
            false
        }
    }

    /** 차에서 적용했다고 서버에 알린다 (실패하면 남겨 두고 다시 시도). 화면의 슬롯도 바로 적용됨으로 바꾼다. */
    fun onPresetApplied(slot: String, id: String) {
        publishPresets(_allPresets.value.map { if (it.slot == slot && it.id == id) it.copy(pending = false) else it })
        presetSync.queueApplied(id)
        kick { presetSync.flush() }
    }

    /** 요청 하나를 백그라운드로 실행한다. 로그인하지 않았으면 건너뛰고, 실패는 로그만 남긴다 (남은 일은 다음 주기에 다시 한다). */
    private fun kick(block: suspend () -> Unit) {
        if (!loggedIn) return
        scope.launch {
            try {
                ensureOwner()
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "kick failed, will retry on next sync", e)
            }
        }
    }

    /**
     * 표시가 바뀐 영역([change])과 못 올린 것이 남은 영역을 맞춘다. 영역마다 따로 실행해 하나가 실패해도 나머지는 계속한다.
     * 반환한 [RefreshResult.done]에 든 영역만 호출한 쪽이 "반영 끝"으로 기억한다.
     */
    suspend fun refresh(change: SyncChange): RefreshResult {
        if (!loggedIn) return RefreshResult(done = SyncChange(true, true, true, true, true))
        ensureOwner()
        var unauthorized = false
        suspend fun area(name: String, needed: Boolean, block: suspend () -> Unit): Boolean {
            if (unauthorized) return false
            if (!needed) return true
            return try {
                block()
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                if (e.code() == 401) unauthorized = true else Log.w(TAG, "$name sync failed: ${e.code()}")
                false
            } catch (e: Exception) {
                Log.w(TAG, "$name sync failed", e)
                false
            }
        }
        // 가족 → 수유 → 방문 → 프리셋 순서. 수유가 가족 정보(수유 방식·간격)를 쓰기 때문이다.
        val familyDone = area("family", change.family || family.hasPendingUpload()) { family.reconcile() }
        val feedingsDone = area("feedings", change.feedings || feedings.hasPending()) {
            feedings.flush()
            feedings.pull()
        }
        val visitsDone = area("visits", change.visits || visits.hasPendingUpload()) { visits.reconcile() }
        // 케어 장소는 방문 기록을 따라간다. 실패해도(예: 아직 이 API가 없는 서버) 방문 영역의 '반영 끝'은 막지 않는다.
        area("care places", visitsDone && (change.visits || carePlaces.hasPendingUpload())) { carePlaces.reconcile() }
        val presetsDone = area("presets", change.presets || presetSync.hasPending()) { publishPresets(presetSync.fetch()) }
        return RefreshResult(
            done = SyncChange(
                presets = presetsDone,
                feedings = feedingsDone,
                visits = visitsDone,
                family = familyDone,
                cars = true,
            ),
            unauthorized = unauthorized,
        )
    }

    /**
     * 로그인한 계정이 이전과 다르면 서버와 맞춘 기록(서버 id, 올린 목록)을 비운다. 다른 가족의 데이터가 섞이지 않게 하기 위해서다.
     * 차 기기에 쌓인 방문·수유·가족 정보 자체는 지우지 않는다.
     */
    private fun ensureOwner() {
        val id = loginId() ?: return
        val saved = owner.load()
        if (saved == id) return
        if (saved.isNotEmpty()) {
            Log.i(TAG, "계정이 바뀌어 서버와 맞춘 기록을 비웁니다")
            visits.forget()
            family.forget()
            carePlaces.forget()
            presetSync.forget()
            publishPresets(emptyList())
            profileStore.load()?.let { p -> profileStore.save(p.copy(children = p.children.map { it.copy(serverId = null) })) }
        }
        feedings.onAccountChanged(id)
        owner.save(id)
    }

    private companion object {
        const val TAG = "ServerSync"
    }
}
