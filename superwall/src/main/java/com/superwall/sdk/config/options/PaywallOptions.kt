package com.superwall.sdk.config.options

// Options for configuring the appearance and behavior of paywalls.
class PaywallOptions {
    // Determines whether the paywall should use haptic feedback. Defaults to true.
    //
    // Haptic feedback occurs when a user purchases or restores a product, opens a URL
    // from the paywall, or closes the paywall.
    var isHapticFeedbackEnabled: Boolean = true

    // Defines the messaging of the alert presented to the user when restoring a transaction fails.
    class RestoreFailed {
        // The title of the alert presented to the user when restoring a transaction fails. Defaults to
        // `No Subscription Found`.
        var title: String = "No Subscription Found"

        // Defines the message of the alert presented to the user when restoring a transaction fails.
        // Defaults to `We couldn't find an active subscription for your account.`
        var message: String = "We couldn't find an active subscription for your account."

        // Defines the title of the close button in the alert presented to the user when restoring a
        // transaction fails. Defaults to `Okay`.
        var closeButtonTitle: String = "Okay"
    }

    // Defines the messaging of the alert presented to the user when restoring a transaction fails.
    var restoreFailed: RestoreFailed = RestoreFailed()

    // Pre-loads and caches trigger paywalls and products when you initialize the SDK via ``Superwall/configure(apiKey:purchaseController:options:completion:)-52tke``. Defaults to `true`.
    //
    // Set this to `false` to load and cache paywalls and products in a just-in-time fashion.
    //
    // If you want to preload them at a later date, you can call ``Superwall/preloadAllPaywalls()``
    // or ``Superwall/preloadPaywalls(forEvents:)``
    var shouldPreload: Boolean = true

    // Loads paywall template websites from disk, if available. Defaults to `true`.
    //
    // When you save a change to your paywall in the Superwall dashboard, a key is
    // appended to the end of your paywall website URL, e.g. `sw_cache_key=<Date saved>`.
    // This is used to cache your paywall webpage to disk after it's first loaded. Superwall will
    // continue to load the cached version of your paywall webpage unless the next time you
    // make a change on the Superwall dashboard.
    var useCachedTemplates: Boolean = false

    // Automatically dismisses the paywall when a product is purchased or restored. Defaults to `true`.
    //
    // Set this to `false` to prevent the paywall from dismissing on purchase/restore.
    var automaticallyDismiss: Boolean = true

    // Defines the different types of views that can appear behind Apple's payment sheet during a transaction.
    enum class TransactionBackgroundView {
        // This shows your paywall background color overlayed with an activity indicator.
        SPINNER
    }

    // The view that appears behind Apple's payment sheet during a transaction. Defaults to `.spinner`.
    //
    // Set this to `null` to remove any background view during the transaction.
    //
    // **Note:** This feature is still in development and could change.
    var transactionBackgroundView: TransactionBackgroundView? = TransactionBackgroundView.SPINNER
}
