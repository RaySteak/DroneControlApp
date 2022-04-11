package com.example.dronecontrolapp

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.navigation.fragment.findNavController
import com.example.dronecontrolapp.databinding.FragmentFirstBinding
import java.io.PrintWriter
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.Semaphore


/** commit test
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class DroneConnection(activity : FragmentActivity, info_text : TextView) : Thread() {
    private var socket: Socket? = null
    private var sem: Semaphore = Semaphore(0)
    private var Function = ""
    private var hostname = ""
    private var port = 0
    private var info_text = info_text
    private var activity = activity
    private var busy = false
    private var throttle_value = 0

    fun connect(hostname: String, port: Int) {
        Function = "connect"
        this.hostname = hostname
        this.port = port
        sem.release()
    }

    fun is_connected() : Boolean {
        return socket!!.isConnected
    }

    fun send_throttle(value: Int) {
        if (busy)
            return

        Function = "send_throttle"
        throttle_value = value
        sem.release()
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

            busy = true
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
                "send_throttle" -> {
                    try
                    {
                        if(socket!!.isConnected) {
                            var os = socket?.getOutputStream()
                            var output = PrintWriter(os)
                            output.print("Throttle: $throttle_value\n")
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
    private lateinit var throttle_bar : SeekBar
    private lateinit var steer_bar : SeekBar
    private lateinit var info_text : TextView
    private lateinit var drone_connection : DroneConnection

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

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

        throttle_bar = binding.throttle
        throttle_bar.max = 100
        throttle_bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean): Unit
            {
                try {
                    if (!drone_connection.is_connected()) {
                        info_text.text = "Drone is not connected"
                    }
                    else {
                        drone_connection.send_throttle(throttle_bar.progress)
                    }
                }
                catch(e : NullPointerException) {
                    info_text.text = "Drone is not connected"
                }
            }

            override fun onStartTrackingTouch(bar: SeekBar?) {
                //
            }

            override fun onStopTrackingTouch(bar: SeekBar?) {
                //
            }
        })

        steer_bar = binding.steer
        steer_bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean): Unit
            {

            }

            override fun onStartTrackingTouch(bar: SeekBar?) {
                //
            }

            override fun onStopTrackingTouch(bar: SeekBar?) {
                bar?.progress = 50
            }
        })

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