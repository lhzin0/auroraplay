package com.auroraplay.iptv.presentation.player

import androidx.lifecycle.ViewModel
import com.auroraplay.iptv.player.PlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Thin Hilt entry point exposing the app-wide singleton PlayerManager to
 * Composables via hiltViewModel(), without tying the shared ExoPlayer
 * instance's lifecycle to any single screen's ViewModel.
 */
@HiltViewModel
class PlayerManagerHolderViewModel @Inject constructor(
    val playerManager: PlayerManager,
) : ViewModel()
