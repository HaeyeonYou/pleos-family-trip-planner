package io.github.haeyeonyou.familytrip.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.haeyeonyou.familytrip.vehicle.GearState
import io.github.haeyeonyou.familytrip.vehicle.VehicleState

/**
 * 정차(Drive Off) / 주행(Drive On) 상태. 화면은 `isParked`를 따로 받지 말고 [LocalDriveMode]를 읽는다.
 *
 * '정차'의 정의는 한 곳, [VehicleState.isParked](기어 P단)에만 있다. 11/10 디자인 Q01(P단인지 주차 브레이크인지)이
 * 정해지면 그곳만 고치면 되고, 여기는 그 값을 그대로 옮긴다 (기존 앱 기준을 바꾸지 않았다).
 * 기어를 읽지 못한 상태(UNKNOWN)는 지금까지처럼 주행으로 본다.
 */
@Immutable
data class DriveMode(
    val gear: GearState,
    /**
     * 차속(km/h). Pleos SDK에는 차속 API가 없어(docs/research/pleos-sdk.md) 지금은 항상 null이다.
     * 정차·주행 판단에는 쓰지 않고, API가 생기면 표시용으로만 채운다.
     */
    val speedKph: Float? = null,
    private val parked: Boolean,
) {
    /** 정차 중 (Figma Drive=Off). */
    val isStopped: Boolean get() = parked

    /** 주행 중 (Figma Drive=On). 목록형 시트 대신 1곳 제안, 키보드 입력 막기 등을 이 값으로 판단한다. */
    val isDriving: Boolean get() = !parked

    companion object {
        val Stopped = DriveMode(GearState.PARK, parked = true)
        val Driving = DriveMode(GearState.DRIVE, parked = false)

        fun from(vehicle: VehicleState, speedKph: Float? = null) =
            DriveMode(vehicle.gear, speedKph, parked = vehicle.isParked)
    }
}

/** 공통 상태. [AppShell]이 차 상태에서 만들어 아래로 내려 준다. 셸 밖(미리보기·테스트)에서는 정차로 본다. */
val LocalDriveMode = staticCompositionLocalOf { DriveMode.Stopped }

@Composable
fun ProvideDriveMode(drive: DriveMode, content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalDriveMode provides drive, content = content)

/** Figma 셸 변형 3종 (Pleos 공통 UI 351:2613 / 351:2758 / 351:2903). Dark + 주행 조합은 디자인에 없다. */
enum class ShellVariant(val dark: Boolean, val driving: Boolean) {
    LightOff(dark = false, driving = false),
    DarkOff(dark = true, driving = false),
    LightOn(dark = false, driving = true),
    ;

    companion object {
        /** 주행 중이면 항상 LightOn. 수면(다크) 중 주행이 되어도 앱 색은 [SitterSurface]가 정하고 변형만 LightOn으로 본다. */
        fun of(dark: Boolean, drive: DriveMode) = when {
            drive.isDriving -> LightOn
            dark -> DarkOff
            else -> LightOff
        }
    }
}
