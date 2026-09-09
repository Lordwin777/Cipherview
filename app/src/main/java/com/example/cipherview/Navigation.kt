package com.example.cipherview

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.cipherview.ui.screens.history.HistoryScreen
import com.example.cipherview.ui.screens.onboarding.NicknameSetupScreen
import com.example.cipherview.ui.screens.profile.ProfileScreen
import com.example.cipherview.ui.screens.receive.ReceiveDocumentScreen
import com.example.cipherview.ui.screens.share.ShareDocumentScreen
import com.example.cipherview.ui.screens.vault.VaultHomeScreen
import com.example.cipherview.ui.screens.viewer.ProtectedViewerScreen

@Composable
fun MainNavigation() {
    val app = CipherViewApp.instance
    val profile by app.vaultRepository.userProfile.collectAsState()

    val initialKey = if (profile != null) HomeNavKey else OnboardingNavKey
    val backStack = rememberNavBackStack(initialKey)

    NavDisplay(
        backStack = backStack,
        onBack = {
            if (backStack.size > 1) {
                backStack.removeLastOrNull()
            }
        },
        entryProvider = entryProvider {
            entry<OnboardingNavKey> {
                NicknameSetupScreen(
                    onNicknameSet = { nickname ->
                        app.vaultRepository.setupInitialNickname(nickname)
                        backStack.add(HomeNavKey)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            entry<HomeNavKey> {
                VaultHomeScreen(
                    repository = app.vaultRepository,
                    onNavigateShare = { backStack.add(ShareNavKey) },
                    onNavigateReceive = { backStack.add(ReceiveNavKey) },
                    onNavigateHistory = { backStack.add(HistoryNavKey) },
                    onNavigateProfile = { backStack.add(ProfileNavKey) },
                    onOpenDocument = { docId -> backStack.add(ViewerNavKey(docId)) },
                    modifier = Modifier.fillMaxSize()
                )
            }

            entry<ShareNavKey> {
                ShareDocumentScreen(
                    repository = app.vaultRepository,
                    discoveryManager = app.nsdDiscoveryManager,
                    transferEngine = app.transferEngine,
                    onBack = { backStack.removeLastOrNull() },
                    onNavigateToDocument = { docId -> backStack.add(ViewerNavKey(docId)) },
                    modifier = Modifier.fillMaxSize()
                )
            }

            entry<ReceiveNavKey> {
                ReceiveDocumentScreen(
                    repository = app.vaultRepository,
                    discoveryManager = app.nsdDiscoveryManager,
                    transferEngine = app.transferEngine,
                    onBack = { backStack.removeLastOrNull() },
                    onNavigateToDocument = { docId, code -> backStack.add(ViewerNavKey(docId, code)) },
                    modifier = Modifier.fillMaxSize()
                )
            }

            entry<HistoryNavKey> {
                HistoryScreen(
                    repository = app.vaultRepository,
                    onBack = { backStack.removeLastOrNull() },
                    onOpenDocument = { docId -> backStack.add(ViewerNavKey(docId)) },
                    modifier = Modifier.fillMaxSize()
                )
            }

            entry<ProfileNavKey> {
                ProfileScreen(
                    repository = app.vaultRepository,
                    onBack = { backStack.removeLastOrNull() },
                    modifier = Modifier.fillMaxSize()
                )
            }

            entry<ViewerNavKey> { key ->
                ProtectedViewerScreen(
                    documentId = key.documentId,
                    prefilledCode = key.prefilledCode,
                    repository = app.vaultRepository,
                    onBack = { backStack.removeLastOrNull() },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    )
}
