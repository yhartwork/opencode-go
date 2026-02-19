package com.example.codeonly

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.codeonly.api.OpenCodeClient
import com.example.codeonly.databinding.ActivityConnectionBinding
import com.example.codeonly.util.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ConnectionActivity : AppCompatActivity() {
    private lateinit var binding: ActivityConnectionBinding
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var prefs: Preferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConnectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = Preferences(this)

        if (prefs.hasCompletedSetup && prefs.baseUrl.isNotBlank()) {
            navigateToMain()
            return
        }

        val savedUrl = prefs.baseUrl
        if (savedUrl.isNotBlank()) {
            binding.serverUrlInput.setText(savedUrl)
        }

        binding.testConnectionButton.setOnClickListener {
            testConnection()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun testConnection() {
        var url = binding.serverUrlInput.text.toString().trim()
        if (url.isBlank()) {
            binding.statusText.text = "Please enter a server URL"
            return
        }

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://$url"
        }
        url = url.trimEnd('/')

        binding.progressBar.visibility = View.VISIBLE
        binding.statusText.text = "Connecting..."
        binding.testConnectionButton.isEnabled = false

        scope.launch {
            val client = OpenCodeClient(url)
            try {
                val health = client.healthCheck()
                if (health.healthy) {
                    prefs.baseUrl = url
                    prefs.hasCompletedSetup = true
                    binding.statusText.text = "Connected! Version: ${health.version}"
                    binding.versionText.text = "OpenCode ${health.version}"
                    
                    // Try to get project info
                    val project = client.getCurrentProject()
                    if (project != null) {
                        binding.statusText.text = "Connected! Project: ${project.name ?: project.worktree}"
                    }
                    
                    // Save and navigate
                    binding.statusText.postDelayed({
                        navigateToMain()
                    }, 1000)
                } else {
                    binding.statusText.text = "Server returned unhealthy status"
                }
            } catch (e: Exception) {
                binding.statusText.text = "Connection failed: ${e.message}"
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.testConnectionButton.isEnabled = true
            }
        }
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
