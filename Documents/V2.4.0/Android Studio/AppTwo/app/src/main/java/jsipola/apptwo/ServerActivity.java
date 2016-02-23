package jsipola.apptwo;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.Toolbar;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;


import dji.sdk.AirLink.DJIAirLink;
import dji.sdk.AirLink.DJIWiFiLink;
import dji.sdk.FlightController.DJIFlightController;
import dji.sdk.FlightController.DJIFlightControllerDataType;
import dji.sdk.FlightController.DJIFlightControllerDelegate;
import dji.sdk.MissionManager.DJIMission;
import dji.sdk.MissionManager.DJIMissionManager;
import dji.sdk.MissionManager.DJIWaypoint;
import dji.sdk.MissionManager.DJIWaypointMission;
import dji.sdk.Products.DJIAircraft;
import dji.sdk.base.DJIBaseComponent;
import dji.sdk.base.DJIBaseProduct;
import dji.sdk.base.DJIError;



public class ServerActivity extends Activity {
    // katoin mallia              // AppCompatActivity
    // https://thinkandroid.wordpress.com/2010/03/27/incorporating-socket-programming-into-your-applications/

    private TextView serverStatus, text_in;

    // Define server IP
    public String server_ip = "";  // laita myöhemmin
    // Define port
    public static final int server_port = 8080;
    // connection status to the drone
    public boolean drone_con = false;

    public DJIBaseProduct mProduct = null;

    protected DJIFlightControllerDelegate.FlightControllerUpdateSystemStateCallback flightcontrollercallback = null;
    protected TextView mConnectStatusTextView;

//    public DJIFlightController mFlight;
    public DJIFlightControllerDataType.DJILocationCoordinate3D posit;
    public DJIWaypointMission mWay = new DJIWaypointMission();
    // handler
    private Handler handler = new Handler();
  //  private Handler mHandler = new Handler();
    private static final String TAG = "AppTwo";

    private ServerSocket serversocket; // creates a new serversocket
    private String msg1 = "null";
    private Map<String, String> Waymap = new HashMap<String, String>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
  //      mHandler = new Handler(Looper.getMainLooper());
        setContentView(R.layout.activity_server);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        //setSupportActionBar(toolbar);

