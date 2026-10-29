package com.example.mangaverse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

private val Ink = Color(0xFF191744)
private val Violet = Color(0xFF6754E8)
private val Paper = Color(0xFFF8F8FC)
private val Muted = Color(0xFF77788E)

data class Manga(
    val id: String, val title: String, val genre: String, val rating: String,
    val chapters: Int, val color: Color, val emoji: String, val description: String
)

private val demoManga = listOf(
    Manga("solo", "Solo Leveling", "أكشن • فانتازيا", "9.8", 200, Color(0xFF272A59), "⚔️", "صياد ضعيف يحصل على فرصة فريدة ليتطور ويتحدى أخطر الأبراج."),
    Manga("onepiece", "One Piece", "مغامرات • كوميديا", "9.7", 1120, Color(0xFFFFE0B5), "🏴‍☠️", "رحلة لوفي وطاقمه للعثور على الكنز الأسطوري ون بيس."),
    Manga("naruto", "Naruto", "نينجا • مغامرات", "9.2", 700, Color(0xFFFFD6A1), "🍥", "حكاية نينجا يحلم بأن يصبح الهوكاغي ويحمي قريته."),
    Manga("jujutsu", "Jujutsu Kaisen", "أكشن • خارق للطبيعة", "9.4", 270, Color(0xFF34334D), "👁️", "طلاب يواجهون لعنات خطيرة في عالم مليء بالأسرار."),
    Manga("demon", "Demon Slayer", "أكشن • تاريخي", "9.3", 205, Color(0xFFBFE7D9), "🌊", "رحلة تانجيرو لإنقاذ أخته ومواجهة الشياطين.")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MangaVerseApp() }
    }
}

