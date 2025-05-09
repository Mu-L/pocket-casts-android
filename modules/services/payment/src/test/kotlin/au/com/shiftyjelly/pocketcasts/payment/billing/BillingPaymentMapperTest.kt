package au.com.shiftyjelly.pocketcasts.payment.billing

import au.com.shiftyjelly.pocketcasts.payment.BillingPeriod
import au.com.shiftyjelly.pocketcasts.payment.Price
import au.com.shiftyjelly.pocketcasts.payment.TestLogger
import com.android.billingclient.api.GoogleOfferDetails
import com.android.billingclient.api.GooglePricingPhase
import com.android.billingclient.api.GoogleProductDetails
import com.android.billingclient.api.ProductDetails.RecurrenceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner::class)
class BillingPaymentMapperTest {
    private val logger = TestLogger()
    private val mapper = BillingPaymentMapper(logger)

    @Test
    fun `map product`() {
        assertNotNull(mapper.toProduct(GoogleProductDetails()))
    }

    @Test
    fun `no errors are logged when mapped successfully`() {
        mapper.toProduct(GoogleProductDetails())

        logger.assertNoLogs()
    }

    @Test
    fun `map base product properties`() {
        val googleProduct = GoogleProductDetails(
            productId = "ID 1234",
            name = "Cool product",
        )

        val product = mapper.toProduct(googleProduct)!!

        assertEquals("ID 1234", product.id)
        assertEquals("Cool product", product.name)
    }

    @Test
    fun `map product without offers`() {
        val googleProduct = GoogleProductDetails(
            subscriptionOfferDetails = listOf(
                GoogleOfferDetails(
                    basePlanId = "Base plan ID",
                    offerId = null,
                    pricingPhases = listOf(
                        GooglePricingPhase(
                            priceAmountMicros = 200_500_000,
                            priceCurrencyCode = "AUD",
                            formattedPrice = "$200.50",
                            billingPeriod = "P1M",
                            recurrenceMode = RecurrenceMode.INFINITE_RECURRING,
                            billingCycleCount = 0,
                        ),

                    ),
                    offerTags = listOf("Tag", "Another tag"),
                ),
            ),
        )

        val basePlan = mapper.toProduct(googleProduct)!!.pricingPlans.basePlan

        assertEquals("Base plan ID", basePlan.planId)
        assertEquals(listOf("Tag", "Another tag"), basePlan.tags)
        assertEquals(1, basePlan.pricingPhases.size)

        val pricingPhase = basePlan.pricingPhases[0]
        assertEquals(
            Price(200.5.toBigDecimal().setScale(6), "AUD", "$200.50"),
            pricingPhase.price,
        )
        assertEquals(
            BillingPeriod(BillingPeriod.Cycle.Infinite, BillingPeriod.Interval.Monthly, intervalCount = 1),
            pricingPhase.billingPeriod,
        )
    }

    @Test
    fun `map product with offers`() {
        val googleProduct = GoogleProductDetails(
            subscriptionOfferDetails = listOf(
                GoogleOfferDetails(offerId = null),
                GoogleOfferDetails(
                    basePlanId = "Offer base plan ID",
                    offerId = "Offer ID",
                    offerTags = listOf("Offer Tag", "Another offer tag"),
                ),
            ),
        )

        val offerPlans = mapper.toProduct(googleProduct)!!.pricingPlans.offerPlans
        assertEquals(1, offerPlans.size)
        val offerPlan = offerPlans[0]

        assertEquals("Offer base plan ID", offerPlan.planId)
        assertEquals("Offer ID", offerPlan.offerId)
        assertEquals(listOf("Offer Tag", "Another offer tag"), offerPlan.tags)
    }

