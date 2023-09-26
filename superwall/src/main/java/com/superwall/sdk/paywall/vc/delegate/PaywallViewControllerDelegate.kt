package com.superwall.sdk.paywall.vc.delegate

import com.superwall.sdk.paywall.vc.PaywallViewController
import com.superwall.sdk.paywall.vc.web_view.messaging.PaywallWebEvent

sealed class PaywallResult {
    object Unknown : PaywallResult()
    object LoadingPurchase : PaywallResult()
    object LoadingURL : PaywallResult()
    object ManualLoading : PaywallResult()
    object Ready : PaywallResult()
}

interface PaywallViewControllerDelegate {
    // TODO: missing `shouldDismiss`
    fun paywallViewController(controller: PaywallViewController, didFinishWith: PaywallResult)
}

interface PaywallViewControllerEventDelegate {
    suspend fun eventDidOccur(paywallEvent: PaywallWebEvent, on: PaywallViewController)
}

sealed class PaywallLoadingState {
    class Unknown : PaywallLoadingState()
    class LoadingPurchase : PaywallLoadingState()
    class LoadingURL : PaywallLoadingState()
    class ManualLoading : PaywallLoadingState()
    class Ready : PaywallLoadingState()
}