package com.atlasai.reportgenerator

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.atlasai.reportgenerator.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }

        when (AiSettings.getProvider(this)) {
            AiProvider.LOCAL -> binding.rbLocal.isChecked = true
            AiProvider.OPENAI -> binding.rbOpenAi.isChecked = true
            AiProvider.GEMINI -> binding.rbGemini.isChecked = true
        }
        binding.etApiKey.setText(AiSettings.getApiKey(this))
        binding.etModel.setText(AiSettings.getModel(this))

        binding.rgProvider.setOnCheckedChangeListener { _, checkedId ->
            val provider = when (checkedId) {
                R.id.rbOpenAi -> AiProvider.OPENAI
                R.id.rbGemini -> AiProvider.GEMINI
                else -> AiProvider.LOCAL
            }
            // Suggest default model when switching
            if (binding.etModel.text.isNullOrBlank() ||
                binding.etModel.text.toString() in listOf("gpt-4o-mini", "gemini-2.0-flash", "")
            ) {
                binding.etModel.setText(
                    when (provider) {
                        AiProvider.OPENAI -> "gpt-4o-mini"
                        AiProvider.GEMINI -> "gemini-2.0-flash"
                        AiProvider.LOCAL -> ""
                    }
                )
            }
        }

        binding.btnSave.setOnClickListener {
            val provider = when (binding.rgProvider.checkedRadioButtonId) {
                R.id.rbOpenAi -> AiProvider.OPENAI
                R.id.rbGemini -> AiProvider.GEMINI
                else -> AiProvider.LOCAL
            }
            val key = binding.etApiKey.text?.toString().orEmpty()
            val model = binding.etModel.text?.toString().orEmpty()

            if (provider != AiProvider.LOCAL && key.isBlank()) {
                Toast.makeText(this, R.string.api_key_required, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            AiSettings.setProvider(this, provider)
            AiSettings.setApiKey(this, key)
            if (model.isBlank()) {
                AiSettings.setModel(
                    this,
                    when (provider) {
                        AiProvider.OPENAI -> "gpt-4o-mini"
                        AiProvider.GEMINI -> "gemini-2.0-flash"
                        else -> ""
                    }
                )
            } else {
                AiSettings.setModel(this, model)
            }
            val msg = when (provider) {
                AiProvider.OPENAI -> "ChatGPT connected — generate a report next"
                AiProvider.GEMINI -> "Gemini connected — generate a report next"
                else -> getString(R.string.settings_saved)
            }
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            finish()
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }
    }
}
