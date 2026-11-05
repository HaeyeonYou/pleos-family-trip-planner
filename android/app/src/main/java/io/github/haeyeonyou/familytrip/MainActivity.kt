package io.github.haeyeonyou.familytrip

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.haeyeonyou.familytrip.location.FUSED_LOCATION_PERMISSION
import io.github.haeyeonyou.familytrip.ui.CourseScreen
import io.github.haeyeonyou.familytrip.ui.DevScreen
import io.github.haeyeonyou.familytrip.ui.GuideScreen
import io.github.haeyeonyou.familytrip.ui.InputScreen
import io.github.haeyeonyou.familytrip.ui.TripViewModel

private object Routes {
    const val INPUT = "input"
    const val COURSE = "course"
    const val GUIDE = "guide"
    const val DEV = "dev"
}

class MainActivity : ComponentActivity() {

    private val container by lazy { (application as FamilyTripApp).container }

    private val locationPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) container.startLocationIfPermitted()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container.startLocationIfPermitted()
        if (checkSelfPermission(FUSED_LOCATION_PERMISSION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            locationPermissionRequest.launch(FUSED_LOCATION_PERMISSION)
        }

        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = TripViewModel(container) as T
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val vm: TripViewModel = viewModel(factory = factory)
                    val state by vm.state.collectAsStateWithLifecycle()
                    val vehicle by vm.vehicle.collectAsStateWithLifecycle()
                    val nav = rememberNavController()

                    NavHost(navController = nav, startDestination = Routes.INPUT) {
                        composable(Routes.INPUT) {
                            InputScreen(
                                state = state,
                                isParked = vehicle.isParked,
                                onTextChange = vm::onRequestTextChange,
                                onParse = vm::parse,
                                onConditionsChange = vm::updateConditions,
                                onMakeCourse = { vm.requestCourse { nav.navigate(Routes.COURSE) } },
                                onOpenDev = { nav.navigate(Routes.DEV) },
                            )
                        }
                        composable(Routes.COURSE) {
                            state.course?.let { course ->
                                CourseScreen(
                                    course = course,
                                    isParked = vehicle.isParked,
                                    onStartGuide = {
                                        vm.startGuide()
                                        nav.navigate(Routes.GUIDE)
                                    },
                                    onBack = { nav.popBackStack() },
                                )
                            }
                        }
                        composable(Routes.GUIDE) {
                            GuideScreen(
                                state = state,
                                isParked = vehicle.isParked,
                                onNext = vm::goToNext,
                                onMarkArrived = vm::markArrived,
                                onEnd = {
                                    vm.endGuide()
                                    nav.popBackStack(Routes.INPUT, inclusive = false)
                                },
                            )
                        }
                        composable(Routes.DEV) {
                            DevScreen(container, onBack = { nav.popBackStack() })
                        }
                    }
                }
            }
        }
    }
}
