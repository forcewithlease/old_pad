package com.oldpad.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.oldpad.app.databinding.ActivityTransferBinding
import com.oldpad.app.transfer.TransferServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlin.random.Random

class TransferActivity : BaseActivity() {

    companion object {
        const val EXTRA_DEBUG_PIN = "debug_pin"
    }

    private lateinit var binding: ActivityTransferBinding
    private var server: TransferServer? = null
    private var port = 0
    private var pin = ""
    private var url = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTransferBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemInsets()
        applyKeepAwake(true)

        binding.btnBack.setOnClickListener { finish() }

        pin = if (BuildConfig.DEBUG) {
            intent.getStringExtra(EXTRA_DEBUG_PIN) ?: newPin()
        } else {
            newPin()
        }
        binding.pin.text = pin

        val ip = localIpAddress()
        if (ip == null) {
            binding.status.text = getString(R.string.transfer_no_wifi)
            binding.url.text = "-"
            binding.btnCopy.isEnabled = false
            return
        }

        if (!startServer()) {
            binding.status.text = getString(R.string.transfer_port_busy)
            return
        }

        url = "http://$ip:$port/?pin=$pin"
        binding.url.text = url
        binding.status.text = getString(R.string.transfer_waiting)

        binding.btnCopy.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("OldPad 快传地址", url))
            Toast.makeText(this, R.string.transfer_copied, Toast.LENGTH_SHORT).show()
        }

        lifecycleScope.launch {
            val qr = withContext(Dispatchers.Default) { makeQr(url) }
            if (qr != null) binding.qr.setImageBitmap(qr)
        }
    }

    private fun startServer(): Boolean {
        for (candidate in 8080..8086) {
            try {
                server = TransferServer(applicationContext, candidate, pin)
                port = candidate
                return true
            } catch (e: Exception) {
                // 端口被占用，换下一个
            }
        }
        return false
    }

    private fun localIpAddress(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList()
            val candidates = ArrayList<Pair<String, String>>()
            for (network in interfaces) {
                if (!network.isUp || network.isLoopback) continue
                for (address in network.inetAddresses) {
                    if (address is Inet4Address && !address.isLoopbackAddress &&
                        address.isSiteLocalAddress
                    ) {
                        candidates += network.name to address.hostAddress.orEmpty()
                    }
                }
            }
            candidates.firstOrNull { it.first.startsWith("wlan") }?.second
                ?: candidates.firstOrNull()?.second
        } catch (e: Exception) {
            null
        }
    }

    private fun newPin(): String = (1000 + Random.nextInt(9000)).toString()

    private fun makeQr(text: String): Bitmap? {
        return try {
            val size = 560
            val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap.setPixel(
                        x, y,
                        if (matrix.get(x, y)) Color.BLACK else Color.WHITE
                    )
                }
            }
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    override fun onDestroy() {
        try {
            server?.stop()
        } catch (ignored: Exception) {
        }
        server = null
        super.onDestroy()
    }
}
