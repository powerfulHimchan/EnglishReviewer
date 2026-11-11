package app.englishreviewer.ui

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.englishreviewer.data.*
import app.englishreviewer.notifications.ReviewScheduler
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Green = Color(0xFF155D50)
private val Cream = Color(0xFFF8F8F2)
private val Ink = Color(0xFF1D302A)
private val Muted = Color(0xFF63736C)
private val colors = lightColorScheme(primary = Green, secondary = Color(0xFF526C55), background = Cream,
    surface = Cream, onSurface = Ink, surfaceContainer = Color(0xFFEAF0E8), surfaceContainerLow = Color.White,
    primaryContainer = Color(0xFFDCEDE5), onPrimaryContainer = Ink)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewerApp(reviewRequest: Int = 0, vm: ReviewerViewModel = viewModel()) {
    val sentences by vm.sentences.collectAsStateWithLifecycle()
    val review by vm.review.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val date by vm.date.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var permissionRefresh by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) { vm.refreshDay(); permissionRefresh++ } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) { while (true) { delay(60_000); vm.refreshDay() } }
    LaunchedEffect(reviewRequest) { if (reviewRequest > 0) { tab = 0; selectedId = null; adding = false } }
    BackHandler(selectedId != null || adding || tab != 0) {
        if (selectedId != null) selectedId = null else if (adding) adding = false else tab = 0
    }
    val selected = sentences.firstOrNull { it.id == selectedId }
    MaterialTheme(colorScheme = colors, typography = Typography()) {
        Scaffold(
            containerColor = Cream,
            topBar = { TopAppBar(
                title = { Column {
                    Text(if (selectedId != null) "문장 자세히" else if (adding) "새 문장" else "문장복습", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    if (selectedId == null && !adding) Text("매일 세 문장, 내 영어로", fontSize = 12.sp, color = Muted)
                } },
                navigationIcon = { if (selectedId != null || adding) IconButton(onClick = { selectedId = null; adding = false }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "뒤로")
                } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream)
            ) },
            bottomBar = { if (selectedId == null && !adding) NavigationBar(containerColor = Color.White) {
                val labels = listOf("오늘의 복습", "보관함", "설정")
                val icons = listOf(Icons.Outlined.WbSunny, Icons.AutoMirrored.Outlined.MenuBook, Icons.Outlined.Settings)
                labels.forEachIndexed { index, label -> NavigationBarItem(selected = tab == index, onClick = { tab = index },
                    icon = { Icon(icons[index], null) }, label = { Text(label) }) }
            } },
            snackbarHost = { SnackbarHost(snackbar) },
            floatingActionButton = { if (selectedId == null && !adding && tab == 1) FloatingActionButton(onClick = { adding = true }, containerColor = Green, contentColor = Color.White) { Icon(Icons.Outlined.Add, "문장 등록") } }
        ) { padding ->
            val modifier = Modifier.fillMaxSize().padding(padding)
            when {
                adding -> AddScreen(saving, modifier) { vm.add(it) { id -> adding = false; selectedId = id } }
                selectedId != null && selected != null -> DetailScreen(selected, review.firstOrNull { it.sentence.id == selected.id }, modifier,
                    retry = { vm.retry(selected.id) }, delete = { vm.delete(selected.id); selectedId = null }, complete = { vm.complete(selected.id, it) })
                selectedId != null -> Box(modifier, contentAlignment = Alignment.Center) { Text("문장을 불러오고 있어요.") }
                tab == 0 -> LazyColumn(modifier, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item {
                        Text(date.format(DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)), color = Muted, fontSize = 14.sp)
                        Spacer(Modifier.height(10.dp))
                        Text("오늘도 세 문장씩", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        Text("저장한 표현을 내 것으로 만드는 시간", color = Muted, modifier = Modifier.padding(top = 8.dp))
                    }
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = Green), shape = RoundedCornerShape(24.dp)) {
                            Column(Modifier.padding(24.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("TODAY’S REVIEW", color = Color(0xFFCBE5DA), fontSize = 12.sp, letterSpacing = 2.sp)
                                Text("${review.count { it.completed }} / ${review.size} 문장 복습", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                                LinearProgressIndicator(progress = { if (review.isEmpty()) 0f else review.count { it.completed }.toFloat() / review.size },
                                    modifier = Modifier.fillMaxWidth(), color = Color(0xFFBDE5B1), trackColor = Color.White.copy(alpha = .2f))
                                Text(if (review.isEmpty()) "나만의 첫 문장을 등록해 보세요." else "문장을 열어 설명을 읽고 복습을 완료해 보세요.", color = Color.White, fontSize = 13.sp)
                            }
                        }
                    }
                    if (review.isEmpty()) item { EmptyCard("영어 공부를 기록해 볼까요?", "기억하고 싶은 문장을 저장하면\n문법, 예문, 유사 표현을 함께 볼 수 있어요.", "첫 문장 등록", { adding = true }) }
                    items(review, key = { it.sentence.id }) { entry -> SentenceCard(entry.sentence, entry.completed) { selectedId = entry.sentence.id } }
                    item { Text("보관함 ${sentences.size}문장 · 최근에 뽑히지 않은 문장부터 복습해요.", color = Muted, fontSize = 12.sp) }
                    if (review.isNotEmpty()) item { OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Add, null); Text("새 문장 등록") } }
                }
                tab == 1 -> LibraryScreen(sentences, modifier) { selectedId = it }
                else -> SettingsScreen(settings, vm, permissionRefresh, modifier)
            }
        }
    }
}