@Composable
fun MangaVerseApp() {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("mangaverse_local", android.content.Context.MODE_PRIVATE) }
    var catalog by remember { mutableStateOf(demoManga) }
    var catalogMessage by remember { mutableStateOf("") }
    DisposableEffect(Unit) {
        var registration: com.google.firebase.firestore.ListenerRegistration? = null
        try {
            registration = FirebaseFirestore.getInstance().collection("manga")
                .whereEqualTo("status", "published")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) { catalogMessage = "تعذّر تحديث المكتبة؛ نعرض المحتوى المحلي."; return@addSnapshotListener }
                    val remote = snapshot?.documents.orEmpty().mapNotNull { doc ->
                        val title = doc.getString("title")?.trim().orEmpty()
                        if (title.isBlank()) return@mapNotNull null
                        Manga(
                            id = doc.id,
                            title = title,
                            genre = doc.getString("genre") ?: "غير مصنّف",
                            rating = doc.getDouble("rating")?.toString() ?: "—",
                            chapters = (doc.getLong("chapterCount") ?: doc.getLong("chapters") ?: 1L).toInt().coerceAtLeast(1),
                            color = Color(0xFFE9E5FF),
                            emoji = "📚",
                            description = doc.getString("description") ?: "لا يوجد وصف متاح حالياً."
                        )
                    }
                    if (remote.isNotEmpty()) { catalog = remote; catalogMessage = "" }
                    else catalogMessage = "ماكو مانجا منشورة بقاعدة البيانات بعد؛ نعرض أمثلة محلية."
                }
        } catch (_: IllegalStateException) {
            catalogMessage = "Firebase غير مهيأ؛ نعرض أمثلة محلية."
        }
        onDispose { registration?.remove() }
    }
    var tab by remember { mutableStateOf("الرئيسية") }
    var selected by remember { mutableStateOf<Manga?>(null) }
    var reader by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var favorites by remember {
        mutableStateOf(preferences.getStringSet("favorite_ids", emptySet())?.toSet() ?: emptySet())
    }
    var userEmail by remember { mutableStateOf(currentFirebaseUserEmail()) }
    var lastReadMangaId by remember { mutableStateOf(preferences.getString("last_read_manga_id", "").orEmpty()) }
    var lastReadChapter by remember { mutableIntStateOf(preferences.getInt("last_read_chapter", 1)) }
    var showAuth by remember { mutableStateOf(false) }
    var admin by remember { mutableStateOf(false) }

    MaterialTheme(colorScheme = lightColorScheme(primary = Violet, background = Paper, surface = Color.White)) {
        Surface(Modifier.fillMaxSize(), color = Paper) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                when {
                    reader && selected != null -> ReaderScreen(
                        manga = selected!!,
                        initialChapter = preferences.getInt("last_read_chapter_${selected!!.id}", 1),
                        onBack = { reader = false },
                        onProgress = { chapter ->
                            preferences.edit()
                                .putString("last_read_manga_id", selected!!.id)
                                .putInt("last_read_chapter", chapter)
                                .apply()
                            lastReadMangaId = selected!!.id
                            lastReadChapter = chapter
                            syncReadingProgress(selected!!.id, chapter)
                        }
                    )
                    selected != null -> DetailScreen(
                        manga = selected!!,
                        favorite = favorites.contains(selected!!.id),
                        onBack = { selected = null },
                        onRead = { chapter ->
                            preferences.edit().putInt("last_read_chapter_${selected!!.id}", chapter).apply()
                            reader = true
                        },
                        onFavorite = {
                            favorites = if (favorites.contains(selected!!.id)) favorites - selected!!.id else favorites + selected!!.id
                            preferences.edit().putStringSet("favorite_ids", favorites.toSet()).apply()
                            syncFavorite(selected!!.id, favorites.contains(selected!!.id))
                        }
                    )
                    showAuth -> AuthScreen(
                        onBack = { showAuth = false },
                        onSignedIn = { email -> userEmail = email; showAuth = false }
                    )
                    else -> Scaffold(
                        containerColor = Paper,
                        bottomBar = {
                            NavigationBar(containerColor = Color.White) {
                                val tabs = listOf("الرئيسية", "استكشاف", "مكتبتي", "حسابي")
                                val icons = listOf(Icons.Default.Home, Icons.Default.Explore, Icons.Default.Bookmarks, Icons.Default.Person)
                                tabs.forEachIndexed { index, label ->
                                    NavigationBarItem(
                                        selected = tab == label,
                                        onClick = { tab = label },
                                        icon = { Icon(icons[index], contentDescription = label) },
                                        label = { Text(label, fontSize = 11.sp) },
                                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Violet, indicatorColor = Color(0xFFECE9FF), selectedTextColor = Violet)
                                    )
                                }
                            }
                        }
                    ) { padding ->
                        Column(Modifier.fillMaxSize().padding(padding)) {
                            Header(userEmail)
                            if (catalogMessage.isNotBlank()) Text(catalogMessage, color = Muted, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
                            when (tab) {
                                "الرئيسية" -> HomeScreen(
                                    catalog = catalog, search = search, onSearch = { search = it },
                                    onOpen = { selected = it },
                                    onExplore = { tab = "استكشاف" },
                                    lastReadManga = catalog.firstOrNull { it.id == lastReadMangaId },
                                    lastReadChapter = lastReadChapter,
                                    onContinue = { manga, chapter ->
                                        selected = manga
                                        preferences.edit().putInt("last_read_chapter_${manga.id}", chapter).apply()
                                        reader = true
                                    }
                                )
                                "استكشاف" -> ExploreScreen(catalog, search, { search = it }, { selected = it })
                                "مكتبتي" -> LibraryScreen(catalog.filter { favorites.contains(it.id) }, { selected = it })
                                else -> AccountScreen(
                                    email = userEmail,
                                    onAuth = { showAuth = true },
                                    onSignOut = {
                                        try {
                                            FirebaseAuth.getInstance().signOut()
                                            userEmail = ""
                                        } catch (_: IllegalStateException) {
                                            userEmail = ""
                                        }
                                    },
                                    onAdmin = { admin = !admin },
                                    admin = admin
                                )
                            }
                            if (admin) AdminPanel(onClose = { admin = false })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(email: String) {
    Row(
        Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(Violet), contentAlignment = Alignment.Center) {
                Text("M", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
            }
            Column {
                Text("MangaVerse", color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                Text(if (email.isBlank()) "عالم المانجا بين يديك" else email, color = Muted, fontSize = 10.sp)
            }
        }
        Icon(Icons.Default.NotificationsNone, contentDescription = "الإشعارات", tint = Ink)
    }
}

@Composable
private fun HomeScreen(
    catalog: List<Manga>, search: String, onSearch: (String) -> Unit,
    onOpen: (Manga) -> Unit, onExplore: () -> Unit,
    lastReadManga: Manga?, lastReadChapter: Int,
    onContinue: (Manga, Int) -> Unit
) {
    val visibleCatalog = catalog.filter {
        search.isBlank() || it.title.contains(search, true) || it.genre.contains(search, true) || it.description.contains(search, true)
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            OutlinedTextField(
                value = search, onValueChange = onSearch, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("ابحث عن مانجا، اسم أو تصنيف...") },
                leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true,
                shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color(0xFFE5E4EF), focusedBorderColor = Violet)
            )
        }
        if (lastReadManga != null) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFFECE9FF)).clickable { onContinue(lastReadManga, lastReadChapter) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Cover(lastReadManga, Modifier.size(62.dp, 76.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("كمّل قراءتك", color = Violet, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(lastReadManga.title, color = Ink, fontWeight = FontWeight.ExtraBold)
                        Text("الفصل $lastReadChapter", color = Muted, fontSize = 12.sp)
                    }
                    Icon(Icons.Default.PlayArrow, contentDescription = "متابعة القراءة", tint = Violet)
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Box(Modifier.fillMaxWidth().height(174.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF24234E)).clickable { onOpen(catalog.firstOrNull() ?: demoManga[0]) }.padding(20.dp)) {
                    Column(Modifier.align(Alignment.CenterStart), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("اختيار الأسبوع", color = Color(0xFFC9C2FF), fontSize = 12.sp)
                        Text("Solo Leveling", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
                        Text("ابدأ المغامرة الآن", color = Color.White.copy(alpha = .85f), fontSize = 13.sp)
                        Surface(color = Violet, shape = RoundedCornerShape(10.dp), modifier = Modifier.clickable { onOpen(catalog.firstOrNull() ?: demoManga[0]) }) {
                            Text("اقرأ الآن  ←", color = Color.White, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), fontWeight = FontWeight.Bold)
                        }
                    }
                    Text("⚔", Modifier.align(Alignment.CenterEnd).padding(end = 8.dp), color = Color(0xFF9B91FF), fontSize = 76.sp)
                }
            }
        }
        item { SectionTitle("الأكثر قراءة", "عرض الكل", onExplore) }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(visibleCatalog) { manga -> MangaPoster(manga, onClick = { onOpen(manga) }) }
            }
        }
        item { SectionTitle("أحدث الفصول", "كل الفصول", onExplore) }
        items(visibleCatalog.take(4)) { manga ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).clickable { onOpen(manga) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Cover(manga, Modifier.size(66.dp, 78.dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(manga.title, fontWeight = FontWeight.Bold, color = Ink)
                    Text("الفصل ${manga.chapters} • منذ يوم", color = Muted, fontSize = 12.sp)
                    Text("تابع القراءة ←", color = Violet, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Icon(Icons.Default.ChevronLeft, null, tint = Muted)
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, action: String, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontWeight = FontWeight.ExtraBold, color = Ink, fontSize = 18.sp)
        Text(action, color = Violet, fontSize = 12.sp, modifier = Modifier.clickable { onAction() })
    }
}

@Composable
private fun Cover(manga: Manga, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(manga.color), contentAlignment = Alignment.Center) {
        Text(manga.emoji, fontSize = 32.sp)
    }
}

