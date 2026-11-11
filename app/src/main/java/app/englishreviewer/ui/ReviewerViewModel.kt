package app.englishreviewer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.englishreviewer.core.*
import app.englishreviewer.data.*
import app.englishreviewer.notifications.ReviewScheduler
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.LocalDate

class ReviewerViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application
    private val db = LearningDatabase.get(context)
    private val dao = db.dao()
    val settingsStore = Settings(context)
    val settings = settingsStore.flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())
    val sentences = dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val date = MutableStateFlow(LocalDate.now())
    @OptIn(ExperimentalCoroutinesApi::class)
    val review = date.flatMapLatest { dao.observeReview(it.toString()) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 5)
    var saving = MutableStateFlow(false)
        private set
    fun refreshDay() {
        date.value = LocalDate.now()
        viewModelScope.launch(Dispatchers.IO) { db.ensureReview(date.value) }
    }
    init { refreshDay() }
    fun add(original: String, onSaved: (String) -> Unit) {
        if (saving.value) return
        saving.value = true
        viewModelScope.launch {
            try {
                val text = validateSentence(original)
                val id = withContext(Dispatchers.IO) {
                    val existing = dao.findNormalized(normalizeSentence(text))
                    if (existing != null) {
                        messages.emit("이미 저장한 문장이에요.")
                        existing.id
                    } else {
                        val sentence = Sentence(original = text, status = "queued")
                        dao.insert(sentence)
                        AnalysisWorker.enqueue(context, sentence.id)
                        messages.emit("문장을 저장했어요.")
                        sentence.id
                    }
                }
                refreshDay()
                onSaved(id)
            } catch (e: Exception) { messages.emit(e.message ?: "문장을 저장하지 못했어요.") }
            finally { saving.value = false }
        }
    }
    fun retry(id: String) = viewModelScope.launch(Dispatchers.IO) {
        dao.status(id, "queued")
        AnalysisWorker.enqueue(context, id, replace = true)
    }
    fun delete(id: String) = viewModelScope.launch(Dispatchers.IO) {
        androidx.work.WorkManager.getInstance(context).cancelUniqueWork("analysis-$id")
        dao.delete(id)
    }
    fun complete(id: String, value: Boolean) = viewModelScope.launch(Dispatchers.IO) { dao.complete(date.value.toString(), id, value) }
    fun reminders(enabled: Boolean) = viewModelScope.launch {
        settingsStore.saveReminders(enabled)
        ReviewScheduler.schedule(context, settingsStore.flow.first())
    }
    fun time(hour: Int, minute: Int) = viewModelScope.launch {
        settingsStore.saveTime(hour, minute)
        ReviewScheduler.schedule(context, settingsStore.flow.first())
    }
    fun server(url: String, token: String) = viewModelScope.launch {
        try {
            withContext(Dispatchers.IO) { settingsStore.saveServer(url, token) }
            messages.emit("연결 설정을 저장했어요. 보관함에서 문장 분석을 다시 시도할 수 있어요.")
        } catch (e: Exception) { messages.emit(e.message ?: "설정을 저장하지 못했어요.") }
    }
}
