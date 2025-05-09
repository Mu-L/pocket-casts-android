package au.com.shiftyjelly.pocketcasts.repositories.di

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import au.com.shiftyjelly.pocketcasts.crashlogging.di.ProvideApplicationScope
import au.com.shiftyjelly.pocketcasts.payment.Logger
import au.com.shiftyjelly.pocketcasts.payment.PaymentDataSource
import au.com.shiftyjelly.pocketcasts.repositories.lists.ListRepository
import au.com.shiftyjelly.pocketcasts.repositories.sync.SyncAccountManager
import au.com.shiftyjelly.pocketcasts.repositories.sync.SyncManager
import au.com.shiftyjelly.pocketcasts.servers.server.ListWebService
import au.com.shiftyjelly.pocketcasts.servers.sync.TokenHandler
import au.com.shiftyjelly.pocketcasts.utils.Util
import au.com.shiftyjelly.pocketcasts.utils.log.LogBuffer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import timber.log.Timber

@Module
@InstallIn(SingletonComponent::class)
class RepositoryProviderModule {

    @Provides
    @Singleton
    fun provideTokenHandler(syncAccountManager: SyncAccountManager): TokenHandler = syncAccountManager

    @Provides
    @Singleton
    @ApplicationScope
    fun coroutineScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    fun provideApplicationScope(
        @ApplicationScope appScope: CoroutineScope,
    ): ProvideApplicationScope = ProvideApplicationScope { appScope }

    @Provides
    @Singleton
    @ProcessLifecycle
    fun processLifecycle(): LifecycleOwner = ProcessLifecycleOwner.get()

    @Provides
    fun providePaymentLogger(): Logger = object : Logger {
        private val TAG = "Payments"

        override fun info(message: String) {
            Timber.tag(TAG).i(message)
        }

        override fun warning(message: String) {
            Timber.tag(TAG).w(message)
            LogBuffer.w(TAG, message)
        }

        override fun error(message: String, exception: Throwable) {
            Timber.tag(TAG).e(exception, message)
            LogBuffer.e(TAG, exception, message)
        }
    }

    @Provides
    @Singleton
    fun providePaymentDataSource(
        @ApplicationContext context: Context,
        logger: Logger,
    ): PaymentDataSource {
        return if (context.packageName == "au.com.shiftyjelly.pocketcasts") {
            PaymentDataSource.billing(context, logger)
        } else {
            PaymentDataSource.fake()
        }
    }

    @Provides
    @Singleton
    internal fun provideDiscoverRepository(listWebService: ListWebService, syncManager: SyncManager, @ApplicationContext context: Context): ListRepository {
        val platform = if (Util.isAutomotive(context)) "automotive" else "android"
        return ListRepository(
            listWebService,
            syncManager,
            platform,
        )
    }
}