@Composable
private fun MangaPoster(manga: Manga, onClick: () -> Unit) {
    Column(Modifier.width(126.dp).clickable { onClick() }, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Box {
            Cover(manga, Modifier.fillMaxWidth().height(150.dp))
            Surface(Modifier.align(Alignment.TopStart).padding(7.dp), color = Color.White.copy(alpha = .94f), shape = RoundedCornerShape(8.dp)) {
                Row(Modifier.padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, null, tint = Color(0xFFFFB62E), modifier = Modifier.size(12.dp))
                    Text(manga.rating, fontSize = 10.sp, color = Ink, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text(manga.title, color = Ink, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
        Text(manga.genre, color = Muted, maxLines = 1, fontSize = 10.sp)
    }
}

@Composable
private fun ExploreScreen(catalog: List<Manga>, search: String, onSearch: (String) -> Unit, onOpen: (Manga) -> Unit) {
    val filtered = catalog.filter { it.title.contains(search, true) || it.genre.contains(search, true) }
    Column(Modifier.fillMaxSize()) {
        Text("استكشاف المانجا", fontSize = 24.sp, color = Ink, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(20.dp))
        OutlinedTextField(search, onSearch, Modifier.fillMaxWidth().padding(horizontal = 16.dp), placeholder = { Text("اسم المانجا أو التصنيف") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, shape = RoundedCornerShape(14.dp))
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(filtered) { manga ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).clickable { onOpen(manga) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Cover(manga, Modifier.size(78.dp, 94.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(manga.title, color = Ink, fontWeight = FontWeight.Bold)
                        Text(manga.genre, color = Muted, fontSize = 12.sp)
                        Text("★ ${manga.rating}   •   ${manga.chapters} فصل", color = Violet, fontSize = 12.sp)
                    }
                    Icon(Icons.Default.ChevronLeft, null, tint = Muted)
                }
            }
        }
    }
}

@Composable
private fun DetailScreen(manga: Manga, favorite: Boolean, onBack: () -> Unit, onRead: (Int) -> Unit, onFavorite: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowForward, "رجوع") }
            IconButton(onClick = onFavorite) { Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "المفضلة", tint = if (favorite) Color.Red else Violet) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(manga, Modifier.size(132.dp, 176.dp))
            Column(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(manga.title, color = Ink, fontWeight = FontWeight.Black, fontSize = 23.sp)
                Text(manga.genre, color = Muted, fontSize = 12.sp)
                Text("★ ${manga.rating}  •  ${manga.chapters} فصل", color = Violet, fontWeight = FontWeight.Bold)
                Text("الحالة: مستمرة", color = Muted, fontSize = 12.sp)
            }
        }
        Button(onClick = { onRead(1) }, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp).height(52.dp), shape = RoundedCornerShape(15.dp)) {
            Icon(Icons.Default.MenuBook, null); Spacer(Modifier.width(8.dp)); Text("ابدأ القراءة")
        }
        Text("القصة", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        Text(manga.description, color = Muted, lineHeight = 25.sp, modifier = Modifier.padding(horizontal = 20.dp))
        Text("الفصول", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(20.dp))
        (1..minOf(12, manga.chapters)).reversed().forEach { chapter ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).clickable { onRead(chapter) }.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("الفصل $chapter", color = Ink, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Default.ChevronLeft, null, tint = Violet)
            }
        }
    }
}

