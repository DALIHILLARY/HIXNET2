package ug.hix.hixnet2

import android.content.Intent
import android.net.wifi.p2p.WifiP2pManager
import android.os.Bundle
import android.os.Handler
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.*
import ug.hix.hixnet2.cyphers.Generator
import ug.hix.hixnet2.viewmodel.MainActivityViewModel


class MainActivity : AppCompatActivity(), CoroutineScope by MainScope() {
    private val SPLASH_TIMEOUT = 4000
    private lateinit var manager : WifiP2pManager
    private lateinit var channel : WifiP2pManager.Channel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN)
        setContentView(R.layout.activity_main)

        val viewModel : MainActivityViewModel by viewModels()
        viewModel.finished.observe(this, Observer {
            if(it) {
                val intent = Intent(this,HomeActivity::class.java)
                startActivity(intent)
                finish()
            }
        })
        viewModel.config(this.applicationContext)

//        Handler().postDelayed({
//            val intent = Intent(this,HomeActivity::class.java)
//            startActivity(intent)
//            finish()
//        }, SPLASH_TIMEOUT.toLong())

    }

}

