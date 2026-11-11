package app.englishreviewer.data

import android.content.Context
import androidx.work.*
import app.englishreviewer.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.io.IOException

class AnalysisWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val id = inputData.getString("sentenceId") ?: return@withContext Result.failure()
        val dao = LearningDatabase.get(applicationContext).dao()
        val sentence = dao.find(id) ?: return@withContext Result.success()
        if (sentence.analysisJson != null) return@withContext Result.success()
        try {
            val store = Settings(applicationContext)
            val settings = store.flow.first()
            if (settings.endpoint.isBlank() || settings.encryptedToken.isBlank()) {
                dao.status(id, "failed", "설정에서 AI 서버 주소와 연결 토큰을 입력한 뒤 다시 분석해 주세요.")
                return@withContext Result.failure()
            }
            dao.status(id, "analyzing")
            val body = learningJson.encodeToString(AnalyzeRequest(sentence.original)).toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(settings.endpoint + "/api/analyze")
                .header("Authorization", "Bearer ${store.decrypt(settings)}").post(body).build()
            client.newCall(request).execute().use { response ->
                if (response.code == 429 || response.code >= 500) throw IOException("서버가 응답하지 않아요.")
                if (!response.isSuccessful) {
                    val message = if (response.code == 401) "연결 토큰을 확인해 주세요." else "분석 요청을 처리하지 못했어요. 서버 설정과 문장을 확인해 주세요."
                    dao.status(id, "failed", message)
                    return@withContext Result.failure()
                }
                val text = response.body?.string() ?: throw IOException("빈 응답")
                val analysis = learningJson.decodeFromString<Analysis>(text).validate()
                val search = listOf(analysis.corrected, analysis.translation).joinToString(" ")
                dao.analyzed(id, learningJson.encodeToString(analysis), search)
            }
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: IOException) {
            if (runAttemptCount < 2) {
                dao.status(id, "queued", "연결을 기다리고 있어요. 자동으로 다시 시도할게요.")
                Result.retry()
            } else {
                dao.status(id, "failed", "서버에 연결하지 못했어요. 네트워크와 서버를 확인한 뒤 다시 분석해 주세요.")
                Result.failure()
            }
        } catch (e: Exception) {
            dao.status(id, "failed", "분석 결과를 읽지 못했어요. 연결 설정을 확인하고 다시 분석해 주세요.")
            Result.failure()
        }
    }
    companion object {
        private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(75, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
        fun enqueue(context: Context, id: String, replace: Boolean = false) {
            val request = OneTimeWorkRequestBuilder<AnalysisWorker>()
                .setInputData(workDataOf("sentenceId" to id))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
            WorkManager.getInstance(context).enqueueUniqueWork("analysis-$id", if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, request)
        }
    }
}