@Composable
private fun ReaderScreen(manga: Manga, initialChapter: Int, onBack: () -> Unit, onProgress: (Int) -> Unit) {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences("mangaverse_local", android.content.Context.MODE_PRIVATE)
    }
    var chapter by remember(manga.id) {
        mutableIntStateOf(
            initialChapter.coerceIn(1, manga.chapters.coerceAtLeast(1))
        )
    }
    var showControls by remember { mutableStateOf(true) }
    var pageUrls by remember(manga.id, chapter) { mutableStateOf<List<String>>(emptyList()) }
    var pageLoadMessage by remember(manga.id, chapter) { mutableStateOf("جاري البحث عن صفحات الفصل…") }
    LaunchedEffect(manga.id, chapter) {
        pageUrls = emptyList()
        pageLoadMessage = "جاري البحث عن صفحات الفصل…"
        try {
            FirebaseFirestore.getInstance()
                .collection("manga").document(manga.id)
                .collection("chapters").document(chapter.toString())
                .get()
                .addOnSuccessListener { document ->
                    val urls = (document.get("pageUrls") as? List<*>)
                        ?: (document.get("pages") as? List<*>)
                        ?: emptyList<Any>()
                    pageUrls = urls.filterIsInstance<String>().filter { it.startsWith("https://") }
                    pageLoadMessage = if (pageUrls.isEmpty()) "ماكو صفحات منشورة لهذا الفصل بعد." else ""
                }
                .addOnFailureListener { pageLoadMessage = "تعذّر تحميل الصفحات. تحقق من اتصال الإنترنت وإعداد Firebase." }
        } catch (_: IllegalStateException) {
            pageLoadMessage = "قارئ تجريبي: اربط Firebase وأضف صفحات مرخّصة لهذا الفصل."
        }
    }
    LaunchedEffect(manga.id, chapter) {
        preferences.edit()
            .putInt("last_read_chapter_${manga.id}", chapter)
            .putString("last_read_manga_id", manga.id)
            .putInt("last_read_chapter", chapter)
            .apply()
        onProgress(chapter)
    }
    Column(Modifier.fillMaxSize().background(Color(0xFF111118))) {
        if (showControls) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowForward, null, tint = Color.White) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(manga.title, color = Color.White, fontWeight = FontWeight.Bold)
                    Text("الفصل $chapter", color = Color.LightGray, fontSize = 12.sp)
                }
                IconButton(onClick = { showControls = false }) { Icon(Icons.Default.Fullscreen, null, tint = Color.White) }
            }
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).clickable { showControls = !showControls }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (pageUrls.isNotEmpty()) {
                pageUrls.forEachIndexed { index, url ->
                    coil.compose.AsyncImage(
                        model = url,
                        contentDescription = "صفحة ${index + 1} من الفصل $chapter",
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 1.dp),
                    )
                }
            } else {
                Box(Modifier.fillMaxWidth().padding(18.dp).height(260.dp).background(Color(0xFFE9E6E0), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(20.dp)) {
                        Text(manga.emoji, color = Color.DarkGray, fontSize = 38.sp)
                        Text("قارئ MangaVerse", color = Color.DarkGray, fontWeight = FontWeight.Bold)
                        Text(pageLoadMessage, color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }
        }
        if (showControls) {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { chapter = (chapter - 1).coerceAtLeast(1) }) { Text("السابق", color = Color.White) }
                Text("صفحة تجريبية", color = Color.LightGray, fontSize = 12.sp)
                TextButton(onClick = { chapter = (chapter + 1).coerceAtMost(manga.chapters.coerceAtLeast(1)) }) { Text("التالي", color = Color.White) }
            }
        }
    }
}

