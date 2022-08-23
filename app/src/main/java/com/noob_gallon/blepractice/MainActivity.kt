package com.noob_gallon.blepractice

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.noob_gallon.blepractice.databinding.ActivityMainBinding
import com.noob_gallon.blepractice.databinding.RecyclerviewItemBinding

class MainActivity : AppCompatActivity() {

    private val TAG:String = "jdk"
    private val REQUEST_ENABLE_BT=1
    private val REQUEST_ALL_PERMISSION = 2

    private var bluetoothAdapter: BluetoothAdapter? = null
    private lateinit var binding: ActivityMainBinding

    // PERMISSIONS 배열은 Bluetotth Scan 기능을 사용하기 위해 필요한 ACCESS_FINE_LOCATION 이라는
    // 위치 접근 Permission을 저장하고 있다. 버전에 따라 다른데, API 28 이하는 ACCESS_COARSE_LOCATION
    // 권한을 선언할 수 있으므로, 나중에 추가할 때는 SDK 버전을 먼저 기록해놓고, 이에 따라 권한 허용을 다르게
    // 설정하면 될 듯 하다.

    // 또 설정해야되는 권한이 있다면, 나중에 arrayOf로 더 추가해주어도 될듯.
    private val PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    private var scanning: Boolean = false // scan 중인지 나타내는 state 변수
    private var devicesArr = ArrayList<BluetoothDevice>() // scan한 Device를 담는 배열
    private val SCAN_PERIOD = 3000L // scan을 실행하는 시간?

    // RecyclerView 설정을 위한 변수
    private lateinit var viewManager: RecyclerView.LayoutManager
    private lateinit var recyclerViewAdapter : RecyclerViewAdapter

    // scan에 실패하였을 때 실행되는 OnScanFailed()와 Batch Scan Result가 전달될 때 콜백하는 onBatchScanResults(),
    // BLE advertisement가 발견되었을 때 실행되는 onScanResult()를 override 한다.
    private val mLeScanCallback:ScanCallback = // 롤리팝(API 21)부터 사용 가능.
    object : ScanCallback() {
        // Callback when scan could not be started.
        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.d(TAG, "BLE Scan Failed : " + errorCode)
        }

        // Callback when batch results are delivered.
        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            super.onBatchScanResults(results)