    @Test
    fun `map product prices`() {
        val googleProduct = GoogleProductDetails(
            subscriptionOfferDetails = listOf(
                GoogleOfferDetails(offerId = null),
                GoogleOfferDetails(
                    offerId = "ID",
                    pricingPhases = listOf(
                        GooglePricingPhase(
                            priceAmountMicros = 10_000_000,
                            priceCurrencyCode = "USD",
                            formattedPrice = "$10.00",
                        ),
                        GooglePricingPhase(
                            priceAmountMicros = 15_000_000,
                            priceCurrencyCode = "EUR",
                            formattedPrice = "15.00 €",
                        ),
                        GooglePricingPhase(
                            priceAmountMicros = 20_000_000,
                            priceCurrencyCode = "PLN",
                            formattedPrice = "20.00 zł",
                        ),
                    ),
                ),
            ),
        )

        val prices = mapper.toProduct(googleProduct)!!.pricingPlans
            .offerPlans
            .flatMap { it.pricingPhases }
            .map { it.price }

        assertEquals(
            listOf(
                Price(10.toBigDecimal().setScale(6), "USD", "$10.00"),
                Price(15.toBigDecimal().setScale(6), "EUR", "15.00 €"),
                Price(20.toBigDecimal().setScale(6), "PLN", "20.00 zł"),
            ),
            prices,
        )
    }

    @Test
    fun `map product billing intervals`() {
        val googleProduct = GoogleProductDetails(
            subscriptionOfferDetails = listOf(
                GoogleOfferDetails(offerId = null),
                GoogleOfferDetails(
                    offerId = "ID",
                    pricingPhases = listOf(
                        GooglePricingPhase(
                            billingPeriod = "P1M",
                            billingCycleCount = 0,
                            recurrenceMode = RecurrenceMode.INFINITE_RECURRING,
                        ),
                    ),
                ),
                GoogleOfferDetails(
                    offerId = "ID",
                    pricingPhases = listOf(
                        GooglePricingPhase(
                            billingPeriod = "P1M",
                            billingCycleCount = 0,
                            recurrenceMode = RecurrenceMode.NON_RECURRING,
                        ),
                    ),
                ),
                GoogleOfferDetails(
                    offerId = "ID",
                    pricingPhases = listOf(
                        GooglePricingPhase(
                            billingPeriod = "P1M",
                            billingCycleCount = 1,
                            recurrenceMode = RecurrenceMode.FINITE_RECURRING,
                        ),
                    ),
                ),
                GoogleOfferDetails(
                    offerId = "ID",
                    pricingPhases = listOf(
                        GooglePricingPhase(
                            billingPeriod = "P1M",
                            billingCycleCount = 2,
                            recurrenceMode = RecurrenceMode.FINITE_RECURRING,
                        ),
                    ),
                ),
                GoogleOfferDetails(
                    offerId = "ID",
                    pricingPhases = listOf(
                        GooglePricingPhase(
                            billingPeriod = "P2M",
                            billingCycleCount = 0,
                            recurrenceMode = RecurrenceMode.INFINITE_RECURRING,
                        ),
                    ),
                ),
                GoogleOfferDetails(
                    offerId = "ID",
                    pricingPhases = listOf(
                        GooglePricingPhase(
                            billingPeriod = "P1W",
                            billingCycleCount = 0,
                            recurrenceMode = RecurrenceMode.INFINITE_RECURRING,
                        ),
                    ),
                ),
                GoogleOfferDetails(
                    offerId = "ID",
                    pricingPhases = listOf(
                        GooglePricingPhase(
                            billingPeriod = "P3Y",
                            billingCycleCount = 0,
                            recurrenceMode = RecurrenceMode.INFINITE_RECURRING,
                        ),
                    ),
                ),
            ),
        )

        val billingPeriods = mapper.toProduct(googleProduct)!!.pricingPlans
            .offerPlans
            .flatMap { it.pricingPhases }
            .map { it.billingPeriod }

        assertEquals(
            listOf(
                BillingPeriod(
                    intervalCount = 1,
                    interval = BillingPeriod.Interval.Monthly,
                    cycle = BillingPeriod.Cycle.Infinite,
                ),
                BillingPeriod(
                    intervalCount = 1,
                    interval = BillingPeriod.Interval.Monthly,
                    cycle = BillingPeriod.Cycle.NonRecurring,
                ),
                BillingPeriod(
                    intervalCount = 1,
                    interval = BillingPeriod.Interval.Monthly,
                    cycle = BillingPeriod.Cycle.Recurring(1),
                ),
                BillingPeriod(
                    intervalCount = 1,
                    interval = BillingPeriod.Interval.Monthly,
                    cycle = BillingPeriod.Cycle.Recurring(2),
                ),
                BillingPeriod(
                    intervalCount = 2,
                    interval = BillingPeriod.Interval.Monthly,
                    cycle = BillingPeriod.Cycle.Infinite,
                ),
                BillingPeriod(
                    intervalCount = 1,
                    interval = BillingPeriod.Interval.Weekly,
                    cycle = BillingPeriod.Cycle.Infinite,
                ),
                BillingPeriod(
                    intervalCount = 3,
                    interval = BillingPeriod.Interval.Yearly,
                    cycle = BillingPeriod.Cycle.Infinite,
                ),
            ),
            billingPeriods,
        )
    }