@Composable
private fun LibraryScreen(favorites: List<Manga>, onOpen: (Manga) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text("مكتبتي", fontSize = 24.sp, color = Ink, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(20.dp))
        if (favorites.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Bookmarks, null, tint = Violet, modifier = Modifier.size(52.dp))
                Text("مكتبتك بعدّها فارغة", color = Ink, fontWeight = FontWeight.Bold)
                Text("أضف المانجا إلى المفضلة من صفحة التفاصيل.", color = Muted)
            }
        } else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(favorites) { manga -> Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(14.dp)).clickable { onOpen(manga) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Cover(manga, Modifier.size(62.dp, 78.dp)); Text(manga.title, Modifier.weight(1f).padding(horizontal = 12.dp), color = Ink, fontWeight = FontWeight.Bold); Icon(Icons.Default.ChevronLeft, null, tint = Violet)
            } }
        }
    }
}

@Composable
private fun AccountScreen(email: String, onAuth: () -> Unit, onSignOut: () -> Unit, onAdmin: () -> Unit, admin: Boolean) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("حسابي", color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 25.sp)
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(58.dp).clip(CircleShape).background(Color(0xFFECE9FF)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, null, tint = Violet, modifier = Modifier.size(32.dp)) }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(if (email.isBlank()) "أهلاً بك!" else email, color = Ink, fontWeight = FontWeight.Bold)
                Text(if (email.isBlank()) "سجّل الدخول لمزامنة مكتبتك" else "حساب MangaVerse", color = Muted, fontSize = 12.sp)
            }
        }
        AccountRow(Icons.Default.CloudSync, "المزامنة السحابية", if (email.isBlank()) "سجّل الدخول لتفعيلها" else "تتم مزامنة بيانات حسابك")
        AccountRow(Icons.Default.Settings, "الإعدادات", "اللغة • المظهر • القارئ")
        AccountRow(Icons.Default.Notifications, "الإشعارات", "الفصول الجديدة")
        Button(onClick = onAuth, Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)) { Text(if (email.isBlank()) "تسجيل الدخول / إنشاء حساب" else "إضافة حساب آخر") }
        if (email.isNotBlank()) {
            OutlinedButton(onClick = onSignOut, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Default.Logout, null)
                Spacer(Modifier.width(8.dp))
                Text("تسجيل الخروج")
            }
        }
        OutlinedButton(onClick = onAdmin, Modifier.fillMaxWidth()) { Icon(Icons.Default.AdminPanelSettings, null); Spacer(Modifier.width(8.dp)); Text(if (admin) "إغلاق نموذج الاقتراح" else "اقتراح مانجا جديدة") }
        Text("لوحة الإدارة هنا نموذج أولي؛ صلاحيات الإدارة الحقيقية يجب أن يفرضها الخادم.", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun AccountRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Color.White).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Violet); Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(title, color = Ink, fontWeight = FontWeight.Bold); Text(subtitle, color = Muted, fontSize = 11.sp) }; Icon(Icons.Default.ChevronLeft, null, tint = Muted)
    }
}

