package com.noob_gallon.blepractice

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
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
        @SuppressLint("MissingPermission")
        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            super.onBatchScanResults(results)

            results?.let {
                Log.d(TAG, "check_batch")

                // results is not null
                for (result in it) {
                    if(!devicesArr.contains(result.device)) devicesArr.add(result.device)
                }
            }
        }

        // Callback when a BLE advertisement has been found.

        // int: Determines how this callback was triggered.
        // Could be one of ScanSettings.CALLBACK_TYPE_ALL_MATCHES (1),
        // ScanSettings#CALLBACK_TYPE_FIRST_MATCH (2) or ScanSettings#CALLBACK_TYPE_MATCH_LOST (4)

        // 	ScanResult: A Bluetooth LE scan result.
        @SuppressLint("NotifyDataSetChanged", "MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result) // 나중에는 Callback에 따른 when statement 작성 필요?
            result?.let {
                Log.d(TAG, "check")

                // result is not null
                // scan하면, result에 device가 담김?
                // result 안에 device라는 이름으로 element들이 담김.

                Log.d(TAG, "onScanResult: ${result}")
                if(!devicesArr.contains(it.device)) devicesArr.add(it.device)


                // List의 크기와 Item이 둘 다 변경되는 경우에 사용된다.
                // 사용하기 가장 간편한 Updating 방법.
                // 그러나, performance 측면에서 부족하므로, 가능하다면 상황에 맞춰 update 해주는 것이 좋다.
                recyclerViewAdapter.notifyDataSetChanged()
            }
        }
    }

    @SuppressLint("MissingPermission") //
    private fun scanDevice(state:Boolean) = if(state){
        Handler(Looper.getMainLooper()).postDelayed({
            scanning = false
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(mLeScanCallback)
        }, SCAN_PERIOD)
        scanning = true
        devicesArr.clear()
        bluetoothAdapter?.bluetoothLeScanner?.startScan(mLeScanCallback)
    }else{
        scanning = false
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(mLeScanCallback)
    }

    private var bleGatt : BluetoothGatt? = null // BluetoothGatt 변수.
    private var mContext:Context? = null // Toast 알림을 위한 Context 전달.

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

        mContext = this // mContext는 Toast 알림을 띄우기 위한 Context 전달 변수. onCreate에 this를 하면, Activity가 전달된다?
        recyclerViewAdapter.mListener = object : RecyclerViewAdapter.OnItemClickListener {
        // recyclerViewAdapter는 Class RecyclerViewAdapter의 instance...

            // 특정 button이 Click된다면, 그 button의 정보를 통해
            // target device와 connect하는 코드를 실행한다.
            override fun onClick(view: View, position: Int) {
                scanDevice(false)
                val device = devicesArr[position] // device.get(position)
                bleGatt = DeviceControlActivity(mContext, bleGatt).connectGatt(device)
                // bleGatt는 callback, device는 연결할 device.
                // 아직 bleGatt를 사용하는 코드는 존재하지 않음.

            }
        }


        // 그리고, recyclerView에 접근할 수 있는 변수를 만들고,
        // apply를 이용해 위에서 선언한 값들을 설정해준다.
        // 추가적인 참조 없이 값을 넣을 수 있게 되므로,
        // recyclerView.layoutManager = viewManager
        // recyclerView.adapter = recyclerViewAdapter와 같다.
        /* val recyclerView = */ binding.recyclerView.apply {
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
            scanDevice(true) // scanDevice 함수에 true를 전달, 항상 실행?, 바꿈.
        }

    }

//    @SuppressLint("MissingPermission")
//    fun write() {
//        val cmdCharacteristic = BluetoothUtils.findCommandCharacteristic(bleGatt!!)
//        // disconnect if the characteristic is not found
//
//        val cmdBytes = ByteArray(2)
//        cmdBytes[0] = 1
//        cmdBytes[1] = 2
//        val success:Boolean = bleGatt?.writeCharacteristic(cmdCharacteristic)
//
//        if (!success) {
//            Log.e(TAG, "Failed to write command")
//        }
//    }

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

    // OnItemClickListenr를 implement 받는 변수 mListener
    // onClick이 정의된 instance가 들어가있음.
    var mListener : OnItemClickListener? = null

    // interface를 class와 class간의 통신에 사용...
    // fun onClick은 abstract로 만들어 implement 받는 class에서
    // 정의될 수 있도록 한다.
    interface OnItemClickListener{
        fun onClick(view: View, position: Int)
    }

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
        // item이 재활용될 때 사용된다?
        // 데이터가 스크롤 되어서 맨 위에 있던 ViewHolder 객체가 맨 아래로 이동한다면, 그 레이아웃은 재활용하되,
        // 데이터는 새롭게 바인딩되는 것이다.

        // 이 때, 새롭게 보여질 데이터의 인덱스는 position이라는 이름으로 사용 가능하다.
        // 즉, 아래에서 새롭게 올라오는 데이터가 리스트의 20번째 데이터라면 position은 20이 들어오게 된다.

        holder.bind(myDataset[position]) // ViewHolder와 RecylcerView를 bind해준다.

        // mListener가 null이 아닌 경우에 실행된다.
        // mListener는 onCreate 시에 바로 값이 들어가므로,
        // Bind되기 전부터 반드시 null이 아니다.

        // Bind할 때 setOnClickListener를 지정해두어야
        // event 설정 가능?

        // 새롭게 갱신하는 데이터(화면에 보이게 될 것이므로,)에 listener를 달아서
        // event로 동작하게 한다.
        // 즉, onBindViewHolder를 거친 data라는 것은,
        // 현재 화면에 보이게 될 data라는 뜻이므로, onClickListener가 동작해야 한다.
        if (mListener != null) {
            holder.itemView.setOnClickListener{v ->
                mListener?.onClick(v, position)}
        }
    }

    override fun getItemCount() = myDataset.size
}

