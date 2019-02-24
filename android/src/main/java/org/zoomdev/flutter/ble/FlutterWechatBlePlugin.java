package org.zoomdev.flutter.ble;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.os.Build;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;

/** FlutterWechatBlePlugin */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class FlutterWechatBlePlugin implements MethodCallHandler, BleListener {
  /** Plugin registration. */
  public static void registerWith(Registrar registrar) {
    final MethodChannel channel = new MethodChannel(registrar.messenger(), "flutter_wechat_ble");
    channel.setMethodCallHandler(new FlutterWechatBlePlugin(registrar.context().getApplicationContext(),channel));
  }


  private MethodChannel channel;

  @Override
  public void onMethodCall(MethodCall call, Result result) {
    String method = call.method;
    if("openBluetoothAdapter".equals(method)){
      openBluetoothAdapter(result);
    }else if("closeBluetoothAdapter".equals(method)){
      closeBluetoothAdapter(result);
    }else if("startBluetoothDevicesDiscovery".equals(method)){
      startBluetoothDevicesDiscovery(result);
    }else if("stopBluetoothDevicesDiscovery".equals(method)){
      stopBluetoothDevicesDiscovery(result);
    }else if("getBLEDeviceServices".equals(method)){
      getBLEDeviceServices((Map) call.arguments,result);
    }else if("getBLEDeviceCharacteristics".equals(method)){
      getBLEDeviceCharacteristics((Map) call.arguments,result);
    }else if("createBLEConnection".equals(method)){
      createBLEConnection((Map) call.arguments,result);
    }else if("closeBLEConnection".equals(method)){
      closeBLEConnection((Map) call.arguments,result);
    }else if("notifyBLECharacteristicValueChange".equals(method)){
      notifyBLECharacteristicValueChange((Map) call.arguments,result);
    }else if("writeBLECharacteristicValue".equals(method)){
      writeBLECharacteristicValue((Map) call.arguments,result);
    }else if("readBLECharacteristicValue".equals(method)){
      readBLECharacteristicValue((Map) call.arguments,result);
    }else {
      result.notImplemented();
    }
  }

  public FlutterWechatBlePlugin(Context context,MethodChannel channel) {
    super();
    adapter = new BleAdapter(context);
    adapter.setListener(this);
    this.channel = channel;
  }

  public static final String NOT_INIT = "10000";
  public static final String NOT_AVALIABLE = "10001";
  public static final String NO_DEVICE = "10002";
  public static final String CONNECTION_FAIL = "10003";
  public static final String NO_SERVICE = "10004";
  public static final String NO_CHARACTERISTIC = "10005";
  public static final String NO_CONNECTION = "10006";
  public static final String PROPERTY_NOT_SUPPOTT = "10007";
  public static final String SYSTEM_ERROR = "10008";
  public static final String SYSTEM_NOT_SUPPORT = "10009";

  private BleAdapter adapter;


  public synchronized void openBluetoothAdapter(Result promise) {
    if (adapter.open()) {
      promise.success(null);
    } else {
      promise.error(NOT_AVALIABLE, "Bluetooth is not avaliable",null);
    }
  }

  
  public synchronized void startBluetoothDevicesDiscovery( Result promise) {
    BluetoothAdapterResult ret = adapter.startScan();
    if (BluetoothAdapterResult.BluetoothAdapterResultOk == adapter.startScan()) {
      promise.success(null);
    } else {
      retToCallback(ret, promise);
    }
  }


  
  public synchronized void stopBluetoothDevicesDiscovery( Result promise) {
    adapter.stopScan();
    promise.success(null);
  }


  public synchronized void readBLECharacteristicValue(Map data, final Result promise){
    String deviceId = (String) data.get("deviceId");
    if (deviceId == null) {
      retToCallback(BluetoothAdapterResult.BluetoothAdapterResultNotInit, promise);
      return;
    }
    DeviceAdapter deviceAdapter = null;
    try {
      deviceAdapter = getDeviceAdapter(deviceId);
      String serviceId = (String) data.get("serviceId");
      String characteristicId = (String) data.get("characteristicId");
      readListener = promise;
      deviceAdapter.read( serviceId,characteristicId);
    }catch (BluetoothException e){
      retToCallback(e.ret, promise);
    }
  }
  
  public synchronized void writeBLECharacteristicValue(Map data, final Result promise){
    String deviceId = (String) data.get("deviceId");
    if (deviceId == null) {
      retToCallback(BluetoothAdapterResult.BluetoothAdapterResultNotInit, promise);
      return;
    }
    DeviceAdapter deviceAdapter = null;
    try {
      deviceAdapter = getDeviceAdapter(deviceId);
      String serviceId = (String) data.get("serviceId");
      String characteristicId = (String) data.get("characteristicId");
      String value = (String) data.get("value");
      byte[] bytes = HexUtil.decodeHex(value);
      writeListener = promise;
      deviceAdapter.write( serviceId,characteristicId,bytes );
    }catch (BluetoothException e){
      retToCallback(e.ret, promise);
    }


  }

  
  public synchronized void getBLEDeviceCharacteristics(Map data, final Result promise) {

    String deviceId = (String) data.get("deviceId");
    if (deviceId == null) {
      retToCallback(BluetoothAdapterResult.BluetoothAdapterResultNotInit, promise);
      return;
    }
    DeviceAdapter deviceAdapter = null;
    try {
      deviceAdapter = getDeviceAdapter(deviceId);
      if (deviceAdapter.getServices() == null) {
        promise.error(SYSTEM_ERROR, "Call getBLEDeviceServices first",null);
        return;
      }

      String serviceId = (String) data.get("serviceId");
      BluetoothGattService service = deviceAdapter.getService(serviceId);
      if (service == null) {
        promise.error(NO_SERVICE, "Cannot find service",null);
        return;
      }
      List<BluetoothGattCharacteristic> characteristics = deviceAdapter.getCharacteristics(service);

      List arr = new ArrayList();
      for (BluetoothGattCharacteristic characteristic : characteristics) {
        Map map = new HashMap();
        map.put("uuid", Utils.getUuidOfCharacteristic(characteristic));
        //  map.putBoolean("isPrimary",service.get);
        arr.add(map);
      }
      promise.success(arr);

    }catch (BluetoothException e){
      retToCallback(e.ret, promise);
    }
  }

  protected DeviceAdapter getDeviceAdapter(String deviceId) throws BluetoothException {
    BluetoothDevice device = adapter.getDevice(deviceId);
    if (device == null) {
      throw new BluetoothException(BluetoothAdapterResult.BluetoothAdapterResultDeviceNotFound);
    }
    DeviceAdapter deviceAdapter = adapter.getConnectedDevice(deviceId);
    if (deviceAdapter == null) {
      throw new BluetoothException(BluetoothAdapterResult.BluetoothAdapterResultDeviceNotConnected);
    }

    return deviceAdapter;

  }


  
  public synchronized void getBLEDeviceServices(Map data, final Result promise) {
    String deviceId = (String) data.get("deviceId");
    if (deviceId == null) {
      retToCallback(BluetoothAdapterResult.BluetoothAdapterResultNotInit, promise);
      return;
    }
    DeviceAdapter deviceAdapter = null;
    try {
      deviceAdapter = getDeviceAdapter(deviceId);
      deviceAdapter.getServices(new DeviceAdapter.GetServicesListener() {
        @Override
        public void onGetServices(List<BluetoothGattService> services, boolean success) {
          if (success) {
            List arr = new ArrayList();
            for (BluetoothGattService service : services) {
              Map map = new HashMap();
              map.put("uuid", Utils.getUuidOfService(service));
              //  map.putBoolean("isPrimary",service.get);
              arr.add(map);
            }
            promise.success(arr);
          } else {
            promise.error(SYSTEM_ERROR, "Cannot get services",null);
          }
        }
      });
    } catch (BluetoothException e) {
      retToCallback(e.ret, promise);
    }

  }

  
  public synchronized void notifyBLECharacteristicValueChange(Map data, final Result promise) {
    String deviceId = (String) data.get("deviceId");
    if (deviceId == null) {
      retToCallback(BluetoothAdapterResult.BluetoothAdapterResultNotInit, promise);
      return;
    }
    DeviceAdapter deviceAdapter = null;
    try {
      deviceAdapter = getDeviceAdapter(deviceId);
      String serviceId = (String) data.get("serviceId");
      String characteristicId = (String) data.get("characteristicId");
      boolean notify = (Boolean) data.get("state");
      deviceAdapter.setNotify(serviceId,characteristicId,notify);
      promise.success(null);
    } catch (BluetoothException e) {
      retToCallback(e.ret, promise);
    }
  }


  private void retToCallback(BluetoothAdapterResult ret, Result promise) {
    switch (ret) {
      case BluetoothAdapterResultNotInit:
        promise.error(NOT_INIT, "Not initialized",null);
        break;
      case BluetoothAdapterResultDeviceNotFound:
        promise.error(NO_DEVICE, "Cannot find the device",null);
        break;
      case BluetoothAdapterResultDeviceNotConnected:
        promise.error(NO_CONNECTION, "The device is not connected",null);
        break;
      case BluetoothAdapterResultServiceNotFound:
        promise.error(NO_SERVICE, "Cannot find the service",null);
        break;
      case BluetoothAdapterResultCharacteristicsNotFound:
        promise.error(NO_CHARACTERISTIC, "Cannot find the characteristic",null);
        break;
      case BluetoothAdapterResultCharacteristicsPropertyNotSupport:
        promise.error(PROPERTY_NOT_SUPPOTT, "Property is not supported",null);
        break;
    }

  }

  
  public synchronized void closeBluetoothAdapter( Result promise) {
    adapter.close();
    promise.success(null);
  }



  public synchronized void closeBLEConnection(Map data, Result promise){
    String deviceId = (String) data.get("deviceId");
    if (deviceId == null) {
      retToCallback(BluetoothAdapterResult.BluetoothAdapterResultNotInit, promise);
      return;
    }
    this.connectListener = promise;
    if (BluetoothAdapterResult.BluetoothAdapterResultOk != adapter.disconnectDevice(deviceId)) {
      retToCallback(BluetoothAdapterResult.BluetoothAdapterResultNotInit, promise);
      return;
    }
    //返回关闭
    promise.success(null);
  }

  
  public synchronized void createBLEConnection(Map data, Result promise) {
    String deviceId = (String) data.get("deviceId");
    if (deviceId == null) {
      retToCallback(BluetoothAdapterResult.BluetoothAdapterResultNotInit, promise);
      return;
    }
    this.connectListener = promise;
    if (BluetoothAdapterResult.BluetoothAdapterResultOk != adapter.connectDevice(deviceId)) {
      retToCallback(BluetoothAdapterResult.BluetoothAdapterResultNotInit, promise);
      return;
    }

  }

  private Result connectListener;
  private Result writeListener;
  private Result readListener;

  @Override
  public synchronized void onDeviceConnected(DeviceAdapter device) {
    if (connectListener != null) {
      Map map = new HashMap();
      map.put("deviceId", device.getDeviceId());
      connectListener.success(map);
      connectListener = null;
    }

    dispatchStateChange(device.getDeviceId(),true);
  }

  @Override
  public synchronized void onDeviceConnectFailed(DeviceAdapter device) {
    if (connectListener != null) {
      connectListener.error(CONNECTION_FAIL, "Connect to device failed",null);
      connectListener = null;
    }

    dispatchStateChange(device.getDeviceId(),false);
  }

  @Override
  public synchronized void onCharacteristicWrite(DeviceAdapter device, BluetoothGattCharacteristic characteristic, boolean success) {
    if(writeListener!=null){
      if(success){
        writeListener.success(null);
      }else{
        writeListener.error(SYSTEM_ERROR,"Write value failed",null);
      }
      writeListener = null;
    }
  }

  @Override
  public synchronized void onCharacteristicRead(DeviceAdapter device, BluetoothGattCharacteristic characteristic, boolean success) {
    if(readListener!=null){
      if(success){
        readListener.success(HexUtil.encodeHex(characteristic.getValue()));
      }else{
        readListener.error(SYSTEM_ERROR,"Read value failed",null);
      }
      readListener = null;
    }
  }


  @Override
  public synchronized void onCharacteristicChanged(
          DeviceAdapter device, BluetoothGattCharacteristic characteristic) {
    Map map = new HashMap();
    map.put("deviceId",device.getDeviceId());
    map.put("serviceId",Utils.getUuidOfService(characteristic.getService()));
    map.put("characteristicId",Utils.getUuidOfCharacteristic(characteristic));
    map.put("value",HexUtil.encodeHexStr(characteristic.getValue()));

    channel.invokeMethod("valueUpdate",map);
  }



  @Override
  public synchronized void onDeviceDisconnected(DeviceAdapter device) {
    dispatchStateChange(device.getDeviceId(),false);
  }

  private void dispatchStateChange(String deviceId,boolean connected){
    Map map = new HashMap();
    map.put("deviceId",deviceId);
    map.put("connected",connected);
    channel.invokeMethod("stateChange",map);
  }

  @Override
  public synchronized void onDeviceFound(BluetoothDevice device, int rssi) {
    Map map = new HashMap();
    map.put("deviceId",Utils.getDeviceId(device));
    map.put("name", device.getName() == null ? "" : device.getName());
    channel.invokeMethod("foundDevice",map);
  }
}
