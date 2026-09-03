package com.auroraplay.iptv

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.auroraplay.iptv.core.util.NetworkMonitor
import com.auroraplay.iptv.domain.repository.SettingsRepository
import com.auroraplay.iptv.player.PlayerManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import dagger.hilt.android.AndroidEntryPoint
import com.auroraplay.iptv.core.theme.AuroraColors
import com.auroraplay.iptv.core.theme.AuroraPlayTheme
import com.auroraplay.iptv.navigation.AuroraNavGraph

@AndroidEntryPoint
class MainActivity : androidx.fragment.app.FragmentActivity() {

    @javax.inject.Inject
    lateinit var settingsRepository: SettingsRepository

    @javax.inject.Inject
    lateinit var networkMonitor: NetworkMonitor

    @javax.inject.Inject
    lateinit var playerManager: PlayerManager

    @Volatile private var pipEnabledSetting: Boolean = true

    // Registered as a property (not inside onCreate) since it must exist
    // before the activity reaches STARTED — declaring it here, rather than
    // lazily on first use, is what the contract requires.
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* declining just means no notifications */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // POST_NOTIFICATIONS is declared in the manifest but, on API 33+,
        // still needs this runtime grant — without it, every notification
        // (download-complete, new-episode) silently never shows.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        lifecycleScope.launch {
            settingsRepository.observeSettings().collect { pipEnabledSetting = it.pipEnabled }
        }

        // Picture-in-Picture, the modern way (API 31+): the system slides the
        // activity into a PiP window by itself when the user goes Home/Recents,
        // as long as the params say auto-enter is on. onUserLeaveHint (below) is
        // the manual fallback for API 26–30, where it often fired too late on
        // gesture navigation — the activity was already stopping, so the call
        // was ignored and playback just continued as background audio.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            lifecycleScope.launch {
                kotlinx.coroutines.flow.combine(
                    playerManager.pipEligible,
                    playerManager.state
                        .map { Triple(it.isPlaying, it.videoWidth, it.videoHeight) }
                        .distinctUntilChanged(),
                    settingsRepository.observeSettings().map { it.pipEnabled }.distinctUntilChanged(),
                ) { eligible, playState, enabled ->
                    enabled && eligible && playState.first
                }.distinctUntilChanged().collect { canPip ->
                    runCatching { setPictureInPictureParams(buildPipParams(autoEnter = canPip)) }
                }
            }
        }

        val isTvDevice = isRunningOnTv()

        setContent {
            // Observe ONLY the accent hex, not the whole AppSettings — otherwise
            // toggling any unrelated setting (animations, quality, Wi-Fi-only…)
            // re-emits AppSettings and recomposes this root, i.e. the whole app.
            val accentHex by remember {
                settingsRepository.observeSettings().map { it.accentColorHex }.distinctUntilChanged()
            }.collectAsState(initial = com.auroraplay.iptv.domain.repository.AppSettings().accentColorHex)
            val accent = remember(accentHex) {
                runCatching { Color(android.graphics.Color.parseColor(accentHex)) }
                    .getOrDefault(com.auroraplay.iptv.core.theme.AuroraColors.AccentDefault)
            }
            // Same rationale as the accent hex above: observe only this flag so
            // flipping it (rare) is the only setting that recomposes the root.
            val frostGlass by remember {
                settingsRepository.observeSettings().map { it.frostGlass }.distinctUntilChanged()
            }.collectAsState(initial = com.auroraplay.iptv.domain.repository.AppSettings().frostGlass)
            // Assumed online until the real callback fires, so app launch
            // never flashes an "offline" banner for the one frame before
            // NetworkMonitor reports the actual state.
            val isOnline by networkMonitor.isOnline.collectAsState(initial = true)

            AuroraPlayTheme(accentColor = accent) {
              androidx.compose.runtime.CompositionLocalProvider(
                  com.auroraplay.iptv.core.theme.LocalFrostGlass provides frostGlass
              ) {
                Box(Modifier.fillMaxSize()) {
                    AuroraNavGraph(isTvDevice = isTvDevice)

                    // One single place this shows, instead of every screen
                    // guessing "offline" from whichever call happened to time
                    // out — sits above everything, including the player.
                    AnimatedVisibility(
                        visible = !isOnline,
                        enter = slideInVertically(),
                        exit = slideOutVertically(),
                        modifier = Modifier.align(Alignment.TopCenter),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(AuroraColors.Error)
                                .statusBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Icon(Icons.Default.CloudOff, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Sem conexão com a internet", color = Color.White, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
              }
            }
        }
    }

    private fun isRunningOnTv(): Boolean {
        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        return uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }

    // ---- Picture-in-Picture -------------------------------------------------

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // API 31+ auto-enters via the params set above; only drive it by hand
        // on the older versions that have no auto-enter.
        if (Build.VERSION.SDK_INT !in Build.VERSION_CODES.O until Build.VERSION_CODES.S) return
        if (!pipEnabledSetting || !playerManager.pipEligible.value) return
        if (!playerManager.state.value.isPlaying) return
        runCatching { enterPictureInPictureMode(buildPipParams(autoEnter = false)) }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    private fun buildPipParams(autoEnter: Boolean): android.app.PictureInPictureParams {
        val s = playerManager.state.value
        val w = s.videoWidth.takeIf { it > 0 } ?: 16
        val h = s.videoHeight.takeIf { it > 0 } ?: 9
        // Android rejects ratios outside ~[1:2.39 .. 2.39:1].
        val ratio = (w.toFloat() / h.toFloat()).coerceIn(0.42f, 2.38f)
        val num = (ratio * 1000).toInt()
        val builder = android.app.PictureInPictureParams.Builder()
            .setAspectRatio(android.util.Rational(num, 1000))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnter)
            // Video is not a seamless resize target — let the system letterbox
            // it during the enter/exit animation instead of stretching.
            builder.setSeamlessResizeEnabled(false)
        }
        return builder.build()
    }

    override fun onPictureInPictureModeChanged(isInPip: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPip, newConfig)
        playerManager.pipActive.value = isInPip
    }

}

