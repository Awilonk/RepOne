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
import android.os.ParcelFileDescriptor;
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
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;



import dji.sdk.FlightController.DJIFlightController;
import dji.sdk.FlightController.DJIFlightControllerDataType;
import dji.sdk.MissionManager.DJIMission;
import dji.sdk.MissionManager.DJIMissionManager;
import dji.sdk.MissionManager.DJIWaypoint;
import dji.sdk.MissionManager.DJIWaypointMission;
import dji.sdk.Products.DJIAircraft;
import dji.sdk.base.DJIBaseComponent;
import dji.sdk.base.DJIBaseProduct;
import dji.sdk.base.DJIError;



public class ServerActivity extends Activity {
    // For reference              // AppCompatActivity
    // https://thinkandroid.wordpress.com/2010/03/27/incorporating-socket-programming-into-your-applications/

    private TextView serverStatus, text_in;

    // Define server IP
    public String server_ip = "";  // laita myöhemmin
    // Define port
    public static final int server_port = 8080;
    // connection status to the drone

    protected TextView mConnectStatusTextView;

    public DJIFlightControllerDataType.DJILocationCoordinate3D posit;
    public DJIWaypointMission mWay = new DJIWaypointMission();
    // handler
    private Handler handler = new Handler();
    private static final String TAG = "AppTwo";

    private ServerSocket serversocket; // a new serversocket
    private String msg1 = "null";
    private Map<String, String> Waymap = new HashMap<String, String>();

    // target coordinates for a waypoin mission
    private volatile double target_x = 0,target_y = 0;

    // list of clients
    private ArrayList<CommunicationThread> clients;
    // if a waypoin mission is running
    volatile boolean flag_running = false;

    // command buffer
    volatile ConcurrentLinkedQueue<String> que = new ConcurrentLinkedQueue();

    // msg buffer
    volatile ConcurrentLinkedQueue<String> que_com = new ConcurrentLinkedQueue();

    // For the coordinate conversion
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

        Thread thread_buffer = new Thread(new BufferThread(que, que_com/*, flag_pos*/));
        thread_buffer.start();