@Composable
private fun AddScreen(saving: Boolean, modifier: Modifier, save: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    Column(modifier.verticalScroll(rememberScrollState()).padding(20.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("어떤 표현을 기억할까요?", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("직접 만든 문장도 좋아요. 원문을 보관하고 더 자연스러운 표현을 함께 배워요.", color = Muted)
        OutlinedTextField(value = text, onValueChange = { if (it.length <= 2000) text = it }, label = { Text("영어 문장") },
            placeholder = { Text("I'm in charge of cooking at home.") }, minLines = 6, maxLines = 12,
            modifier = Modifier.fillMaxWidth(), supportingText = { Text("${text.length} / 2,000") }, shape = RoundedCornerShape(16.dp))
        Button(onClick = { save(text) }, enabled = text.isNotBlank() && !saving, modifier = Modifier.fillMaxWidth().height(54.dp)) {
            if (saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.Add, null)
            Spacer(Modifier.width(8.dp)); Text(if (saving) "저장 중" else "저장하고 분석하기")
        }
        Text("등록한 문장은 휴대폰에 저장돼요. AI 분석 시 입력한 문장이 설정한 서버와 AI 제공업체에 전송돼요.", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun LibraryScreen(sentences: List<Sentence>, modifier: Modifier, open: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val key = query.trim().lowercase(Locale.ROOT)
    val filtered = remember(sentences, key) { sentences.filter { (it.original + " " + it.searchText).lowercase(Locale.ROOT).contains(key) } }
    LazyColumn(modifier, contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("내 문장 보관함", fontSize = 28.sp, fontWeight = FontWeight.Bold) }
        item { OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            placeholder = { Text("영어 문장이나 한국어 뜻 검색") }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Outlined.Close, "검색어 지우기") } }, shape = RoundedCornerShape(16.dp)) }
        item { Text("${filtered.size}개의 문장", color = Muted, fontSize = 13.sp) }
        if (filtered.isEmpty()) item { Text(if (query.isEmpty()) "아직 저장한 문장이 없어요. + 버튼으로 등록해 보세요." else "검색 결과가 없어요. 다른 단어로 찾아보세요.", color = Muted) }
        items(filtered, key = { it.id }) { sentence -> SentenceCard(sentence) { open(sentence.id) } }
    }
}

@Composable
private fun SentenceCard(sentence: Sentence, completed: Boolean = false, open: () -> Unit) {
    val analysis = remember(sentence.analysisJson) { sentence.analysis() }
    Card(onClick = open, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(20.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (completed) "복습 완료" else if (analysis != null) "학습 준비 완료" else statusLabel(sentence.status), color = Green, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Icon(if (completed) Icons.Outlined.CheckCircle else Icons.AutoMirrored.Outlined.ArrowForward, null, tint = Green, modifier = Modifier.size(19.dp))
            }
            Text(analysis?.corrected ?: sentence.original, fontSize = 19.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold)
            analysis?.let { Text(it.translation, color = Muted, fontSize = 14.sp) }
        }
    }
}
private fun statusLabel(status: String) = when (status) { "queued" -> "분석 대기"; "analyzing" -> "문장 분석 중"; "failed" -> "분석 재시도 필요"; else -> "저장한 문장" }

