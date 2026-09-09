package com.example.cipherview

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object OnboardingNavKey : NavKey

@Serializable
data object HomeNavKey : NavKey

@Serializable
data object ShareNavKey : NavKey

@Serializable
data object ReceiveNavKey : NavKey

@Serializable
data object HistoryNavKey : NavKey

@Serializable
data object ProfileNavKey : NavKey

@Serializable
data class ViewerNavKey(val documentId: String, val prefilledCode: String? = null) : NavKey

