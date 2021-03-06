package com.example.dronecontrolapp

import android.content.Context
import android.content.Context.SENSOR_SERVICE
import android.graphics.Matrix
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.Bundle
import android.view.*
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.graphics.rotationMatrix
import androidx.core.graphics.values
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.navigation.fragment.findNavController
import com.example.dronecontrolapp.databinding.FragmentFirstBinding
import java.io.PrintWriter
import java.lang.Math.sin
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.Semaphore
import kotlin.math.absoluteValue
import kotlin.math.roundToInt


/** commit test
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class DroneConnection(private var activity: FragmentActivity, private var info_text: TextView) : Thread() {
    private var socket: Socket? = null
    private var sem = Semaphore(0)
    private val lock = Object()
    private var Function = ""
    private var hostname = ""
    private var port = 0
    private var busy = false
    private var throttle_value = 0
    private var spin_value = 0
    private var pitch_dir_val = 0
    private var pitch_strength_val = 0

    fun connect(hostname: String, port: Int) {
        synchronized(lock) {
            Function = "connect"
            this.hostname = hostname
            this.port = port
            busy = true
            sem.release()
        }
    }

    fun is_connected() : Boolean {
        return socket!!.isConnected
    }

    fun send_throttle_spin(throttle: Int, spin: Int) {
        synchronized(lock) {
            if (busy)
                return

            Function = "send_throttle_spin"
            throttle_value = throttle
            spin_value = spin
            busy = true
            sem.release()
        }
    }

    fun send_pitch(pitch_dir: Int, pitch_strength: Int) {
        synchronized(lock) {
            if (busy)
                return

            Function = "send_pitch"
            pitch_dir_val = pitch_dir
            pitch_strength_val = pitch_strength
            busy = true
            sem.release()
        }
    }

    override fun run() {
        while (!interrupted()) {
            try {
                sem.acquire()
            }
            catch(e : InterruptedException) {
                Function = ""
                interrupt()
            }

            when(Function) {
                "connect" -> {
                    try {
                        socket = Socket(hostname, port)
                    }
                    catch(e : Exception) {
                        activity.runOnUiThread(object: Runnable {
                            override fun run() {
                                info_text.text = "Could not connect to drone"
                            }
                        })
                        when(e) {
                            is SocketException -> {
                                println("SOCKET EXCEPTION")
                            }
                            is SocketTimeoutException -> {
                                println("SOCKET TIMEOUT")
                            }
                            else -> {
                                println("UNKNOWN CONNECTION ERROR")
                            }
                        }
                        socket = null
                    }
                    if(socket != null) {
                        activity.runOnUiThread(object : Runnable {
                            override fun run() {
                                info_text.text = "Connected"
                            }
                        })
                    }
                }
                "send_throttle_spin" -> {
                    try
                    {
                        if(socket!!.isConnected) {
                            var os = socket?.getOutputStream()
                            var output = PrintWriter(os)
                            output.print("Throttle: $throttle_value\n")
                            output.print("Spin: $spin_value\n")
                            output.flush()
                        }
                    }
                    catch (e : NullPointerException) {
                        //
                    }
                }
                "send_pitch" -> {
                    try
                    {
                        if(socket!!.isConnected) {
                            var os = socket?.getOutputStream()
                            var output = PrintWriter(os)
                            output.print("Pitch: $pitch_dir_val, $pitch_strength_val\n")
                            output.flush()
                        }
                    }
                    catch (e : NullPointerException) {
                        //
                    }
                }
            }
            busy = false
        }
        socket?.close()
    }
}

class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null
    private lateinit var info_text : TextView
    private lateinit var drone_connection : DroneConnection
    private lateinit var right_joystick: JoystickView
    private lateinit var left_joystick: JoystickView
    private lateinit var sensorManager: SensorManager
    private var sensor: Sensor? = null
    private lateinit var mode_button: Button
    private var gyro_mode = false

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private fun mul(a : FloatArray, b : FloatArray, c : FloatArray) {
        for (i in 0..2) {
            for (j in 0..2) {
                c[3 * i + j] = 0F
                for (k in 0..2)
                    c[3 * i + j] += a[3 * i + k] * b[3 * k + j]
            }
        }
    }

    private fun handle_tilt(angle : Double, strength : Double) {
        println("PITCHU ESTE " + angle + ", " + strength)
        try {
            if(!drone_connection.is_connected()) {
                info_text.text = "Drone is not connected"
            } else {
                drone_connection.send_pitch(angle.toInt(), strength.toInt())
            }
        } catch (e: NullPointerException) {
            info_text.text = "Drone is not connected"
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        //val policy = ThreadPolicy.Builder().permitAll().build()
        //StrictMode.setThreadPolicy(policy)
        var wifi_network: Network? = null
        val cm = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        for (n in cm.getAllNetworks()) {
            if (cm.getNetworkInfo(n)?.getTypeName()  == "WIFI") {
                wifi_network = n
                break
            }
        }
        cm.bindProcessToNetwork(wifi_network)

        info_text = binding.textView

        drone_connection = DroneConnection(requireActivity(), info_text)
        drone_connection.start()
        if(SecondFragment.port == 0)
            drone_connection.connect(getString(R.string.default_ip), Integer.parseInt(getString(R.string.default_port)))
        else
            drone_connection.connect(SecondFragment.hostname, SecondFragment.port)

        left_joystick = binding.leftJoystick
        left_joystick.isAutoReCenterButton = false
        left_joystick.isAutoReCenterButtonHorizontal = true
        left_joystick.isSquareJoystick = true

        right_joystick = binding.rightJoystick
        right_joystick.isAutoReCenterButton = true

        sensorManager = requireContext().getSystemService(SENSOR_SERVICE) as SensorManager
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        val reference_measurement = FloatArray(9)
        var first_measurement = true

        mode_button = binding.modeButton
        mode_button.text = "Joystick"

        mode_button.setOnClickListener {
            if(mode_button.text == "Joystick") {
                mode_button.text = "Gyro"
                right_joystick.isAutoReCenterButton = false
                first_measurement = true
            }
            else {
                mode_button.text = "Joystick"
                right_joystick.isAutoReCenterButton = true
                right_joystick.setButtonPosition(0, 0)
                right_joystick.invalidate()
            }
            gyro_mode = !gyro_mode;
        }

        var last_changed = 0L
        sensor.also { rot ->
            sensorManager.registerListener(object : SensorEventListener {
                override fun onSensorChanged(sensor: SensorEvent?) {
                    if (!gyro_mode)
                        return
                    if (first_measurement) {
                        SensorManager.getRotationMatrixFromVector(
                            reference_measurement,
                            sensor!!.values
                        )
                        first_measurement = false
                    }
                    val actual_measurement = FloatArray(3)
                    var ref_matrix = Matrix()
                    ref_matrix.setValues(reference_measurement)
                    var ref_inverse_matrix = Matrix()
                    ref_matrix.invert(ref_inverse_matrix)

                    val cur_measurement = FloatArray(9)
                    SensorManager.getRotationMatrixFromVector(
                        cur_measurement,
                        sensor!!.values
                    )

                    val oz_measurement = FloatArray(9)
                    for (i in 0..8)
                        oz_measurement[i] = cur_measurement[i]
                    for (i in 0..1) {
                        oz_measurement[3 * 2 + i] = 0F
                        oz_measurement[3 * i + 2] = 0F
                    }
                    oz_measurement[3 * 2 + 2] = 1F
                    var oz_matrix = Matrix()
                    oz_matrix.setValues(oz_measurement)
                    var oz_inverse_matrix = Matrix()
                    oz_matrix.invert(oz_inverse_matrix)

                    val rot = FloatArray(9)
                    mul(cur_measurement, ref_inverse_matrix.values(), rot)
                    val final_rot = FloatArray(9)
                    mul(oz_inverse_matrix.values(), rot, final_rot)

                    val unit_vector = FloatArray(3)
                    unit_vector[0] = 0F
                    unit_vector[1] = 0F
                    unit_vector[2] = 1F

                    for (i in 0..2) {
                        actual_measurement[i] = 0F
                        for (j in 0..2)
                            actual_measurement[i] += unit_vector[j] * final_rot[3 * i + j]
                    }
                    right_joystick.setButtonPosition((-actual_measurement[1] * 200).toInt(), (-actual_measurement[0] * 200).toInt())
                    right_joystick.invalidate()
                    var cur_time = System.currentTimeMillis()
                    if(cur_time - last_changed >= JoystickView.DEFAULT_LOOP_INTERVAL) {
                        handle_tilt(right_joystick.angle, right_joystick.strength)
                        last_changed = cur_time
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, acc: Int) {
                    //
                }

            }, rot, SensorManager.SENSOR_DELAY_GAME)
        }

        left_joystick.setOnMoveListener { angle, strength ->
            var throttle =
                (kotlin.math.sin(angle * kotlin.math.PI / 180.0) * strength + 100.0) / 2.0
            println("THROTTLEUL ESTE " + throttle)
            var spin = kotlin.math.cos(angle * kotlin.math.PI / 180.0) * strength / 2.0
            println("SPINU ESTE " + spin)
            try {
                if (!drone_connection.is_connected()) {
                    info_text.text = "Drone is not connected"
                } else {
                    if(throttle <= 5.0)
                        throttle = 0.0
                    if(spin.absoluteValue <= 10.0)
                        spin = 0.0
                    drone_connection.send_throttle_spin(throttle.coerceIn(0.0, 100.0).roundToInt(), spin.coerceIn(-50.0, 50.0).roundToInt())
                }
            } catch (e: NullPointerException) {
                info_text.text = "Drone is not connected"
            }
        }


        right_joystick.setOnMoveListener { angle, strength ->
            handle_tilt(angle, strength)
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonNext.setOnClickListener {
            findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        drone_connection.interrupt()
        _binding = null
    }
}