@Composable
private fun DetailScreen(sentence: Sentence, review: ReviewEntry?, modifier: Modifier, retry: () -> Unit, delete: () -> Unit, complete: (Boolean) -> Unit) {
    val analysis = remember(sentence.analysisJson) { sentence.analysis() }
    var deleting by remember { mutableStateOf(false) }
    LazyColumn(modifier, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { ContentCard("내가 등록한 문장") { SelectableText(sentence.original, 22) } }
        if (analysis == null) item {
            ContentCard(statusLabel(sentence.status)) {
                if (sentence.status in listOf("queued", "analyzing")) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(sentence.error ?: "분석이 완료되면 문법 설명, 예문, 유사 표현이 여기에 표시돼요.", color = Muted)
                if (sentence.status !in listOf("queued", "analyzing")) Button(onClick = retry) { Text("다시 분석하기") }
            }
        } else {
            item { ContentCard("자연스러운 문장") { SelectableText(analysis.corrected, 24); Text(analysis.translation, color = Muted); HorizontalDivider(); Text(analysis.correctionNote, fontSize = 14.sp) } }
            item { ContentCard("문법 포인트") { analysis.grammar.forEachIndexed { index, point ->
                Text("${index + 1}. ${point.title}", fontWeight = FontWeight.Bold, color = Green)
                Text(point.explanation, lineHeight = 25.sp)
            } } }
            item { ContentCard("사용 예시") { analysis.examples.forEachIndexed { index, example ->
                if (index > 0) HorizontalDivider(color = Color(0xFFE5EBE5))
                SelectableText(example.english); Text(example.korean, color = Muted, fontSize = 14.sp)
            } } }
            item { ContentCard("유사 표현") { analysis.similar.forEachIndexed { index, similar ->
                if (index > 0) HorizontalDivider(color = Color(0xFFE5EBE5))
                SelectableText(similar.english); Text(similar.korean, color = Muted, fontSize = 14.sp)
                Text(similar.nuance, color = Green, fontSize = 14.sp)
            } } }
            if (review != null) item { Button(onClick = { complete(!review.completed) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Outlined.CheckCircle, null); Spacer(Modifier.width(8.dp)); Text(if (review.completed) "복습 완료 · 다시 학습하기" else "오늘의 복습 완료")
            } }
            item { Text("AI 설명에는 오류가 있을 수 있어요.", color = Muted, fontSize = 12.sp) }
        }
        item { TextButton(onClick = { deleting = true }, modifier = Modifier.fillMaxWidth()) { Text("문장 삭제", color = MaterialTheme.colorScheme.error) } }
    }
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("문장을 삭제할까요?") },
        text = { Text("문장과 저장된 학습 내용이 삭제돼요.") }, confirmButton = { TextButton({ deleting = false; delete() }) { Text("삭제") } },
        dismissButton = { TextButton({ deleting = false }) { Text("취소") } })
}

@Composable
private fun ContentCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, fontSize = 13.sp, color = Green, fontWeight = FontWeight.Bold)
            content()
        }
    }
}
@Composable private fun SelectableText(text: String, size: Int = 17) { SelectionContainer { Text(text, fontSize = size.sp, lineHeight = (size + 10).sp, fontWeight = FontWeight.Medium) } }
@Composable private fun EmptyCard(title: String, body: String, action: String, onClick: () -> Unit) {
    ContentCard(title) { Text(body, color = Muted, lineHeight = 25.sp); Button(onClick = onClick) { Text(action) } }
}

@Composable
private fun SettingsScreen(settings: AppSettings, vm: ReviewerViewModel, refresh: Int, modifier: Modifier) {
    val context = LocalContext.current
    var endpoint by remember(settings.endpoint) { mutableStateOf(settings.endpoint) }
    var token by remember(settings.encryptedToken) { mutableStateOf(runCatching { vm.settingsStore.decrypt(settings) }.getOrDefault("")) }
    val exact = remember(refresh) { ReviewScheduler.exactAllowed(context) }
    val allowed = remember(refresh) { NotificationManagerCompat.from(context).areNotificationsEnabled() }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> vm.reminders(granted) }
    LazyColumn(modifier, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Text("나의 학습 설정", fontSize = 28.sp, fontWeight = FontWeight.Bold) }
        item { ContentCard("매일 복습") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("복습 알림", fontWeight = FontWeight.Bold); Text("하루에 한 번, 저장한 문장 복습", fontSize = 12.sp, color = Muted) }
                Switch(settings.reminders, onCheckedChange = { enabled ->
                    if (enabled && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else vm.reminders(enabled)
                })
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().clickable { TimePickerDialog(context, { _, h, m -> vm.time(h, m) }, settings.hour, settings.minute, true).show() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Schedule, null, tint = Green); Spacer(Modifier.width(12.dp)); Text("복습 시간", modifier = Modifier.weight(1f))
                Text("%02d:%02d".format(settings.hour, settings.minute), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Green)
            }
            if (settings.reminders && !allowed) {
                Text("알림이 차단되어 있어요. 휴대폰 설정에서 허용해 주세요.", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                TextButton({ context.startActivity(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)) }) { Text("알림 권한 설정") }
            }
            Text(if (exact) "설정한 시각에 알림을 예약해요." else "현재는 절전 상태에 따라 알림이 늦어질 수 있어요.", color = Muted, fontSize = 13.sp)
            if (!exact && Build.VERSION.SDK_INT >= 31) OutlinedButton({ context.startActivity(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))) }) { Text("정확한 시간 알림 허용") }
        } }
        item { ContentCard("AI 분석 연결") {
            Text("문장을 분석할 서버를 연결해 주세요. 연결 전에도 문장 저장과 검색은 사용할 수 있어요.", color = Muted, fontSize = 14.sp)
            OutlinedTextField(endpoint, { endpoint = it }, label = { Text("서버 주소") }, placeholder = { Text("https://your-server.example") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(token, { token = it }, label = { Text("연결 토큰") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Button({ vm.server(endpoint, token) }, modifier = Modifier.fillMaxWidth()) { Text("연결 설정 저장") }
        } }
        item { ContentCard("저장 안내") {
            Text("문장과 학습 내용은 이 휴대폰에 저장돼요. 앱을 삭제하면 함께 삭제되니 주의해 주세요.", color = Muted, fontSize = 14.sp)
            Text("문장복습 0.1.0", color = Muted, fontSize = 12.sp)
        } }
    }
}
