package com.example.yolodetect

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.yolodetect.databinding.ActivityHistoryBinding
import com.example.yolodetect.db.AppDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class HistoryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHistoryBinding
    private val adapter = HistoryAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "History"

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        lifecycleScope.launch {
            val data = withContext(Dispatchers.IO) {
                AppDb.get(this@HistoryActivity).scans().latest(100)
            }
            adapter.submit(data)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

class HistoryAdapter : androidx.recyclerview.widget.RecyclerView.Adapter<HistoryVH>() {
    private val items = mutableListOf<com.example.yolodetect.db.ScanResult>()
    fun submit(list: List<com.example.yolodetect.db.ScanResult>) {
        items.clear(); items.addAll(list); notifyDataSetChanged()
    }
    override fun onCreateViewHolder(p: android.view.ViewGroup, vType: Int): HistoryVH {
        val v = android.view.LayoutInflater.from(p.context).inflate(R.layout.item_scan, p, false)
        return HistoryVH(v)
    }
    override fun getItemCount() = items.size
    override fun onBindViewHolder(h: HistoryVH, i: Int) = h.bind(items[i])
}

class HistoryVH(itemView: android.view.View) :
    androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {

    private val tDetected = itemView.findViewById<android.widget.TextView>(R.id.tDetected)
    private val tTime = itemView.findViewById<android.widget.TextView>(R.id.tTime)
    private val tSnippet = itemView.findViewById<android.widget.TextView>(R.id.tSnippet)
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    fun bind(r: com.example.yolodetect.db.ScanResult) {
        tDetected.text = "Detected: ${r.detected.ifBlank { "None" }}"
        tTime.text = fmt.format(Date(r.ts))
        tSnippet.text = r.ocrText
    }
}
