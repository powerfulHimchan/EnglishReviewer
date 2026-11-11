package app.englishreviewer

import android.app.Application
import app.englishreviewer.data.*
import app.englishreviewer.notifications.ReviewScheduler
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class ReviewerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            ReviewScheduler.schedule(this@ReviewerApplication, Settings(this@ReviewerApplication).flow.first())
        }
    }
}
