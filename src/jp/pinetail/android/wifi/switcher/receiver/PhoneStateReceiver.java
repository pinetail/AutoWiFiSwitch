package jp.pinetail.android.wifi.switcher.receiver;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.telephony.CellLocation;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;
import au.com.bytecode.opencsv.CSVWriter;

public class PhoneStateReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        Log.d("hoge", "SampleLocation:onReceive" + intent.getAction());
        // 電話情報の受信開始
        TelephonyManager telManager = (TelephonyManager) context
                .getSystemService(Context.TELEPHONY_SERVICE);

        telManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        telManager.listen(phoneStateListener,
                PhoneStateListener.LISTEN_CELL_LOCATION);

    }

    // 電話情報を受信するためのリスナー
    public PhoneStateListener phoneStateListener = new PhoneStateListener() {

        // 基地局の変化時に呼ばれる
        @Override
        public void onCellLocationChanged(CellLocation location) {
            String str[] = new String[8];

            long currentTimeMillis = System.currentTimeMillis();
            Date date = new Date(currentTimeMillis);
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss");
            str[0] = "DATE:" + simpleDateFormat.format(date);

            // GSMの基地局情報
            if (location instanceof GsmCellLocation) {
                GsmCellLocation loc = (GsmCellLocation) location;
                // str += "CID:" + loc.getCid() + "\n";
                // str += "LAC:" + loc.getLac() + "\n";
            }
            // CDMAの基地局情報
            else if (location instanceof CdmaCellLocation) {
                CdmaCellLocation loc = (CdmaCellLocation) location;
                str[1] = "BaseStationId:" + loc.getBaseStationId();
                str[2] = "BaseStationLatitude:" + loc.getBaseStationLatitude();
                str[3] = "BaseStationLongitude:"
                        + loc.getBaseStationLongitude();
                str[4] = "NetworkId:" + loc.getNetworkId();
                str[5] = "SystemId:" + loc.getSystemId();
                str[6] = "lat:" + (double) loc.getBaseStationLatitude() / 14400;
                str[7] = "lng:" + (double) loc.getBaseStationLongitude()
                        / 14400;
            }

            String file = "/samplewifi/cell.csv";
            String fname = Environment.getExternalStorageDirectory() + file;
            File f = new File(Environment.getExternalStorageDirectory()
                    + "/samplewifi/");
            f.mkdirs();

            // CSVファイル出力
            CSVWriter writer;
            try {
                writer = new CSVWriter(new FileWriter(fname, true));
                List<String[]> strList = new ArrayList<String[]>();
                strList.add(str);
                writer.writeAll(strList);
                writer.flush();

                return;
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    };
}
