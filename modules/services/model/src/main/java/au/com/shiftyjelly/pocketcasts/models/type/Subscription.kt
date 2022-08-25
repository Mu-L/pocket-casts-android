package au.com.shiftyjelly.pocketcasts.models.type

import android.content.res.Resources
import au.com.shiftyjelly.pocketcasts.localization.R
import au.com.shiftyjelly.pocketcasts.utils.log.LogBuffer
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.ProductDetails.PricingPhase
import java.time.Period
import java.time.format.DateTimeParseException

sealed interface Subscription {
    val recurringPricingPhase: RecurringSubscriptionPricingPhase
    val trialPricingPhase: TrialSubscriptionPricingPhase?
    val productDetails: ProductDetails
    val offerToken: String
    val shortTitle: String
        get() = productDetails.title.split(" (").first()

    fun numFreeThenPricePerPeriod(res: Resources): String?

    // Simple subscriptions do not have a trial phase
    class Simple(
        override val recurringPricingPhase: RecurringSubscriptionPricingPhase,
        override val productDetails: ProductDetails,
        override val offerToken: String
    ) : Subscription {
        override val trialPricingPhase = null
        override fun numFreeThenPricePerPeriod(res: Resources): String? = null

        companion object {
            fun instanceOf(
                recurringPricingPhase: RecurringSubscriptionPricingPhase,
                productDetails: ProductDetails
            ): Simple? =
                productDetails
                    .subscriptionOfferDetails
                    ?.firstOrNull { !it.hasTrialOffers() }
                    ?.offerToken
                    ?.let { offerToken ->
                        Simple(
                            recurringPricingPhase = recurringPricingPhase,
                            productDetails = productDetails,
                            offerToken = offerToken
                        )
                    }
        }
    }

    class WithTrial(
        override val recurringPricingPhase: RecurringSubscriptionPricingPhase,
        override val trialPricingPhase: TrialSubscriptionPricingPhase, // override to not be nullable
        override val productDetails: ProductDetails,
        override val offerToken: String
    ) : Subscription {
        override fun numFreeThenPricePerPeriod(res: Resources): String {
            val stringRes = when (recurringPricingPhase) {
                is SubscriptionPricingPhase.Years -> R.string.plus_trial_then_slash_year
                is SubscriptionPricingPhase.Months -> R.string.plus_trial_then_slash_month
            }
            return res.getString(
                stringRes,
                trialPricingPhase.periodValue(res),
                recurringPricingPhase.formattedPrice
            )
        }

        companion object {
            fun instanceOf(
                recurringPricingPhase: RecurringSubscriptionPricingPhase,
                trialPricingPhase: TrialSubscriptionPricingPhase,
                productDetails: ProductDetails,
            ): WithTrial? =
                productDetails
                    .subscriptionOfferDetails
                    ?.firstOrNull { it.hasTrialOffers() }
                    ?.offerToken
                    ?.let { offerToken ->
                        WithTrial(
                            recurringPricingPhase = recurringPricingPhase,
                            trialPricingPhase = trialPricingPhase,
                            productDetails = productDetails,
                            offerToken = offerToken
                        )
                    }
        }
    }

    companion object {
        fun fromProductDetails(productDetails: ProductDetails, isFreeTrialEligible: Boolean): Subscription? {

            val recurringPhase = productDetails.recurringSubscriptionPricingPhase?.fromPricingPhase()
            val trialPhase = productDetails.trialSubscriptionPricingPhase?.fromPricingPhase()

            return when {
                recurringPhase !is RecurringSubscriptionPricingPhase -> {
                    // This should never happen. Every subscription is expected to have a recurring phase.
                    LogBuffer.e(LogBuffer.TAG_SUBSCRIPTIONS, "unable to convert product details to a subscription")
                    null
                }
                trialPhase is TrialSubscriptionPricingPhase && isFreeTrialEligible -> WithTrial.instanceOf(
                    recurringPricingPhase = recurringPhase,
                    trialPricingPhase = trialPhase,
                    productDetails = productDetails
                )
                else -> Simple.instanceOf(
                    recurringPricingPhase = recurringPhase,
                    productDetails = productDetails
                )
            }
        }

        private fun ProductDetails.SubscriptionOfferDetails.getTrialOffers() =
            pricingPhases.pricingPhaseList.filter { pricingPhase -> pricingPhase.recurrenceMode == ProductDetails.RecurrenceMode.FINITE_RECURRING }

        private fun ProductDetails.SubscriptionOfferDetails.hasTrialOffers() = getTrialOffers().isNotEmpty()

        private val ProductDetails.recurringSubscriptionPricingPhase: PricingPhase?
            get() = findOnlyMatchingPricingPhase(
                predicate = { it.recurrenceMode == ProductDetails.RecurrenceMode.INFINITE_RECURRING },
                errorMessageIfNotSingleMatch = { "ProductDetails did not have a single infinite recurring pricing phase, instead it had $it" }
            )

        private val ProductDetails.trialSubscriptionPricingPhase: PricingPhase?
            get() = findOnlyMatchingPricingPhase(
                predicate = { it.recurrenceMode == ProductDetails.RecurrenceMode.FINITE_RECURRING },
                errorMessageIfNotSingleMatch = { "ProductDetails did not have a single finite recurring pricing phase, instead it had $it" }
            )

        private fun ProductDetails.findOnlyMatchingPricingPhase(
            predicate: (PricingPhase) -> Boolean,
            errorMessageIfNotSingleMatch: (Int) -> String
        ): PricingPhase? {
            val subscriptionOfferDetailsSafe = subscriptionOfferDetails
            if (subscriptionOfferDetailsSafe == null) {
                LogBuffer.e(LogBuffer.TAG_SUBSCRIPTIONS, "ProductDetails had null subscriptionOfferDetails")
                return null
            }

            // TODO just taking the first one doesn't seem like the way to go here
            val pricingPhases = subscriptionOfferDetailsSafe.first().pricingPhases.pricingPhaseList
            val matchingPhases = pricingPhases.filter(predicate)

            if (matchingPhases.size != 1) {
                LogBuffer.e(LogBuffer.TAG_SUBSCRIPTIONS, errorMessageIfNotSingleMatch(matchingPhases.size))
                return null
            }
            return matchingPhases.first()
        }

        private fun PricingPhase.fromPricingPhase(): SubscriptionPricingPhase? =
            try {
                val period = Period.parse(this.billingPeriod)
                when {
                    period.years > 0 -> SubscriptionPricingPhase.Years(this, period)
                    period.months > 0 -> SubscriptionPricingPhase.Months(this, period)
                    period.days > 0 -> SubscriptionPricingPhase.Days(period)
                    else -> null
                }
            } catch (_: DateTimeParseException) {
                LogBuffer.e(LogBuffer.TAG_SUBSCRIPTIONS, "Unable to parse billingPeriod: $billingPeriod")
                null
            }
    }
}
