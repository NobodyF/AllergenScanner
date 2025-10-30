package com.example.yolodetect

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.yolodetect.databinding.ActivityMainBinding
import com.example.yolodetect.db.AppDb
import com.example.yolodetect.db.ScanResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.min

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var detector: Detector
    private var currentBitmap: Bitmap? = null
    private var currentImageUri: String? = null
    private var detections: List<Detection> = emptyList()
    private val SHOW_OCR_DEBUG = false

    private val reqReadImages = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) showGalleryDialog() else Toast.makeText(this, "Permission denied.", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        detector = Detector(this)
        binding.emptyHint.visibility = View.VISIBLE

        binding.btnAssets.setOnClickListener { showAssetsDialog() }
        binding.btnScan.setOnClickListener { scanForAllergens() }
        binding.btnHistory.setOnClickListener { startActivity(Intent(this, HistoryActivity::class.java)) }

        // Quick gallery via long-press on preview
        binding.imageView.setOnLongClickListener { openGalleryWithPermission(); true }
    }

    private fun setScanningUI(active: Boolean) {
        binding.progressBar.visibility = if (active) View.VISIBLE else View.GONE
        binding.btnScan.isEnabled = !active
        binding.btnAssets.isEnabled = !active
        binding.btnHistory.isEnabled = !active
        if (active) binding.resultText.text = "Scanning…"
    }

    private fun onImageSet(bmp: Bitmap, sourceUri: String?) {
        currentBitmap = letterbox(bmp, 640)
        currentImageUri = sourceUri
        binding.imageView.setImageBitmap(currentBitmap)
        binding.emptyHint.visibility = View.GONE
        runDetection(currentBitmap!!)
    }

    private fun runDetection(bitmap: Bitmap) {
        thread {
            val dets = detector.detect(bitmap)
            detections = dets
            runOnUiThread { binding.overlay.setDetections(dets) }
        }
    }

    private fun openGalleryWithPermission() {
        val perm = if (Build.VERSION.SDK_INT >= 33)
            Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED)
            showGalleryDialog()
        else reqReadImages.launch(perm)
    }

    private fun showGalleryDialog() {
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME)
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val cursor = contentResolver.query(uri, projection, null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC")

        val imageNames = mutableListOf<String>()
        val imageUris = mutableListOf<Uri>()
        cursor?.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            while (it.moveToNext()) {
                val id = it.getLong(idCol)
                val name = it.getString(nameCol)
                imageNames.add(name)
                imageUris.add(Uri.withAppendedPath(uri, id.toString()))
            }
        }

        if (imageUris.isEmpty()) {
            Toast.makeText(this, "No images found", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Select image")
            .setItems(imageNames.toTypedArray()) { _, idx ->
                val u = imageUris[idx]
                val bmp = contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it) }
                if (bmp != null) onImageSet(bmp, u.toString())
                else Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showAssetsDialog() {
        val files = assets.list("")?.filter { it.endsWith(".jpg", true) || it.endsWith(".png", true) } ?: emptyList()
        if (files.isEmpty()) {
            Toast.makeText(this, "No assets found", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Select asset")
            .setItems(files.toTypedArray()) { _, idx ->
                val name = files[idx]
                val bmp = assets.open(name).use { BitmapFactory.decodeStream(it) }
                onImageSet(bmp, "asset://$name")
            }
            .show()
    }

    private fun letterbox(src: Bitmap, size: Int): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val scale = minOf(size / src.width.toFloat(), size / src.height.toFloat())
        val dx = (size - src.width * scale) / 2f
        val dy = (size - src.height * scale) / 2f
        val m = Matrix().apply { postScale(scale, scale); postTranslate(dx, dy) }
        canvas.drawBitmap(src, m, null)
        return out
    }

    private fun ensureArgb8888(src: Bitmap): Bitmap =
        if (src.config == Bitmap.Config.ARGB_8888) src else src.copy(Bitmap.Config.ARGB_8888, false)

    private fun enhanceForOcr(srcIn: Bitmap, scale: Float = 2.0f): Bitmap {
        val src = ensureArgb8888(srcIn)
        val w = (src.width * scale).toInt().coerceAtLeast(1)
        val h = (src.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(src, w, h, true)

        val gray = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(gray).drawBitmap(scaled, 0f, 0f, Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        })

        val contrast = 1.8f
        val brightness = 20f
        val cm = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, brightness,
                0f, contrast, 0f, 0f, brightness,
                0f, 0f, contrast, 0f, brightness,
                0f, 0f, 0f, 1f, 0f
            )
        )
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { out ->
            Canvas(out).drawBitmap(gray, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(cm) })
        }
    }

    private val ingredientsKeywords = listOf(
        "ingredients","ingredient","contains","may contain",
        "sastāvs","sastavs","var saturēt","satur",
        "состав","может содержать","содержит",
        "sudėtis","sudedamosios dalys","suded.","sudėtyje yra","gali būti",
        "sastojci",
        "składniki","zawiera","może zawierać"
    )

    private fun looksLikeIngredientsLine(line: String): Boolean {
        val l = line.lowercase()
        return ingredientsKeywords.any { k -> l.contains(k) }
    }
    private fun scoreAsIngredientsBlock(text: String): Int {
        val l = text.lowercase()
        var score = 0
        if (ingredientsKeywords.any { l.contains(it) }) score += 8
        val separators = Regex("[,;•]|\\band\\b|\\bun\\b|\\bи\\b|\\bir\\b|\\ba\\b|\\bor\\b")
        score += separators.findAll(l).count()
        score += (l.length / 50).coerceAtMost(10)
        return score
    }

    private fun normalizeForOcr(raw: String): String {
        val map = mapOf('0' to 'o','1' to 'l','¡' to 'l','|' to 'l','5' to 's','€' to 'e','3' to 'e','4' to 'a','@' to 'a','8' to 'b','§' to 's')
        val sb = StringBuilder(raw.length)
        raw.forEach { ch -> sb.append(map[ch] ?: ch) }
        return sb.toString().lowercase().replace(Regex("\\s+"), " ").trim()
    }

    private fun scanForAllergens() {
        val bmp = currentBitmap ?: run {
            Toast.makeText(this, "Pick an image first.", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launchWhenStarted {
            setScanningUI(true)
            try {
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

                // OCR YOLO crops
                val cropTexts = mutableListOf<String>()
                if (detections.isNotEmpty()) {
                    for (d in detections) {
                        val cropRaw = crop(bmp, d)
                        val cropImg = enhanceForOcr(cropRaw)
                        val t = withContext(Dispatchers.IO) {
                            recognizer.process(InputImage.fromBitmap(cropImg, 0)).await().text
                        }.trim()
                        if (t.isNotBlank()) cropTexts += t
                    }
                }

                // Choose best ingredients-like crop
                var chosenIngredients = ""
                var chosenScore = Int.MIN_VALUE
                for (t in cropTexts) {
                    val s = scoreAsIngredientsBlock(t)
                    if (s > chosenScore) { chosenScore = s; chosenIngredients = t }
                }

                val fullImgText = withContext(Dispatchers.IO) {
                    recognizer.process(InputImage.fromBitmap(enhanceForOcr(bmp), 0)).await().text
                }.trim()

                val explicitLines = cropTexts.asSequence().filter { looksLikeIngredientsLine(it) }.joinToString("\n")
                val merged = buildString {
                    if (chosenIngredients.isNotBlank()) appendLine(chosenIngredients)
                    appendLine(fullImgText)
                    if (explicitLines.isNotBlank()) appendLine(explicitLines)
                }.trim()

                if (SHOW_OCR_DEBUG) binding.resultText.text = "DEBUG OCR:\n$merged\n\n"
                Log.d("SCAN_DEBUG", "OCR TEXT:\n$merged")

                val patterns = allergenPatterns()
                val analysis = analyzeAllergens(merged, patterns)
                val result = renderResult(merged, analysis)
                binding.resultText.setText(result, TextView.BufferType.SPANNABLE)

                // Persist in DB
                val detectedNames = analysis.map { it.name }.distinct().sorted().joinToString(", ")
                val sevJson = analysis.associate { it.name to it.severity }
                    .entries.joinToString(prefix="{", postfix="}") { "\"${it.key}\":\"${it.value}\"" }
                withContext(Dispatchers.IO) {
                    AppDb.get(this@MainActivity).scans().insert(
                        ScanResult(
                            ts = System.currentTimeMillis(),
                            imageUri = currentImageUri,
                            detected = detectedNames,
                            severityJson = sevJson,
                            ocrText = merged
                        )
                    )
                }
            } finally {
                setScanningUI(false)
            }
        }
    }

    private fun crop(src: Bitmap, d: Detection): Bitmap {
        val x1 = max(0f, d.x1).toInt()
        val y1 = max(0f, d.y1).toInt()
        val x2 = min(src.width.toFloat(), d.x2).toInt()
        val y2 = min(src.height.toFloat(), d.y2).toInt()
        val w = (x2 - x1).coerceAtLeast(1)
        val h = (y2 - y1).coerceAtLeast(1)
        return Bitmap.createBitmap(src, x1, y1, w, h)
    }

    private fun allergenPatterns(): Map<String, List<Regex>> = mapOf(
        "milk" to listOf(
            rx("milk|piens|pienas|молок"),
            rx("cheese|siers|sir|ser|sūris|suris|sier|sieer|sier5"),
            rx("lactose|laktoz|laktoze|laktozė|лактоз"),
            rx("casein|caseinat(e|s)?|kazeīn|kazein|казеин"),
            rx("whey|sūkal|сыворот|išrūg"),
            rx("butter|sviest|sviestas"),
            rx("cream|krēj|сливк|grietinėl"),
            rx("yogurt|jogurt(s|a|as)?|йогурт")
        ),
        "egg" to listOf(
            rx("egg(s)?|ola(s)?|kiauš(ini(ai|o|ų)?)|яйц"),
            rx("albumin|ovalbumin"),
            rx("e1105")
        ),
        "fish" to listOf(rx("fish|ziv[si]|žuvi|рыб"), rx("omega[- ]?3")),
        "crustaceans" to listOf(rx("crustace|vēžveid|vėžiagyv|ракообраз"), rx("shrimp|krab|кревет|краб")),
        "molluscs" to listOf(rx("mollusc|moliusk|моллюск|molu(sk|šķ)"), rx("mussel|clam|squid|octopus")),
        "peanut" to listOf(
            rx("peanut(s)?|zemesriekst(i|u)?|žemės\\s?riešut(ai|ų|as)?|арахис(а|у|ы)?"),
            rx("peanut\\s?butter|arahisa\\s?sviests")
        ),
        "tree nuts" to listOf(
            rx("tree\\s+nut(s)?|other\\s+nut(s)?|riekst(i)?(?!iņ)|riešut(ai|ų)?(?!inis)|орех(и|а|ов)?"),
            rx("almond(s)?|mande(l|ļu)|migdol(ų|ai|as)?"),
            rx("hazelnut(s)?|lazdu|lazdyn(ų|ai)?"),
            rx("walnut(s)?|valriekst(i)?|graikinių"),
            rx("cashew(s)?|keš(u|j)|anakard(ų|ai)?"),
            rx("pecan(s)?"),
            rx("pistachio(s)?|pistāc|pistacij(ų|os)?"),
            rx("macadamia"),
            rx("brazil\\s+nut(s)?|brazīl"),
            rx("nut\\s+paste|riekstu\\s+pasta|riešutų\\s+pasta")
        ),
        "soy" to listOf(
            rx("soy(a)?|soya|soy[- ]?bean(s)?|soj(a)?|со[йи]я"),
            rx("lecithin|lecitīns|lecitinas|лецитин|e322")
        ),
        "gluten" to listOf(
            rx("gluten|glutēn|глютен|glitimas|bezglut|be\\s?gli"),
            rx("barley|miež|ячмен|miež"),
            rx("rye|rud|рож|rug"),
            rx("oats|auz|овс|avi(e|ž)"),
            rx("spelt|speltas|spelt|шпальт"),
            rx("triticale|tritik"),
            rx("durum"),
            rx("kamut"),
            rx("malt|iesal|солод|salykl"),
            rx("semolina|mann|манн")
        ),
        "wheat" to listOf(rx("wheat|kvieš|kvieč|пшен")),
        "sesame" to listOf(rx("sesame|sezam|sezamas|кунжут"), rx("tahini|tahin")),
        "celery" to listOf(rx("celery|seler(ij|y)|salier|сельдере")),
        "mustard" to listOf(rx("mustard|sinep|garstyč|горчиц")),
        "lupin" to listOf(rx("lupin|lupīn|lubin|люпин")),
        "sulphites" to listOf(rx("sulphit|sulfit|sulfīt|сульфит"), rx("e22[0-8]"))
    )

    private fun rx(p: String) = Regex("(?iu)(?<!\\p{L})($p)(?!\\p{L})")

    private val containPhrases = listOf(
        Regex("(?iu)\\b(contains|satur|содержит|sudėtyje\\s+yra|sadrži|zawiera)\\b"),
        Regex("(?iu)\\b(may\\s+contain|var\\s+saturēt|может\\s+содержать|gali\\s+būti|može\\s+sadržavati|może\\s+zawierać)\\b"),
        Regex("(?iu)\\b(trace|pēdas|следы|pėdsak(ai|ų)|sl(ij)ed|ślady)\\b")
    )

    data class AllergenHit(
        val name: String,
        val matchedWords: Set<String>,
        val severity: String,   // "contains" / "may contain" / "trace" / "mentioned"
        val evidence: String
    )

    private fun analyzeAllergens(text: String, patterns: Map<String, List<Regex>>): List<AllergenHit> {
        val orig = text.split(Regex("(?m)[\\r\\n]+")).filter { it.isNotBlank() }
        val norm = orig.map { normalizeForOcr(it) }

        val hits = mutableListOf<AllergenHit>()
        for ((name, regs) in patterns) {
            val matched = mutableSetOf<String>()
            norm.forEachIndexed { idx, nline ->
                val oline = orig[idx]
                val lw = regs.flatMap { r -> r.findAll(nline).map { it.value } }.toSet()
                if (lw.isNotEmpty()) {
                    matched.addAll(lw)
                    val sev = when {
                        containPhrases[0].containsMatchIn(nline) -> "contains"
                        containPhrases[1].containsMatchIn(nline) -> "may contain"
                        containPhrases[2].containsMatchIn(nline) -> "trace"
                        else -> "mentioned"
                    }
                    hits += AllergenHit(name, matched.toSet(), sev, oline.trim())
                }
            }
        }

        fun rank(s: String) = when (s) { "contains" -> 3; "may contain" -> 2; "trace" -> 1; else -> 0 }
        return hits.groupBy { it.name }.map { (name, group) ->
            val best = group.maxBy { rank(it.severity) }
            AllergenHit(name, group.flatMap { it.matchedWords }.toSet(), best.severity, best.evidence)
        }.sortedByDescending { listOf("contains","may contain","trace","mentioned").indexOf(it.severity) }
    }

    private fun explanationFor(name: String, severity: String): String {
        val why = when(name) {
            "milk" -> "Dairy (cheese, yogurt, whey/casein)."
            "egg" -> "Egg proteins (incl. lysozyme E1105)."
            "fish" -> "Fish proteins."
            "crustaceans" -> "Crustacean shellfish."
            "molluscs" -> "Molluscs (mussels/squid)."
            "peanut" -> "Peanut proteins."
            "tree nuts" -> "Almond, hazelnut, walnut, pistachio, etc."
            "soy" -> "Soy (incl. lecithin E322)."
            "gluten" -> "Gluten grains/derivatives (barley/rye/oats/spelt/malt)."
            "wheat" -> "Wheat (a gluten source)."
            "sesame" -> "Sesame (incl. tahini)."
            "celery" -> "Celery."
            "mustard" -> "Mustard."
            "lupin" -> "Lupin flour/beans."
            "sulphites" -> "Sulphites/preservatives (E220–E228)."
            else -> name
        }
        val sev = when(severity) { "contains"->"Contains"; "may contain"->"May contain"; "trace"->"Trace"; else->"Mentioned" }
        return "$sev: $why"
    }

    private fun renderResult(fullText: String, hits: List<AllergenHit>): SpannableStringBuilder {
        val builder = SpannableStringBuilder()

        // Summary
        if (hits.isEmpty()) builder.append("Detected: None\n\n")
        else builder.append("Detected: ${hits.map { it.name }.distinct().sorted().joinToString(", ")}\n\n")

        // Details
        builder.append("Details:\n")
        if (hits.isEmpty()) builder.append("• No EU-14 allergens found in the scanned text.\n\n")
        else {
            for (h in hits) {
                builder.append("• ${h.name} — ${explanationFor(h.name, h.severity)}\n")
                if (h.evidence.isNotBlank()) builder.append("   ↳ “${h.evidence}”\n")
            }
            builder.append("\n")
        }

        builder.append("Ingredients (OCR):\n\n")
        val start = builder.length
        builder.append(fullText)

        val yellowBg = BackgroundColorSpan(0x66FFD54F.toInt())
        val redFg = ForegroundColorSpan(Color.RED)
        for ((_, regs) in allergenPatterns()) {
            for (r in regs) {
                val m = r.toPattern().matcher(normalizeForOcr(fullText))
                while (m.find()) {
                    // Need to map to raw text: best-effort – highlight same substring length
                    val s = start + m.start()
                    val e = start + m.end()
                    val safeEnd = e.coerceAtMost(builder.length)
                    if (s in start until safeEnd) {
                        builder.setSpan(yellowBg, s, safeEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        builder.setSpan(redFg, s, safeEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        builder.setSpan(StyleSpan(Typeface.BOLD), s, safeEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
            }
        }
        return builder
    }
}
