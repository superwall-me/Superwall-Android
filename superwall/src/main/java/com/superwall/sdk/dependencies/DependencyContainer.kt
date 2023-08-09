package com.superwall.sdk.dependencies

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.core.view.ContentInfoCompat
import com.superwall.sdk.Superwall
import com.superwall.sdk.analytics.SessionEventsManager
import com.superwall.sdk.analytics.session.AppManagerDelegate
import com.superwall.sdk.analytics.session.AppSession
import com.superwall.sdk.analytics.session.AppSessionManager
import com.superwall.sdk.analytics.trigger_session.TriggerSessionManager
import com.superwall.sdk.config.ConfigLogic
import com.superwall.sdk.config.ConfigManager
import com.superwall.sdk.config.options.SuperwallOptions
import com.superwall.sdk.delegate.DefaultSuperwallDelegate
import com.superwall.sdk.delegate.SubscriptionStatus
import com.superwall.sdk.delegate.SuperwallDelegate
import com.superwall.sdk.identity.IdentityInfo
import com.superwall.sdk.identity.IdentityManager
import com.superwall.sdk.misc.ActivityLifecycleTracker
import com.superwall.sdk.misc.runOnUiThread
import com.superwall.sdk.misc.sdkVersion
import com.superwall.sdk.models.events.EventData
import com.superwall.sdk.models.paywall.Paywall
import com.superwall.sdk.models.product.ProductVariable
import com.superwall.sdk.network.Api
import com.superwall.sdk.network.Network
import com.superwall.sdk.network.device.DeviceHelper
import com.superwall.sdk.network.device.DeviceInfo
import com.superwall.sdk.paywall.manager.PaywallManager
import com.superwall.sdk.paywall.manager.PaywallViewControllerCache
import com.superwall.sdk.paywall.presentation.internal.PresentationRequest
import com.superwall.sdk.paywall.presentation.internal.PresentationRequestType
import com.superwall.sdk.paywall.presentation.internal.request.PaywallOverrides
import com.superwall.sdk.paywall.presentation.internal.request.PresentationInfo
import com.superwall.sdk.paywall.presentation.rule_logic.RuleAttributes
import com.superwall.sdk.paywall.request.PaywallRequest
import com.superwall.sdk.paywall.request.PaywallRequestManager
import com.superwall.sdk.paywall.request.PaywallRequestManagerDepFactory
import com.superwall.sdk.paywall.request.ResponseIdentifiers
import com.superwall.sdk.paywall.vc.PaywallViewController
import com.superwall.sdk.paywall.vc.delegate.PaywallViewControllerDelegate
import com.superwall.sdk.paywall.vc.delegate.PaywallViewControllerDelegateAdapter
import com.superwall.sdk.paywall.vc.web_view.SWWebView
import com.superwall.sdk.paywall.vc.web_view.messaging.PaywallMessageHandler
import com.superwall.sdk.paywall.vc.web_view.templating.models.OuterVariables
import com.superwall.sdk.paywall.vc.web_view.templating.models.Variables
import com.superwall.sdk.storage.EventsQueue
import com.superwall.sdk.storage.Storage
import com.superwall.sdk.store.StoreKitManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.StateFlow
import java.net.HttpURLConnection



class PurchaseController {}

