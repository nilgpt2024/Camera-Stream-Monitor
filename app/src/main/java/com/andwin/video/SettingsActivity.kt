package com.andwin.video

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import com.andwin.video.databinding.ActivitySettingsBinding
import com.andwin.video.utils.LocaleHelper

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportFragmentManager.beginTransaction()
            .replace(R.id.settings_container, SettingsFragment())
            .commit()

        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
            
            // Setup language preference change listener
            val languagePreference = findPreference<ListPreference>("app_language")
            languagePreference?.setOnPreferenceChangeListener { preference, newValue ->
                showLanguageRestartDialog(newValue.toString())
                true
            }
        }
        
        private fun showLanguageRestartDialog(languageCode: String) {
            val languageName = when (languageCode) {
                LocaleHelper.LANGUAGE_EN -> getString(R.string.language_english)
                else -> getString(R.string.language_chinese)
            }
            
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.language_settings))
                .setMessage(getString(R.string.restart_app_hint))
                .setPositiveButton(getString(R.string.ok)) { _, _ ->
                    // Save the language setting
                    LocaleHelper.setLocale(requireContext(), languageCode)
                    
                    // Restart the app to apply changes
                    val intent = Intent(requireContext(), MainActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    startActivity(intent)
                    requireActivity().finishAffinity()
                    
                    // Kill the process to ensure complete restart
                    android.os.Process.killProcess(android.os.Process.myPid())
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }
    }
    
    override fun attachBaseContext(newBase: android.content.Context?) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase!!, LocaleHelper.getLocale(newBase)))
    }
}
