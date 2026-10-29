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
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.io.File
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
            text = "اختر ملف النموذج (GGUF) حتى نبدأ"; textSize = 12f; setTextColor(MUTED)
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
        bar.addView(btn("النموذج") { pickModel() }, lp())
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
        prefs.getString("uri", null)?.let { loadModel(Uri.parse(it)) }
    }

    private var emptyView: View? = null

    private fun showEmpty() {
        emptyView = TextView(this).apply {
            text = "حمّل ملف فيرا (GGUF) من زر «النموذج»، وبعدها اكتب أي شي."
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
    private fun pickModel() {
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
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
            prefs.edit().putString("uri", uri.toString()).apply()
            loadModel(uri)
        }
    }

    private fun loadModel(uri: Uri) {
        if (busy) return
        loaded = false
        status.text = "جاري تحميل النموذج…"
        thread {
            var err = ""
            var info = ""
            try {
                pfd?.close(); Native.unload()
                val p = contentResolver.openFileDescriptor(uri, "r")!!
                pfd = p
                val sizeGb = p.statSize / 1e9
                val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
                info = "%.1f GB، الرام المتاح %.1f GB".format(sizeGb, mi.availMem / 1e9)
                if (sizeGb * 1e9 > mi.availMem * 0.9) info += " (الرام قليلة، قد يتوقف)"

                err = Native.load("/proc/self/fd/${p.fd}")
                if (err.isNotEmpty()) {
                    // خطة بديلة: ننسخ الملف لمساحة التطبيق
                    runOnUiThread { status.text = "ننسخ النموذج لمساحة التطبيق… ($info)" }
                    val dst = File(filesDir, "model.gguf")
                    contentResolver.openInputStream(uri)!!.use { ins ->
                        dst.outputStream().use { ins.copyTo(it, 1 shl 20) }
                    }
                    err = Native.load(dst.absolutePath)
                }
            } catch (e: Exception) {
                err = "ERR_EX"
            }
            runOnUiThread {
                if (err.isEmpty()) { loaded = true; status.text = "جاهز، النموذج: $info" }
                else status.text = "فشل تحميل النموذج. تأكد انه ملف GGUF سليم."
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
