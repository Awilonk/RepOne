package jsipola.apptwo;

import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import android.widget.Toast;

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


import dji.sdk.FlightController.DJIFlightController;
import dji.sdk.FlightController.*;
import dji.sdk.FlightController.DJIFlightControllerDataType;
import dji.sdk.MissionManager.DJIMission;
import dji.sdk.MissionManager.DJIMissionManager;
import dji.sdk.MissionManager.DJIWaypoint;
import dji.sdk.MissionManager.DJIWaypointMission;
import dji.sdk.SDKManager.DJISDKManager;
import dji.sdk.base.DJIBaseComponent;
import dji.sdk.base.DJIBaseProduct;
import dji.sdk.base.DJIError;
import dji.sdk.base.DJISDKError;


public class ServerActivity2 extends AppCompatActivity {
    // katoin mallia              // AppCompatActivity
    // https://thinkandroid.wordpress.com/2010/03/27/incorporating-socket-programming-into-your-applications/

    private TextView serverStatus, text_in;

    // Define server IP
    public String server_ip = "";  // laita myöhemmin
    // Define port
    public static final int server_port = 8080;
    // connection status to the drone
    public static boolean drone_con;

    private DJIBaseProduct mProduct = null;
    private DJIFlightController mFlight = null;
    private DJIFlightControllerDataType.DJILocationCoordinate3D posit;
    private DJIWaypointMission mWay;
    // handler
    private Handler handler = new Handler();

    private static final String TAG = "AppTwo";

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