            results?.let {
                // results is not null
                for (result in it) {
                    if(!devicesArr.contains(result.device) /* && result.device.name!=null */) devicesArr.add(result.device)
                }
            }
        }

        // Callback when a BLE advertisement has been found.

        // int: Determines how this callback was triggered.
        // Could be one of ScanSettings.CALLBACK_TYPE_ALL_MATCHES (1),
        // ScanSettings#CALLBACK_TYPE_FIRST_MATCH (2) or ScanSettings#CALLBACK_TYPE_MATCH_LOST (4)

        // 	ScanResult: A Bluetooth LE scan result.
        @SuppressLint("NotifyDataSetChanged")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result) // 나중에는 Callback에 따른 when statement 작성 필요?
            result?.let {
                // result is not null
                // scan하면, result에 device가 담김?
                // result 안에 device라는 이름으로 element들이 담김.

                Log.d(TAG, "onScanResult: ${result}")
                if(!devicesArr.contains(it.device) /* && it.device.name!=null */ ) devicesArr.add(it.device)

                // List의 크기와 Item이 둘 다 변경되는 경우에 사용된다.
                // 사용하기 가장 간편한 Updating 방법.
                // 그러나, performance 측면에서 부족하므로, 가능하다면 상황에 맞춰 update 해주는 것이 좋다.
                recyclerViewAdapter.notifyDataSetChanged()
            }
        }
    }

    // 구문 분석 필요.
    // Q. 블루투스 스캔은 어떤 기준으로 이루어져야 하는가?
    // 스캔은 사용자의 디바이스 배터리 소모량을 증가시킨다.
    // 계속해서 탐색하되, 디바이스를 찾게되면 멈춘다?
    // 흠... 어떻게 해야되지?

    @SuppressLint("MissingPermission") //
    private fun scanDevice() {
        // Handler => deprecated.
        // Handler는 일반적으로 UI 갱신을 위해 사용된다.
        // Handler() -> Handler(Looper.getMainLooper())

        // bluetoothAdapter?.bluetoothLeScanner?. => BLE scanner를 동작한다.

        // BluetoothAdapter#getBluetoothLeScanner()를 사용하여 BluetoothLeScanner의 instance를 얻을 수 있음.
        // ScanFilter를 이용하여 특정 BLE Device만 scan할 수 있음.

        // startScan 함수에서 전달되는 값이 항상 true이므로, else는 실행되지 않는다.
        // 그리고, 이 함수는 1초간 Device를 탐색하고 꺼진다.

        Handler(Looper.getMainLooper()).postDelayed({ // 1초 후에 실행, SCAN_PERIOD = 1초.
            scanning = false

            // 이와 같은 형태의 scan은
            // startScan(ScanCallback callback)
            // Start Bluetooth LE scan with default parameters and no filters.
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(mLeScanCallback)
            Log.d(TAG, "scanDevice: scan stopped.")
        }, SCAN_PERIOD)

        scanning = true // 현재 BLE Scan 중인지를 나타내는 전역 변수
        devicesArr.clear() // Devices name을 담는 Array Clear, 시작 전에 Clear를 진행.
        bluetoothAdapter?.bluetoothLeScanner?.startScan(mLeScanCallback)

        // Device 자체에서 신호를 내보내는 간격이 있기 때문에, Scan 간격을 늘려야 감지하기 쉽다.
        Log.d(TAG, "scanDevice: scan for 3 second.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // viewBinding을 이용해 activity_main.xml의 view id를 받아온다.
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root

        setContentView(view)

        // val bleOnOffBtn:ToggleButton = findViewById(R.id.ble_on_off_btn)
        // scanBtn에 접근할 수 있는 변수 생성
        val scanBtn: Button = binding.scanBtn
        val onOffBtn: ToggleButton = binding.bleOnOffBtn

        // 전역변수로 생성한 viewManager, recyclerViewAdapter에 값을 할당한다.
        // viewManager는 LinearLayoutManager
        // recyclerViewAdapter에는 검색한 기기에 대한 정보를 담은 배열을 넘겨준다.(뷰홀더?)

        // scanDevice()를 통해서 Callback과 result가 전달되고,
        // result에 있는 값을 devicesArr에 추가한다.
        // 그 뒤, notifyDataSetChanged를 이용, recyclerView를 다시 그리라고 명령한다.
        // 그 과정이 이루어질 수 있는 이유는, Adapter에 devicesArr를 넣어놨기 때문인가?
        // 아하, 여기서 이미 class의 instance를 생성해놓은 것이었다.
        // devicesArr를 argument로 전달해 준 instance가 recyclerViewAdapter이다.
        // 이미 만들어진 instance에 data를 새롭게 넣어주고, notifyDataSetChanged를 이용,
        // recyclerView를 새롭게 그리게 만들면 갱신 끝.

        viewManager = LinearLayoutManager(this)
        recyclerViewAdapter = RecyclerViewAdapter(devicesArr)

        // 그리고, recyclerView에 접근할 수 있는 변수를 만들고,
        // apply를 이용해 위에서 선언한 값들을 설정해준다.
        // 추가적인 참조 없이 값을 넣을 수 있게 되므로,
        // recyclerView.layoutManager = viewManager
        // recyclerView.adapter = recyclerViewAdapter와 같다.
        val recyclerView = binding.recyclerView.apply {
            layoutManager = viewManager
            adapter = recyclerViewAdapter
        }

        // 블루투스와 관련된 모든 작업은 해당 구문으로 시작한다.
        // BluetoothAdapter.getDefaultAdapter() 함수를 이용하여
        // BluetoothAdapter 클래스를 얻는 작업부터 시작한다.
        // 만약 null을 반환하면, 스마트폰에서 블루투스를 지원하지 않는 것이다.
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if(bluetoothAdapter != null) {

            // bluetoothAdapter != null, 즉, 기기가 bluetoothAdapter를 지원할 경우.

            if(bluetoothAdapter?.isEnabled==false){
                // bluetooth가 꺼져있는 상황인 경우

                Log.d(TAG, "Bluetooth is not enabled")
                onOffBtn.isChecked = true
                scanBtn.isVisible = false
            } else{
                // bluetooth가 켜져있는 상황인 경우

                Log.d(TAG, "Bluetooth is enabled already")
                onOffBtn.isChecked = false
                scanBtn.isVisible = true
            }
        }

        // bluetooth_On/Off Button이 눌릴 경우에 대한 리스너.
        // bluetoothOnOff() 함수를 통해 현재 상태에 맞게 동작을 실시한다.
        binding.bleOnOffBtn.setOnCheckedChangeListener { _, isChecked ->
            bluetoothOnOff()

            // bluetoothOnOff()에서 버튼이 눌렸을 때, On 상태라면 scanBtn의 Visiblity를 Invisible로 설정.
            // Off 상태라면, scanBtn의 Visibility를 Visible로 설정.
            scanBtn.visibility = if(scanBtn.visibility == View.VISIBLE) {
                View.INVISIBLE
            } else {
                View.VISIBLE
            }
        }

        // 만약 scanBtn이 눌릴 경우. 위에서 binding으로 설정되어 있음.
        scanBtn.setOnClickListener {
                _:View? ->// Scan Button Onclick
            if(!hasPermissions(this, PERMISSIONS)) {

                // hasPermissions는 Permission을 만족하는지에 대한 Boolean을 반환한다.
                // 즉, 어플리케이션이 Permission을 만족하지 못하면, Permission을 요청하는 함수를 실행한다.
                // REQUEST_ALL_PERMISSION이 왜 2인가?
                requestPermissions(PERMISSIONS, REQUEST_ALL_PERMISSION)

            }

            // Permission이 만족되었다면, Device를 scan한다.
            scanDevice() // scanDevice 함수에 true를 전달, 항상 실행?, 바꿈.
        }

    }

    fun bluetoothOnOff(){
        if (bluetoothAdapter == null) {
            // Device doesn't support Bluetooth
            Log.d(TAG,"Device doesn't support Bluetooth")
        }else{
            Log.d(TAG, "bluetoothOnOff: check")
            if (bluetoothAdapter?.isEnabled == false) { // 현재 Bluetooth가 꺼져있는 상태

                Log.d(TAG, "bluetooth is off now")
                // permission Check, 권한이 허용되지 않은 상태에서만 실행.
                // ActivityCompat vs ContextCompat
                // There's no trade-off really.
                // Not sure why they wrote so - checkSelfPermission() is a method of ContextCompat
                // and ActivityCompat is subclass (child) of ContextCompat => ActivityCompat은 ContextCompat의 subclass이다.
                // so you can pass either one whenever object of ContextCompat class is required.
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                {
                    Log.d(TAG, "permission is not granted")
                    // 특정 버젼 이상에서는 권한 설정이 필요하므로, 이를 물어본다.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    {
                        Log.d(TAG, "version >= S")
                        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 2);
                        return;

                        // requestCode의 의미에 대해서 조사 필요.
                    }
                    Log.d(TAG, "API is not over S, no need to get permission")
                }

                Log.d(TAG, "bluetooth enabled")
                // Bluetooth On
                val enableBtIntent = Intent(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            } else { // 블루투스가 켜져 있으면 블루투스 비활성화

                Log.d(TAG, "bluetooth disabled")
                bluetoothAdapter?.disable()
            }
        }
    }

    // 요청할 Permission을 PERMISSIONS라는 이름의 배열로 저장 후, 해당 배열에 저장된 Permission을 모두 요청한다.
    // Bluetooth Scan 기능을 사용하려면, ACCESS_FINE_LOCATION이라는 위치 접근 Permission을 허용해줘야한다.
    private fun hasPermissions(context: Context?, permissions: Array<String>): Boolean {

        // 버전이 M보다 높고, 전달된 context가 null이 아니고, 전달된 permissions가 null이 아닐 경우.
        // 그런데, 해당 프로젝트에서는 N(M보다 한 개 높은 API)부터 지원하므로, 이 조건은 무시한다.
        if (context != null /* &&permissions != null */) { // 해당 코드에서는 permissions가 항상 null이 아니므로 필요없지만,
                                                           // 추후에 재사용시 null checking이 필요할 수도 있음.
            for (permission in permissions) {
                // 권한이 허용되어 있지 않다면, 내부적으로 false를 return
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false
                }
            }
        }
        // 권한이 허용되어 있다면 true를 return
        return true

        // 즉, 하나라도 권한 허용이 되어있지 않다면 false를 return한다.
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
    }

    // requestPermission()을 한 뒤, 반환값이 전달되는 부분.
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) { // requestCode == REQUEST_ALL_PERMISSION인 경우
            REQUEST_ALL_PERMISSION -> {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Permissions granted!", Toast.LENGTH_SHORT).show()
                } else {
                    requestPermissions(permissions, REQUEST_ALL_PERMISSION)
                    Toast.makeText(this, "Permissions must be granted", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
class RecyclerViewAdapter(private val myDataset: ArrayList<BluetoothDevice>): RecyclerView.Adapter<RecyclerViewAdapter.MyViewHolder > () {
    class MyViewHolder(private val binding: RecyclerviewItemBinding):RecyclerView.ViewHolder(binding.root) {

        fun bind(bluetoothDevice: BluetoothDevice) {
            binding.itemName.text = bluetoothDevice.name
            binding.itemAddress.text = bluetoothDevice.address
        }

    }

    override fun onCreateViewHolder(parent: ViewGroup,
                                    viewType: Int):MyViewHolder {
        // create a new view
//        val linearView = LayoutInflater.from(parent.context)
//            .inflate(R.layout.recyclerview_item, parent, false) as LinearLayout

        val binding = RecyclerviewItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MyViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        holder.bind(myDataset[position])
    }
    override fun getItemCount() = myDataset.size
}

