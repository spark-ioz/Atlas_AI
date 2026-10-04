package com.atlasai.reportgenerator

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.atlasai.reportgenerator.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupAnimations()
        setupClicks()
    }

    private fun setupAnimations() {
        val fade = AnimationUtils.loadAnimation(this, R.anim.fade_in)
        val slide = AnimationUtils.loadAnimation(this, R.anim.slide_up)
        binding.tvGreeting.startAnimation(fade)
        binding.tvHelpPrompt.startAnimation(fade)
        binding.featureGrid.startAnimation(slide)
        binding.btnStart.startAnimation(slide)
    }

    private fun setupClicks() {
        binding.btnStart.setOnClickListener { animate(it) { openStudio(ReportMode.STYLE_CLONE) } }
        binding.cardStyleClone.setOnClickListener { animate(it) { openStudio(ReportMode.STYLE_CLONE) } }
        binding.cardExecutive.setOnClickListener { animate(it) { openStudio(ReportMode.EXECUTIVE) } }
        binding.cardResearch.setOnClickListener { animate(it) { openStudio(ReportMode.RESEARCH) } }
        binding.cardCreative.setOnClickListener { animate(it) { openStudio(ReportMode.CREATIVE) } }

        binding.btnTheme.setOnClickListener {
            animate(it) {
                startActivity(Intent(this, SettingsActivity::class.java))
                overridePendingTransition(R.anim.slide_up, R.anim.fade_out)
            }
        }

        // Bottom nav — all functional
        binding.navHome.setOnClickListener {
            // already home — gentle feedback
            binding.scrollMain.smoothScrollTo(0, 0)
        }
        binding.navCreate.setOnClickListener {
            openStudio(ReportMode.STYLE_CLONE)
        }
        binding.navLibrary.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
            overridePendingTransition(R.anim.slide_up, R.anim.fade_out)
        }
    }

    private fun openStudio(mode: ReportMode) {
        startActivity(Intent(this, GenerateActivity::class.java).apply {
            putExtra(GenerateActivity.EXTRA_MODE, mode.name)
        })
        overridePendingTransition(R.anim.slide_up, R.anim.fade_out)
    }

    private fun animate(view: View, action: () -> Unit) {
        view.animate().scaleX(0.95f).scaleY(0.95f).setDuration(70)
            .withEndAction {
                view.animate().scaleX(1f).scaleY(1f).setDuration(90)
                    .withEndAction { action() }.start()
            }.start()
    }
}

enum class ReportMode {
    STYLE_CLONE, EXECUTIVE, RESEARCH, CREATIVE
}
