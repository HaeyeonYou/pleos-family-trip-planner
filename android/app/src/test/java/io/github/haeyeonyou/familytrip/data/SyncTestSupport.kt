package io.github.haeyeonyou.familytrip.data

import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import java.io.File
import java.nio.file.Files

/** 단위 테스트용 도우미: 임시 폴더, 가짜 서버 오류, 메모리 저장소. */
fun tempDir(): File = Files.createTempDirectory("sync-test").toFile().apply { deleteOnExit() }

fun httpError(code: Int) = HttpException(Response.error<Any>(code, "{}".toResponseBody()))

class MemoryRoutine(var routine: FeedingRoutine = FeedingRoutine()) : FeedingRoutineStore {
    override fun load() = routine
    override fun save(routine: FeedingRoutine) {
        this.routine = routine
    }
}

class MemoryProfile(var profile: FamilyProfile? = null) : ProfileRepo {
    override fun load() = profile
    override fun save(profile: FamilyProfile) {
        this.profile = profile
    }
}

fun outing(id: Long, name: String = "서울숲", key: String = "tour:1", theme: String? = "nature", day: String = "2026-11-05", depart: String? = null) =
    Visit(id = id, placeName = name, role = "outing", theme = theme, address = "서울 성동구 비밀주소 1", placeKey = key, arrivedAt = "${day}T10:00:00", departedAt = depart)
