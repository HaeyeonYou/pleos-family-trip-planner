package io.github.haeyeonyou.familytrip.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** 저장한 코스. 다시 안내하거나(#51) 같은 조건으로 다시 계산할 때 쓴다. */
@Serializable
data class SavedCourse(
    val id: Long,
    /** 저장 시각 (epoch ms) */
    val savedAt: Long,
    val title: String,
    val conditions: TripConditions,
    val origin: LatLng,
    val course: CourseResponse,
)

/**
 * 저장한 코스를 차 안(앱 내부 파일)에만 둔다. 서버로 보내지 않는다.
 * 양이 적어 JSON 파일 하나로 충분하다.
 */
class CourseStore(context: Context) {
    private val file = File(context.filesDir, "saved_courses.json")
    private val json = Json { ignoreUnknownKeys = true }

    fun all(): List<SavedCourse> =
        runCatching { json.decodeFromString<List<SavedCourse>>(file.readText()) }.getOrDefault(emptyList())

    fun save(course: SavedCourse) {
        val courses = listOf(course) + all().filter { it.id != course.id }
        file.writeText(json.encodeToString(courses.take(MAX_COURSES)))
    }

    private companion object {
        const val MAX_COURSES = 20
    }
}