    @Test
    fun `do not map product with unknown type`() {
        val googleProduct = GoogleProductDetails(
            productId = "Product ID",
            type = "foo",
        )

        assertNull(mapper.toProduct(googleProduct))
        logger.assertWarnings("Unrecognized product type 'foo' in {productId=Product ID}")
    }

    @Test
    fun `do not map product with no subscription offers`() {
        val googleProduct = GoogleProductDetails(
            productId = "Product ID",
            subscriptionOfferDetails = null,
        )

        assertNull(mapper.toProduct(googleProduct))
        logger.assertWarnings("No subscription offers in {productId=Product ID}")
    }

    @Test
    fun `do not map product with empty subscription offers`() {
        val googleProduct = GoogleProductDetails(
            productId = "Product ID",
            subscriptionOfferDetails = emptyList(),
        )

        assertNull(mapper.toProduct(googleProduct))
        logger.assertWarnings("No subscription offers in {productId=Product ID}")
    }

    @Test
    fun `do not map product with no base offer`() {
        val googleProduct = GoogleProductDetails(
            productId = "Product ID",
            subscriptionOfferDetails = listOf(
                GoogleOfferDetails(offerId = "Offer ID"),
            ),
        )

        assertNull(mapper.toProduct(googleProduct))
        logger.assertWarnings("No single base offer in {productId=Product ID}")
    }

    @Test
    fun `do not map product with multiple base offer`() {
        val googleProduct = GoogleProductDetails(
            productId = "Product ID",
            subscriptionOfferDetails = listOf(
                GoogleOfferDetails(),
                GoogleOfferDetails(),
            ),
        )

        assertNull(mapper.toProduct(googleProduct))
        logger.assertWarnings("No single base offer in {productId=Product ID}")
    }

    @Test
    fun `do not map product with unknown recurrence mode`() {
        val googleProduct = GoogleProductDetails(
            productId = "Product ID",
            subscriptionOfferDetails = listOf(
                GoogleOfferDetails(),
                GoogleOfferDetails(
                    basePlanId = "Base plan ID",
                    offerId = "Offer ID",
                    pricingPhases = listOf(GooglePricingPhase(recurrenceMode = -100)),
                ),
            ),
        )

        assertNull(mapper.toProduct(googleProduct))
        logger.assertWarnings("Unrecognized recurrence mode '-100' in {basePlanId=Base plan ID, offerId=Offer ID, productId=Product ID}")
    }

    @Test
    fun `do not map product with invalid billing duration`() {
        val durations = listOf("1M", "D1M", "PM", "P-1M", "P1U", "P1MY", "P1")
        val googleProducts = durations.map { duration ->
            GoogleProductDetails(
                productId = "Product ID",
                subscriptionOfferDetails = listOf(
                    GoogleOfferDetails(
                        basePlanId = "Base plan ID",
                        pricingPhases = listOf(GooglePricingPhase(billingPeriod = duration)),
                    ),
                ),
            )
        }

        val products = googleProducts.map(mapper::toProduct)

        assertTrue(products.all { it == null })
        logger.assertWarnings(
            "Missing billing period duration designator in {basePlanId=Base plan ID, productId=Product ID, rawDuration=1M}",
            "Missing billing period duration designator in {basePlanId=Base plan ID, productId=Product ID, rawDuration=D1M}",
            "Invalid billing period interval count '' in {basePlanId=Base plan ID, productId=Product ID, rawDuration=PM}",
            "Invalid billing period interval count '' in {basePlanId=Base plan ID, productId=Product ID, rawDuration=P-1M}",
            "Unrecognized billing interval period designator 'U' in {basePlanId=Base plan ID, productId=Product ID, rawDuration=P1U}",
            "Unrecognized billing interval period designator 'MY' in {basePlanId=Base plan ID, productId=Product ID, rawDuration=P1MY}",
            "Unrecognized billing interval period designator '' in {basePlanId=Base plan ID, productId=Product ID, rawDuration=P1}",
        )
    }
}
