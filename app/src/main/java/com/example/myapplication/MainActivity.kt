package com.example.myapplication

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.Button
import android.widget.Switch
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.get
import com.example.myapplication.com.example.myapplication.LocalService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.Thread.sleep
import kotlin.math.asin
import kotlin.math.atan2

class MainActivity : AppCompatActivity() {
    class MyListener(val callback: (listen: MyListener) -> Unit) : SensorEventListener {
        public var roll: Float = 0.0f
        public var pitch: Float = 0.0f
        public var yaw: Float = 0.0f
        var Q: FloatArray = FloatArray(4)
        var R: FloatArray = FloatArray(9)

        override fun onSensorChanged(event: SensorEvent?) {
            var test: FloatArray = FloatArray(3)
            if (event != null) {
                SensorManager.getQuaternionFromVector(Q, event.values)
                SensorManager.getRotationMatrixFromVector(R, event.values)
                SensorManager.getOrientation(R, test)
                //Q2Angle(Q)
                test = test.map { it * 180.0f / 3.14159f }.toFloatArray()
                yaw = test[0]
                roll = test[1]
                pitch = test[2]
                callback(this)
                //println("roll $roll   pitch:$pitch")
            }
        }

        fun Q2Angle(q: FloatArray) {
            pitch = asin(-1 * q[1] * q[3] + 2 * q[0] * q[2]) * 57.3f;           //pitch
            roll = atan2(2 * q[2] * q[3] + 2 * q[0] * q[1], -2 * q[1] * q[1] - 2 * q[2] * q[2] + 1) * 57.3f;   //roll
            yaw =
                atan2(2 * q[1] * q[2] + 2 * q[0] * q[3], -2 * q[2] * q[2] - 2 * q[3] * q[3] + 1) * 57.3f;         //yaw

        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        }
    }


    private lateinit var bytes: ByteArray
    private val crsfData: CRSFData = CRSFData()
    private var useGyroControl = false

    private lateinit var leftJoyStick: Joystick
    private lateinit var rightJoyStick: Joystick

    private var myService: LocalService? = null

    private lateinit var manualSwitch: Switch

    fun duty2CRSF(duty:Float)=(duty * 1639 + 172).toInt()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as LocalService.LocalBinder
            myService = binder.service
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            myService = null
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStart() {
        super.onStart()
        Intent(this, LocalService::class.java).also { service ->
            startForegroundService(service)
            bindService(service, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        unbindService(serviceConnection)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)

        val z = findViewById<Button>(R.id.openSerialButton)
        z.setOnClickListener {
            openSerial()
        }

        sensorManager.registerListener(MyListener(this::sensorCallBack), sensor, 10000);

        //bytes=getTestByteArray("C8 18 16 E0 03 1F 2B C0 F7 8B 5F FC E2 17 E5 2B 5F F9 CA 07 00 00 44 3C E2 B8")
        leftJoyStick = findViewById<Joystick>(R.id.leftJoystick)
        rightJoyStick = findViewById<Joystick>(R.id.rightJoystick)
        manualSwitch = findViewById(R.id.switchManual)

        // ch1 roll
        // ch2 pitch
        // ch3 throttle
        // ch4 yaw
        leftJoyStick.setOnJoystickMoveListener(
            object : OnJoystickMoveListener {
                override fun onJoystickValueChanged(x: Float, y: Float) {
                    if (!useGyroControl) {
                        crsfData.data_array[3] = duty2CRSF(x / 2f + 0.5f)
                        crsfData.data_array[2] = duty2CRSF(y / 2f + 0.5f)
                    }
                }
            }, 10
        )

        rightJoyStick.setOnJoystickMoveListener(
            object : OnJoystickMoveListener {
                override fun onJoystickValueChanged(x: Float, y: Float) {
                    if (!useGyroControl) {
                        crsfData.data_array[0] = duty2CRSF(x / 2 + 0.5f)
                        crsfData.data_array[1] = duty2CRSF(y / 2 + 0.5f)
                    }
                }
            }, 10
        )

        //val test=TextView(this)
        //test.setText("BV")
        //(tableLayoutView[3] as TableRow).addView(test)

        for (i in 1..16) {
            crsfData.data_array[i - 1] = i
        }
        bytes = crsfData.pack().toByteArray()
        for (i in bytes) {
            print(String.format("%02X,", i))
        }
        println("")
    }

    private fun debugInfo(str: String) =
        AlertDialog.Builder(this).setMessage(str).setTitle(getString(R.string.app_name)).create().show()

    fun getTestByteArray(str: String): ByteArray {
        val arr = str.split(" ")
        var result = ByteArray(26)
        for ((cnt, i) in arr.withIndex()) {
            result[cnt] = i.toUByte(16).toByte()
        }
        return result
    }


    /** Called when the user taps the Send button */
    fun openSerial() {
        myService?.tryToConnectToUsb()
        findViewById<Button>(R.id.openSerialButton).isEnabled = false
    }


    @SuppressLint("SetTextI18n")
    fun useGyro(view: View) {
        if (!useGyroControl) {
            useGyroControl = true
            leftJoyStick.enable = false
            rightJoyStick.enable = false

            findViewById<Button>(R.id.useGyroButton).text = "USE Joystick"
        } else {
            useGyroControl = false
            leftJoyStick.enable = true
            rightJoyStick.enable = true
            findViewById<Button>(R.id.useGyroButton).text = "USE Gyro"
        }
    }

    fun constrain(min: Float, max: Float, value: Float): Float {
        if (value < min) {
            return min
        } else if (value > max) {
            return max
        } else {
            return value
        }
    }

    @SuppressLint("SetTextI18n")
    fun sensorCallBack(listen: MyListener) {
            val channel_text = crsfData.data_array.map { "$it" }.joinToString("  ")
            findViewById<TextView>(R.id.testView).text = channel_text

            if (useGyroControl) {
                val tempRoll = constrain(-130f, -50f, listen.pitch) + 50
                val tempPitch = constrain(-40f, 40f, listen.roll) + 40

                crsfData.data_array[0] = duty2CRSF(1 + tempRoll / 80)   // raw:0~-180
                crsfData.data_array[1] = duty2CRSF(tempPitch / 80)  // raw:-90~90
                rightJoyStick.setXY((1 + tempRoll / 80) * 2 - 1, (tempPitch / 80) * 2 - 1)
            }

            if (manualSwitch.isChecked) {
                //crsfData.data_array[7]=1500
            } else {
                //crsfData.data_array[7]=500
            }
            bytes = crsfData.pack().toByteArray()
            myService?.sendToPort(bytes)
    }
}
