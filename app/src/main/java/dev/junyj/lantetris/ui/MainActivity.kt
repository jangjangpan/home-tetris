package dev.junyj.lantetris.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /**
     * 멀티 중에 띄우는 포그라운드 서비스는 알림이 필수다.
     * 권한을 거절해도 연결 유지 자체는 되지만, 무엇 때문에 앱이 살아 있는지 보이지 않는다.
     */
    private fun askNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
        if (granted != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        askNotificationPermissionIfNeeded()
        // 게임 중에 화면이 꺼지면 곤란하다.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            val vm: AppViewModel = viewModel()
            val t = vm.theme
            // 시스템 바 뒤까지 테마 색으로 칠해야 밝은 테마에서 위아래가 검게 남지 않는다.
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = t.accent,
                    background = t.bg,
                    surface = t.panel,
                    onBackground = t.ink,
                    onSurface = t.ink,
                    outline = t.edge,
                )
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(t.bg)
                        .systemBarsPadding()
                ) {
                    AppRoot(vm)
                }
            }
        }
    }
}