        Thread thread_process = new Thread(new processLine(que, que_com));
        thread_process.start();

    }


    public class serverthread implements Runnable {
        public void run() {
            try {
                clients = new ArrayList<CommunicationThread>();
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
                            final Socket client = serversocket.accept();
                            CommunicationThread commsthread = new CommunicationThread(client, que, que_com);
                            clients.add(commsthread);
                            new Thread(commsthread).start();
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    text_in.append("Connected to: " + client.getInetAddress() +"\n");
                                }
                            });
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


    class processLine implements Runnable {
        /*Processes the next command in the queue */
        ConcurrentLinkedQueue<String> que;
        ConcurrentLinkedQueue<String> que_com;
        public processLine(ConcurrentLinkedQueue<String> que,ConcurrentLinkedQueue<String> que_com) {
            this.que = que;
            this.que_com = que_com;
        }
        @Override
        public void run() {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    if (flag_running) {
                        checkManualCoor(target_x, target_y);
                    } else {
                        String s = null;
                        parserReal(s,0);
                    }
                }
            } catch (Exception e) {
                sendall(e.getMessage());
            }
        }



    }


    class BufferThread implements Runnable{
        /*Checks the message queue, if queue isn't empty, sends the message from queue to all clients*/
        ConcurrentLinkedQueue<String> que;
        ConcurrentLinkedQueue<String> que_com;

        public BufferThread(ConcurrentLinkedQueue<String> que, ConcurrentLinkedQueue<String> que_com) {
            this.que = que;
            this.que_com = que_com;
        }
        @Override
        public void run() {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    final String s;
                    if (!(que_com.isEmpty())) {
                        String r = "";
                        r = que_com.poll();
                        sendall(r);
                    }
                }
            } catch (Exception e) {
                sendall(e.getMessage());
            }

        }
    }

    class CommunicationThread implements Runnable {
        /*Thread to communicate and handle the clients connected to the app*/
    // reference : https://examples.javacodegeeks.com/android/core/socket-core/android-socket-example/
    // (7.3.2016)
        private boolean connected = true;
        private Socket clientSocket;
        private BufferedReader input;
        private PrintWriter out;
        ConcurrentLinkedQueue<String> que;
        ConcurrentLinkedQueue<String> que_com;
        public CommunicationThread(Socket clientSocket,ConcurrentLinkedQueue<String> que, ConcurrentLinkedQueue<String> que_com) {
            this.clientSocket = clientSocket;
            this.que = que;
            this.que_com = que_com;
            try {
                this.input = new BufferedReader(new InputStreamReader(this.clientSocket.getInputStream()));
                this.out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(this.clientSocket.getOutputStream())), true);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        public void write(String line) {
            try{
                out.println(line);
            }
            catch(Exception e){
                e.printStackTrace();
            }
        }

        public void run() {
            try {
                while (connected) {
                    try {
                        String read = input.readLine();
                        if (read.equals("quit")) {
                            connected = false;
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    text_in.append("Client disconnected: " + clientSocket.getInetAddress() + "\n");
                                }
                            });
                        } else {
                            combuffer(read, out);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        connected = false;
                        sendall(e.getMessage());
                    }
                }
            } finally {
                try {
                    input.close();
                    out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    sendall(e.getMessage());
                }
            }
        }
    }

    public void sendall(String msg_all){
        // Sends message to all clients
        for(CommunicationThread client : clients)
            client.write(msg_all);
    }

    public void parserReal(final String line_old, int priority){
        /*Parses the incoming message and executes the necessary actions*/
        try {
            if (que.isEmpty() && flag_running && priority == 0) { // ||
                return ;
            } else {
                List<String> comms = new ArrayList<>();
                if (priority == 0) {
                    String line = que.poll();
                    comms = Arrays.asList(line.split(";"));
                } else {
                    comms = Arrays.asList(line_old.split(";"));
                }

                for (int i = 0; i < comms.size(); i++) {
                    String val = comms.get(i);

                    //out.println(val);

                    if (flag_running && priority == 0) { // if a waypoint mission is running waits until coordinates have been reached
                        checkManualCoor(target_x,target_y);
                        i--;
                        continue;
                    }

                    // splits the line by .
                    List<String> vallist = new ArrayList<String>(Arrays.asList(val.split("\\.")));
                    // splits the line by " " so we get the parametres
                    List<String> vallist_com = new ArrayList<String>(Arrays.asList(val.split(" ")));

                    if (vallist.get(0).equals("base")) {
                        msg1 = msg1 + "\n" + doBasestuff(vallist, vallist_com);
                    } else if (vallist.get(0).equals("targets")) {
                        msg1 = msg1 + "\n" + doTargetstuff(vallist, vallist_com);
                    } else if (vallist.get(0).equals("camera")) {
                        msg1 = msg1 + "\n" + doCamerastuff(vallist, vallist_com);
                    } else if (vallist.get(0).equals("sensors")) {
                        msg1 = msg1 + "\n" + doSensorstuff(vallist, vallist_com);
                    } else if (vallist.get(0).equals("actuators")) {
                        msg1 = msg1 + "\n" + doActuatorstuff(vallist, vallist_com);
                    } else if (vallist.get(0).equals("operator")) {
                        msg1 = msg1 + "\n" + doOperatorstuff(vallist, vallist_com);
                    } else if (vallist.get(0).equals("airlink")) {
                        msg1 = msg1 + "\n" + doAirlinkstuff(vallist, vallist_com);
                    } else if (vallist.get(0).equals("moveto") || vallist_com.get(0).equals("moveto")) {
                        flag_running = true; // true
                        msg1 = msg1 + "\n" + doMovestuff(vallist, vallist_com);
                    } else if (vallist.get(0).equals("battery")) {
                        msg1 = msg1 + "\n" + doBatterystuff(vallist, vallist_com);
                    } else if (vallist.get(0).equals("scripts")) {
                        msg1 = msg1 + "\n" + doScriptsstuff(vallist, vallist_com);
                    } else if (vallist.get(0).equals("pos")) {
                        msg1 = msg1 + "\n" + doPosstuff(vallist, vallist_com);
                    } else {
                        msg1 = msg1 + "\n" + "command not recognised";
                    }
                    try {
                        Thread.sleep(1500); // 1000
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                //msg1 = msg1 +"\n" + from_latlon(64.86741742, 25.02979013); //
                //msg1 = msg1 + "\n"+ to_latlon(406643.0001527777, 7195132.001421091, 35, "W", null);
                if (msg1.substring(0, 4).equals("null")) {                   //65.0123600,  25.4681600
                    msg1 = msg1.substring(5);
                    Log.e(TAG, msg1);
                    que_com.add(msg1);
//                String r = "";
//                r = que_com.poll();
//                sendall(r);
                    //out.println(msg1);
                    msg1 = "null";
                } else {
                    //out.println(msg1);
                    que_com.add(msg1);
//                String r = "";
//                r = que_com.poll();
//                sendall(r);
                    msg1 = "null";
                }
            }
        } catch (Exception e) {
            //out.println(e.toString());
        }
    }

    private void checkManualCoor(double lat_tar,double lon_tar){
            /*When the copter is moving to a waypoint checks if waypoint has been reached
            * if the waypoint has been reached starts process the next command from the buffer*/
        String current_pos = position();
        List<String> coords = new ArrayList<String>(Arrays.asList(current_pos.split(" ")));
        //sendall(String.valueOf(coords));
        double current_lat = Double.parseDouble(coords.get(0));
        double current_lon = Double.parseDouble(coords.get(1));

        double latitude_diff = Math.abs(lat_tar - current_lat);
        double longitude_diff = Math.abs(lon_tar - current_lon);
        //sendall(String.valueOf(latitude_diff) +" "+ String.valueOf(longitude_diff));
        if ((latitude_diff < 1.0) && (longitude_diff < 1.0)) {
            flag_running = false;
            sendall("Waypoint reached");
            target_x = 0;
            target_y = 0;
            try {
                Thread.sleep(5000); // 1000
            } catch(InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } else {
            try {
                Thread.sleep(3000); // 1000
            } catch(InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void combuffer(String line, PrintWriter out) {
        // Checks if the message is a priority message, for example "pos"
        if (priority(line)) {
            //String s = position();
            parserReal(line,1);
            //sendall(s);
        } else {
            que.add(line);
            Log.e(TAG, line);
        }
    }

    public static boolean priority(String str)
            // the priority messages such as position
    {
        String[] words = {"pos", "listen", "jotakinmuuta"};
        return (Arrays.asList(words).contains(str));
    }


    public String getIP() {
        //Gets the device wifi address
        //http://stackoverflow.com/questions/6064510/how-to-get-ip-address-of-the-device
        WifiManager wifiman = (WifiManager) getSystemService(WIFI_SERVICE);
        WifiInfo wifi_inf = wifiman.getConnectionInfo();
        int ipaddr = wifi_inf.getIpAddress();
        String server_ip = String.format("%d.%d.%d.%d", (ipaddr & 0xff),(ipaddr >> 8 & 0xff),(ipaddr >> 16 & 0xff),(ipaddr >> 24 & 0xff));
        return server_ip;
    }


    public String takeoff(){
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

    private String landing() {
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

    private String position() {
        // gets the aircraft current position: latitude, longitude and altitude and transforms them to ETRS89 system
        try {
            DJIAircraft Air = Tutorial.getAircraftInstance();
            DJIFlightController mFlight = Air.getFlightController();
            DJIFlightControllerDataType.DJIFlightControllerCurrentState state =  mFlight.getCurrentState();
            posit = state.getAircraftLocation();
            String alti = String.valueOf(posit.getAltitude());
            String coords = from_latlon(posit.getLatitude(), posit.getLongitude());
            return coords+" Altitude: "+alti;
        } catch (Exception e){
            e.printStackTrace();
            return "Error getting coordinates";
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
        try {
            String home_coords = position();
            List<String> coords_2 = new ArrayList<String>(Arrays.asList(home_coords.split(" ")));

            Log.e(TAG, String.valueOf(moveData));
            Log.e(TAG, String.valueOf(coords_2));
            String waydata = Waymap.get(moveData.get(1));
            List<String> coor_data = new ArrayList<String>(Arrays.asList(waydata.split(" ")));

            Log.e(TAG, String.valueOf(coor_data));
            final double lat = Double.parseDouble(coor_data.get(0)) + Double.parseDouble(coords_2.get(0));
            final double lon = Double.parseDouble(coor_data.get(1)) + Double.parseDouble(coords_2.get(1));

            target_x = lat; // saves target coordinates
            target_y = lon; // for later use

            String zone = coords_2.get(2);
            //sendall(String.valueOf(coords_2.get(3).length()));
            int zone_num = Integer.parseInt(zone);
            //String coord_glb = to_latlon(lat, lon, 35, "W", null);
            String coord_glb = to_latlon(lat, lon, zone_num, coords_2.get(3), null);
            //sendall(coord_glb);
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

            DJIWaypoint wp1, wp2 = null;
            //wp2 = moveData.get(1);
            wp1 = new DJIWaypoint(lat_glb, lon_glb, first_alti);
            wp2 = new DJIWaypoint(lat_glb, lon_glb, final_alti);

            mWay.removeAllWaypoints();
            //final DJIWaypointMission mWay = new DJIWaypointMission();
            mWay.addWaypoint(wp1);
            mWay.addWaypoint(wp2);
            DJIBaseComponent.DJICompletionCallback mCall = mWay.mInternalCallback;

            final DJIMission.DJIMissionProgressHandler handy = new DJIMission.DJIMissionProgressHandler() {
                @Override
                public void onProgress(DJIMission.DJIProgressType Upload, float v) {

                }
            };
            mMan1.prepareMission(mWay, handy, new DJIBaseComponent.DJICompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    mMan1.startMissionExecution(new DJIBaseComponent.DJICompletionCallback() {
                        @Override
                        public void onResult(DJIError djiError) {
                        }
                    });
                }
            });
            mMan1.setMissionExecutionFinishedCallback(mCall);
        } catch (Exception e) {
            sendall("Problem initiating mission");
            sendall(e.getMessage());
        }
    }


    private String doBasestuff(List<String> vallist, List<String> vallist_com){
        if (vallist_com.get(0).equals("base.ls")) {
            return "All commands regarding base";
        } else {
            return null;
        }
    }

    private String doTargetstuff(List<String> vallist, List<String> vallist_com) {
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

    private String doMovestuff(List<String> vallist, List<String> vallist_com) {
        if (vallist_com.get(0).equals("moveto.ls")) {
            return "moveto <home>\nmoveto <x y z ...>\nmoveto@global <x y ..>\nmoveto@work <x y ..>\nmoveto.base <global / operator/...>\n";
        } else if (vallist_com.get(0).equals("moveto")) {
            waypoint(vallist_com);
            //check_pos(vallist_com);
            return "Moving to waypoint";
        } else {
            return null;
        }
    }

    private String doSensorstuff(List<String> vallist, List<String> vallist_com) {
        if (vallist_com.get(0).equals("sensors.ls")) {
            return "sensors.compass.c\nsensors.compass calibrate\nsensors.camera.<...>";
        } else {
            return null;
        }
    }

    private String doCamerastuff(List<String> vallist, List<String> vallist_com) {
        if (vallist_com.get(0).equals("camera.ls")) {
            return "camera.fps\ncamera.size <x> <y>\ncamera.mode <???>\ncamera.brightness\ncamera.pos\ncamera.base gimbal";
        } else {
            return null;
        }
    }

    private String doAirlinkstuff(List<String> vallist, List<String> vallist_com) {
        if (vallist_com.get(0).equals("airlink.ls")) {
            return "airlink.quality\nairlink.db\nairlink.password\nairlink.reboot\nairlink.ssid\n";
        } else {
            return null;
        }
    }

    private String doBatterystuff(List<String> vallist, List<String> vallist_com) {
        if (vallist_com.get(0).equals("battery.ls")) {
            return "battery1.I\nbattery1.V\nbattery1.charge\nbattery1.charge\nbattery1.level\nbattery1.level\nbattery1.T\n";
        } else {
            return null;
        }
    }

    private String doScriptsstuff(List<String> vallist, List<String> vallist_com) {
        if (vallist_com.get(0).equals("scripts.ls")) {
            return "scripts.<name> record\nscripts.<name> stop\nscripts.<name> run\nscripts.list\n";
        } else {
            return null;
        }
    }

    private String doActuatorstuff(List<String> vallist, List<String> vallist_com) {
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

    private String doOperatorstuff(List<String> vallist, List<String> vallist_com) {
        if (vallist_com.get(0).equals("operator.ls")) {
            return "operator.pos\noperator.battery";
        } else {
            return null;
        }
    }
    private String doPosstuff(List<String> vallist, List<String> vallist_com) {
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
