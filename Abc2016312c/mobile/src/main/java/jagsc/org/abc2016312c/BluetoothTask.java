package jagsc.org.abc2016312c;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class BluetoothTask {
    private static final String TAG = "BluetoothTask";

    /**
     * UUIDはサーバと一致している必要がある。
     * - 独自サービスのUUIDはツールで生成する。（ほぼ乱数）
     * - 注：このまま使わないように。
     */
    //private static final UUID APP_UUID = UUID.fromString("11111111-1111-1111-1111-111111111123");//UUID.fromString("11111111-1111-1111-1111-111111111123");
    private static final UUID APP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private MainActivity activity;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothDevice bluetoothDevice = null;
    private BluetoothSocket bluetoothSocket;
    private InputStream btIn;
    private OutputStream btOut;

    private boolean is_inited = false;
    private boolean is_connected = false;
    private boolean await_ = false;

    public BluetoothTask(MainActivity activity) {
        this.activity = activity;
    }

    /**
     * Bluetoothの初期化。
     */
    public void init() {
        // BTアダプタ取得。取れなければBT未実装デバイス。
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            activity.errorDialog("This device is not implement Bluetooth.");
            return;
        }
        // BTが設定で有効になっているかチェック。
        if (!bluetoothAdapter.isEnabled()) {
            // TODO: ユーザに許可を求める処理。
            activity.errorDialog("This device is disabled Bluetooth.");
            return;
        }
        is_inited = true;
    }

    public boolean is_inited() {
        return is_inited;
    }

    public boolean is_connected() {
        return is_connected;
    }

    /**
     * @return ペアリング済みのデバイス一覧を返す。デバイス選択ダイアログ用。
     */
    public Set<BluetoothDevice> getPairedDevices() {
        return bluetoothAdapter.getBondedDevices();
    }

    /**
     * 非同期で指定されたデバイスの接続を開始する。
     * - 選択ダイアログから選択されたデバイスを設定される。
     *
     * @param device 選択デバイス
     *               nullがきたら再接続なのでbluetoothDeviceの値は更新しない
     */
    public void doConnect(BluetoothDevice device) {
        if (device != null) {
            bluetoothDevice = device;
        }
        try {
            bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(APP_UUID);
            //Method m = bluetoothDevice.getClass().getMethod("createInsecureRfcommSocket", new Class[] {int.class});
            //bluetoothSocket = (BluetoothSocket) m.invoke(bluetoothDevice, 1);
            new ConnectTask().execute();
        } catch (IOException e) {
            Log.e(TAG, e.toString(), e);
            activity.errorDialog(e.toString());
        }
        is_connected = true;
    }

    /**
     * 非同期でBluetoothの接続を閉じる。
     */
    public void doClose() {
        new CloseTask().execute();
        is_connected = false;
    }

    /**
     * 非同期でメッセージの送受信を行う。
     *
     * @param msg 送信メッセージ.
     */
    public void doSend(String msg) {
        byte[] buffer_ = new byte[msg.length() / 2 + 1];
        for (int i = 0; i < msg.length(); ++i) {
            int tmp;
            switch (msg.charAt(i)) {
                case '0':
                    tmp = 0x01;
                    break;
                case '1':
                    tmp = 0x02;
                    break;
                case '2':
                    tmp = 0x03;
                    break;
                case '3':
                    tmp = 0x04;
                    break;
                case '4':
                    tmp = 0x05;
                    break;
                case '5':
                    tmp = 0x06;
                    break;
                case '6':
                    tmp = 0x07;
                    break;
                case '7':
                    tmp = 0x08;
                    break;
                case '8':
                    tmp = 0x09;
                    break;
                case '9':
                    tmp = 0x0a;
                    break;
                case '-':
                    tmp = 0x0b;
                    break;
                case '.':
                    tmp = 0x0c;
                    break;
                case ',':
                    tmp = 0x0d;
                    break;
                default:
                    tmp = 0x00;
                    break;
            }
            if (i % 2 == 0) {
                buffer_[i / 2] = (byte) (tmp << 4);
            } else {
                buffer_[i / 2] += (byte) tmp;
            }
        }
        try {
            btOut.write(buffer_);
            if( await_ ) {
                btOut.write('\n');
            }
            else {
                new SendTask().execute(msg);
            }
        } catch (Throwable t) {
            doClose();
        }
    }

    /**
     * Bluetoothと接続を開始する非同期タスク。
     * - 時間がかかる場合があるのでProcessDialogを表示する。
     * - 双方向のストリームを開くところまで。
     */
    private class ConnectTask extends AsyncTask<Void, Void, Object> {
        @Override
        protected void onPreExecute() {
            activity.showWaitDialog("Connect Bluetooth Device.");
        }

        @Override
        protected Object doInBackground(Void... params) {
            try {
                bluetoothSocket.connect();
                btIn = bluetoothSocket.getInputStream();
                btOut = bluetoothSocket.getOutputStream();
            } catch (Throwable t) {
                doClose();
                return t;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Object result) {
            if (result instanceof Throwable) {
                Log.e(TAG, result.toString(), (Throwable) result);
                activity.errorDialog(result.toString());
            } else {
                activity.hideWaitDialog();
            }
        }
    }

    /**
     * Bluetoothと接続を終了する非同期タスク。
     * - 不要かも知れないが念のため非同期にしている。
     */
    private class CloseTask extends AsyncTask<Void, Void, Object> {
        @Override
        protected Object doInBackground(Void... params) {
            try {
                try {
                    btOut.close();
                } catch (Throwable t) {/*ignore*/}
                try {
                    btIn.close();
                } catch (Throwable t) {/*ignore*/}
                bluetoothSocket.close();
            } catch (Throwable t) {
                return t;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Object result) {
            if (result instanceof Throwable) {
                Log.e(TAG, result.toString(), (Throwable) result);
                activity.errorDialog(result.toString());
            }
        }
    }

    /**
     * サーバとメッセージの送受信を行う非同期タスク。
     */
    private class SendTask extends AsyncTask<String, Void, Object> {
        @Override
        protected Object doInBackground(String... params) {
            try {
                btOut.flush();

                byte[] buff = new byte[512];
                await_ = true;
                int len = btIn.read(buff);

                return new String(buff, 0, len);
            } catch (Throwable t) {
                doClose();
                return t;
            }
        }

        @Override
        protected void onPostExecute(Object result) {
            if (result instanceof Exception) {
                Log.e(TAG, result.toString(), (Throwable) result);
                activity.errorDialog(result.toString());
            } else {
                String r_ = result.toString();
                if( r_.equals("\0") ){
                    //Log.i("test","empty message");
                } else {
                    // TODO:Wearに転送する処理
                    Pattern p = Pattern.compile("scene:([^\n\0\r]*)");
                    Matcher m = p.matcher(r_);
                    if( m.find() ){
                        String scene_name_ = m.group(1);
                        Toast.makeText(activity,"scene is changed"+scene_name_,Toast.LENGTH_SHORT).show();//とりあえずトーストで表示
                    }
                }
                await_ = false;
            }
        }
    }
}
