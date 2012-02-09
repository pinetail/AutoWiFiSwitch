package jp.pinetail.android.wifi.switcher.activity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;

import jp.pinetail.android.wifi.switcher.Const;
import jp.pinetail.android.wifi.switcher.PreferenceWrapper;
import jp.pinetail.android.wifi.switcher.R;
import jp.pinetail.android.wifi.switcher.overlay.GeoHexOverlay;
import jp.pinetail.android.wifi.switcher.receiver.AlarmBroadcastReceiver.StringUtil;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.telephony.CellLocation;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;
import android.view.View;
import android.view.ViewStub;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.MyLocationOverlay;

public class MainActivity extends MapActivity {
    private final static int WC = LinearLayout.LayoutParams.WRAP_CONTENT;
    private TextView textView;
    protected MapView mMapView;
    protected MapController mMapController = null;
    protected MyLocationOverlay overlay;
    protected static final int E6 = 1000000;
    protected static final int DEFAULT_ZOOM_LEVEL = 15;
    private GeoHexOverlay watchHexOverlay = new GeoHexOverlay();
    private PreferenceWrapper pref = null;

    private GeoHexOverlay.OnTapHexListener onTapHexListener = new GeoHexOverlay.OnTapHexListener() {

        @Override
        public void onTap(GeoHexOverlay sender, String hexCode) {

            try {
                Log.d("MainActivity.OnTapHexListener", "watchHexOverlay_onTap() called.");
                Log.d("MainActivity.OnTapHexListener", "watchHexOverlay_onTap() hex:" + hexCode);
                if (pref.getBoolean(R.string.pref_alarm_enabled_key, false)) {
                    Toast.makeText(MainActivity.this,
                            "エリアを選択するには、[STOP] をして下さい",
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                // 選択 or 選択解除
                Set<String> watchHexesSet = watchHexOverlay.getSelectedGeoHexCodes();
                Set<String> hexCodes = sender.getSelectedGeoHexCodes();
                if (hexCodes.contains(hexCode)) {
                    hexCodes.remove(hexCode);
                } else {
//                    hexCodes.clear();
                    hexCodes.add(hexCode);
                }

                String prevWatchHexes = pref.getString(R.string.pref_watch_hexes_key, "");
                String[] watchHexes = new String[watchHexesSet.size()];
                watchHexesSet.toArray(watchHexes);
                String newWatchHexed = StringUtil.fromArray(watchHexes, Const.ARRAY_SPLITTER);

                // 監視する Hex が変わったら、前回位置を破棄する
                if (!prevWatchHexes.equals(newWatchHexed)) {
                    pref.saveString(R.string.pref_last_hex_key, "");
                }

                pref.saveString(R.string.pref_watch_hexes_key, newWatchHexed);

                // 再描画
                mMapView.invalidate();
            } catch (Exception e) {
                Log.w("MainActivity.OnTapHexListener", "watchHexOverlay_onTap() failed.", e);
            }
        }
    };
    
    // 初期化
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
//
//        // レイアウトの生成
//        LinearLayout layout = new LinearLayout(this);
//        layout.setBackgroundColor(Color.rgb(255, 255, 255));
//        layout.setOrientation(LinearLayout.VERTICAL);
//        setContentView(layout);
//
//        // テキストビューの生成
//        textView = new TextView(this);
//        textView.setText("TelephonyEx");
//        textView.setTextSize(16.0f);
//        textView.setTextColor(Color.rgb(0, 0, 0));
//        textView.setLayoutParams(new LinearLayout.LayoutParams(WC, WC));
//        layout.addView(textView);
//
//        // 電話情報の取得
//        TelephonyManager telManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
//        String str = "";
//        str += "電話番号:" + telManager.getLine1Number() + "\n";
//        str += "SIM国コード:" + telManager.getSimCountryIso() + "\n";
//        str += "SIMシリアル番号:" + telManager.getSimSerialNumber() + "\n";
//        str += "デバイスID:" + telManager.getDeviceId() + "\n";
//        textView.setText(str);
        
        pref = new PreferenceWrapper(this.getApplicationContext());

        mMapView = getMapView();
        mMapView.setBuiltInZoomControls(true);

        mMapController = mMapView.getController();
        
        // 六角形の描画
        watchHexOverlay.setOnTapHexListener(onTapHexListener);
        watchHexOverlay.setBitmap(
                BitmapFactory.decodeResource(getResources(), R.drawable.ringer_map));

        mMapView.getOverlays().add(watchHexOverlay);
        
        // GEOHEXの描画
        Set<String> watchHexes = watchHexOverlay.getSelectedGeoHexCodes();
        String watchHexesStr = pref.getString(R.string.pref_watch_hexes_key, null);
        if (!StringUtil.isNullOrEmpty(watchHexesStr)) {
            String[] array = StringUtil.toArray(watchHexesStr, Const.ARRAY_SPLITTER);
            for (String string : array) {
                watchHexes.add(string);
            }
        }

    }

    /**
     * MapViewオブジェクトを返すメソッド
     * 
     * デバッグモードかどうか判断して最適なMapViewオブジェクトを生成します。
     * 
     * @return
     */
    protected MapView getMapView() {
        ViewStub stub = (ViewStub) findViewById(R.id.mapview_stub);
//        if (Utility.isDebuggable(this)) {
            stub.setLayoutResource(R.layout.map4dev);
//        } else {
//            stub.setLayoutResource(R.layout.map4prod);
//        }
        View inflated = stub.inflate();
        return (MapView) inflated;
    }
    
    // アプリの再開
    @Override
    protected void onResume() {
        super.onResume();

//        // 電話情報の受信開始
//        TelephonyManager telManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
//        telManager.listen(phoneStateListener,
//                PhoneStateListener.LISTEN_CALL_STATE
//                        | PhoneStateListener.LISTEN_SERVICE_STATE
//                        | PhoneStateListener.LISTEN_SIGNAL_STRENGTH
//                        | PhoneStateListener.LISTEN_CELL_LOCATION);
    }

    // アプリの一時停止
    @Override
    protected void onPause() {
        super.onPause();

//        // 電話情報の受信停止
//        TelephonyManager telManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
//        telManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
    }

    // 電話情報を受信するためのリスナー
    public PhoneStateListener phoneStateListener = new PhoneStateListener() {
        // 電話コール状態の変化時に呼ばれる
        @Override
        public void onCallStateChanged(int state, String number) {
//            String str = "電話コール状態:";
//            if (state == TelephonyManager.CALL_STATE_RINGING)
//                str += "電話着信";
//            if (state == TelephonyManager.CALL_STATE_OFFHOOK)
//                str += "通話開始";
//            if (state == TelephonyManager.CALL_STATE_IDLE)
//                str += "電話終了";
//            str += " " + number;
//            textView.setText(textView.getText() + "\n" + str);
        }

        // サービス状態の変化時に呼ばれる
        @Override
        public void onServiceStateChanged(ServiceState serviceState) {
//            String str = "";
//            long currentTimeMillis = System.currentTimeMillis();
//            Date date = new Date(currentTimeMillis);
//            SimpleDateFormat simpleDateFormat = new SimpleDateFormat(
//                    "yyyy-MM-dd HH:mm:ss");
//            str += "DATE:" + simpleDateFormat.format(date) + "\n";
//            str += "サービス状態:";
//            
//            
//            int state = serviceState.getState();
//            if (state == ServiceState.STATE_EMERGENCY_ONLY)
//                str += "エマージェンシーのみ";
//            if (state == ServiceState.STATE_IN_SERVICE)
//                str += "サービス内";
//            if (state == ServiceState.STATE_OUT_OF_SERVICE)
//                str += "サービス外";
//            if (state == ServiceState.STATE_POWER_OFF)
//                str += "電源オフ";
//            textView.setText(textView.getText() + "\n" + str);
            super.onServiceStateChanged(serviceState);
        }

        // 通信強度の変化時に呼ばれる
        @Override
        public void onSignalStrengthChanged(int asu) {
//            String str = "通信強度:" + String.valueOf(-113 + 2 * asu) + "dBm";
//            textView.setText(textView.getText() + "\n" + str);
        }

        // 基地局の変化時に呼ばれる
        @Override
        public void onCellLocationChanged(CellLocation location) {
            String str = "";
            
            long currentTimeMillis = System.currentTimeMillis();
            Date date = new Date(currentTimeMillis);
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss");
            str += "DATE:" + simpleDateFormat.format(date) + "\n";
            
            // GSMの基地局情報
            if (location instanceof GsmCellLocation) {
                GsmCellLocation loc = (GsmCellLocation) location;
                str += "CID:" + loc.getCid() + "\n";
                str += "LAC:" + loc.getLac() + "\n";
            }
            // CDMAの基地局情報
            else if (location instanceof CdmaCellLocation) {
                CdmaCellLocation loc = (CdmaCellLocation) location;
                str += "BaseStationId:" + loc.getBaseStationId() + "\n";
                str += "BaseStationLatitude:" + loc.getBaseStationLatitude()
                        + "\n";
                str += "BaseStationLongitude:" + loc.getBaseStationLongitude()
                        + "\n";
                str += "NetworkId:" + loc.getNetworkId() + "\n";
                str += "SystemId:" + loc.getSystemId() + "\n";
            }
            textView.setText(textView.getText() + "\n" + str);
        }
    };

    @Override
    protected boolean isRouteDisplayed() {
        // TODO 自動生成されたメソッド・スタブ
        return false;
    }
}