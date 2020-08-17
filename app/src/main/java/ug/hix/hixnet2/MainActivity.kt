package ug.hix.hixnet2

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.p2p.WifiP2pManager
import android.os.Bundle
import android.os.Handler
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.knexis.hotspot.Hotspot
import kotlinx.android.synthetic.main.activity_main.*
import ug.hix.hixnet2.cyphers.Generator
import ug.hix.hixnet2.fragments.FileFragment
import ug.hix.hixnet2.meshlink.MeshServiceManager
import ug.hix.hixnet2.meshlink.NetworkCardManager
import ug.hix.hixnet2.models.DeviceNode
import ug.hix.hixnet2.services.MeshDaemon
import kotlin.concurrent.thread

//import ug.hix.hixnet2.MeshDaemon

class MainActivity : AppCompatActivity() {
    private val SPLASH_TIMEOUT = 4000
    private lateinit var manager : WifiP2pManager
    private lateinit var channel : WifiP2pManager.Channel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN)
        setContentView(R.layout.activity_main)


        thread{
            Generator.getDatabaseInstance(this)
            Generator.loadKeys()
        }


        Handler().postDelayed({
            val intent = Intent(this,HomeActivity::class.java)
            startActivity(intent)
            finish()
        }, SPLASH_TIMEOUT.toLong())

    }


}

