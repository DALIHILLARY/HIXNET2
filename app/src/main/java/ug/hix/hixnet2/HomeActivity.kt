package ug.hix.hixnet2

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View.GONE
import android.view.View.VISIBLE

import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider

import ug.hix.hixnet2.models.DeviceNode
import ug.hix.hixnet2.services.MeshDaemon

import kotlinx.android.synthetic.main.activity_home.*
import kotlinx.coroutines.*
import okio.ByteString.Companion.toByteString
import ug.hix.hixnet2.fragments.CloudFragment
import ug.hix.hixnet2.fragments.FileFragment
import ug.hix.hixnet2.licklider.Licklider
import ug.hix.hixnet2.models.TransFile
import ug.hix.hixnet2.viewmodel.HomeViewModel

class HomeActivity : AppCompatActivity(), CoroutineScope by MainScope(){
    private val deviceInstance = DeviceNode()
    private val TAG = javaClass.simpleName
    private var fabLastClick = 0L

    private var PERMISSION_ALL = 1
    private val PERMISSIONS = arrayOf(
        android.Manifest.permission.READ_EXTERNAL_STORAGE,
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.ACCESS_FINE_LOCATION
    )

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_home)

            val viewModel = ViewModelProvider(this).get(HomeViewModel::class.java)

            //Check for permissions
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
                if(!hasPermission(this, *PERMISSIONS)){
                    ActivityCompat.requestPermissions(this,PERMISSIONS,PERMISSION_ALL)
                }
            }

            val filesFragment = FileFragment.newFileInstance(this)
            val cloudFragment = CloudFragment.newInstance(this)
            if(MeshDaemon.isServiceRunning){
                gray_out_home.visibility = GONE
                fabStart?.setImageResource(R.drawable.ic_stop)

            }else{
                gray_out_home.visibility = VISIBLE
                fabStart?.setImageResource(R.drawable.ic_start)

            }
            fabStart?.setOnClickListener {
                if(System.currentTimeMillis() - fabLastClick >= 5000L){
                    fabLastClick = System.currentTimeMillis()
                    val intent = Intent(this,MeshDaemon::class.java)
                    if(viewModel.isMyServiceRunning(this,MeshDaemon::class.java)){

                        stopService(intent)
                        fabStart?.setImageResource(R.drawable.ic_start)
                        gray_out_home.visibility = VISIBLE

                    }else{
                        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
                            startForegroundService(intent)
                        }else{
                            startService(intent)

                        }
                        fabStart?.setImageResource(R.drawable.ic_stop)
                        gray_out_home.visibility = GONE
                    }


                }

            }

            bottomAppBar?.setOnMenuItemClickListener {
                when(it.itemId){
                    R.id.menuFiles -> {
                        loadFragment(filesFragment)
                    }
                    R.id.menuCloud -> {
                        loadFragment(cloudFragment)
                    }
                }
                true
            }


        }


    private fun loadFragment(fragment: Fragment){
        supportFragmentManager.beginTransaction().apply {
            replace(R.id.fl_wrapper,fragment)
            commit()
        }
    }

    private fun hasPermission(context: Context, vararg permissions : String): Boolean = permissions.all{
        ActivityCompat.checkSelfPermission(context,it) == PackageManager.PERMISSION_GRANTED
    }


}
