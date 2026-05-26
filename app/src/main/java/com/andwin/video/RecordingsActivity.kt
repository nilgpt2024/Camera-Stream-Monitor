package com.andwin.video

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.andwin.video.databinding.ActivityRecordingsBinding
import com.andwin.video.recorder.VideoRecorder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecordingsBinding
    private lateinit var videoRecorder: VideoRecorder
    private lateinit var adapter: RecordingAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecordingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        videoRecorder = VideoRecorder(this)
        
        setupToolbar()
        setupRecyclerView()
        loadRecordings()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "录制记录"
    }

    private fun setupRecyclerView() {
        adapter = RecordingAdapter(
            onItemClick = { file ->
                playVideo(file)
            },
            onDeleteClick = { file, position ->
                confirmDelete(file, position)
            }
        )
        
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@RecordingsActivity)
            adapter = this@RecordingsActivity.adapter
        }
    }

    private fun loadRecordings() {
        val files = videoRecorder.getRecordingFiles()
        
        if (files.isEmpty()) {
            binding.emptyView.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
            binding.tvCount.text = "暂无录制"
        } else {
            binding.emptyView.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
            
            val totalSize = formatFileSize(videoRecorder.getTotalSize())
            binding.tvCount.text = "${files.size} 个视频 · $totalSize"
            
            adapter.submitList(files)
        }
    }

    private fun playVideo(file: File) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("video_path", file.absolutePath)
            putExtra("auto_play", true)
        }
        startActivity(intent)
    }

    private fun confirmDelete(file: File, position: Int) {
        AlertDialog.Builder(this)
            .setTitle("删除确认")
            .setMessage("确定要删除 \"${file.name}\" 吗？")
            .setPositiveButton("删除") { _, _ ->
                if (videoRecorder.deleteRecording(file)) {
                    adapter.notifyItemRemoved(position)
                    loadRecordings()
                    // 重新加载完整列表
                    val newList = videoRecorder.getRecordingFiles()
                    if (newList.isEmpty()) {
                        adapter.submitList(emptyList())
                        loadRecordings()
                    } else {
                        adapter.submitList(newList)
                    }
                } else {
                    android.widget.Toast.makeText(this, "删除失败", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_recordings, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            R.id.action_clear_all -> {
                confirmClearAll()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun confirmClearAll() {
        val count = videoRecorder.getRecordingFiles().size
        if (count == 0) return
        
        AlertDialog.Builder(this)
            .setTitle("清空确认")
            .setMessage("确定要删除全部 $count 个录制文件吗？此操作不可恢复！")
            .setPositiveButton("清空全部") { dialog: android.content.DialogInterface, which: Int ->
                val deleted = videoRecorder.clearAllRecordings()
                adapter.submitList(emptyList())
                loadRecordings()
                android.widget.Toast.makeText(this, "已删除 $deleted 个文件", android.widget.Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        loadRecordings()
    }

    /**
     * 格式化文件大小
     */
    private fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format("%.1f MB", mb)
        val gb = mb / 1024.0
        return String.format("%.2f GB", gb)
    }

    /**
     * 格式化时间
     */
    private fun formatTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    inner class RecordingAdapter(
        private val onItemClick: (File) -> Unit,
        private val onDeleteClick: (File, Int) -> Unit
    ) : RecyclerView.Adapter<RecordingAdapter.ViewHolder>() {

        private var items = listOf<File>()

        fun submitList(newItems: List<File>) {
            items = newItems
            notifyDataSetChanged()
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val ivThumbnail: ImageView = itemView.findViewById(R.id.ivThumbnail)
            val tvFileName: TextView = itemView.findViewById(R.id.tvFileName)
            val tvFileInfo: TextView = itemView.findViewById(R.id.tvFileInfo)
            val tvFileSize: TextView = itemView.findViewById(R.id.tvFileSize)
            val btnPlay: ImageView = itemView.findViewById(R.id.btnPlay)
            val btnDelete: ImageView = itemView.findViewById(R.id.btnDelete)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = layoutInflater.inflate(R.layout.item_recording, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val file = items[position]
            
            holder.tvFileName.text = file.name
            holder.tvFileInfo.text = formatTime(file.lastModified())
            holder.tvFileSize.text = formatFileSize(file.length())
            
            // 设置缩略图（使用视频图标）
            holder.ivThumbnail.setImageResource(R.drawable.ic_video_file)
            
            holder.itemView.setOnClickListener { onItemClick(file) }
            holder.btnPlay.setOnClickListener { onItemClick(file) }
            holder.btnDelete.setOnClickListener { onDeleteClick(file, position) }
        }

        override fun getItemCount(): Int = items.size
    }
}
