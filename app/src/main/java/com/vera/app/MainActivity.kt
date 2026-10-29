package com.vera.app

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ProgressBar
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private val BG = Color.parseColor("#0F1626")
    private val PANEL = Color.parseColor("#16203A")
    private val INK = Color.parseColor("#E8EDF8")
    private val MUTED = Color.parseColor("#92A0BD")
    private val LAPIS = Color.parseColor("#5B86F0")
    private val BOT = Color.parseColor("#1C2847")
    private val LINE = Color.parseColor("#2B3A5E")

    // نفس تعليمات النظام اللي تدرب عليها فيرا
    private val modes = linkedMapOf(
        "عراقي" to "انت فيرا، مساعد ذكي عراقي. تجاوب باللهجة العراقية بشكل مباشر وواضح بدون حشو ولا وعظ.",
        "فصحى" to "انت فيرا، مساعد ذكي. تجاوب بالعربية الفصحى الواضحة وبشكل مباشر.",
        "ترجمة" to "انت فيرا، مترجم محترف للمانهوا والكتب والمحادثات. تترجم للهجة العراقية الطبيعية أو الفصحى حسب الطلب، وتعطي الترجمة فقط.",
        "برمجة" to "انت فيرا، مبرمج خبير. تشرح بالعراقي وتكتب كود نظيف وصحيح."
    )

    private lateinit var msgs: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var sendBtn: Button
    private lateinit var status: TextView
    private lateinit var modeSpinner: Spinner

    private val history = mutableListOf<Pair<String, String>>()
    @Volatile private var busy = false
    private var loaded = false
    private var pfd: ParcelFileDescriptor? = null
    private val prefs by lazy { getSharedPreferences("vera", Context.MODE_PRIVATE) }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun round(color: Int, r: Int, stroke: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            setColor(color); cornerRadius = dp(r).toFloat()
            if (stroke != 0) setStroke(dp(1), stroke)
        }

    private fun btn(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label; setTextColor(INK); textSize = 13f; isAllCaps = false
        background = round(PANEL, 10, LINE); setPadding(dp(12), 0, dp(12), 0)
        minHeight = 0; minimumHeight = dp(40)
        setOnClickListener { onClick() }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = PANEL
        window.navigationBarColor = PANEL

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        // ---- الهيدر ----
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(PANEL)
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        header.addView(TextView(this).apply {
            text = "فيرا"; textSize = 22f; setTextColor(INK); typeface = Typeface.DEFAULT_BOLD
        })
        status = TextView(this).apply {
            text = "اضغط «النماذج» وحمّل نموذج حتى نبدأ"; textSize = 12f; setTextColor(MUTED)
        }
        header.addView(status)

        modeSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item, modes.keys.toList())
            setSelection(prefs.getInt("mode", 0))
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        val lp = { LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)).apply { marginEnd = dp(6) } }
        bar.addView(modeSpinner, LinearLayout.LayoutParams(0, dp(44), 1f))
        bar.addView(btn("النماذج") { models() }, lp())
        bar.addView(btn("جديدة") { newChat() }, lp())
        bar.addView(btn("إعدادات") { settings() }, lp())
        header.addView(bar)
        root.addView(header)

        // ---- الرسائل ----
        msgs = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        scroll = ScrollView(this).apply { addView(msgs) }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        showEmpty()

        // ---- الادخال ----
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(PANEL)
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        input = EditText(this).apply {
            hint = "اكتب رسالتك…"; setHintTextColor(MUTED); setTextColor(INK)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            maxLines = 5; background = round(BG, 14, LINE); setPadding(dp(14), dp(10), dp(14), dp(10))
            textDirection = View.TEXT_DIRECTION_ANY_RTL
        }
        sendBtn = Button(this).apply {
            text = "ارسل"; setTextColor(Color.parseColor("#0B1226")); isAllCaps = false
            background = round(LAPIS, 14); minHeight = 0; minimumHeight = dp(44)
            setOnClickListener { send() }
        }
        row.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
        row.addView(sendBtn)
        root.addView(View(this).apply { setBackgroundColor(LINE) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))
        root.addView(row)
        setContentView(root)

        // تحميل آخر نموذج تلقائياً
        prefs.getString("last", null)?.let { if (it.startsWith("content:")) loadUri(Uri.parse(it)) else if (File(it).exists()) loadPath(it) }
    }

    private var emptyView: View? = null

    private fun showEmpty() {
        emptyView = TextView(this).apply {
            text = "اضغط «النماذج» فوق، حمّل نموذج، وبعدها اكتب أي شي."
            setTextColor(MUTED); gravity = Gravity.CENTER; textSize = 14f
            setPadding(dp(20), dp(60), dp(20), 0)
        }
        msgs.addView(emptyView)
    }

    private fun newChat() {
        if (busy) Native.stop()
        history.clear(); msgs.removeAllViews(); showEmpty()
    }

    // ---------------- النموذج ----------------
    private class Entry(val title: String, val url: String, val gb: Double, val ram: String) {
        val file: String get() = url.substringAfterLast('/').substringBefore('?')
    }

    // نماذج Qwen3 الأصلية للتجربة (نفس القواعد اللي تتدرب عليها فيرا)
    private val catalog = listOf(
        Entry("مستوى 1 • Qwen3 0.6B", "https://huggingface.co/unsloth/Qwen3-0.6B-GGUF/resolve/main/Qwen3-0.6B-Q4_K_S.gguf", 0.4, "1 GB"),
        Entry("مستوى 2 • Qwen3 1.7B", "https://huggingface.co/unsloth/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf", 1.1, "2 GB"),
        Entry("مستوى 3 • Qwen3 4B (Q3)", "https://huggingface.co/unsloth/Qwen3-4B-GGUF/resolve/main/Qwen3-4B-Q3_K_M.gguf", 2.1, "3 GB"),
        Entry("مستوى 4 • Qwen3 4B (Q5)", "https://huggingface.co/Qwen/Qwen3-4B-GGUF/resolve/main/Qwen3-4B-Q5_K_M.gguf", 2.9, "4 GB"),
        Entry("مستوى 5 • Qwen3 8B (Q3)", "https://huggingface.co/unsloth/Qwen3-8B-GGUF/resolve/main/Qwen3-8B-Q3_K_M.gguf", 4.1, "5 GB"),
        Entry("مستوى 6 • Qwen3 8B (Q4)", "https://huggingface.co/Qwen/Qwen3-8B-GGUF/resolve/main/Qwen3-8B-Q4_K_M.gguf", 5.0, "6 GB")
    )

    private fun modelsDir(): File = (getExternalFilesDir("models") ?: File(filesDir, "models")).also { it.mkdirs() }

    private fun dialog() = AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)

    private fun models() {
        if (busy) return
        val dir = modelsDir()
        val labels = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()
        for (e in catalog) {
            val f = File(dir, e.file)
            val have = f.exists()
            labels.add((if (have) "✓ " else "⬇ ") + e.title + "  •  " + "%.1f".format(e.gb) + " GB  •  رام " + e.ram)
            actions.add { if (have) haveDialog(f) else download(e.url, e.file) }
        }
        dir.listFiles { x -> x.name.endsWith(".gguf") && catalog.none { it.file == x.name } }?.forEach { f ->
            labels.add("✓ " + f.name + "  •  " + "%.1f".format(f.length() / 1e9) + " GB")
            actions.add { haveDialog(f) }
        }
        labels.add("🔗 رابط مخصص (مثلاً فيرا اللي دربتها)"); actions.add { customUrl() }
        labels.add("📁 اختيار ملف من الجوال"); actions.add { pickFile() }
        dialog().setTitle("النماذج (Qwen3 الأصلية للتجربة)")
            .setItems(labels.toTypedArray()) { _, i -> actions[i]() }.show()
    }

    private fun haveDialog(f: File) {
        dialog().setTitle(f.name)
            .setPositiveButton("تشغيل") { _, _ -> loadPath(f.absolutePath) }
            .setNegativeButton("حذف") { _, _ ->
                if (prefs.getString("last", "") == f.absolutePath) { Native.unload(); loaded = false }
                f.delete(); status.text = "انحذف النموذج"
            }
            .setNeutralButton("الغاء", null).show()
    }

    private fun customUrl() {
        val et = EditText(this).apply {
            hint = "https://huggingface.co/.../resolve/main/vera.gguf"
            setTextColor(INK); setHintTextColor(MUTED); textDirection = View.TEXT_DIRECTION_LTR
            inputType = InputType.TYPE_TEXT_VARIATION_URI
        }
        val box = LinearLayout(this).apply { setPadding(dp(20), dp(8), dp(20), 0); addView(et, LinearLayout.LayoutParams(-1, -2)) }
        dialog().setTitle("رابط ملف GGUF").setView(box)
            .setPositiveButton("تحميل") { _, _ ->
                val url = et.text.toString().trim()
                if (url.startsWith("http")) {
                    var name = url.substringAfterLast('/').substringBefore('?')
                    if (!name.endsWith(".gguf")) name += ".gguf"
                    download(url, name)
                }
            }.setNegativeButton("الغاء", null).show()
    }

    private fun pickFile() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(i, 1)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == 1 && res == RESULT_OK && data?.data != null) {
            val uri = data.data!!
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            loadUri(uri)
        }
    }

    private fun ramInfo(sizeGb: Double): String {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        var s = "%.1f GB، الرام المتاح %.1f GB".format(sizeGb, mi.availMem / 1e9)
        if (sizeGb * 1e9 > mi.availMem * 0.9) s += " (الرام قليلة، قد يتوقف)"
        return s
    }

    private fun loadDone(err: String, info: String) {
        if (err.isEmpty()) { loaded = true; status.text = "جاهز • $info" }
        else status.text = "فشل تحميل النموذج. تأكد انه ملف GGUF سليم ومكتمل."
    }

    private fun loadPath(path: String) {
        if (busy) return
        prefs.edit().putString("last", path).apply()
        loaded = false; status.text = "جاري تحميل النموذج…"
        thread {
            val gb = File(path).length() / 1e9
            val err = try { Native.unload(); Native.load(path) } catch (e: Exception) { "ERR_EX" }
            runOnUiThread { loadDone(err, ramInfo(gb)) }
        }
    }

    private fun loadUri(uri: Uri) {
        if (busy) return
        prefs.edit().putString("last", uri.toString()).apply()
        loaded = false; status.text = "جاري تحميل النموذج…"
        thread {
            var err = ""; var gb = 0.0
            try {
                pfd?.close(); Native.unload()
                val p = contentResolver.openFileDescriptor(uri, "r")!!
                pfd = p; gb = p.statSize / 1e9
                err = Native.load("/proc/self/fd/${p.fd}")
                if (err.isNotEmpty()) {
                    runOnUiThread { status.text = "ننسخ النموذج لمساحة التطبيق…" }
                    val dst = File(filesDir, "model.gguf")
                    contentResolver.openInputStream(uri)!!.use { ins -> dst.outputStream().use { ins.copyTo(it, 1 shl 20) } }
                    err = Native.load(dst.absolutePath)
                }
            } catch (e: Exception) { err = "ERR_EX" }
            runOnUiThread { loadDone(err, ramInfo(gb)) }
        }
    }

    // ---------------- التحميل داخل التطبيق ----------------
    private fun download(url: String, name: String) {
        val dir = modelsDir()
        val dst = File(dir, name); val part = File(dir, "$name.part")
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 1000 }
        val txt = TextView(this).apply { setTextColor(INK); text = "جاري الاتصال…" }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(16), dp(20), dp(8))
            addView(txt); addView(bar)
        }
        val cancel = java.util.concurrent.atomic.AtomicBoolean(false)
        val dlg = dialog().setTitle("تحميل $name").setView(box).setCancelable(false)
            .setNegativeButton("الغاء") { _, _ -> cancel.set(true) }.create()
        dlg.show()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        thread {
            var err = ""
            try {
                val have = if (part.exists()) part.length() else 0L
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 20000; conn.readTimeout = 30000
                conn.setRequestProperty("User-Agent", "VeraApp/1.0")
                if (have > 0) conn.setRequestProperty("Range", "bytes=$have-")
                val code = conn.responseCode
                if (code != 200 && code != 206) throw RuntimeException("HTTP $code")
                val append = code == 206
                val startAt = if (append) have else 0L
                val total = startAt + conn.contentLengthLong
                conn.inputStream.use { ins ->
                    FileOutputStream(part, append).use { out ->
                        val buf = ByteArray(256 * 1024)
                        var done = startAt; var last = 0L
                        while (true) {
                            if (cancel.get()) throw InterruptedException()
                            val n = ins.read(buf); if (n < 0) break
                            out.write(buf, 0, n); done += n
                            val now = System.currentTimeMillis()
                            if (now - last > 300) {
                                last = now
                                val d = done; val t = maxOf(total, done)
                                runOnUiThread {
                                    bar.progress = (d * 1000 / t).toInt()
                                    txt.text = "%.2f / %.2f GB".format(d / 1e9, t / 1e9)
                                }
                            }
                        }
                    }
                }
                if (!part.renameTo(dst)) throw RuntimeException("rename")
            } catch (e: InterruptedException) { err = "cancel" }
            catch (e: Exception) { err = e.message ?: "error" }
            runOnUiThread {
                dlg.dismiss(); window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                when (err) {
                    "" -> loadPath(dst.absolutePath)
                    "cancel" -> status.text = "انلغى التحميل (يكمل من نفس المكان اذا حملت ثانية)"
                    else -> status.text = "فشل التحميل: $err. تأكد من الإنترنت او استعمل رابط مخصص."
                }
            }
        }
    }

    // ---------------- المحادثة ----------------
    private fun addBubble(text: String, user: Boolean): TextView {
        val tv = TextView(this).apply {
            this.text = text; textSize = 16f; setLineSpacing(0f, 1.25f)
            setTextColor(if (user) Color.parseColor("#0B1226") else INK)
            background = round(if (user) LAPIS else BOT, 16, if (user) 0 else LINE)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            textDirection = View.TEXT_DIRECTION_ANY_RTL
            setTextIsSelectable(true)
        }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = if (user) Gravity.START else Gravity.END
            bottomMargin = dp(10)
            if (user) marginEnd = dp(40) else marginStart = dp(40)
        }
        emptyView?.let { msgs.removeView(it); emptyView = null }
        msgs.addView(tv, lp)
        scrollDown()
        return tv
    }

    private fun scrollDown() { scroll.post { scroll.fullScroll(View.FOCUS_DOWN) } }

    private fun clean(s: String): String =
        s.replace(Regex("(?s)<think>.*?</think>"), "").replace("<think>", "").replace("</think>", "").trimStart()

    private fun buildPrompt(sys: String, maxTok: Int, nCtx: Int): String {
        var budget = (nCtx - maxTok - 64) * 2          // تقدير بالحروف
        budget -= sys.length
        val keep = ArrayDeque<Pair<String, String>>()
        for (m in history.asReversed()) {
            if (budget - m.second.length < 0 && keep.isNotEmpty()) break
            keep.addFirst(m); budget -= m.second.length
        }
        val sb = StringBuilder()
        sb.append("<|im_start|>system\n").append(sys).append("<|im_end|>\n")
        for ((r, c) in keep) sb.append("<|im_start|>").append(r).append("\n").append(c).append("<|im_end|>\n")
        sb.append("<|im_start|>assistant\n<think>\n\n</think>\n\n")
        return sb.toString()
    }

    private fun send() {
        if (busy) { Native.stop(); return }
        if (!loaded) { Toast.makeText(this, "حمّل النموذج أول", Toast.LENGTH_SHORT).show(); return }
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        input.setText("")
        prefs.edit().putInt("mode", modeSpinner.selectedItemPosition).apply()

        val sys = modes[modeSpinner.selectedItem as String]!!
        val maxTok = prefs.getInt("maxTok", 512)
        val nCtx = intArrayOf(1024, 2048, 4096)[prefs.getInt("ctx", 1)]
        val threads = prefs.getInt("threads", (Runtime.getRuntime().availableProcessors() / 2).coerceIn(2, 6))
        val temp = prefs.getInt("temp", 70) / 100f

        history.add("user" to text)
        addBubble(text, true)
        val bot = addBubble("…", false)
        val prompt = buildPrompt(sys, maxTok, nCtx).toByteArray(Charsets.UTF_8)
        busy = true; sendBtn.text = "وقّف"

        thread {
            val sb = StringBuilder(); var n = 0; val t0 = System.nanoTime()
            val err = Native.generate(prompt, maxTok, temp, 0.9f, nCtx, threads, object : Native.TokenCallback {
                override fun onToken(bytes: ByteArray) {
                    sb.append(String(bytes, Charsets.UTF_8)); n++
                    val s = clean(sb.toString())
                    runOnUiThread { bot.text = s; scrollDown() }
                }
            })
            val secs = (System.nanoTime() - t0) / 1e9
            runOnUiThread {
                busy = false; sendBtn.text = "ارسل"
                if (err.isNotEmpty()) {
                    history.removeAt(history.size - 1)
                    bot.text = when (err) {
                        "ERR_TOO_LONG" -> "المحادثة طويلة هواية، ابدأ محادثة جديدة."
                        "ERR_CTX" -> "الرام ما تكفي. صغّر حجم السياق من الإعدادات او استعمل مستوى أصغر."
                        else -> "صار خطأ ($err). جرب تعيد تحميل النموذج."
                    }
                } else {
                    history.add("assistant" to clean(sb.toString()))
                    status.text = "%.1f توكن بالثانية".format(n / secs)
                }
            }
        }
    }

    // ---------------- الإعدادات ----------------
    private fun seekRow(box: LinearLayout, label: String, min: Int, max: Int, key: String, def: Int,
                        fmt: (Int) -> String) {
        val cur = prefs.getInt(key, def)
        val t = TextView(this).apply { setTextColor(INK); text = "$label: ${fmt(cur)}"; setPadding(0, dp(12), 0, 0) }
        val s = SeekBar(this).apply {
            this.min = min; this.max = max; progress = cur
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, u: Boolean) {
                    t.text = "$label: ${fmt(p)}"; prefs.edit().putInt(key, p).apply()
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }
        box.addView(t); box.addView(s)
    }

    private fun settings() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(8))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        seekRow(box, "الحرارة (الإبداع)", 0, 150, "temp", 70) { "%.2f".format(it / 100f) }
        seekRow(box, "أقصى طول للرد", 64, 1024, "maxTok", 512) { "$it توكن" }
        seekRow(box, "حجم السياق", 0, 2, "ctx", 1) { listOf("1024", "2048", "4096")[it] + " (اكبر = رام اكثر)" }
        val cores = Runtime.getRuntime().availableProcessors()
        seekRow(box, "عدد الأنوية", 1, cores, "threads", (cores / 2).coerceIn(2, 6)) { "$it" }
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("الإعدادات").setView(box).setPositiveButton("تم", null).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (busy) Native.stop()
        if (isFinishing) { Native.unload(); pfd?.close() }
    }
}
