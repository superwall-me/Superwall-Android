package com.superwall.sdk.models.paywall

import com.superwall.sdk.models.config.FeatureGatingBehavior
import com.superwall.sdk.models.serialization.URLSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URL

@Serializable
data class Paywalls(val paywalls: List<Paywall>)

@Serializable
data class Paywall(
    @SerialName("id")
    val databaseId: String,
    val identifier: String,
    val name: String,
    val url: @Serializable(with =  URLSerializer::class) URL,
    @SerialName("paywalljs_event")
    val htmlSubstitutions: String,
    @kotlinx.serialization.Transient()
    var presentation: Presentation = Presentation(PaywallPresentationStyle.MODAL, PresentationCondition.ALWAYS),

    @SerialName("presentation_style_v2")
    private val presensentationStyle: String,

    private val presentationCondition: String,

    val backgroundColorHex: String,

    var products: List<Product>,
    @kotlinx.serialization.Transient()
    var productIds: List<String> = arrayListOf(),
    @kotlinx.serialization.Transient()
    var responseLoadingInfo: LoadingInfo = LoadingInfo(),

    @kotlinx.serialization.Transient()
    var webviewLoadingInfo: LoadingInfo = LoadingInfo(),
    @kotlinx.serialization.Transient()
    var productsLoadingInfo: LoadingInfo = LoadingInfo(),
    var productVariables: List<ProductVariable>? = null,
    var swProductVariablesTemplate: List<ProductVariable>? = null,
    var paywalljsVersion: String? = null,
    var isFreeTrialAvailable: Boolean = false,
    var featureGating: FeatureGatingBehavior = FeatureGatingBehavior.NonGated
) {
    init {
        productIds = products.map { it.id }
        presentation = Presentation(
            style = PaywallPresentationStyle.valueOf(presensentationStyle.uppercase()),
            condition = PresentationCondition.valueOf(presentationCondition.uppercase())
        )
    }

    @Serializable
    data class Presentation(val style: PaywallPresentationStyle, val condition: PresentationCondition)

    @Serializable
    data class LoadingInfo(
        val startAt: String? = null,
        val endAt: String? = null,
        val failAt: String? = null
    )

    @Serializable
    data class Product(val id: String)

    @Serializable
    data class ProductVariable(val type: String, val attributes: Map<String, String>)


    companion object {
        fun fromJson(json: String): Paywall {
            return Json.decodeFromString(json)
        }
    }
}
