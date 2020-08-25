package com.example.flutter_bt

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat.startActivity
import androidx.core.app.ActivityCompat.startActivityForResult
import androidx.core.content.ContextCompat

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.Registrar

import kotlin.collections.ArrayList
import kotlin.collections.HashMap

/** FlutterBtPlugin */
public class FlutterBtPlugin: FlutterPlugin, MethodCallHandler {
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private val TAG: String = "FlutterBtPlugin"
  private lateinit var channel : MethodChannel
  private lateinit var registrar: Registrar

  private var bluetoothAdapter: BluetoothAdapter
  private lateinit var bondStateBroadcastReceiver: BroadcastReceiver

  private val REQUEST_ENABLE_BT: Int =2137

  init { // 构造器
      bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
  }

  override fun onAttachedToEngine(
          @NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    channel = MethodChannel(
            flutterPluginBinding.getFlutterEngine().getDartExecutor(), "flutter_bt")
    channel.setMethodCallHandler(this);
  }

  // This static function is optional and equivalent to onAttachedToEngine. It supports the old
  // pre-Flutter-1.12 Android projects. You are encouraged to continue supporting
  // plugin registration via this function while apps migrate to use the new Android APIs
  // post-flutter-1.12 via https://flutter.dev/go/android-project-migration.
  //
  // It is encouraged to share logic between onAttachedToEngine and registerWith to keep
  // them functionally equivalent. Only one of onAttachedToEngine or registerWith will be called
  // depending on the user's project. onAttachedToEngine or registerWith must both be defined
  // in the same class.
  companion object {
    @JvmStatic
    fun registerWith(registrar: Registrar) {
      val channel = MethodChannel(registrar.messenger(), "flutter_bt")
      channel.setMethodCallHandler(FlutterBtPlugin())
    }
  }

  @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
    when (call.method){
      // bluetooth basic operation
      "ifSupport" -> result.success(bluetoothAdapter != null)
      "openSettins" -> {
        ContextCompat.startActivity(
                registrar.activity(), Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS),
                null)
        result.success(null)
      }
      "btState" -> result.success(bluetoothAdapter?.state)
      "isEnable" -> result.success(
              if (bluetoothAdapter?.isEnabled == true) "enable" else "disable")
      "enableBt" -> result.success(enableBt())
      "disableBt" -> result.success(disableBt())
      "getName" -> result.success(bluetoothAdapter?.name)
      // discovery, bond and connect bluetooth device
      "getBondedDevices" -> result.success(getBondedDevices())
      "discovery" -> {

      }
      "isDiscovering" -> result.success(bluetoothAdapter?.isDiscovering)
      "getDeviceBondState" -> {
        if (!call.hasArgument("address")){
          result.error(
                  "invalid_argument", "argument 'address' not found", null)
        } else {
          val address: String? = call.argument("address")
          if (!BluetoothAdapter.checkBluetoothAddress(address)){
            result.error("invalid_argument",
                    "'address' argument is required to be string containing remote MAC adress",
                    null)
          } else {
            val device: BluetoothDevice? = bluetoothAdapter?.getRemoteDevice(address)
            result.success(device?.bondState)
          }
        }
      }
      "bondDevice" -> {
        if (!call.hasArgument("address")){
          result.error(
                  "invalid_argument", "argument 'address' not found", null)
        } else {
          val address: String? = call.argument("address")
          if (!BluetoothAdapter.checkBluetoothAddress(address)){
            result.error("invalid_argument",
                    "'address' argument is required to be string containing remote MAC adress",
                    null)
          } else if (bondStateBroadcastReceiver != null) {
            result.error("bond_error", "another bonding process is ongoing from local device", null)
          } else {
            val device: BluetoothDevice? = bluetoothAdapter?.getRemoteDevice(address)
            when (device?.bondState) {
                BluetoothDevice.BOND_BONDING -> result.error("bond_error", "device already bonding", null)
                BluetoothDevice.BOND_BONDED -> result.error("bond_error", "device already bonded", null)
                else -> {
                }
            }
          }
        }
      }
      "unbondDevice" -> {}
      "deviceConnectState" -> {}
      "connectDevice" -> {}
      "disconnectDevice" -> {}
      // send and receive data

      //
      else -> result.notImplemented()
    }

    if (call.method == "getPlatformVersion") {
      result.success("Android ${android.os.Build.VERSION.RELEASE}")
    } else {
      result.notImplemented()
    }
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
  }

  fun enableBt(): Boolean{
    if (bluetoothAdapter?.isEnabled == false) {
      val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
      startActivityForResult(registrar.activity() ,enableBtIntent, REQUEST_ENABLE_BT, null)
      return true
    } else {
      return false
    }
  }

  fun disableBt(): Boolean{
    if (bluetoothAdapter?.isEnabled == true){
      bluetoothAdapter!!.disable()
      return true
    } else {
      return false
    }
  }

  fun bondDevice() {
//    bondStateBroadcastReceiver = BroadcastReceiver()
  }

  fun discovery(timeout: Int): ArrayList<HashMap<String, Any>>{
    val list: ArrayList<HashMap<String, Any>> = ArrayList()
    val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
//    registerReceiver()
    val discoverableIntent: Intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
      putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, timeout)
    }
    ContextCompat.startActivity(registrar.activity(), discoverableIntent, null)

    bluetoothAdapter?.startDiscovery()

    return list
  }

  @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
  fun getBondedDevices(): ArrayList<HashMap<String, Any>> {
    val list: ArrayList<HashMap<String, Any>> = ArrayList()
    val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices

    pairedDevices?.forEach{device ->
      val entry: HashMap<String, Any> = HashMap()
      entry.put("name", device.name)
      entry.put("address", device.address)
      entry.put("type", device.type)
      entry.put("bondState", device.bondState)
      entry.put("isConnected", "")
      list.add(entry)
    }

    return list
  }

}
