package com.oldpad.app

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.oldpad.app.data.AppSettings
import com.oldpad.app.data.PhotoRepository
import com.oldpad.app.data.VideoRepository
import com.oldpad.app.databinding.ActivitySettingsBinding
import com.oldpad.app.ui.Dialogs
import com.oldpad.app.util.MediaPermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class SettingsActivity : BaseActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        // 开机自启
        binding.switchAutoStart.isChecked = AppSettings.autoStart(this)
        binding.switchAutoStart.setOnCheckedChangeListener { _, checked ->
            AppSettings.prefs(this).edit().putBoolean(AppSettings.KEY_AUTO_START, checked).apply()
        }

        // 桌面模式
        binding.switchLauncher.isChecked = AppSettings.launcherMode(this)
        binding.switchLauncher.setOnCheckedChangeListener { _, checked ->
            setLauncherMode(checked)
        }

        // 屏幕常亮
        binding.switchKeepAwake.isChecked = AppSettings.keepAwake(this)
        binding.switchKeepAwake.setOnCheckedChangeListener { _, checked ->
            AppSettings.prefs(this).edit().putBoolean(AppSettings.KEY_KEEP_AWAKE, checked).apply()
        }

        // 字幕大小
        binding.btnSizeBig.setOnClickListener { setSubtitleScale(0.08f) }
        binding.btnSizeBigger.setOnClickListener { setSubtitleScale(0.11f) }
        binding.btnSizeBiggest.setOnClickListener { setSubtitleScale(0.14f) }
        updateSizeButtons()

        binding.btnManageHome.setOnClickListener {
            startActivity(Intent(this, ManageHomeActivity::class.java))
        }

        binding.btnRescan.setOnClickListener { rescan() }
        binding.btnSystemSettings.setOnClickListener { openAppSettings() }
        binding.btnBattery.setOnClickListener { requestIgnoreBattery() }

        setupAllFilesButton()
    }

    override fun onResume() {
        super.onResume()
        setupAllFilesButton()
    }

    private fun setSubtitleScale(scale: Float) {
        AppSettings.prefs(this).edit()
            .putFloat(AppSettings.KEY_SUBTITLE_SCALE, scale)
            .apply()
        updateSizeButtons()
    }

    private fun updateSizeButtons() {
        val current = AppSettings.subtitleScale(this)
        val options = listOf(
            binding.btnSizeBig to 0.08f,
            binding.btnSizeBigger to 0.11f,
            binding.btnSizeBiggest to 0.14f
        )
        for ((button, value) in options) {
            val selected = abs(current - value) < 0.001f
            button.setBackgroundResource(
                if (selected) R.drawable.bg_button_accent else R.drawable.bg_button
            )
            button.setTextColor(
                getColor(if (selected) R.color.bg else R.color.text)
            )
        }
    }

    private fun setLauncherMode(enabled: Boolean) {
        val alias = ComponentName(this, "com.oldpad.app.LauncherAlias")
        try {
            packageManager.setComponentEnabledSetting(
                alias,
                if (enabled) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                },
                PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {
            toast("设置失败：" + e.message)
        }
        AppSettings.prefs(this).edit()
            .putBoolean(AppSettings.KEY_LAUNCHER, enabled)
            .apply()

        if (enabled) {
            Dialogs.confirm(
                this,
                "设为桌面",
                "接下来系统会让你选择默认桌面，请选 OldPad。这样开机后就直接显示首页；想换回来时，在系统设置里改回原来的桌面即可。",
                "去设置",
                "稍后"
            ) {
                openHomeSettings()
            }
        }
    }

    private fun openHomeSettings() {
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (ignored: Exception) {
                toast(getString(R.string.not_supported))
            }
        }
    }

    private fun setupAllFilesButton() {
        if (Build.VERSION.SDK_INT < 30) {
            binding.btnAllFiles.visibility = android.view.View.GONE
            return
        }
        binding.btnAllFiles.visibility = android.view.View.VISIBLE
        if (MediaPermissions.hasAllFilesAccess()) {
            binding.btnAllFiles.text = getString(R.string.all_files_granted)
            binding.btnAllFiles.setOnClickListener {
                toast(getString(R.string.all_files_granted))
            }
            return
        }
        binding.btnAllFiles.setOnClickListener {
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (e: Exception) {
                toast(getString(R.string.not_supported))
            }
        }
    }

    private fun rescan() {
        lifecycleScope.launch {
            VideoRepository.invalidate()
            PhotoRepository.invalidate()
            val (videoCount, photoCount) = withContext(Dispatchers.IO) {
                VideoRepository.videos(this@SettingsActivity, true).size to
                    PhotoRepository.photos(this@SettingsActivity, true).size
            }
            toast(getString(R.string.refresh_count, videoCount, photoCount))
        }
    }

    private fun openAppSettings() {
        try {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (e: Exception) {
            toast(getString(R.string.not_supported))
        }
    }

    private fun requestIgnoreBattery() {
        val power = getSystemService(PowerManager::class.java)
        if (power != null && power.isIgnoringBatteryOptimizations(packageName)) {
            toast("已经关闭电池优化")
            return
        }
        try {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (e: Exception) {
            toast("请在系统设置里找到 OldPad，选择“不限制电量”")
        }
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }
}
