package com.example.myapplication.com.example.myapplication

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcelable
import android.util.Log
import android.widget.Toast
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.Exception

class LocalService : Service(), SerialInputOutputManager.Listener, CoroutineScope by CoroutineScope(
    SupervisorJob() + Dispatchers.IO) {

    private val binder: IBinder = LocalBinder()
    private var usbDevice: UsbDevice? = null
    private var mUsbManager: UsbManager? = null
    private var serialPort: UsbSerialPort? = null
    private var ioManager: SerialInputOutputManager? = null

    private val handlerException = CoroutineExceptionHandler { _, exception ->
    }

    override fun onCreate() {
        super.onCreate()
        mUsbManager = getSystemService(USB_SERVICE) as UsbManager
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        registerReceiver(usbReceiver, filter)
    }

    inner class LocalBinder : Binder() {
        // Return this instance of LocalService so clients can call public methods.
        val service: LocalService
            get() =// Return this instance of LocalService so clients can call public methods.
                this@LocalService
    }
    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }

    fun tryToConnectToUsb() {
        val usbManager = applicationContext.getSystemService(Context.USB_SERVICE) as UsbManager
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (availableDrivers.size == 0) return
        val driver = availableDrivers[0]
        serialPort = driver.ports[0]
        val connection = usbManager.openDevice(driver.device)

        this@LocalService.launch(handlerException) {
                serialPort?.open(connection)
                val baudRate = 400000
                serialPort?.setParameters(
                    baudRate,
                    UsbSerialPort.DATABITS_8,
                    UsbSerialPort.STOPBITS_1,
                    UsbSerialPort.PARITY_NONE
                )

                ioManager = SerialInputOutputManager(serialPort, this@LocalService)

                ioManager?.start()
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(applicationContext, "Готов к передаче!", Toast.LENGTH_SHORT)
                        .show()
                }
        }
    }

    fun sendToPort(bytes: ByteArray){
        if (flag == null)
            flag = this@LocalService.launch(Dispatchers.IO) {
                serialPort?.write(bytes, 200)
                delay(200)
                flag = null
            }
    }
    private var flag: Job? = null

    override fun onNewData(p0: ByteArray?) {

    }

    private fun getUsbDevice(intent: Intent): UsbDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            intent.getParcelableExtra<Parcelable>(UsbManager.EXTRA_DEVICE) as? UsbDevice
        }
    }

    private val usbReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            usbDevice = getUsbDevice(intent)
            if (ACTION_USB_PERMISSION == action) {
                Log.e("USB", "Permission")
                try {
                    synchronized(this) {
                        usbDevice = getUsbDevice(intent)
                    }
                } catch (e: Exception) {
                    Log.e("SerialService", e.toString())
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED == action) {
                serialPort?.close()
                serialPort = null
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED == action) {
                Log.e("USB", "ATTACHED")
                usbDevice = getUsbDevice(intent)
                //                tryToconnectToUsb(usbDevice);
            }
        }
    }

    companion object {
        private const val ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION"
    }

    override fun onRunError(p0: Exception?) {

    }

}