class DependencyContainer(val context: Context, purchaseController: PurchaseController? = null, options: SuperwallOptions = SuperwallOptions()): ApiFactory, DeviceInfoFactory, AppManagerDelegate, RequestFactory, TriggerSessionManagerFactory, RuleAttributesFactory, IdentityInfoFactory, LocaleIdentifierFactory, IdentityInfoAndLocaleIdentifierFactory, ViewControllerCacheDevice,
    PaywallRequestManagerDepFactory, VariablesFactory {

    var network: Network
    override lateinit var api: Api
    var deviceHelper: DeviceHelper
    override lateinit var storage: Storage
    override lateinit var configManager: ConfigManager
    override lateinit var identityManager: IdentityManager
    var appSessionManager: AppSessionManager
    var sessionEventsManager: SessionEventsManager
    var delegateAdapter: SuperwallDelegate
    var queue: EventsQueue
    var paywallManager: PaywallManager
    var paywallRequestManager: PaywallRequestManager
    var storeKitManager: StoreKitManager
    val activityLifecycleTracker: ActivityLifecycleTracker


    init {

        // TODO: Add delegate adapter
        delegateAdapter = DefaultSuperwallDelegate()


        activityLifecycleTracker = ActivityLifecycleTracker()
        // onto
        (context.applicationContext as Application).registerActivityLifecycleCallbacks(
            activityLifecycleTracker)

        storage = Storage(context = context, factory = this)
        network = Network(factory = this)

        deviceHelper = DeviceHelper(context = context, storage, factory = this)

        configManager = ConfigManager(
            storage = storage,
            network = network
        )

        // TODO: Pass in config manager
        api = Api(networkEnvironment = SuperwallOptions.NetworkEnvironment.Developer())


        identityManager = IdentityManager(
            storage = storage,
            deviceHelper = deviceHelper,
            configManager = configManager
        )

        appSessionManager = AppSessionManager(
            context = context,
            storage = storage,
            configManager = configManager,
            delegate = this
        )

        sessionEventsManager = SessionEventsManager(
            network = network,
            storage = storage,
            configManager = configManager,
            factory = this
        )

        storeKitManager = StoreKitManager(context)

        paywallRequestManager = PaywallRequestManager(
            storeKitManager = storeKitManager,
            network = network,
            factory = this
        )
        paywallManager = PaywallManager(
            paywallRequestManager = paywallRequestManager,
            factory = this,
        )

        queue = EventsQueue(context, configManager = configManager, network = network)
    }



    override suspend fun makeHeaders(
        isForDebugging: Boolean,
        requestId: String
    ): Map<String, String> {
        // TODO: Add storage
        val key = if (isForDebugging) storage.debugKey else storage.apiKey
        val auth = "Bearer $key"
        val headers = mapOf<String, String>(
            "Authorization" to auth,
            "X-Platform" to "iOS",
            "X-Platform-Environment" to "SDK",
            // TODO: Add app user id
            "X-App-User-ID" to (identityManager.appUserId ?: ""),
            "X-Alias-ID" to identityManager.aliasId,
            "X-URL-Scheme" to deviceHelper.urlScheme,
            "X-Vendor-ID" to deviceHelper.vendorId,
            "X-App-Version" to deviceHelper.appVersion,
            "X-OS-Version" to deviceHelper.osVersion,
            "X-Device-Model" to deviceHelper.model,
            "X-Device-Locale" to deviceHelper.locale,
            "X-Device-Language-Code" to deviceHelper.languageCode,
            "X-Device-Currency-Code" to deviceHelper.currencyCode,
            "X-Device-Currency-Symbol" to deviceHelper.currencySymbol,
            "X-Device-Timezone-Offset" to deviceHelper.secondsFromGMT,
            "X-App-Install-Date" to deviceHelper.appInstalledAtString,
            "X-Radio-Type" to deviceHelper.radioType,
            "X-Device-Interface-Style" to deviceHelper.interfaceStyle,
            "X-SDK-Version" to sdkVersion,
            "X-Request-Id" to requestId,
            "X-Bundle-ID" to deviceHelper.bundleId,
            "X-Low-Power-Mode" to deviceHelper.isLowPowerModeEnabled.toString(),
//            "X-Is-Sandbox" to deviceHelper.isSandbox,
            "Content-Type" to "application/json"
        )

        return headers
    }

    override suspend fun makePaywallViewController(
        paywall: Paywall,
        cache: PaywallViewControllerCache?,
        delegate: PaywallViewControllerDelegate?
    ): PaywallViewController {
        // TODO: Fix this up

        val messageHandler = PaywallMessageHandler(
            sessionEventsManager = sessionEventsManager,
            factory = this
        )


        val webViewDeffered = CompletableDeferred<SWWebView>()

       runOnUiThread {
           val _webView = SWWebView(
               context = context,
               messageHandler = messageHandler,
               sessionEventsManager = sessionEventsManager,
           )
           webViewDeffered.complete(_webView)
       }

        val webView = webViewDeffered.await()


        val paywallViewController =  PaywallViewController(
            paywall = paywall,
            factory = this,
            cache = cache,
            delegate = delegate,
            deviceHelper = deviceHelper,
            paywallManager = paywallManager,
            storage = storage,
            webView = webView,
            eventDelegate = Superwall.instance
        )
        webView.delegate = paywallViewController
        messageHandler.delegate = paywallViewController

        return paywallViewController
    }

    override fun makeCache(): PaywallViewControllerCache {
        return PaywallViewControllerCache(deviceHelper.locale)
    }


    override fun makeDeviceInfo(): DeviceInfo {
       return DeviceInfo(
           appInstalledAtString = deviceHelper.appInstalledAtString,
           locale = deviceHelper.locale,
       )
    }


    override suspend fun didUpdateAppSession(appSession: AppSession) {

    }



    // Mark - RequestFactory

    // Extension of DependencyContainer implementing RequestFactory
    override fun makePaywallRequest(
        eventData: EventData?,
        responseIdentifiers: ResponseIdentifiers,
        overrides: PaywallRequest.Overrides?,
        isDebuggerLaunched: Boolean
    ): PaywallRequest {
        return PaywallRequest(
            eventData = eventData,
            responseIdentifiers = responseIdentifiers,
            overrides = overrides ?: PaywallRequest.Overrides(products = null, isFreeTrial = null),
            isDebuggerLaunched = isDebuggerLaunched
        )
    }

    override fun makePresentationRequest(
        presentationInfo: PresentationInfo,
        paywallOverrides: PaywallOverrides?,
        presenter: Activity?,
        isDebuggerLaunched: Boolean?,
        subscriptionStatus: StateFlow<SubscriptionStatus?>?,
        isPaywallPresented: Boolean,
        type: PresentationRequestType
    ): PresentationRequest {
        return PresentationRequest(
            presentationInfo = presentationInfo,
            presenter = presenter,
            paywallOverrides = paywallOverrides,
            flags = PresentationRequest.Flags(
                // TODO: (PresentationCritical) debug manager
//                isDebuggerLaunched = isDebuggerLaunched ?: debugManager.isDebuggerLaunched,
                isDebuggerLaunched = isDebuggerLaunched ?: false,
                // TODO: (PresentationCritical) Fix subscription status
                subscriptionStatus = subscriptionStatus ?: Superwall.instance.subscriptionStatus,
//                subscriptionStatus = subscriptionStatus!!,
                isPaywallPresented = isPaywallPresented,
                type = type
            )
        )
    }

    override fun makeTriggerSessionManager(): TriggerSessionManager {
        return TriggerSessionManager(
            delegate = sessionEventsManager,
            sessionEventsManager = sessionEventsManager,
            storage = storage,
            configManager =  configManager,
            appSessionManager = appSessionManager,
            identityManager = identityManager
        )
    }

    override fun getTriggerSessionManager(): TriggerSessionManager {
        return sessionEventsManager.triggerSession
    }

    override fun makeStaticPaywall(paywallId: String?): Paywall? {
        val deviceInfo = makeDeviceInfo()
        return ConfigLogic.getStaticPaywall(
            withId = paywallId,
            config = configManager.config.value,
            deviceLocale = deviceInfo.locale
        )
    }

    override suspend fun makeRuleAttributes(): RuleAttributes {
        val userAttributes = identityManager.userAttributes.toMutableMap()
        userAttributes["isLoggedIn"] = identityManager.isLoggedIn
        val device = deviceHelper.getTemplateDevice().toDictionary()

        return RuleAttributes(userAttributes, device)
    }

    override suspend fun makeIdentityInfo(): IdentityInfo {
        return IdentityInfo(
            aliasId = identityManager.aliasId,
            appUserId = identityManager.appUserId,
        )
    }

    override fun makeLocaleIdentifier(): String? {
        return configManager.options?.localeIdentifier
    }

    override suspend fun makeJsonVariables(
        productVariables: List<ProductVariable>?,
        params: Map<String, Any?>?
    ): OuterVariables {
        val templateDeviceDictionary = deviceHelper.getTemplateDevice()
        return Variables.fromProperties(
            productVariables = productVariables ?: listOf<ProductVariable>(),
            params = params ?: mapOf(),
            userAttributes = identityManager.userAttributes,
            templateDeviceDictionary = templateDeviceDictionary
        )
    }


}