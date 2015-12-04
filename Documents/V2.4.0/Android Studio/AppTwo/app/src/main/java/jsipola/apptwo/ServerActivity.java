package jsipola.apptwo;

import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.system.ErrnoException;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import dji.sdk.api.GroundStation.DJIGroundStation;
import dji.sdk.api.Battery.DJIBatteryProperty;
import dji.sdk.api.DJIDrone;
import dji.sdk.api.DJIDroneTypeDef;
import dji.sdk.api.DJIError;
import dji.sdk.interfaces.DJIBatteryUpdateInfoCallBack;
import dji.sdk.interfaces.DJIGeneralListener;
import dji.sdk.interfaces.DJIGroundStationExecuteCallBack;
import dji.sdk.interfaces.DJIExecuteResultCallback;

public class ServerActivity extends AppCompatActivity {
    // katoin mallia              // AppCompatActivity
    // https://thinkandroid.wordpress.com/2010/03/27/incorporating-socket-programming-into-your-applications/

    private TextView serverStatus, text_in;

    // Define server IP
    public String server_ip = "";  // laita myöhemmin
    // Define port
    public static final int server_port = 8080;
    // connection status to the drone
    public static boolean drone_con;
    // handler
    private Handler handler = new Handler();

    private static final String TAG = "AppTwo";

    public DJIGroundStationExecuteCallBack mCallBack;
    public DJIExecuteResultCallback rCallBack;
    public DJIGroundStation GS;


    private ServerSocket serversocket; // creates a new serversocket


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        serverStatus = (TextView) findViewById(R.id.server_status);
        text_in = (TextView) findViewById(R.id.text_in);
        server_ip = getIP();


        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
        new Thread(){
            public void run(){
                try{
                    DJIDrone.checkPermission(getApplicationContext(), new DJIGeneralListener(){
                        @Override
                        public void onGetPermissionResult(int result){
                            if(result == 0) {
                                // show success
                                Log.e(TAG, "onGetPermissionResult = "+result);
                                Log.e(TAG, "onGetPermissionResultDescription="+DJIError.getCheckPermissionErrorDescription(result));
                            } else {
                                // show errors
                                Log.e(TAG, "onGetPermissionResult ="+result);
                                Log.e(TAG, "onGetPermissionResultDescription="+ DJIError.getCheckPermissionErrorDescription(result));
                            }
                        }
                    });
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }.start();

        // defines the sdk to Inspire 1
        DJIDrone.initWithType(this.getApplicationContext(), DJIDroneTypeDef.DJIDroneType.DJIDrone_Inspire1);
        // connects the app to the drone
        drone_con = DJIDrone.connectToDrone();
        Log.e(TAG, "Connection to the Drone = "+drone_con);
        GS = DJIDrone.getDjiGroundStation();

        Thread thread = new Thread(new serverthread());
        thread.start();

    }

    public class serverthread implements Runnable {
        public void run(){
            try {
                if (server_ip != null) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            serverStatus.setText("Listening at :" + server_ip);
                        }
                    });
                    serversocket = new ServerSocket(server_port);
                    while (true) {
                        Socket client = serversocket.accept(); // checks for the client
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                serverStatus.append("\n" + "Connected to Client");
                            }
                        });

                        try {
                            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                            final PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(client.getOutputStream())), true);
                            String line = null;
                            while ((line = in.readLine()) != null) {
                                Log.d("Server activity", line);
                                final List<String> moveData = new ArrayList<String>(Arrays.asList(line.split(" ")));
                                int A = 0;
                                if (line.equals("e"))
                                {
                                    text_in.append("Client Disconnected\n");
                                    break;
                                }
                                else {
                                    final String msg = line;
                                    final String msg_sent = line + " on aasi";
                                    //final int finalA = A;
                                    handler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            // viestien ja komentojen edelleen lähetys
                                            // ja muut toiminnalisuudet
                                            // if ( finalA == 1) {
                                            //GS.sendFlightControlData(yaw, pitch, roll, throttle, rCallBack);
                                            //}
                                            if (moveData.get(0).equals("move")) {
                                                sendflightdata(moveData, out, drone_con);
                                            } else {
                                                text_in.append("Client Sent: " + msg + "\n");
                                                text_in.append("Sent To Client: " + msg_sent + "\n");
                                                out.println(msg_sent);
                                            }
                                        }

                                    });

                                }

                            }
                            break;
                        } catch (Exception e) {
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    serverStatus.setText("Connection interrupted");
                                }
                            });
                            e.printStackTrace();
                        }
                    }
                } else {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            serverStatus.setText("No internet connection detected");
                        }
                    });
                }
            } catch (IOException e) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        serverStatus.setText("Error");
                    }
                });
                e.printStackTrace();
            }
        }
    }

    private String getIP() {
        //Gets the device wifi address
        //http://stackoverflow.com/questions/6064510/how-to-get-ip-address-of-the-device
        WifiManager wifiman = (WifiManager) getSystemService(WIFI_SERVICE);
        WifiInfo wifi_inf = wifiman.getConnectionInfo();
        int ipaddr = wifi_inf.getIpAddress();
        String server_ip = String.format("%d.%d.%d.%d", (ipaddr & 0xff),(ipaddr >> 8 & 0xff),(ipaddr >> 16 & 0xff),(ipaddr >> 24 & 0xff));
        return server_ip;
    }

    public void sendflightdata(List<String> moveData, PrintWriter out, boolean drone_con) {
        final float yaw = Float.parseFloat(moveData.get(1));
        final float pitch = Float.parseFloat(moveData.get(2));
        final float roll = Float.parseFloat(moveData.get(3));
        final float throttle = Float.parseFloat(moveData.get(4));
        if (drone_con == true){
            GS.openGroundStation(mCallBack);
            GS.sendFlightControlData(yaw, pitch, roll, throttle, rCallBack);
            GS.closeGroundStation(mCallBack);
            text_in.append("Sent To Drone: " + moveData + "\n");
            out.println("Sent Command to Drone");
        } else {
            out.println("Couldn't relay command to the Drone");
            text_in.append("Couldn't relay command to the Drone\n");
            //break;
        }
        return;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_server, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
    @Override
    protected void onStop() {
        super.onStop();
        try {
            serversocket.close();
            DJIDrone.disconnectToDrone();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
    }
}
