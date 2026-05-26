package com.andwin.video

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.andwin.video.databinding.ItemCameraBinding
import com.andwin.video.model.CameraConfig

class CameraListAdapter(
    private val cameras: List<CameraConfig>,
    private val onItemClick: (CameraConfig) -> Unit
) : RecyclerView.Adapter<CameraListAdapter.CameraViewHolder>() {

    inner class CameraViewHolder(val binding: ItemCameraBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CameraViewHolder {
        val binding = ItemCameraBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CameraViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CameraViewHolder, position: Int) {
        val camera = cameras[position]
        holder.binding.apply {
            tvCameraName.text = camera.name
            tvResolution.text = "${camera.resolution.width}x${camera.resolution.height}"
            tvFps.text = "${camera.fps} FPS"

            ivStatus.setImageResource(
                if (camera.isRecording || camera.isStreaming)
                    android.R.drawable.ic_media_play
                else
                    android.R.drawable.ic_media_pause
            )

            root.setOnClickListener {
                onItemClick(camera)
            }
        }
    }

    override fun getItemCount(): Int = cameras.size
}