@Composable
private fun AuthScreen(onBack: () -> Unit, onSignedIn: (String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowForward, null) }
        Text(if (creating) "إنشاء حساب" else "أهلاً بعودتك", color = Ink, fontWeight = FontWeight.Black, fontSize = 28.sp)
        Text("سجّل الدخول حتى تبقى مكتبتك معك على كل أجهزتك.", color = Muted)
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("البريد الإلكتروني") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("كلمة المرور") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true
        )
        if (message.isNotBlank()) Text(message, color = Color(0xFFB3261E), fontSize = 12.sp)
        Button(onClick = {
            if (email.isBlank() || password.length < 6) { message = "أدخل بريدًا صحيحًا وكلمة مرور من 6 أحرف على الأقل."; return@Button }
            busy = true; message = ""
            val auth = try { FirebaseAuth.getInstance() } catch (_: IllegalStateException) {
                busy = false
                message = "Firebase غير مربوط بعد. أضف google-services.json إلى مجلد app ثم أعد تشغيل التطبيق."
                return@Button
            }
            val task = if (creating) auth.createUserWithEmailAndPassword(email.trim(), password) else auth.signInWithEmailAndPassword(email.trim(), password)
            task.addOnCompleteListener { result ->
                busy = false
                if (result.isSuccessful) {
                    val actualEmail = result.result?.user?.email ?: email
                    FirebaseFirestore.getInstance().collection("users").document(result.result!!.user!!.uid)
                        .set(mapOf("email" to actualEmail, "updatedAt" to System.currentTimeMillis()), com.google.firebase.firestore.SetOptions.merge())
                    onSignedIn(actualEmail)
                } else message = result.exception?.localizedMessage ?: "تعذّر تسجيل الدخول. تأكد من إعداد Firebase."
            }
        }, Modifier.fillMaxWidth().height(52.dp), enabled = !busy, shape = RoundedCornerShape(14.dp)) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White) else Text(if (creating) "إنشاء الحساب" else "تسجيل الدخول")
        }
        TextButton(onClick = { creating = !creating; message = "" }, Modifier.align(Alignment.CenterHorizontally)) { Text(if (creating) "لديك حساب؟ سجّل الدخول" else "ليس لديك حساب؟ أنشئ حسابًا") }
    }
}

