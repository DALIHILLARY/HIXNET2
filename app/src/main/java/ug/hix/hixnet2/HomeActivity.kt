package ug.hix.hixnet2

import android.content.Context
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
import ug.hix.hixnet2.fragments.CloudFragment
import ug.hix.hixnet2.fragments.DeviceFragment
import ug.hix.hixnet2.fragments.FileFragment
import ug.hix.hixnet2.viewmodel.HomeViewModel

class HomeActivity : AppCompatActivity(), CoroutineScope by MainScope(){
    private val TAG = javaClass.simpleName
    private var fabLastClick = 0L
    private lateinit var viewModel: HomeViewModel

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

            viewModel = ViewModelProvider(this).get(HomeViewModel::class.java)

            //Check for permissions
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
                if(!hasPermission(this, *PERMISSIONS)){
                    ActivityCompat.requestPermissions(this,PERMISSIONS,PERMISSION_ALL)
                }
            }
            val filesFragment = FileFragment.newFileInstance(this)
            val cloudFragment = CloudFragment.newInstance(this)
            val deviceFragment = DeviceFragment.newInstance(this)
            if(!viewModel.fragmentSet){
                loadFragment(filesFragment) //load filesFragment as initial display
                viewModel.fragmentSet = true
            }
            if(MeshDaemon.isServiceRunning){
                gray_out_home.visibility = GONE
                fabStart?.setImageResource(R.drawable.ic_stop)

            }else{
                gray_out_home.visibility = VISIBLE
                fabStart?.setImageResource(R.drawable.ic_start)
            }
            fabStart?.setOnClickListener {
                if(System.currentTimeMillis() - fabLastClick >= 2000L){
                    fabLastClick = System.currentTimeMillis()
                    if(viewModel.isMyServiceRunning(this,MeshDaemon::class.java)){
                        MeshDaemon.stopService(this)
                        fabStart?.setImageResource(R.drawable.ic_start)
                        gray_out_home.visibility = VISIBLE

                    }else{
                       MeshDaemon.startService(this)
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
                    R.id.menuDevices -> {
                        loadFragment(deviceFragment)
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
