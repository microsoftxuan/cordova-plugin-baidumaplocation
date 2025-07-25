package com.aruistar.cordova.baidumap;


import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;

import com.baidu.location.BDLocation;
import com.baidu.location.BDLocationListener;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.location.LocationClientOption.LocationMode;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.LOG;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

import java.util.ArrayList;

/**
 * 百度定位cordova插件android端
 *
 * @author aruis
 * @author KevinWang15
 */
public class BaiduMapLocation extends CordovaPlugin {

    /**
     * LOG TAG
     */
    private static final String LOG_TAG = BaiduMapLocation.class.getSimpleName();

    /**
     * JS回调接口对象
     */
    public static CallbackContext cbCtx = null;

    /**
     * 百度定位客户端
     */
    public LocationClient mLocationClient = null;

    private LocationClientOption mOption;

    public static String AK = "替换AK";

    public static String SK = "替换SK";

    public static String URL = "https://api.map.baidu.com/reverse_geocoding/v3?";


    /**
     * 百度定位监听
     */
    public BDLocationListener myListener = new BDLocationListener() {
        @Override
        public void onReceiveLocation(BDLocation location) {
            try {
                JSONObject json = new JSONObject();

                json.put("time", location.getTime());
                json.put("locType", location.getLocType());
                json.put("locTypeDescription", location.getLocTypeDescription());
                json.put("latitude", location.getLatitude());
                json.put("longitude", location.getLongitude());
                json.put("radius", location.getRadius());

                json.put("countryCode", location.getCountryCode());
                json.put("country", location.getCountry());
                json.put("citycode", location.getCityCode());
                json.put("city", location.getCity());
                json.put("district", location.getDistrict());
                json.put("street", location.getStreet());
                json.put("addr", location.getAddrStr());
                json.put("province", location.getProvince());
//                json.put("version", location.getVersion());

                json.put("userIndoorState", location.getUserIndoorState());
                json.put("direction", location.getDirection());
                json.put("locationDescribe", location.getLocationDescribe());

                PluginResult pluginResult;

//                逆地理开始
                Map params = new LinkedHashMap<String, String>();
                params.put("ak", "QPFCXU6F6sq93lJlUGvQHvzbn92mq3XF");
                params.put("output", "json");
                params.put("coordtype", "bd09ll");
                params.put("extensions_poi", "1");
                params.put("sort_strategy", "distance");
                params.put("entire_poi", "1");
                params.put("location", location.getLatitude() + "0000," + location.getLongitude() + "0000");
                params.put("sn", caculateSn(params));

                if (location.getLocType() == BDLocation.TypeServerError
                        || location.getLocType() == BDLocation.TypeNetWorkException
                        || location.getLocType() == BDLocation.TypeCriteriaException || location.getLocType() == 505) {

                    json.put("describe", "定位失败");
                    pluginResult = new PluginResult(PluginResult.Status.ERROR, json);
                } else {
                    requestGetAKAsync("https://api.map.baidu.com/reverse_geocoding/v3?", params, json);
//                    pluginResult = new PluginResult(PluginResult.Status.OK, json);
                }


//                cbCtx.sendPluginResult(pluginResult);
            } catch (JSONException e) {
                String errMsg = e.getMessage();
                LOG.e(LOG_TAG, errMsg, e);

                PluginResult pluginResult = new PluginResult(PluginResult.Status.ERROR, errMsg);
                cbCtx.sendPluginResult(pluginResult);
            } catch (Exception e) {
                LOG.e(LOG_TAG, "请求失败", e);
                throw new RuntimeException(e);
            } finally {
//                mLocationClient.stop();
            }
        }

    };

    /**
     * 安卓6以上动态权限相关
     */

    private static final int REQUEST_CODE = 100001;