        DJISDKManager.getInstance().initSDKManager(this, mDJISDKManagerCallback);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        Thread thread = new Thread(new serverthread());
        thread.start();

    }

    private DJISDKManager.DJISDKManagerCallback mDJISDKManagerCallback = new DJISDKManager.DJISDKManagerCallback() {

        // Reference : https://github.com/DJI-Mobile-SDK/Android-FPVDemo
        //Listens to the SDK registration result
        @Override

        public void onGetRegisteredResult(DJISDKError error) {
            if(error == DJISDKError.REGISTRATION_SUCCESS) {
                DJISDKManager.getInstance().startConnectionToProduct();
            } else {
                Handler handler = new Handler(Looper.getMainLooper());
                handler.post(new Runnable() {

                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), "register sdk fails, check network is available", Toast.LENGTH_LONG).show();
                    }
                });
            }
            Log.e("TAG", error.toString());
        }

        //Listens to the connected product changing, including two parts, component changing or product connection changing.

        @Override
        public void onProductChanged(DJIBaseProduct oldProduct, DJIBaseProduct newProduct) {

            DJIBaseProduct mProduct = newProduct;
            if(mProduct != null) {
                mProduct.setDJIBaseProductListener(mDJIBaseProductListener);
            }
//            notifyStatusChange();
        }
    };

    private DJIBaseProduct.DJIBaseProductListener mDJIBaseProductListener = new DJIBaseProduct.DJIBaseProductListener() {

        @Override
        public void onComponentChange(DJIBaseProduct.DJIComponentKey key, DJIBaseComponent oldComponent, DJIBaseComponent newComponent) {
            if(newComponent != null) {
                newComponent.setDJIComponentListener(mDJIComponentListener);
            }
//            notifyStatusChange();
        }

        @Override
        public void onProductConnectivityChanged(boolean isConnected) {
//            notifyStatusChange();
        }
    };

    private DJIBaseComponent.DJIComponentListener mDJIComponentListener = new DJIBaseComponent.DJIComponentListener() {

        @Override
        public void onComponentConnectivityChanged(boolean isConnected) {
//            notifyStatusChange();
        }
    };


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
//                                drone_con = DJIDrone.connectToDrone();

                                Log.e(TAG, "Connection to the Drone = "+drone_con);
                                // checks the sdk level
                                //  int level = DJIDrone.getLevel();
                                //  Log.e(TAG, String.valueOf(level));

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
                                            //
                                            if (moveData.get(0).equals("move")) {
                                                // moves the drone in a direction
                                                //sendflightdata(moveData, out, drone_con);
                                            } else if (moveData.get(0).equals("takeoff")) {
                                                // drone turns the motors on and takes off
                                                takeoff(out);
                                            } else if (moveData.get(0).equals("land")) {
                                                landing(out);
                                            } else if (moveData.get(0).equals("here")) {
                                                waypoint(moveData, out);
                                            } else if (moveData.get(0).equals("pos")){
                                                position(out);
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


    /* KATO tätä myöhemmin*/

/*
    public static synchronized DJIBaseProduct getProductInstance() {
        if (null == mProduct) {
            mProduct = DJISDKManager.getInstance().getDJIProduct();
        }
        return mProduct;
    }
*/
/*
    public void sendflightdata(final List<String> moveData, final PrintWriter out, boolean drone_con) {
        // Takes input from message and forwards the data to the copter
        final float yaw = Float.parseFloat(moveData.get(1));
        final float pitch = Float.parseFloat(moveData.get(2));
        final float roll = Float.parseFloat(moveData.get(3));
        final float throttle = Float.parseFloat(moveData.get(4));
        //if (drone_con == true) {
        try {
            // opens the groundstation so we can send the flight data

            DJIDrone.getDjiGroundStation().openGroundStation(new DJIGroundStationExecuteCallBack() {
                @Override
                public void onResult(DJIGroundStationTypeDef.GroundStationResult groundStationResult) {
                    String ResultsString = "return code = " + groundStationResult.toString();
//                    handler.sendMessage(handler.obtainMessage(SHOWTOAST, ResultsString));
                    Log.e(TAG, ResultsString);

                    // sets the default control methods for the copter
                    DJIDrone.getDjiGroundStation().setHorizontalControlCoordinateSystem(DJIGroundStationTypeDef.DJINavigationFlightControlCoordinateSystem.Navigation_Flight_Control_Coordinate_System_Body);
                    DJIDrone.getDjiGroundStation().setVerticalControlMode(DJIGroundStationTypeDef.DJINavigationFlightControlVerticalControlMode.Navigation_Flight_Control_Vertical_Control_Position);
                    DJIDrone.getDjiGroundStation().setHorizontalControlMode(DJIGroundStationTypeDef.DJINavigationFlightControlHorizontalControlMode.Navigation_Flight_Control_Horizontal_Control_Angle);
                    DJIDrone.getDjiGroundStation().setYawControlMode(DJIGroundStationTypeDef.DJINavigationFlightControlYawControlMode.Navigation_Flight_Control_Yaw_Control_Angle);

                    // if openGS was a success we can send the data
                    if (groundStationResult == DJIGroundStationTypeDef.GroundStationResult.GS_Result_Success) {
                        DJIDrone.getDjiGroundStation().sendFlightControlData(yaw, pitch, roll, throttle, new DJIExecuteResultCallback() {
                            @Override
                            public void onResult(DJIError djiError) {
                                String result = djiError.errorDescription;
                                Log.e(TAG, "Callback result:  " + result);
                                //       text_in.append("Sent To Drone: " + moveData + "\n");
                                out.println("Result of move: "+ result);
                            }followthis
                        });

                    } else {
                        // sends msg that something went wrong
                        out.println(ResultsString); //"Couldn't establish GroundStation");
                    }
                }
            });
            // closes the groundstation
            DJIDrone.getDjiGroundStation().closeGroundStation(new DJIGroundStationExecuteCallBack() {
                @Override
                public void onResult(DJIGroundStationTypeDef.GroundStationResult groundStationResult) {
                    String ResultsString = "return code = " + groundStationResult.toString();
//                    handler.sendMessage(handler.obtainMessage(SHOWTOAST, ResultsString));
                    Log.e(TAG, ResultsString);

                }
            });
            //GS.sendFlightControlData(yaw, pitch, roll, throttle, rCallBack);

            //GS.closeGroundStation(mCallBack);

        } catch (Exception s) {
//        } else {
            s.printStackTrace();
            out.println("Couldn't relay command to the Drone");
            text_in.append("Couldn't relay command to the Drone\n");
            //break;
        }
        return;
    }
*/

    public void takeoff(final PrintWriter out){
        // drone takes off after server receives "takeoff" message
        try {

            mFlight.takeOff(new DJIBaseComponent.DJICompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    Log.e(TAG, "Takeoff: " + djiError.getDescription());
                    out.println("Drone taking off");
                }
            });

        } catch (Exception e) {
            out.println("Drone couldn't execute take off");
            e.printStackTrace();
        }
        return;
    }

    private void landing(final PrintWriter out) {
        try {
            mFlight.autoLanding(new DJIBaseComponent.DJICompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    Log.e(TAG, "Landing: " + djiError.getDescription());
                    out.println("Drone landing");

                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            out.println("Drone had a problem landing, try again");
        }
    }

    private void position(final PrintWriter out) {
        try {
            String lati = String.valueOf(posit.getLatitude());
            String longi = String.valueOf(posit.getLongitude());
            String alti = String.valueOf(posit.getAltitude());
            String msg = "Latitude:"+lati+" Longitude:"+longi+" Altitude:"+alti;
            out.println(msg);
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    private void waypoint(final List<String> moveData, final PrintWriter out){
        final double lat = Double.parseDouble(moveData.get(1));
        final double lon = Double.parseDouble(moveData.get(2));
        //final double lat =  65.0000000;
        //final double lon =  24.0001600;

//        info.altitude = 10;
//        info.latitude = lat;
//        info.longitude = lon;
        try {
            DJIWaypoint wp1 = new DJIWaypoint(lat, lon, (float) 10.0);
            mWay.addWaypoint(wp1);
            DJIMissionManager mMan = DJIMissionManager.getInstance();
            DJIMission.DJIMissionProgressHandler handy = null;
            mMan.prepareMission(mWay, handy, new DJIBaseComponent.DJICompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    Log.e(TAG, djiError.getDescription());
                }
            });
            mMan.startMissionExecution(new DJIBaseComponent.DJICompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    Log.e(TAG, djiError.getDescription());
                }
            });
            out.println("WaypointMission executed");
        } catch (Exception e) {
            out.println("Problem executing mission");
            e.printStackTrace();
        }
        /*


        DJIDrone.getDjiGroundStation().openGroundStation(new DJIGroundStationExecuteCallBack() {
            @Override
            public void onResult(DJIGroundStationTypeDef.GroundStationResult groundStationResult) {
                if (groundStationResult == DJIGroundStationTypeDef.GroundStationResult.GS_Result_Success) {
                    DJIDrone.getDjiGroundStation().startHotPoint(info, new DJIGroundStationExecuteCallBack() {
                        @Override
                        public void onResult(DJIGroundStationTypeDef.GroundStationResult groundStationResult) {
                            out.println("Result of HP mission: " + groundStationResult);
                        }
                    });
                } else {
                    out.println(groundStationResult);
                }
            }
        });
     */
    }


    /*
    private void gohere(List<String> moveData, final PrintWriter out) {
        //final double lat = Double.parseDouble(moveData.get(1));
        //final double lon = Double.parseDouble(moveData.get(2));
        final double lat =  65.0000000;
        final double lon =  24.4001600;
        DJIFollowMeTarget target = new DJIFollowMeTarget();

        target.latitude = lat;
        target.longitude = lon;

        DJIDrone.getDjiGroundStation().sendFollowTargetGps(target, new DJIGroundStationExecuteCallBack() {
            @Override
            public void onResult(DJIGroundStationTypeDef.GroundStationResult groundStationResult) {
                if (groundStationResult == DJIGroundStationTypeDef.GroundStationResult.GS_Result_Success) {

                }
            }
        });
    }
    */
    private void startgo(List<String> moveData, final PrintWriter out) {
        //final double lat = Double.parseDouble(moveData.get(1));
        //final double lon = Double.parseDouble(moveData.get(2));

                            /*
                    DJIFollowMeInitializationInfo info = new DJIFollowMeInitializationInfo();
                    info.followMeMode = DJIGroundStationTypeDef.GroundStationFollowMeMode.Relative_Mode;
                    info.userLatitude = lat;
                    info.userLongitude = lon;
                    info.yawMode = DJIGroundStationTypeDef.GroundStationFollowMeYawMode.Point_To_Customer;
                    */
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
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