@Composable
private fun AdminPanel(onClose: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var chapter by remember { mutableStateOf("1") }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Color.White).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("اقتراح مانجا جديدة", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, null) }
        }
        Text("أرسل اسم المانجا للمراجعة. النشر الفعلي وإضافة الفصول يبقيان بيد المشرف المصرّح له.", color = Muted, fontSize = 12.sp)
        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("اسم المانجا") })
        OutlinedTextField(chapter, { chapter = it }, Modifier.fillMaxWidth(), label = { Text("رقم الفصل") })
        Button(onClick = {
            val user = try { FirebaseAuth.getInstance().currentUser } catch (_: IllegalStateException) {
                status = "Firebase غير مربوط. أضف google-services.json أولاً."; return@Button
            }
            if (user == null) { status = "يجب تسجيل الدخول أولاً."; return@Button }
            if (title.isBlank()) { status = "اكتب اسم المانجا."; return@Button }
            busy = true
            FirebaseFirestore.getInstance().collection("mangaSubmissions")
                .add(mapOf("title" to title.trim(), "latestChapter" to chapter.toIntOrNull(), "status" to "pending_review", "createdBy" to user.uid, "createdAt" to System.currentTimeMillis()))
                .addOnSuccessListener { status = "تم إرسال اقتراحك للمراجعة."; title = ""; busy = false }
                .addOnFailureListener { status = "فشل الحفظ: ${it.localizedMessage}"; busy = false }
        }, Modifier.fillMaxWidth(), enabled = !busy) { Text(if (busy) "جارٍ الحفظ..." else "حفظ للمراجعة") }
        if (status.isNotBlank()) Text(status, color = Violet)
        Text("هذا النموذج لا يمنح صلاحيات نشر. يجب أن تقيّد قواعد Firebase النشر للمشرفين فقط.", color = Color(0xFF9B3A31), fontSize = 12.sp)
    }
}

private fun currentFirebaseUserEmail(): String = try {
    FirebaseAuth.getInstance().currentUser?.email.orEmpty()
} catch (_: IllegalStateException) { "" }

private fun syncReadingProgress(mangaId: String, chapter: Int) {
    val user = try { FirebaseAuth.getInstance().currentUser } catch (_: IllegalStateException) { null } ?: return
    FirebaseFirestore.getInstance()
        .collection("users").document(user.uid)
        .collection("readingProgress").document(mangaId)
        .set(mapOf(
            "mangaId" to mangaId,
            "chapter" to chapter,
            "updatedAt" to System.currentTimeMillis()
        ), com.google.firebase.firestore.SetOptions.merge())
}

private fun syncFavorite(mangaId: String, favorite: Boolean) {
    val user = try { FirebaseAuth.getInstance().currentUser } catch (_: IllegalStateException) { null } ?: return
    val ref = FirebaseFirestore.getInstance().collection("users").document(user.uid).collection("favorites").document(mangaId)
    if (favorite) ref.set(mapOf("mangaId" to mangaId, "updatedAt" to System.currentTimeMillis()))
    else ref.delete()
}