        serverStatus = (TextView) findViewById(R.id.server_status);
        text_in = (TextView) findViewById(R.id.text_in);
        mConnectStatusTextView = (TextView) findViewById(R.id.ConnectStatusTextView);
        server_ip = getIP();


        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
        // Register the broadcast receiver for receiving the device connection's changes.
        IntentFilter filter = new IntentFilter();
        filter.addAction(Tutorial.FLAG_CONNECTION_CHANGE);
        registerReceiver(mReceiver, filter);



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
//                                drone_con = DJIDrone.connectToDrone();
                                // the BaseProduct instance
                                // null if no drone is present or SDKinstanceManager failed
                                mProduct = Tutorial.getProductInstance();
                                if (mProduct == null) {
                                    drone_con = false;
                                } else {
                                    drone_con = mProduct.isConnected();
                                }
                                if (drone_con) {
                                    serverStatus.append("\n" + "Connection to Drone: True");
                                } else {
                                    serverStatus.append("\n" + "Connection to drone: False");
                                }
                                Log.e(TAG, "Connection to the Drone = "+drone_con);

                            }
                        });
                        // wait fot a message fro ma client
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
                                    final String finalLine = line;
                                    handler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            // viestien ja komentojen edelleen lähetys
                                            // ja muut toiminnalisuudet
                                            //
                                            parser(finalLine, out);
                                            /*
                                            if (moveData.get(0).equals("move")) {
                                                // moves the drone in a direction
                                                sendflightdata(moveData, out);
                                            } else if (moveData.get(0).equals("takeoff")) {
                                                // drone turns the motors on and takes off
                                                //takeoff(out);
                                            } else if (moveData.get(0).equals("land")) {
                                                landing(out);
                                            } else if (moveData.get(0).equals("here")) {
                                                //waypoint(moveData , out);
                                            } else if (moveData.get(0).equals("pos")){
                                                position(out);
                                            } else {
                                                text_in.append("Client Sent: " + msg + "\n");
                                                //text_in.append("Sent To Client: " + msg_sent + "\n");
                                                //out.println(msg_sent);
                                            } */
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


    public String takeoff(/*final PrintWriter out*/){
        // drone takes off after server receives "takeoff" message
        try {
            //DJIAircraft Air = new DJIAircraft();
            DJIAircraft Air = Tutorial.getAircraftInstance();
            DJIFlightController mFlight = Air.getFlightController();
            if (mFlight == null){
                //out.println("FlightController null");
                return "Flightcontoller null";
            }
            mFlight.takeOff(new DJIBaseComponent.DJICompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    //Log.e(TAG, "Takeoff: " + djiError.getDescription());
                }
            });
            return "Drone taking off";
        } catch (Exception e) {
            return "Drone couldn't execute take off";
        }
    }

    private String landing(/*final PrintWriter out*/) {
        // automatic landing when receives the msg "land"
        try {
            DJIAircraft Air = Tutorial.getAircraftInstance();
            DJIFlightController mFlight = Air.getFlightController();
            if (mFlight == null){
                //out.println("FlightController null");
                return "Flightcontroller null";
            }
            mFlight.autoLanding(new DJIBaseComponent.DJICompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    //Log.e(TAG, "Landing: " + djiError.getDescription());
                    //out.println("Drone landing");

                }
            });
            return "Drone landing";
        } catch (Exception e) {
            e.printStackTrace();
            return "Drone had a problem landing";
            //out.println("Drone had a problem landing, try again");
        }
    }

    private String position(/*final PrintWriter out*/) {
        // gets the aircraft current position: latitude, longitude and altitude
        try {
            DJIAircraft Air = Tutorial.getAircraftInstance();
            DJIFlightController mFlight = Air.getFlightController();
            DJIFlightControllerDataType.DJIFlightControllerCurrentState state =  mFlight.getCurrentState();
            posit = state.getAircraftLocation();
            String lati = String.valueOf(posit.getLatitude());
            String longi = String.valueOf(posit.getLongitude());
            String alti = String.valueOf(posit.getAltitude());
            String msg = "Latitude:"+lati+" Longitude:"+longi+" Altitude:"+alti;
            //out.println(msg);
            return msg;
        } catch (Exception e){
            e.printStackTrace();
            //out.println("Error getting coordinates");
            return "Error getting coordinates";
        }
    }

    private void sendflightdata(final List<String> moveData, final PrintWriter out){
        try {
            DJIAircraft Air = Tutorial.getAircraftInstance();
            DJIFlightController mFlight = Air.getFlightController();
            if (mFlight == null){
                out.println("FlightController null");
            } else {
                mFlight.enableVirtualStickControlMode(new DJIBaseComponent.DJICompletionCallback() {
                    @Override
                    public void onResult(DJIError djiError) {
                        if (djiError == null) {
                            Log.e(TAG, "virtualstick mode enabled");
                        } else {
                            out.println(djiError.getDescription());
                        }
                    }
                });
                mFlight.setHorizontalCoordinateSystem(DJIFlightControllerDataType.DJIVirtualStickFlightCoordinateSystem.Ground); // or body
                mFlight.setVerticalControlMode(DJIFlightControllerDataType.DJIVirtualStickVerticalControlMode.Position); // or velocity
                mFlight.setRollPitchControlMode(DJIFlightControllerDataType.DJIVirtualStickRollPitchControlMode.Angle); // or velocity
                mFlight.setYawControlMode(DJIFlightControllerDataType.DJIVirtualStickYawControlMode.Angle); // or angularvelocity

                final float pitch = Float.parseFloat(moveData.get(1));
                final float roll = Float.parseFloat(moveData.get(2));
                final float yaw = Float.parseFloat(moveData.get(3));
                final float throttle = Float.parseFloat(moveData.get(4));

                DJIFlightControllerDataType.DJIVirtualStickFlightControlData fData = new DJIFlightControllerDataType.DJIVirtualStickFlightControlData(pitch, roll, yaw, throttle);
//                fData.setPitch(pitch);
//                fData.setRoll(roll);
//                fData.setYaw(yaw);

                mFlight.sendVirtualStickFlightControlData(fData, new DJIBaseComponent.DJICompletionCallback() {
                    @Override
                    public void onResult(DJIError djiError) {
                        if (djiError == null) {
                            out.println("Executing action");
                        } else {
                            out.println(djiError.getDescription());
                        }
                    }
                });
            }
        } catch (Exception e) {
            out.println("Problem executing action");
            e.printStackTrace();
        }
    }

    private String addwaypoint(final List<String> moveData){
        // Adds the waypoint coordinates to a dictionary for later use
        String wayp = moveData.get(1);
        String lat = moveData.get(2);
        String longi = moveData.get(3);
        Waymap.put(wayp,lat + " " + longi);
        return "Waypoint added";
    }


    private void waypoint(final List<String> moveData/*, final PrintWriter out*/){
        // moves the aircraft to the target location
        // (targets.add <waypointname> <lati> <longi>)
        Log.e(TAG, String.valueOf(moveData));
        String waydata = Waymap.get(moveData.get(1));
        List<String> coor_data = new ArrayList<String>(Arrays.asList(waydata.split(" ")));
        Log.e(TAG, String.valueOf(coor_data));
        final double lat = Double.parseDouble(coor_data.get(0));
        final double lon = Double.parseDouble(coor_data.get(1));

        //Log.e(TAG, String.valueOf(lat));
        DJIAircraft Air = Tutorial.getAircraftInstance();           // gets the aircraft instance
        final DJIMissionManager mMan1 = Air.getMissionManager();    // null if aircraft not connectecd
        try {
            DJIWaypoint wp1, wp2 = null;
            //wp2 = moveData.get(1);
            wp1 = new DJIWaypoint(lat, lon, (float) 10.0);
            wp2 = new DJIWaypoint(lat, lon, (float) 15.0);

            //DJIWaypointMission mWay = new DJIWaypointMission();
            mWay.addWaypoint(wp1);
            mWay.addWaypoint(wp2);
            DJIMission.DJIMissionProgressHandler handy = new DJIMission.DJIMissionProgressHandler() {
                @Override
                public void onProgress(DJIMission.DJIProgressType Upload, float v) {

                }
            };
            mMan1.prepareMission(mWay, handy, new DJIBaseComponent.DJICompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    //Log.e(TAG, djiError.getDescription());
                    mMan1.startMissionExecution(new DJIBaseComponent.DJICompletionCallback() {
                        @Override
                        public void onResult(DJIError djiError) {
                            //Log.e(TAG, djiError.getDescription());
                        }
                    });
                }
            });

            
        } catch (Exception e) {
            //out.println("Problem executing mission");
            e.printStackTrace();
        }
        //out.println("WaypointMission executed");
    }

    private String getssid(){
        DJIBaseProduct mProd = Tutorial.getProductInstance();
        DJIAirLink Alink = mProd.getAirLink();
        DJIWiFiLink Link = Alink.getWiFiLink();
        final String s = "asd";
        Link.getWiFiSSID(new DJIBaseComponent.DJICompletionCallbackWith<String>() {
            @Override
            public void onSuccess(String s) {
                Log.e(TAG, s);
            }

            @Override
            public void onFailure(DJIError djiError) {
                Log.e(TAG, djiError.getDescription());
            }
        });
        return s;
    }

    private void parser(final String line, final PrintWriter out){
        try {
            final List<String> comms = new ArrayList<String>(Arrays.asList(line.split(";")));

            for (int i = 0; i < comms.size(); i++) {
                String val = comms.get(i);

                //out.println(val);

                // splits the line by .
                List<String> vallist = new ArrayList<String>(Arrays.asList(val.split("\\.")));
                // splits the line by " " so we get the parametres
                List<String> vallist_com = new ArrayList<String>(Arrays.asList(val.split(" ")));

                if (vallist.get(0).equals("base")) {
                    msg1 = msg1 + "\n" + dobasestuff(vallist, vallist_com);
                } else if (vallist.get(0).equals("targets")) {
                    msg1 = msg1 + "\n" + dotargetstuff(vallist, vallist_com);
                } else if (vallist.get(0).equals("camera")) {
                    msg1 = msg1 + "\n" + docamerastuff(vallist, vallist_com);
                } else if (vallist.get(0).equals("sensors")) {
                    msg1 = msg1 + "\n" + dosensorstuff(vallist, vallist_com);
                } else if (vallist.get(0).equals("actuators")) {
                    msg1 = msg1 + "\n" + doactuatorstuff(vallist, vallist_com);
                } else if (vallist.get(0).equals("operator")) {
                    msg1 = msg1 + "\n" + doaoperatorstuff(vallist, vallist_com);
                } else if (vallist.get(0).equals("airlink")) {
                    msg1 = msg1 + "\n" + doairlinkstuff(vallist, vallist_com);
                } else if (vallist.get(0).equals("moveto") || vallist_com.get(0).equals("moveto")) {
                    msg1 = msg1 + "\n" + domovestuff(vallist, vallist_com);
                } else if (vallist.get(0).equals("battery")) {
                    msg1 = msg1 + "\n" + dobatterystuff(vallist, vallist_com);
                } else if (vallist.get(0).equals("scripts")) {
                    msg1 = msg1 + "\n" + doscriptsstuff(vallist, vallist_com);
                } else if (vallist.get(0).equals("pos")) {
                    msg1 = msg1 + "\n" + doposstuff(vallist, vallist_com);
                } else {
                    msg1 = msg1 + "\n" + "command not recognised";
                }

                try {
                    Thread.sleep(1000);
                } catch(InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (msg1.substring(0,4).equals("null")) {
                msg1 = msg1.substring(5);
                Log.e(TAG, msg1);
                out.println(msg1);
                msg1 = "null";
            } else {
                out.println(msg1);
                msg1 = "null";
            }
        } catch (Exception e) {
            out.println(e.toString());
        }
    }

    private String dobasestuff(List<String> vallist, List<String> vallist_com){
        if (vallist_com.get(0).equals("base.*")) {
            return "All commands regarding base";
        } else {
            return null;
        }

    }

    private String dotargetstuff(List<String> vallist, List<String> vallist_com) {
        //Log.e(TAG, String.valueOf(vallist_com));
        if (vallist_com.get(0).equals("targets.*")) {
            return "targets.add home <x y z >\ntargets.add <waypointname> <coords>\n targets.remove <waypointname>\ntargets.list\n";
        } else if (vallist_com.get(0).equals("targets.add")) {
            //waypoint(vallist_com);
            return addwaypoint(vallist_com);
        } else {
            return null;
        }
    }

    private String domovestuff(List<String> vallist, List<String> vallist_com) {
        if (vallist_com.get(0).equals("moveto.*")) {
            return "moveto <home>\nmoveto <x y z ...>\nmoveto@global <x y ..>\nmoveto@work <x y ..>\nmoveto.base <global / operator/...>\n";
        } else if (vallist_com.get(0).equals("moveto")) {
            waypoint(vallist_com);
            return "Moving";
        } else {
            return null;
        }
    }

    private String dosensorstuff(List<String> vallist, List<String> vallist_com) {
        if (vallist_com.get(0).equals("sensors.*")) {
            return "sensors.compass.c\nsensors.compass calibrate\nsensors.camera.<...>";
        } else {
            return null;
        }
    }

    private String docamerastuff(List<String> vallist, List<String> vallist_com) {
        if (vallist_com.get(0).equals("camera.*")) {
            return "camera.fps\ncamera.size <x> <y>\ncamera.mode <???>\ncamera.brightness\ncamera.pos\ncamera.base gimbal";
        } else {
            return null;
        }
    }

    private String doairlinkstuff(List<String> vallist, List<String> vallist_com) {
        if (vallist_com.get(0).equals("airlink.*")) {
            return "airlink.quality\nairlink.db\nairlink.password\nairlink.reboot\nairlink.ssid\n";
        } else if (vallist_com.get(0).equals("airlink.ssid")) {
            return getssid();
        } else {
            return null;
        }
    }

    private String dobatterystuff(List<String> vallist, List<String> vallist_com) {
        if (vallist_com.get(0).equals("battery.*")) {
            return "battery1.I\nbattery1.V\nbattery1.charge\nbattery1.charge\nbattery1.level\nbattery1.level\nbattery1.T\n";
        } else {
            return null;
        }
    }

    private String doscriptsstuff(List<String> vallist, List<String> vallist_com) {
        if (vallist_com.get(0).equals("scripts.*")) {
            return "scripts.<name> record\nscripts.<name> stop\nscripts.<name> run\nscripts.list\n";
        } else {
            return null;
        }
    }

    private String doactuatorstuff(List<String> vallist, List<String> vallist_com) {
        if (vallist_com.get(0).equals("actuators.*")) {
            return "actuators.gimbal.pos <x y z a b c> \n actuators.gimbal.pos.base robot \n actuators.gimbal.target.a / b \n actuators.gimbal.a_max / min \n actuators.gimbal.mode <FPS> / <free> / <unknown> / <yaw_follow> \n actuators.gimbal reset \n actuator.takeoff \n actuators.land";
        } else if (vallist_com.get(0).equals("actuators.takeoff")){
            return takeoff();
        } else if (vallist_com.get(0).equals("actuators.land")){
            return landing();
        } else {
            return null;
        }
    }

    private String doaoperatorstuff(List<String> vallist, List<String> vallist_com) {
        if (vallist_com.get(0).equals("operator.*")) {
            return "operator.pos\noperator.battery";
        } else {
            return null;
        }
    }
    private String doposstuff(List<String> vallist, List<String> vallist_com) {
        if (vallist_com.get(0).equals("pos.*")) {
            return "pos\npos.base\n";
        } else if (vallist_com.get(0).equals("pos")){
            return position();
        } else {
            return null;
        }
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


protected BroadcastReceiver mReceiver = new BroadcastReceiver() {

    @Override
    public void onReceive(Context context, Intent intent) {
        updateTitleBar();
//        onProductChange();
    }

};

    private void updateTitleBar() {
        if(mConnectStatusTextView == null) return;
        boolean ret = false;
        DJIBaseProduct product = Tutorial.getProductInstance();
        if (product != null) {

            if(product.isConnected()) {
                //The product is connected
                mConnectStatusTextView.setText(Tutorial.getProductInstance().getModel() + " Connected");
                ret = true;
            } else {

                if(product instanceof DJIAircraft) {
                    DJIAircraft aircraft = (DJIAircraft)product;
                    if(aircraft.getRemoteController() != null && aircraft.getRemoteController().isConnected()) {
                        // The product is not connected, but the remote controller is connected
                        mConnectStatusTextView.setText("only RC Connected");
                        ret = true;
                    }
                }
            }
        }

        if(!ret) {
            // The product or the remote controller are not connected.
            mConnectStatusTextView.setText("Disconnected");
        }
    }
}
