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
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.SortedSet;
import java.util.TreeMap;


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
    private double home_x,home_y;
    //private Map<Integer, String> ZONE_LETTERS = new HashMap<Integer, String>();

    private double K0 = 0.9996;
    private double E = 0.00669438;
    private double E2 = E * E;
    private double E3 = E2 * E;
    private double E_P2 = E / (1.0 - E);
    private double SQRT_E = Math.sqrt(1 - E);
    private double _E = (1 - SQRT_E) / (1 + SQRT_E);
    private double _E2 = _E * _E;
    private double _E3 = _E2 * _E;
    private double _E4 = _E3 * _E;
    private double _E5 = _E3 * _E;
    private double M1 = (1 - E / 4 - 3 * E2 / 64 - 5 * E3 / 256);
    private double M2 = (3 * E / 8 + 3 * E2 / 32 + 45 * E3 / 1024);
    private double M3 = (15 * E2 / 256 + 45 * E3 / 1024);
    private double M4 = (35 * E3 / 3072);
    private double P2 = (3. / 2 * _E - 27. / 32 * _E3 + 269. / 512 * _E5);
    private double P3 = (21. / 16 * _E2 - 55. / 32 * _E4);
    private double P4 = (151. / 96 * _E3 - 417. / 128 * _E5);
    private double P5 = (1097. / 512 * _E4);
    private double R1  = 6378137;



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
        public void run() {
            try {
                Socket socket = null;
                if (server_ip != null) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            serverStatus.setText("Listening at :" + server_ip);
                        }
                    });
                    serversocket = new ServerSocket(server_port);

                    while (!Thread.currentThread().isInterrupted()) {
                        try {
                            Socket client = serversocket.accept(); // checks for the client
                            CommunicationThread commsthread = new CommunicationThread(client);
                            new Thread(commsthread).start();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            } catch (IOException e){
                e.printStackTrace();
            }
        }
    }



    class CommunicationThread implements Runnable {
    // reference : https://examples.javacodegeeks.com/android/core/socket-core/android-socket-example/
    // (7.3.2016)

        private Socket clientSocket;
        private BufferedReader input;
        private PrintWriter out;
        public CommunicationThread(Socket clientSocket) {
            this.clientSocket = clientSocket;
            try {

                this.input = new BufferedReader(new InputStreamReader(this.clientSocket.getInputStream()));
                this.out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(this.clientSocket.getOutputStream())), true);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    String read = input.readLine();
                    //updateConversationHandler.post(new updateUIThread(read));
                    parser(read, out);
                } catch (IOException e) {
                    e.printStackTrace();
                }
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
        // gets the aircraft current position: latitude, longitude and altitude and transforms them to ETRS89 system
        try {
            DJIAircraft Air = Tutorial.getAircraftInstance();
            DJIFlightController mFlight = Air.getFlightController();
            DJIFlightControllerDataType.DJIFlightControllerCurrentState state =  mFlight.getCurrentState();
            posit = state.getAircraftLocation();
            //String lati = String.valueOf(posit.getLatitude());
            //String longi = String.valueOf(posit.getLongitude());
            String alti = String.valueOf(posit.getAltitude());
            String coords = from_latlon(posit.getLatitude(), posit.getLongitude());

            //String msg = "Latitude:"+lati+" Longitude:"+longi+" Altitude:"+alti;
            //out.println(msg);
            return coords+" Altitude: "+alti;
        } catch (Exception e){
            e.printStackTrace();
            //out.println("Error getting coordinates");
            return "Error getting coordinates";
        }
    }

    private String sendflightdata(final List<String> moveData/*, final PrintWriter out*/){
        /*manual drone piloting, doesn't work  */
        try {
            DJIAircraft Air = Tutorial.getAircraftInstance();
            DJIFlightController mFlight = Air.getFlightController();
            if (mFlight == null){
                return "FlightController null";
            } else {
                mFlight.enableVirtualStickControlMode(new DJIBaseComponent.DJICompletionCallback() {
                    @Override
                    public void onResult(DJIError djiError) {
                        if (djiError == null) {
                            Log.e(TAG, "virtualstick mode enabled");
                        } else {
                            Log.e(TAG, "problem enabling virtualstick mode");
                            //out.println(djiError.getDescription());
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
                            Log.e(TAG, "Executing action!!!");
                            //out.println("Executing action");
                        } else {
                            Log.e(TAG, djiError.getDescription());
                        }
                    }
                });
            return "Executing action";
            }
        } catch (Exception e) {
            Log.e(TAG,"Problem executing action");
            e.printStackTrace();
            return "Problem executing action";
        }
    }

    private String addwaypoint(final List<String> moveData){
        // Adds the waypoint coordinates to a hashmap for later use
        String wayp = moveData.get(1);
        String lat = moveData.get(2);
        String longi = moveData.get(3);
        String alti = moveData.get(4);
        Waymap.put(wayp,lat + " " + longi + " "+ alti);
        return "Waypoint added";
    }


    private void waypoint(final List<String> moveData/*, final PrintWriter out*/){
        // moves the aircraft to the target location
        // (targets.add <waypointname> <lati> <longi> <altitude>)
        String home_coords = position();
        List<String> coords_2 = new ArrayList<String>(Arrays.asList(home_coords.split(" ")));

        Log.e(TAG, String.valueOf(moveData));
        String waydata = Waymap.get(moveData.get(1));
        List<String> coor_data = new ArrayList<String>(Arrays.asList(waydata.split(" ")));

        Log.e(TAG, String.valueOf(coor_data));
        final double lat = Double.parseDouble(coor_data.get(0)) + Double.parseDouble(coords_2.get(0));
        final double lon = Double.parseDouble(coor_data.get(1)) + Double.parseDouble(coords_2.get(1));

        String coord_glb = to_latlon(lat, lon, 35, "W", null);
        List<String> coor_local = new ArrayList<String>(Arrays.asList(coord_glb.split(" ")));

        final double lat_glb = Double.parseDouble(coor_local.get(0));
        final double lon_glb = Double.parseDouble(coor_local.get(1));

        float final_alti = Float.parseFloat(coor_data.get(2));
        float first_alti = final_alti - 2;
        if (final_alti - first_alti <= 2){
            first_alti = final_alti + 2;
        }

        //Log.e(TAG, String.valueOf(lat));
        DJIAircraft Air = Tutorial.getAircraftInstance();           // gets the aircraft instance
        final DJIMissionManager mMan1 = Air.getMissionManager();    // null if aircraft not connectecd
        try {
            DJIWaypoint wp1, wp2 = null;
            //wp2 = moveData.get(1);
            wp1 = new DJIWaypoint(lat_glb, lon_glb, first_alti);
            wp2 = new DJIWaypoint(lat_glb, lon_glb, final_alti);

            DJIWaypointMission mWay = new DJIWaypointMission();
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
        /*Parses the incoming message and executes the necessary actions*/
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
                    Thread.sleep(1000); // 1500
                } catch(InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            //msg1 = msg1 +"\n" + from_latlon(64.86741742, 25.02979013); //
            //msg1 = msg1 + "\n"+ to_latlon(406643.0001527777, 7195132.001421091, 35, "W", null);
            if (msg1.substring(0,4).equals("null")) {                   //65.0123600,  25.4681600
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
        if (vallist_com.get(0).equals("base.ls")) {
            return "All commands regarding base";
        } else {
            return null;
        }
    }

    private String dotargetstuff(List<String> vallist, List<String> vallist_com) {
        //Log.e(TAG, String.valueOf(vallist_com));
        if (vallist_com.get(0).equals("targets.ls")) {
            return "targets.add home <x y z >\ntargets.add <waypointname> <coords>\n targets.remove <waypointname>\ntargets.list\n";
        } else if (vallist_com.get(0).equals("targets.add")) {
            //waypoint(vallist_com);
            return addwaypoint(vallist_com);
        } else {
            return null;
        }
    }

    private String domovestuff(List<String> vallist, List<String> vallist_com) {
        if (vallist_com.get(0).equals("moveto.ls")) {
            return "moveto <home>\nmoveto <x y z ...>\nmoveto@global <x y ..>\nmoveto@work <x y ..>\nmoveto.base <global / operator/...>\n";
        } else if (vallist_com.get(0).equals("moveto")) {
            waypoint(vallist_com);
            return "Moving";
        } else {
            return null;
        }
    }

    private String dosensorstuff(List<String> vallist, List<String> vallist_com) {
        if (vallist_com.get(0).equals("sensors.ls")) {
            return "sensors.compass.c\nsensors.compass calibrate\nsensors.camera.<...>";
        } else {
            return null;
        }
    }

    private String docamerastuff(List<String> vallist, List<String> vallist_com) {
        if (vallist_com.get(0).equals("camera.ls")) {
            return "camera.fps\ncamera.size <x> <y>\ncamera.mode <???>\ncamera.brightness\ncamera.pos\ncamera.base gimbal";
        } else {
            return null;
        }
    }

    private String doairlinkstuff(List<String> vallist, List<String> vallist_com) {
        if (vallist_com.get(0).equals("airlink.ls")) {
            return "airlink.quality\nairlink.db\nairlink.password\nairlink.reboot\nairlink.ssid\n";
        } else if (vallist_com.get(0).equals("airlink.ssid")) {
            return getssid();
        } else {
            return null;
        }
    }

    private String dobatterystuff(List<String> vallist, List<String> vallist_com) {
        if (vallist_com.get(0).equals("battery.ls")) {
            return "battery1.I\nbattery1.V\nbattery1.charge\nbattery1.charge\nbattery1.level\nbattery1.level\nbattery1.T\n";
        } else {
            return null;
        }
    }

    private String doscriptsstuff(List<String> vallist, List<String> vallist_com) {
        if (vallist_com.get(0).equals("scripts.ls")) {
            return "scripts.<name> record\nscripts.<name> stop\nscripts.<name> run\nscripts.list\n";
        } else {
            return null;
        }
    }

    private String doactuatorstuff(List<String> vallist, List<String> vallist_com) {
        if (vallist_com.get(0).equals("actuators.ls")) {
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
        if (vallist_com.get(0).equals("operator.ls")) {
            return "operator.pos\noperator.battery";
        } else {
            return null;
        }
    }
    private String doposstuff(List<String> vallist, List<String> vallist_com) {
        if (vallist_com.get(0).equals("pos.ls")) {
            return "pos\npos.base\n";
        } else if (vallist_com.get(0).equals("pos")){
            return position();
        } else {
            return null;
        }
    }

    private String to_latlon(double easting,double northing,int zone_number, String zone_letter,String northern) {
        /*Converts the coordinates from ETRS89 to GPS coordinates*/
        if ((zone_letter.equals("None")) && !(northern.equals("None")))
            return "either zone_letter or northern needs to be set";
        else if (zone_letter.equals("None") && !(northern == "None"))
            return "set either zone_letter or northern, but not both";
        if ((!(100000 <= easting) && !(easting < 1000000)))
            return "easting out of range (must be between 100.000 m and 999.999 m)";
        if (!(0 <= northing) && !(northing <= 10000000))
            return "northing out of range (must be between 0 m and 10.000.000 m)";
        if (!(1 <= zone_number) && !(zone_number <= 60))
            return "zone number out of range (must be between 1 and 60)";
        //double x = 0,y = 0;
        if (!zone_letter.equals("None")) {
            zone_letter = zone_letter.toUpperCase();
            if (!("CDEFGHIJKLMNOPQRSTUVWX".contains(zone_letter)) || ("IO".contains(zone_letter)))
                return "zone letter out of range (must be between C and X)";
            //northern = (zone_letter >= 'N');
        }
        double x = easting - 500000;
        double y = northing;
        if ((northern == "null"))
            y -= 10000000;
        double m = y / K0;
        double mu = m / (R1 * M1);
        double p_rad = (mu + P2 * Math.sin(2 * mu) + P3 * Math.sin(4 * mu) + P4 * Math.sin(6 * mu) + P5 * Math.sin(8 * mu));
        double p_sin = Math.sin(p_rad);
        double p_sin2 = p_sin * p_sin;
        double p_cos = Math.cos(p_rad);
        double p_tan = p_sin / p_cos;
        double p_tan2 = p_tan * p_tan;
        double p_tan4 = p_tan2 * p_tan2;
        double ep_sin = 1 - E * p_sin2;
        double ep_sin_sqrt = Math.sqrt(1 - E * p_sin2);
        double n = R1 / ep_sin_sqrt;
        double r = (1 - E) / ep_sin;
        double c = _E * Math.pow(p_cos,2);
        double c2 = c * c;
        double d = x / (n * K0);
        double d2 = d * d;
        double d3 = d2 * d;
        double d4 = d3 * d;
        double d5 = d4 * d;
        double d6 = d5 * d;
        double latitude = (p_rad - (p_tan / r) * (d2 / 2 - d4 / 24 * (5 + 3 * p_tan2 + 10 * c - 4 * c2 - 9 * E_P2)) + d6 / 720 * (61 + 90 * p_tan2 + 298 * c + 45 * p_tan4 - 252 * E_P2 - 3 * c2));
        double longitude = (d - d3 / 6 * (1 + 2 * p_tan2 + c) + d5 / 120 * (5 - 2 * c + 28 * p_tan2 - 3 * c2 + 8 * E_P2 + 24 * p_tan4)) / p_cos;
        int laz = zone_number_to_central_longitude(zone_number);
        longitude = Math.toDegrees(longitude) + laz;
        String lati = String.valueOf(Math.toDegrees(latitude));
        String longi = String.valueOf(longitude);
        //lati = lati.substring(0,10);
        //longi = longi.substring(0,10);
        return lati+" "+longi; // "" + zone_number
    }

    private String from_latlon(double latitude,double longitude){
        /*Converts GPS coordinates to ETRS89 coordinates*/
        String string = "";
        if (!(-80.0 <= latitude) && !(latitude <= 84.0))
        {
            return "latitude out of range (must be between 80 deg S and 84 deg N)";
        }
        else if (!(-180.0 <= longitude) && !(longitude <= 180.0))
            return "northing out of range (must be between 180 deg W and 180 deg E)";
        else {
            double lat_rad = Math.toRadians(latitude);
            double lat_sin = Math.sin(lat_rad);
            double lat_cos = Math.cos(lat_rad);
            double lat_tan = lat_sin / lat_cos;
            double lat_tan2 = lat_tan * lat_tan;
            double lat_tan4 = lat_tan2 * lat_tan2;
            double lon_rad = Math.toRadians(longitude);
            int zone_number = latlon_to_zone_number(latitude, longitude);
            int central_lon = zone_number_to_central_longitude(zone_number);
            double central_lon_rad = Math.toRadians(central_lon);
            String zone_letter = latitude_to_zone_letter(latitude);
            double n = R1 / Math.sqrt(1 - E * Math.pow(lat_sin, 2));
            double c = E_P2 * Math.pow(lat_cos, 2);
            double a = lat_cos * (lon_rad - central_lon_rad);
            double a2 = a * a;
            double a3 = a2 * a;
            double a4 = a3 * a;
            double a5 = a4 * a;
            double a6 = a5 * a;
            double m = R1 * (M1 * lat_rad - M2 * Math.sin(2 * lat_rad) + M3 * Math.sin(4 * lat_rad) - M4 * Math.sin(6 * lat_rad));
            double easting = K0 * n * (a + a3 / 6 * (1 - lat_tan2 + c) + a5 / 120 * (5 - 18 * lat_tan2 + lat_tan4 + 72 * c - 58 * E_P2)) + 500000;
            double northing = K0 * (m + n * lat_tan * (a2 / 2 + a4 / 24 * (5 - lat_tan2 + 9 * c + 4 * Math.pow(c, 2)) + a6 / 720 * (61 - 58 * lat_tan2 + lat_tan4 + 600 * c - 330 * E_P2)));
            if (latitude < 0)
                northing += 10000000;
            easting = Math.floor(easting * 1e3) / 1e3;
            northing = Math.floor(northing * 1e3) / 1e3;
            string = String.valueOf(easting) + " " + String.valueOf(northing) + " " + String.valueOf(zone_number) + " " + String.valueOf(zone_letter);
            return string;
        }

    }
    private int latlon_to_zone_number(double latitude, double longitude) {
        /*get the ETRS zone number*/
        if (((56 <= latitude) && (latitude <= 64)) && ((3 <= longitude) && (longitude <= 12)))
            return 32;
        if ((72 <= latitude) && (latitude <= 84) && longitude >= 0) {
            if (longitude <= 9)
                return 31;
            else if (longitude <= 21)
                return 33;
            else if (longitude <= 33)
                return 35;
            else if (longitude <= 42)
                return 37;

        }
        return (int) (((longitude + 180) / 6) + 1);
    }

    private int zone_number_to_central_longitude(int zone_number){
        return (zone_number - 1) * 6 - 180 + 3;
    }

    public static final Map<Integer, String> ZONE_LETTERS = new HashMap<Integer, String>() {{
        put(84, "None");
        put(72, "X");
        put(64, "W");
        put(56, "V");
        put(48, "U");
        put(40, "T");
        put(32, "S");
        put(24, "R");
        put(16, "Q");
        put(8, "P");
        put(0, "N");
        put(-8, "M");
        put(-16, "L");
        put(-24, "K");
        put(-32, "J");
        put(-40, "H");
        put(-48, "G");
        put(-56, "F");
        put(-64, "E");
        put(-72, "D");
        put(-80, "C");
    }};

    private String latitude_to_zone_letter(double latitude_1) {
        /*latitude to zone letter*/
        int latitude = (int) latitude_1;
        Map<Integer, String> zoneLetters = ZONE_LETTERS;
        NavigableMap<Integer, String> lat_mins = new TreeMap<Integer, String>(zoneLetters);

        for (int key: lat_mins.descendingKeySet()) {
            if(latitude >= key) {
                return zoneLetters.get(key);
            } else {
                Log.e(TAG, zoneLetters.get(key));
            }
        }
        return "None";
    }
/*
        for lat_min, zone_letter in ZONE_LETTER {
            if latitude >= lat_min:
            return zone_letter;
        }
        return None
    } */
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
