package ug.hix.hixnet2.fragments

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.google.zxing.integration.android.IntentIntegrator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.runBlocking
import ug.hix.hixnet2.CaptureActivity
import ug.hix.hixnet2.HomeActivity
import ug.hix.hixnet2.R
import ug.hix.hixnet2.adapter.DeviceFragAdapter
import ug.hix.hixnet2.database.DeviceNode
import ug.hix.hixnet2.database.WifiConfig
import ug.hix.hixnet2.repository.Repository
import ug.hix.hixnet2.util.AddConfigs
import ug.hix.hixnet2.util.OnSwipeTouchListener
import ug.hix.hixnet2.viewmodel.DeviceViewModel
import java.lang.Thread.sleep


class DeviceFragment (val mContext: Context): Fragment(), CoroutineScope by MainScope() {

    lateinit var deviceViewModel: DeviceViewModel
    val repo = Repository(mContext)
    val addConfig = AddConfigs(mContext,repo)
    val TAG = javaClass.simpleName
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_device, container, false)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val devicesRecycleView : RecyclerView = view.findViewById(R.id.devicesRecycleView)
        val device_details : ImageView = view.findViewById(R.id.device_details)
        val devicesSideBar : FrameLayout = view.findViewById(R.id.devicesSideBar)
        val device_scan : ImageView = view.findViewById(R.id.device_scan)
        val device_search : SearchView = view.findViewById(R.id.device_search)

        devicesRecycleView.setOnTouchListener(object : OnSwipeTouchListener(activity){
            override fun onSwipeLeft() {
                super.onSwipeLeft()
                devicesSideBar.visibility = View.VISIBLE
            }

            override fun onSwipeRight() {
                super.onSwipeRight()
                devicesSideBar.visibility = View.GONE
            }
        })

        val deviceAdapter = DeviceFragAdapter(mContext)
        devicesRecycleView.apply{
            layoutManager = LinearLayoutManager(activity)
            adapter = deviceAdapter
        }
        deviceViewModel = ViewModelProvider(this).get(DeviceViewModel::class.java)
        deviceViewModel.getActiveDeviceLiveData(mContext).observe(viewLifecycleOwner,
            Observer<List<DeviceNode>> {
                deviceAdapter.submitList(it)
            })
        device_details.setOnClickListener {
            val connInfo = runBlocking { repo.getMyWifiConfig() }
            val json = Gson().toJson(connInfo, WifiConfig::class.java)
            Log.d(TAG,json)
            val bitmap = generateQRCode(json)
            val image = ImageView(mContext)
            image.setImageBitmap(bitmap)
            val dialog = Dialog(mContext)
            dialog.setContentView(image)
            dialog.setTitle("Scan QrCode")
            dialog.show()
            Handler(Looper.getMainLooper()).postDelayed({
                dialog.dismiss()
            },5000L)
        }
        device_scan.setOnClickListener {
            scanQRCode()
        }

        device_search.setOnQueryTextListener( object: SearchView.OnQueryTextListener{
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                deviceAdapter.filter.filter(newText)
                return false
            }
        })
    }
    private fun generateQRCode(text: String): Bitmap {
        val width = 500
        val height = 500
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val codeWriter = MultiFormatWriter()
        try {
            val bitMatrix = codeWriter.encode(text, BarcodeFormat.QR_CODE, width, height)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
        } catch (e: WriterException) {
            Log.d(TAG, "generateQRCode: ${e.message}")
        }
        return bitmap
    }
    private fun scanQRCode(){
        val integrator = IntentIntegrator.forSupportFragment(this).apply {
            captureActivity = CaptureActivity::class.java
            setOrientationLocked(false)
            setDesiredBarcodeFormats(IntentIntegrator.ALL_CODE_TYPES)
            setPrompt("Scanning Code")
        }
        integrator.initiateScan()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null) {
            if (result.contents == null) {
                Toast.makeText(this.activity, "Cancelled", Toast.LENGTH_LONG).show()
            }else {
                val json = result.contents
                try{
                    val connObj = Gson().fromJson(json, WifiConfig::class.java)
                    addConfig.insertScanConfig(connObj)

                    Log.d(TAG,"QRScan successful")
                }catch (e : Throwable){
                    Log.e(TAG,"Failed to Scan Code",e)
                    Toast.makeText(this.activity, "Unknown QRCODE", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    companion object {
        @JvmStatic fun newInstance(mContext: Context) = DeviceFragment(mContext)
    }
}