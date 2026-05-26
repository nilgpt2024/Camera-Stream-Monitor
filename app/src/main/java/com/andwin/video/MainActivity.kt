package com.andwin.video

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.andwin.video.databinding.ActivityMainBinding
import com.andwin.video.model.CameraConfig
import com.andwin.video.utils.LocaleHelper
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val cameraList = mutableListOf<CameraConfig>()
    private lateinit var cameraAdapter: CameraListAdapter
    private val PERMISSIONS = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE
    )

    override fun attachBaseContext(newBase: android.content.Context?) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase!!, LocaleHelper.getLocale(newBase)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViews()
        setupBottomNavigation()
        checkPermissions()
        loadCameras()
    }

    private fun setupViews() {
        cameraAdapter = CameraListAdapter(cameraList) { config ->
            openMonitor(config)
        }
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = cameraAdapter
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_add -> {
                    openMonitor(CameraConfig(
                        id = System.currentTimeMillis().toString(),
                        name = getString(R.string.camera_name_prefix, cameraList.size + 1)
                    ))
                    true
                }
                R.id.nav_player -> {
                    startActivity(Intent(this, PlayerActivity::class.java))
                    true
                }
                R.id.nav_recordings -> {
                    startActivity(Intent(this, RecordingsActivity::class.java))
                    true
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }

    private fun checkPermissions() {
        val missingPermissions = PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions, REQUEST_CODE_PERMISSIONS)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_CODE_NOTIFICATION_PERMISSION)
            }
        }
    }

    private fun loadCameras() {
        lifecycleScope.launch {
            cameraList.clear()
            cameraList.addAll(loadCameraConfigs())
            cameraAdapter.notifyDataSetChanged()

            binding.emptyView.visibility = if (cameraList.isEmpty()) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }

            binding.tvCameraCount.text = cameraList.size.toString()
            binding.tvCameraCountLabel.text = getString(R.string.device_count, cameraList.size)
        }
    }

    private suspend fun loadCameraConfigs(): List<CameraConfig> {
        return listOf(
            CameraConfig(id = "1", name = getString(R.string.front_camera_name), cameraId = "0"),
            CameraConfig(id = "2", name = getString(R.string.rear_camera_name), cameraId = "1")
        )
    }

    private fun openMonitor(config: CameraConfig) {
        val intent = Intent(this, MonitorActivity::class.java).apply {
            putExtra("camera_config", config)
        }
        startActivity(intent)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, getString(R.string.permission_granted), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.permission_required), Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadCameras()
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 1001
        private const val REQUEST_CODE_NOTIFICATION_PERMISSION = 1002
    }
}