    private boolean needsToAlertForRuntimePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return !cordova.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION) || !cordova.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION);
        } else {
            return false;
        }
    }

    private void requestPermission() {
        ArrayList<String> permissionsToRequire = new ArrayList<String>();

        if (!cordova.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION))
            permissionsToRequire.add(Manifest.permission.ACCESS_COARSE_LOCATION);

        if (!cordova.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION))
            permissionsToRequire.add(Manifest.permission.ACCESS_FINE_LOCATION);

        String[] _permissionsToRequire = new String[permissionsToRequire.size()];
        _permissionsToRequire = permissionsToRequire.toArray(_permissionsToRequire);
        cordova.requestPermissions(this, REQUEST_CODE, _permissionsToRequire);
    }

    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {
        if (cbCtx == null || requestCode != REQUEST_CODE)
            return;
        for (int r : grantResults) {
            if (r == PackageManager.PERMISSION_DENIED) {
                JSONObject json = new JSONObject();
                json.put("describe", "定位失败");
                LOG.e(LOG_TAG, "权限请求被拒绝");
                cbCtx.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, json));
                return;
            }
        }

        performGetLocation();
    }

    /**
     * 插件主入口
     */
    @Override
    public boolean execute(String action, final JSONArray args, CallbackContext callbackContext) throws JSONException {
        cbCtx = callbackContext;
        LOG.e("xxxxxx", "进入getCurrentPosition");
        if ("getCurrentPosition".equalsIgnoreCase(action)) {
            if (!needsToAlertForRuntimePermission()) {
                performGetLocation();
            } else {
                requestPermission();
                // 会在onRequestPermissionResult时performGetLocation
            }
            return true;
        }

        return false;
    }


    /**
     * 权限获得完毕后进行定位
     */
    private void performGetLocation() {
        if (mLocationClient == null) {
            mLocationClient = new LocationClient(this.webView.getContext());
            mLocationClient.registerLocationListener(myListener);
            mLocationClient.setLocOption(getDefaultLocationClientOption());
        }

        mLocationClient.start();
    }


    public LocationClientOption getDefaultLocationClientOption() {
        if (mOption == null) {
            mOption = new LocationClientOption();
            mOption.setLocationMode(LocationMode.Hight_Accuracy);//可选，默认高精度，设置定位模式，高精度，低功耗，仅设备
            mOption.setCoorType("bd09ll");//可选，默认gcj02，设置返回的定位结果坐标系，如果配合百度地图使用，建议设置为bd09ll;
            mOption.setScanSpan(0);//可选，默认0，即仅定位一次，设置发起定位请求的间隔需要大于等于1000ms才是有效的
            mOption.setIsNeedAddress(true);//可选，设置是否需要地址信息，默认不需要
            mOption.setOpenGps(true); // 可选，默认false,设置是否使用gps            
            mOption.setNeedDeviceDirect(false);//可选，设置是否需要设备方向结果
            mOption.setLocationNotify(false);//可选，默认false，设置是否当gps有效时按照1S1次频率输出GPS结果
            mOption.setIgnoreKillProcess(true);//可选，默认true，定位SDK内部是一个SERVICE，并放到了独立进程，设置是否在stop的时候杀死这个进程，默认不杀死
            mOption.setIsNeedLocationDescribe(true);//可选，默认false，设置是否需要位置语义化结果，可以在BDLocation.getLocationDescribe里得到，结果类似于“在北京天安门附近”
            mOption.setIsNeedLocationPoiList(false);//可选，默认false，设置是否需要POI结果，可以在BDLocation.getPoiList里得到
            mOption.SetIgnoreCacheException(false);//可选，默认false，设置是否收集CRASH信息，默认收集

            mOption.setIsNeedAltitude(false);//可选，默认false，设置定位时是否需要海拔信息，默认不需要，除基础定位版本都可用

        }
        return mOption;
    }

    // 异步请求方法
    private void requestGetAKAsync(String url, Map<String, String> param, JSONObject json) {
        new Thread(() -> {
            try {
                String result = requestGetAK(url, param);
                JSONObject jsonObject = new JSONObject(result);
                json.put("direction", jsonObject);
                PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, json);
                cbCtx.sendPluginResult(pluginResult);
            } catch (Exception e) {
                e.printStackTrace();
                String errMsg = e.getMessage();
                PluginResult pluginResult = new PluginResult(PluginResult.Status.ERROR, errMsg);
                cbCtx.sendPluginResult(pluginResult);
            } finally {
                mLocationClient.stop();
            }
        }).start();
    }


    /**
     * 默认ak
     * 选择了ak，使用IP白名单校验：
     * 根据您选择的AK已为您生成调用代码
     * 检测到您当前的ak设置了IP白名单校验
     * 您的IP白名单中的IP非公网IP，请设置为公网IP，否则将请求失败
     * 请在IP地址为xxxxxxx的计算发起请求，否则将请求失败
     */
    public String requestGetAK(String strUrl, Map<String, String> param) throws Exception {
        LOG.e(LOG_TAG, "开始进入逆地理");
        if (strUrl == null || strUrl.length() <= 0 || param == null || param.size() <= 0) {
            return null;
        }

        StringBuffer queryString = new StringBuffer();
        queryString.append(strUrl);
        for (Map.Entry<?, ?> pair : param.entrySet()) {
            queryString.append(pair.getKey() + "=");
            //    第一种方式使用的 jdk 自带的转码方式  第二种方式使用的 spring 的转码方法 两种均可
            queryString.append(URLEncoder.encode((String) pair.getValue(), "UTF-8").replace("+", "%20") + "&");
//            queryString.append(UriUtils.encode((String) pair.getValue(), "UTF-8") + "&");
        }

        if (queryString.length() > 0) {
            queryString.deleteCharAt(queryString.length() - 1);
        }

        java.net.URL url = new URL(queryString.toString());
        LOG.e("queryString", queryString.toString());
        URLConnection httpConnection = (HttpURLConnection) url.openConnection();
        httpConnection.connect();

        InputStreamReader isr = new InputStreamReader(httpConnection.getInputStream());
        BufferedReader reader = new BufferedReader(isr);
        StringBuffer buffer = new StringBuffer();
        String line;
        while ((line = reader.readLine()) != null) {
            buffer.append(line);
        }
        reader.close();
        isr.close();
        LOG.e(LOG_TAG, "AK: " + buffer.toString());
        return buffer.toString();
    }

    public String caculateSn(Map<String, String> param) throws UnsupportedEncodingException,
            NoSuchAlgorithmException {
        // 计算sn跟参数对出现顺序有关，get请求请使用LinkedHashMap保存<key,value>，该方法根据key的插入顺序排序；post请使用TreeMap保存<key,value>，该方法会自动将key按照字母a-z顺序排序。
        // 所以get请求可自定义参数顺序（sn参数必须在最后）发送请求，但是post请求必须按照字母a-z顺序填充body（sn参数必须在最后）。
        // 以get请求为例：http://api.map.baidu.com/geocoder/v2/?address=百度大厦&output=json&ak=yourak，paramsMap中先放入address，再放output，然后放ak，放入顺序必须跟get请求中对应参数的出现顺序保持一致。


        // 调用下面的toQueryString方法，对LinkedHashMap内所有value作utf8编码，拼接返回结果address=%E7%99%BE%E5%BA%A6%E5%A4%A7%E5%8E%A6&output=json&ak=yourak
        String paramsStr = toQueryString(param);

        // 对paramsStr前面拼接上/geocoder/v2/?，后面直接拼接yoursk得到/geocoder/v2/?address=%E7%99%BE%E5%BA%A6%E5%A4%A7%E5%8E%A6&output=json&ak=yourakyoursk
        String wholeStr = new String("/reverse_geocoding/v3?" + paramsStr + SK);

        System.out.println(wholeStr);
        // 对上面wholeStr再作utf8编码
        String tempStr = URLEncoder.encode(wholeStr, "UTF-8");

        // 调用下面的MD5方法得到最后的sn签名
        String sn = MD5(tempStr);
        System.out.println(sn);
        return sn;
    }

    // 对Map内所有value作utf8编码，拼接返回结果
    public String toQueryString(Map<?, ?> data)
            throws UnsupportedEncodingException {
        StringBuffer queryString = new StringBuffer();
        for (Map.Entry<?, ?> pair : data.entrySet()) {
            queryString.append(pair.getKey() + "=");
            //    第一种方式使用的 jdk 自带的转码方式  第二种方式使用的 spring 的转码方法 两种均可
            queryString.append(URLEncoder.encode((String) pair.getValue(), "UTF-8").replace("+", "%20") + "&");
//            queryString.append(UriUtils.encode((String) pair.getValue(), "UTF-8") + "&");
        }
        if (queryString.length() > 0) {
            queryString.deleteCharAt(queryString.length() - 1);
        }
        return queryString.toString();
    }

    // 来自stackoverflow的MD5计算方法，调用了MessageDigest库函数，并把byte数组结果转换成16进制
    public String MD5(String md5) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest
                    .getInstance("MD5");
            byte[] array = md.digest(md5.getBytes());
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < array.length; ++i) {
                sb.append(Integer.toHexString((array[i] & 0xFF) | 0x100)
                        .substring(1, 3));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
        }
        return null;
    }
}


