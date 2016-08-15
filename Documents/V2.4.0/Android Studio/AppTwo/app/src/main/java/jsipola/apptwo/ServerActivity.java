
package jsipola.apptwo;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.SurfaceTexture;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.os.Environment;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;


import dji.sdk.Battery.DJIBattery;
import dji.sdk.Camera.DJICamera;
import dji.sdk.Camera.DJICameraSettingsDef;
import dji.sdk.Camera.DJIMedia;
import dji.sdk.Camera.DJIMediaManager;
import dji.sdk.Codec.DJICodecManager;
import dji.sdk.FlightController.DJIFlightController;
import dji.sdk.FlightController.DJIFlightControllerDataType;
import dji.sdk.FlightController.DJILandingGear;
import dji.sdk.MissionManager.DJIFollowMeMission;
import dji.sdk.MissionManager.DJIMission;
import dji.sdk.MissionManager.DJIMissionManager;
import dji.sdk.MissionManager.DJIWaypoint;
import dji.sdk.MissionManager.DJIWaypointMission;
import dji.sdk.Products.DJIAircraft;
import dji.sdk.RemoteController.DJIRemoteController;
import dji.sdk.base.DJIBaseComponent;
import dji.sdk.base.DJIBaseProduct;
import dji.sdk.base.DJIError;
import dji.sdk.util.DJILocationCoordinate2D;


public class ServerActivity extends Activity implements TextureView.SurfaceTextureListener, DJIBaseComponent.DJICompletionCallback {
    // For reference              // AppCompatActivity
    // https://thinkandroid.wordpress.com/2010/03/27/incorporating-socket-programming-into-your-applications/

    private TextView serverStatus, text_in, flightstatus;

    // Define server IP
    private String server_ip = "";  // laita myöhemmin
    // Define port
    private static final int server_port = 8080;
    // connection status to the drone

    protected TextView mConnectStatusTextView;

    protected DJICamera.CameraReceivedVideoDataCallback mReceivedVideoDataCallBack = null;
    protected DJICamera.CameraGeneratedNewMediaFileCallback mGeneratedPhotoCallBack = null;

    public DJIFlightControllerDataType.DJILocationCoordinate3D posit;
    public DJIFlightControllerDataType.DJILocationCoordinate2D home;
    public DJIWaypointMission mWay = new DJIWaypointMission(); // Waypoint mission
    DJIFollowMeMission follow; // follow me mission

    // handler
    private Handler handler = new Handler();
    protected static final String TAG = "AppTwo";

    private ServerSocket serversocket;
    private String msg1 = "null";
    private Map<String, String> Waymap = new HashMap<String, String>();
    private Map<String, String> ScriptMap = new HashMap<>();

    private int percents;
    // target coordinates for a waypoin mission
    private volatile double target_x = 0,target_y = 0;

    Thread followThread;
    Thread recThread;

    // list of clients
    protected ArrayList<CommunicationThread> clients;
    // if a waypoint mission is running
    volatile boolean mission_running = false; // if a mission if runnig
    volatile boolean flag_running = false;
    volatile boolean stop_listen = false; // for listen thread
    volatile boolean isFlying = false;

    volatile boolean record = false; // recording coordinates to a file
    volatile boolean val=true;

    volatile boolean rec_script = false; // for script recording
    String script_name=null;
    String script_values="";
    String name;

    InetAddress addr_video;
    // ArrayList for client video stream
    ArrayList<InetAddress> client_addr = new ArrayList<>();

    int counter = 0;
    protected DJICodecManager mCodecManager = null;

    // command buffer
    volatile ConcurrentLinkedQueue<String> que = new ConcurrentLinkedQueue();

    // msg buffer
    volatile ConcurrentLinkedQueue<String> que_com = new ConcurrentLinkedQueue();

    protected TextureView mVideoSurface = null;
    protected CoordConv Conv;

    private static ServerActivity parent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        parent = this;

        Conv = new CoordConv(parent);

        handler = new Handler(Looper.getMainLooper());
        setContentView(R.layout.activity_server);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        //setSupportActionBar(toolbar);

        serverStatus = (TextView) findViewById(R.id.server_status);
        flightstatus = (TextView) findViewById(R.id.takeoff);
        text_in = (TextView) findViewById(R.id.text_in);
        mConnectStatusTextView = (TextView) findViewById(R.id.ConnectStatusTextView);
        server_ip = getIP();


        mVideoSurface = (TextureView)findViewById(R.id.video_previewer_surface);

        if (mVideoSurface !=null) {
            mVideoSurface.setSurfaceTextureListener(this);
        }

        getBatteryStatus();

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        Button takeoff = (Button) findViewById(R.id.takeoff);
        takeoff.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                if (!Tutorial.isAircraftConnected()) {
                    showToast("No aircraft connected");
                } else {
                    if (isFlying) {
                        landing();
                        showToast("Aircraft landing");
                    } else {
                        takeoff();
                        showToast("Aircraft taking off");
                    }
                }
            }
        });

        // Register the broadcast receiver for receiving the device connection's changes.
        IntentFilter filter = new IntentFilter();
        filter.addAction(Tutorial.FLAG_CONNECTION_CHANGE);
        registerReceiver(mReceiver, filter);

        Thread thread = new Thread(new serverthread());
        //thread.setDaemon(true);
        thread.start();

        Thread thread_buffer = new Thread(new BufferThread(que, que_com/*, flag_pos*/));
        thread_buffer.start();

        Thread thread_process = new Thread(new processLine(que, que_com));
        thread_process.start();

        Thread thread_video = new Thread(new VideoConnection());
        //Thread thread_video = new Thread(new VideoConnect());
        thread_video.start();

    }

    @Override
    protected void onResume(){
        super.onResume();
    }

    @Override
    protected void onPause(){
        super.onPause();
    }


    @Override
    public void onResult(DJIError djiError) {
        showToast("Mission completed!");
        if (mission_running) {
            mission_running = false;
        }
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

                    while (!Thread.currentThread().isInterrupted()) { //
                        //Thread.currentThread().isAlive()
                        try {
                            final Socket client = serversocket.accept();
                            CommunicationThread commsthread = new CommunicationThread(client, que, que_com, parent, handler);
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
                    if (flag_running) { // mission_running
                        checkManualCoor(target_x, target_y);
                    } else {
                        String s = null;
                        parserReal(s,0);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG,"processLine error: "+e.getMessage());
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
                Log.e(TAG, "Send msg error: "+e.getMessage());
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
        private ServerActivity parent;
        private Handler handler;
        ConcurrentLinkedQueue<String> que;
        ConcurrentLinkedQueue<String> que_com;

        public CommunicationThread(Socket clientSocket,ConcurrentLinkedQueue<String> que, ConcurrentLinkedQueue<String> que_com,ServerActivity parent,Handler handler) {
            this.clientSocket = clientSocket;
            this.que = que;
            this.que_com = que_com;
            this.parent = parent;
            this.handler = handler;
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
            catch(Exception e) {
                e.printStackTrace();
            }
        }

        public InetAddress getAddr(){
            return clientSocket.getInetAddress();
        }

        public Socket getSocket(){
            return clientSocket;
        }

        public PrintWriter getPrintWriter(){ return out; }


        public void run() {
            try {
                while (connected) {
                    try {
                        String read = input.readLine();
                        if (read.equals("quit")) {
                            connected = false;
                            clients.remove(Thread.currentThread());
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    text_in.append("Client disconnected: " + clientSocket.getInetAddress() + "\n");
                                }
                            });
                        } else {
                            if (read.equals("video")) {
                            /*Not in use*/
                                //client_addr.add(clientSocket.getInetAddress()); // adds client to the list
                                //Log.e(TAG,read);                                // whom the video stream will be sent
                                //write("Client added to the list");

                            } else {
                                combuffer(read, out);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Client exception: " + e.getMessage());
                        //e.printStackTrace();
                        connected = false;
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                text_in.append("Client disconnected: " + clientSocket.getInetAddress() + "\n");
                            }
                        });
                        //sendall(e.getMessage());
                    }
                }
            } finally {
                try {
                    input.close();
                    out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    //sendall(e.getMessage());
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
                    try {
                        comms = Arrays.asList(line.split(";"));
                    } catch (Exception e) {
                        return;
                    }
                } else {
                    comms = Arrays.asList(line_old.split(";"));
                }

                for (int i = 0; i < comms.size(); i++) {
                    String val = comms.get(i);

                    //out.println(val);

                    if (mission_running && priority == 0) {
                        i--;
                        continue;
                    }

                    // splits the line by .
                    List<String> vallist = new ArrayList<String>(Arrays.asList(val.split("\\.")));
                    // splits the line by " " so we get the parametres
                    List<String> vallist_com = new ArrayList<String>(Arrays.asList(val.split(" ")));

                    if (rec_script) { // for creating scripts
                        if (vallist_com.size() > 1 && vallist_com.get(1).equals("stop")) {
                            rec_script = false;
                            ScriptMap.put(script_name,script_values); // saves the script to a Map
                            script_values = "";
                            msg1 = msg1 + "\n" + "Script recorded: " + script_name;
                        } else {
                            Log.e(TAG,"s "+script_values + " " + val);
                            script_values = script_values + val + ";";
                        }
                    } else if (vallist.get(0).equals("base")) {
                        msg1 = msg1 + "\n" + doBasestuff(vallist, vallist_com);
                    } else if (vallist.get(0).equals("ls")) {
                        msg1 = msg1 + "\n" + "base\ntargets\ncamera\nsensors\nact\noperator\nairlink\nmoveto\nbattery\nscripts\npos\nlisten";
                    } else if (vallist.get(0).equals("targets")) {
                        msg1 = msg1 + "\n" + doTargetstuff(vallist, vallist_com);
                    } else if (vallist.get(0).equals("camera")) {
                        msg1 = msg1 + "\n" + doCamerastuff(vallist, vallist_com);
                    } else if (vallist.get(0).equals("sensors")) {
                        msg1 = msg1 + "\n" + doSensorstuff(vallist, vallist_com);
                    } else if (vallist.get(0).equals("act")) {
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
                    } else if (vallist_com.get(0).equals("listen") || vallist.get(0).equals("listen")) {
                        msg1 = msg1 + "\n" + doListenStuff(vallist, vallist_com);
                    } else if (vallist_com.get(0).equals("pause")){
                        stopWaypointMission();
                        msg1 = msg1 +"\n"+ "Waypoint mission stopped";
                    } else {
                        msg1 = msg1 + "\n" + "command not recognised " + val;
                    }
                    if (priority == 0) {
                        try {
                            Thread.sleep(1500); // 1000
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
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
            Log.e(TAG, "Parser exception: " + e.getMessage());
            Log.d(TAG, Log.getStackTraceString(new Exception()));
            //out.println(e.toString());
        }
    }

    private void checkManualCoor(double lat_tar,double lon_tar){
            /*When the copter is moving to a waypoint checks if waypoint has been reached
            * if the waypoint has been reached starts process the next command from the buffer*/
        String current_pos = position();

        //if (!mission_running) { sendall("Mission done"); }

        List<String> coords = new ArrayList<String>(Arrays.asList(current_pos.split(" ")));
        //sendall(String.valueOf(coords));
        double current_lat = Double.parseDouble(coords.get(0));
        double current_lon = Double.parseDouble(coords.get(1));

        double latitude_diff = Math.abs(lat_tar - current_lat);
        double longitude_diff = Math.abs(lon_tar - current_lon);
        //sendall(String.valueOf(latitude_diff) +" "+ String.valueOf(longitude_diff));
        if (!mission_running) {
        //if ((latitude_diff < 1.0) && (longitude_diff < 1.0)) {
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
            // the priority messages such as position and pause
    {
        String[] words = {"pos", "listen", "pause"};
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
        // drone takes off after server receives "takeoff" command "act.takeoff"
        try {
            //DJIAircraft Air = new DJIAircraft();
            DJIAircraft Air = Tutorial.getAircraftInstance();
            DJIFlightController mFlight = Air.getFlightController();
            if (mFlight == null){
                //out.println("FlightController null");
                return "Flightcontoller null";
            }
            mFlight.takeOff(this);
            mission_running = true;
            isFlying = true;
            return "Copter taking off";
        } catch (Exception e) {
            showToast(e.getMessage());
            return "Copter couldn't execute take off";
        }
    }

    private String landing() {
        // automatic landing when receives the land command "act.land"
        try {
            DJIAircraft Air = Tutorial.getAircraftInstance();
            DJIFlightController mFlight = Air.getFlightController();
            if (mFlight == null){
                //out.println("FlightController null");
                return "Flightcontroller null";
            }
            mFlight.autoLanding(this);
            mission_running = true;
            isFlying = false;
            return "Drone landing";
        } catch (Exception e) {
            showToast(e.getMessage());
            Log.e(TAG,e.getMessage());
            //e.printStackTrace();
            return "Drone had a problem landing";
            //out.println("Drone had a problem landing, try again");
        }
    }

    private String travelForm(){ // korjaa tämä
        try {
            DJIFlightController mFlight = Tutorial.getAircraftInstance().getFlightController();
            if (!mFlight.isLandingGearMovable()) {
                return "Couldn't move landing gear";
            } else {
                DJILandingGear landGear = mFlight.getLandingGear();
                if (isFlying) {
                    return "Land the aircraft first";
                } else {
                    if (!landGear.getLandingGearMode().equals("Transport")) {
                        DJILandingGear.DJILandingGearMode mode = landGear.getLandingGearMode();
                        mode.valueOf("Transport");
                        landGear.enterTransportMode(new DJIBaseComponent.DJICompletionCallback() {
                            @Override
                            public void onResult(DJIError djiError) {
                                if (!(djiError == null)) {
                                    Log.e(TAG, djiError.getDescription());
                                }
                            }
                        });
                    }
                    return "Aircraft entering travel form";
                }
            }
        } catch (Exception e) {
            return "Couldn't move landing gear";
        }
    }

    public String position() {
        // gets the aircraft current position: latitude, longitude and altitude and transforms them to ETRS89 system
        if (!Tutorial.isAircraftConnected()) {
            return "Aircraft not connected";
        } else {
            try {
                DJIAircraft Air = Tutorial.getAircraftInstance();
                DJIFlightController mFlight = null;
                if (Air != null) {
                    mFlight = Air.getFlightController();
                } else {
                    return "No product connected";
                }
                DJIFlightControllerDataType.DJIFlightControllerCurrentState state = null;
                if (mFlight != null) {
                    state = mFlight.getCurrentState();
                } else {
                    return "Could not retrieve aircraft state. Check if aircraft is connected";
                }
                posit = state.getAircraftLocation();
                String alti = String.valueOf(posit.getAltitude());

                //String coords = from_latlon(posit.getLatitude(), posit.getLongitude());
                String coords = Conv.from_latlon(posit.getLatitude(), posit.getLongitude());

                List<String> coor_home = new ArrayList<>(Arrays.asList(coords.split(" ")));
                String lat = coor_home.get(0);
                String lon = coor_home.get(1);
                //return lat + " "+ lon +" "+ alti;
                return coords + " Altitude: " + alti; // easting northing zone_number zone_letter alti
            } catch (Exception e) {
                //showToast(e.getMessage());
                e.printStackTrace();
                return "Error getting coordinates";
            }
        }
    }

    private String addwaypoint(final List<String> moveData){
        // Adds the waypoint coordinates to a hashmap for later use
        try {
            String wayp = moveData.get(1);
            String lat = moveData.get(2);
            String longi = moveData.get(3);
            String alti = moveData.get(4);
            if (Waymap.containsKey(wayp)) {
                Waymap.remove(wayp);
            }
            Waymap.put(wayp, lat + " " + longi + " " + alti);
            return "Waypoint " + wayp + " added";
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            showToast(e.getMessage());
            return "Waypoint not valid. Use format: 'targets.add <name> <x> <y> <z>'";
        }
    }

    private void getBatteryStatus(){
        // method to get the battery status
        try {
            DJIBaseProduct prod = Tutorial.getProductInstance();
            if (prod == null){
                showToast("No product connected");
            } else {
                prod.getBattery().setBatteryStateUpdateCallback(new DJIBattery.DJIBatteryStateUpdateCallback() {
                    @Override
                    public void onResult(DJIBattery.DJIBatteryState djiBatteryState) {
                        percents = djiBatteryState.getBatteryEnergyRemainingPercent();
                        if (percents < 20.0) {
                            sendall("Battery charge low. Land as soon as possible");
                        }
                        //volts = djiBatteryState.getCurrentCurrent();
                    }
                });
            }
            //return "";
        } catch (Exception e) {
            showToast("Battery is null");
            Log.e(TAG, e.getMessage());
            //return "";
        }
    }

    private String addHome(List<String> vallist){
        // makes the current location home point
        if (!Tutorial.isAircraftConnected()) {
            return "Aircraft not Connected\nPlease Connect the Aircraft first then add the Homepoint";
        } else {
            try {
                String home = position();
                List<String> moveData = new ArrayList<String>(Arrays.asList(home.split(" ")));
                String wayp = "home";
                String lat = moveData.get(0);
                String longi = moveData.get(1);
                String alti = "1"; //moveData.get(4);
                if (Waymap.containsKey(wayp)) {
                    Waymap.remove(wayp);
                }
                Waymap.put(wayp, lat + " " + longi + " " + alti);
                return "Homepoint set";
            } catch (Exception e) {
                showToast(e.getMessage());
                return "Error setting homepoint";
            }
        }
    }

    public String moveDx(List<String> moveData) {
        // moves the aircraft in specified direction x amount
        // from current location
        if (!Tutorial.isAircraftConnected()) {
            return "Aircraft is not connected";
        } else {
            try {
                String home_coords = position();
                DJIAircraft Air = Tutorial.getAircraftInstance();
                //DJIFlightController mFlight = Air.getFlightController();
                List<String> coor_local = new ArrayList<String>(Arrays.asList(home_coords.split(" ")));
                String lat = coor_local.get(0);
                String lon = coor_local.get(1);
                String alti = coor_local.get(5);

                double lat_change = 0.0;
                double lon_change = 0.0;
                float alti_change = 0;

                if (moveData.get(0).substring(7).equals("dx")) {
                    lat_change = Double.parseDouble(moveData.get(1));
                } else if (moveData.get(0).substring(7).equals("dy")) {
                    lon_change = Double.parseDouble(moveData.get(1));
                } else if (moveData.get(0).substring(7).equals("dz")) {
                    alti_change = Float.parseFloat(moveData.get(1));
                }
                String zone = coor_local.get(2);
                //sendall(String.valueOf(coords_2.get(3).length()));
                int zone_num = Integer.parseInt(zone);

                //String coord_glb = to_latlon(lat, lon, 35, "W", null);

                // transforms the ETRS89 coordinates to GPS form

                //String coord_glb = to_latlon(Double.parseDouble(lat) + lat_change, Double.parseDouble(lon) + lon_change, zone_num, coor_local.get(3), null);
                String coord_glb = Conv.to_latlon(Double.parseDouble(lat) + lat_change, Double.parseDouble(lon) + lon_change, zone_num, coor_local.get(3), null);


                List<String> coor_main = new ArrayList<String>(Arrays.asList(coord_glb.split(" ")));

                final double lat_glb = Double.parseDouble(coor_main.get(0));
                final double lon_glb = Double.parseDouble(coor_main.get(1));

                final DJIMissionManager mMan1 = Air.getMissionManager();    // null if aircraft not connectecd
                if (mMan1 == null) {
                    return "Mission Manager null";
                } else {
                    DJIWaypoint wp1, wp2;
                    //wp2 = moveData.get(1);
                    wp1 = new DJIWaypoint(lat_glb, lon_glb, Float.parseFloat(alti) + alti_change + 2);
                    wp2 = new DJIWaypoint(lat_glb, lon_glb, Float.parseFloat(alti) + alti_change);

                    mWay.removeAllWaypoints();
                    //final DJIWaypointMission mWay = new DJIWaypointMission();
                    mWay.addWaypoint(wp1);
                    mWay.addWaypoint(wp2);

                    startMission(mMan1, mWay);
                    //mMan1.setMissionExecutionFinishedCallback(mCall);
                    mMan1.setMissionExecutionFinishedCallback(this);
                    mission_running = true;
                    return "Copter moving";
                }
            } catch (Exception e) {
                return e.getMessage();
            }
        }
    }


    private String waypoint(final List<String> moveData/*, final PrintWriter out*/) {
        // moves the aircraft to the target location
        // from homepoint
        // (targets.add <waypointname> <latitude> <longitude> <altitude>)
        try {
            String home_coords = position();
            if (!Tutorial.isAircraftConnected()) {
                return "Aircraft not connected";
            } else {
                DJIAircraft Air = Tutorial.getAircraftInstance();           // gets the aircraft instance

                // current coodinates
                List<String> coords_2 = new ArrayList<>(Arrays.asList(home_coords.split(" ")));

                Log.e(TAG, String.valueOf(moveData));
                Log.e(TAG, String.valueOf(coords_2));


                // home point set by user
                String homedata = Waymap.get("home");
                if (homedata == null) {
                    return "Home point not set.\nSet home point first with 'targets.home'";
                } else {
                    List<String> coor_home = new ArrayList<String>(Arrays.asList(homedata.split(" ")));


                    // waypoint to be reached
                    String waydata = Waymap.get(moveData.get(1));
                    final double lat;
                    final double lon;
                    float final_alti;
                    if (moveData.get(1).equals("home")) {
                        lat = Double.parseDouble(coor_home.get(0));
                        lon = Double.parseDouble(coor_home.get(1));
                        final_alti = 1;
                    } else {
                        List<String> coor_data = new ArrayList<String>(Arrays.asList(waydata.split(" ")));

                        Log.e(TAG, String.valueOf(coor_data));

//            final double lat = Double.parseDouble(coor_data.get(0)) + Double.parseDouble(coords_2.get(0));
//            final double lon = Double.parseDouble(coor_data.get(1)) + Double.parseDouble(coords_2.get(1));

                        // adds the target coordinates + user defined homepoint
                        lat = Double.parseDouble(coor_data.get(0)) + Double.parseDouble(coor_home.get(0));
                        lon = Double.parseDouble(coor_data.get(1)) + Double.parseDouble(coor_home.get(1));
                        final_alti = Float.parseFloat(coor_data.get(2));
                    }

                    // adds the home coordinates + the amount client wants the copter to move to create the target coordinates
//            final double lat = Double.parseDouble(coor_data.get(0)) + lati_home;
//            final double lon = Double.parseDouble(coor_data.get(1)) + long_home;


                    target_x = lat; // saves target coordinates
                    target_y = lon; // for later use

                    String zone = coords_2.get(2);
                    //sendall(String.valueOf(coords_2.get(3).length()));
                    int zone_num = Integer.parseInt(zone);

                    //String coord_glb = to_latlon(lat, lon, 35, "W", null);

                    // transforms the ETRS89 coordinates to GPS form
                    //String coord_glb = to_latlon(lat, lon, zone_num, coords_2.get(3), null);
                    String coord_glb = Conv.to_latlon(lat, lon, zone_num, coords_2.get(3), null);

                    //String coord_glb = to_latlon(lat, lon, 35, "W", null);
                    //sendall(coord_glb);
                    List<String> coor_local = new ArrayList<>(Arrays.asList(coord_glb.split(" ")));

                    final double lat_glb = Double.parseDouble(coor_local.get(0));
                    final double lon_glb = Double.parseDouble(coor_local.get(1));

                    // incase the altitude is too low

                    float first_alti = final_alti - 2;
                    if (final_alti - first_alti <= 2) {
                        first_alti = final_alti + 2;
                    }

                    //Log.e(TAG, String.valueOf(lat));
                    //DJIAircraft Air = Tutorial.getAircraftInstance();           // gets the aircraft instance
                    final DJIMissionManager mMan1 = Air.getMissionManager();    // null if aircraft not connectecd

                    DJIWaypoint wp1, wp2;
                    //wp2 = moveData.get(1);
                    wp1 = new DJIWaypoint(lat_glb, lon_glb, first_alti);
                    wp2 = new DJIWaypoint(lat_glb, lon_glb, final_alti);

                    mWay.removeAllWaypoints();
                    //final DJIWaypointMission mWay = new DJIWaypointMission();
                    mWay.addWaypoint(wp1);
                    mWay.addWaypoint(wp2);
                    //DJIBaseComponent.DJICompletionCallback mCall = mWay.mInternalCallback;

                    startMission(mMan1, mWay);
                    mission_running = true;
                    return "Moving to waypoint " + moveData.get(1);
                }
            }
            } catch(Exception e) {
                //sendall("Problem initiating mission");
                //sendall(e.getMessage());
                return "Problem iniating mission: "+e.getMessage();
            }

        }

    private void makeWaypoint(List<String> coordinates){
        /*Makes the waypoints for the mission
        * latitude longitude altitude latitude longitude altitude latitude longitude altitude etc.
        * */
        //List<String> coor_home = new ArrayList<String>(Arrays.asList(coordinates.split(" ")));
        if (!Tutorial.isAircraftConnected()) {
            sendall("Aircraft not connected");
        } else {
            String home_coords = position();
            List<String> coords_home = new ArrayList<>(Arrays.asList(home_coords.split(" ")));
            String zone = coords_home.get(2);
            DJIAircraft Air = Tutorial.getAircraftInstance();
            final DJIMissionManager mMan1 = Air.getMissionManager();

            //sendall(String.valueOf(coords_2.get(3).length()));
            int zone_num = Integer.parseInt(zone);
            for (int x = 0; x < coordinates.size(); x = x + 3) {
                float lat = Float.parseFloat(coordinates.get(x));
                float lon = Float.parseFloat(coordinates.get(x + 1));

                //String coord_glb = to_latlon(lat, lon, zone_num, coords_home.get(3), null);
                String coord_glb = Conv.to_latlon(lat, lon, zone_num, coords_home.get(3), null);

                List<String> coor_local = new ArrayList<>(Arrays.asList(coord_glb.split(" ")));
                final double lat_glb = Double.parseDouble(coor_local.get(0));
                final double lon_glb = Double.parseDouble(coor_local.get(1));
                DJIWaypoint wp1, wp2;
                //wp2 = moveData.get(1);
                int first_alti = Integer.parseInt(coordinates.get(x + 2));
                //int final_alti = 4;
                wp1 = new DJIWaypoint(lat_glb, lon_glb, first_alti);
                //wp2 = new DJIWaypoint(lat_glb, lon_glb, final_alti);
                mWay.addWaypoint(wp1);
                //mWay.addWaypoint(wp2);
            }
            startMission(mMan1, mWay);
        }
    }

    private void startMission(final DJIMissionManager mMan1, DJIMission mission){
        //Starts the mission
        try {
            final DJIMission.DJIMissionProgressHandler handy = new DJIMission.DJIMissionProgressHandler() {
                @Override
                public void onProgress(DJIMission.DJIProgressType Upload, float v) {

                }
            };

            mMan1.prepareMission(mission, handy, new DJIBaseComponent.DJICompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    mMan1.startMissionExecution(new DJIBaseComponent.DJICompletionCallback() {
                        @Override
                        public void onResult(DJIError djiError) {
                            if (djiError != null) {showToast(djiError.getDescription());}
                        }
                    });
                }
            });
            //mMan1.setMissionExecutionFinishedCallback(mCall);
            mMan1.setMissionExecutionFinishedCallback(this);
            mission_running = true;
        } catch (Exception e) {
            Log.e(TAG,e.getMessage());
            showToast("Couldn't start mission");
        }
    }

    private void stopWaypointMission(){
        /*Stops the current mission*/
        DJIMissionManager mMan = null;
        try {
            mMan = Tutorial.getAircraftInstance().getMissionManager();
        } catch (Exception e) {
            //return "Mission manager null";
            Log.e(TAG,"Missionmanager error: "+e.getMessage());
        }
        if (mMan != null) {
            mMan.stopMissionExecution(new DJIBaseComponent.DJICompletionCallback() {

                @Override
                public void onResult(DJIError error) {
                    showToast("Mission Stop: " + (error == null ? "Successfully" : error.getDescription()));
                }
            });
            if (followThread.isAlive()){
                followThread.interrupt();
            }
            mission_running = false;
            if (mWay != null){
                mWay.removeAllWaypoints();
            }
        }
    }

    private String followMission(){
        /*initialise the follow me mission
        * gets the operator coordinates and tells the drone to follow the operators coordinates*/
        DJIBaseProduct prod = Tutorial.getProductInstance();
        DJIMissionManager nMan = prod.getMissionManager();
        Tutorial.getRCGPS();
        double latRC = Tutorial.getRC_lat();
        double lonRC = Tutorial.getRC_lon();
        follow = new DJIFollowMeMission(latRC,lonRC);
        follow.heading = DJIFollowMeMission.DJIFollowMeHeading.TowardFollowPosition;
        startMission(nMan, follow);
        return "Started follow mission";
    }

    private void updateFollowMission(){
        /*updates the coordinates which the copter follows
        * The coordinates must be updated atleast every 6 seconds, otherwise the copter will not follow the target*/

//        Tutorial.getRCGPS();
        double latRC = Tutorial.getRC_lat();
        double lonRC = Tutorial.getRC_lon();
        follow.updateFollowMeCoordinate(latRC, lonRC, new DJIBaseComponent.DJICompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {

            }
        });
    }


    private String doBasestuff(List<String> vallist, List<String> vallist_com){
        // Deprecated
        if (vallist_com.get(0).equals("base.ls")) {
            return "base.rec <filename> <time>\nbase.rec stop";
        } else if (vallist_com.get(0).equals("base.rec")) {

            if (vallist_com.size() > 1 && vallist_com.get(1).equals("stop")) {
                //val = false;
                //Conv.closeFile();
                Conv.closeWriter(); // alt
                stop_listen = true;
                return "stopped recording";
            }
            //val = true;
            Calendar cal = Calendar.getInstance();

            name = vallist_com.get(1) + "_" + cal.get(Calendar.DATE) + "_" + cal.get(Calendar.MONTH) + "_" + cal.get(Calendar.YEAR);
            Conv.setFilename(name);
            //Conv.openFile();

            if (!Conv.makeWriter()) {
                return "Couldn't start recording";
            } else {
                long time = Long.parseLong(vallist_com.get(2));
//            recThread = new Thread(new RecordWriteThread(name,time,handler));
//            recThread.start();

                recThread = new Thread(new ListenThread(time, "base.write"));
                recThread.start();

                return "Started recording to file: " + name;
            }
        } else if (vallist_com.get(0).equals("base.write")) {
            String s;
            if (!Tutorial.isAircraftConnected()) {
                s = "No aircraft connected";
            } else {
                s = position();
            }
            List<String> coor = new ArrayList<>(Arrays.asList(s.split(" ")));
            String coord =  coor.get(0) + " " + coor.get(1) + " " + coor.get(5);
            try {
                //Conv.saveToFile(s + "\n");

                Conv.writeToFile(s); // alt
            } catch (Exception e) {
                Log.e(TAG, "save to file exception: "+e.getMessage());
            }
            return coord;
        } else {
            return "No such command " + vallist_com.get(0);
        }
    }

    private String doTargetstuff(List<String> vallist, List<String> vallist_com) {
        //Commands regarding adding targets and waypoints
        //Log.e(TAG, String.valueOf(vallist_com));
        if (vallist_com.get(0).equals("targets.ls")) {
            return "targets.home\ntargets.add <waypointname> <coords>\n targets.list\ntargets.clear\n";
        } else if (vallist_com.get(0).equals("targets.add")) {
            //waypoint(vallist_com);
            return addwaypoint(vallist_com);
        } else if (vallist_com.get(0).equals("targets.home")) {
            return addHome(vallist_com);
        } else if (vallist_com.get(0).equals("targets.clear")) {
            if (Waymap.isEmpty()) {
                return "No targets to be cleared";
            } else {
                Waymap.clear();
                return "Targets removed";
            }
        } else if (vallist_com.get(0).equals("targets.make")) {
            vallist_com.remove(0);
            makeWaypoint(vallist_com); // makes the waypoint mission from given coordinates and starts it
            return "Waypoint mission started";
        } else if (vallist_com.get(0).equals("targets.start")) {
            /*Not required atm*/
            return "none";
        } else {
            return "No such command " + vallist_com.get(0);
        }
    }

    private String doMovestuff(List<String> vallist, List<String> vallist_com) {
        //Commands regarding moving the copter
        if (vallist_com.get(0).equals("moveto.ls")) {
            return "moveto <home>\nmoveto <x y z ...>\nmoveto.dx <x>\nmoveto.dy <y>\nmoveto.dz <z>\n";
        } else if (vallist_com.get(0).equals("moveto")) {
            //waypoint(vallist_com);
            //check_pos(vallist_com);
            return waypoint(vallist_com); //"Moving to waypoint";
        } else if (vallist_com.get(0).contains(".d")) {
            return moveDx(vallist_com);
        } else if (vallist_com.get(0).equals("moveto.follow")){
            long time = 3;
            String task = "moveto.update";
            Thread followThread = new Thread(new ListenThread(time, task),"follow");
            //Thread followThread = new Thread(new Listen(time, task, parent, handler),"follow");
            followThread.start();
            return followMission();
        } else if (vallist_com.get(0).equals("moveto.update")) {
            updateFollowMission();
            return "";
        } else {
            return "No such command " + vallist_com.get(0);
        }
    }

    private String doSensorstuff(List<String> vallist, List<String> vallist_com) {
        //Commands regarding sensors
        if (vallist_com.get(0).equals("sensors.ls")) {
            return "sensors.compass.c";
        } else if (vallist_com.get(0).equals("sensors.compass.c")){
            if (!Tutorial.isAircraftConnected()) {
                return "No aircraft connected";
            } else {
                return Tutorial.calibrate();
            }
        } else {
            return "No such command " + vallist_com.get(0);
        }
    }

    private String doCamerastuff(List<String> vallist, List<String> vallist_com) {
        //Commands regarding camera actions
        if (vallist_com.get(0).equals("camera.ls")) {
            return "camera.p\ncamera.move <pitch> <roll> <yaw>\ncamera.move.reset\ncamera.pos\ncamera.rec start\ncamera.rec stop";
        } else if(vallist_com.get(0).equals("camera.p")){
            if (!Tutorial.isAircraftConnected()) {
                return "No aircraft connected";
            } else {
                takePhoto();
                return "Photo taken";
            }
        } else if(vallist_com.get(0).equals("camera.pos")) {
            if (!Tutorial.isAircraftConnected()) {
                return "No aircraft connected";
            } else {
                return Tutorial.getGimbalAngles();
            }
        } else if(vallist_com.get(0).equals("camera.rec")) {
            if (!Tutorial.isAircraftConnected()) {
                return "No aircraft connected";
            } else {
                if (vallist_com.get(1).equals("start")) {
                    startRec();
                    return "Started recording";
                }
                if (vallist_com.get(1).equals("stop")) {
                    stopRec();
                    return "Recording stopped";
                } else {
                    return "No option given. For example camera.rec start or camera.rec stop";
                }
            }
        } else if (vallist_com.get(0).equals("camera.move")) {
            return Tutorial.moveGimbal(vallist_com, null);
        } else if (vallist_com.get(0).equals("camera.move.spin")) {
            return Tutorial.moveGimbal(vallist_com, "spin");
        } else if (vallist_com.get(0).equals("camera.move.reset")) {
            return Tutorial.resetGimbal();
        } else {
            return "No such command " + vallist_com.get(0);
        }
    }

    private String doAirlinkstuff(List<String> vallist, List<String> vallist_com) {
        //Commands regarding airlink
        if (vallist_com.get(0).equals("airlink.ls")) {
            return "airlink.quality\nairlink.db\nairlink.password\nairlink.reboot\nairlink.ssid\n";
        } else if (vallist_com.get(0).equals("airlink.ssid")){
            Tutorial.getSSID();
            return "Wifi SSID: "+Tutorial.wifiSS;
        } else {
            return "No such command " + vallist_com.get(0);
        }
    }

    private String doBatterystuff(List<String> vallist, List<String> vallist_com) {
        //Commands regarding battery status
        if (vallist_com.get(0).equals("battery.ls")) {
            return "battery1.I\nbattery1.V\nbattery.charge\nbattery1.charge\nbattery1.level\nbattery1.level\nbattery1.T\n";
        } else if (vallist_com.get(0).equals("battery.charge")){
            getBatteryStatus();
            if (!Tutorial.isAircraftConnected()){
                return "No aircraft connected";
            } else {
                return "Battery charge: " + String.valueOf(percents) + " %";
            }
        } else {
            return "No such command " + vallist_com.get(0);
        }
    }

    private String doScriptsstuff(List<String> vallist, List<String> vallist_com) {
        // Scripts
        Log.e(TAG, String.valueOf(vallist));
        Log.e(TAG, String.valueOf(vallist_com));
        if (vallist_com.get(0).equals("scripts.ls")) {
            return "scripts.<name> record\nscripts.<name> stop\nscripts.<name> run\nscripts.list";
        } else if (vallist_com.get(0).equals("scripts.list")) {

            String str = "";
            for (Map.Entry<String,String> entry : ScriptMap.entrySet()) {
                str = str +"\n" + entry.getKey();
            }
            if (ScriptMap.size() == 0) {
                return "No recorded scripts";
            } else {
                return "Scripts: "+str;
            }
        } else if (vallist_com.size() < 2){ return "No such command " + vallist_com.get(0);
        } else if (vallist_com.get(1).equals("run")) {
            String[] name = vallist.get(1).split(" ");
            String task = ScriptMap.get(name[0]);
            Log.e(TAG,"testi "+task);
            parserReal(ScriptMap.get(name[0]), 1);
            return "Script executed: " + vallist.get(1);
        } else if (vallist_com.get(1).equals("record")) {
            String[] name = vallist.get(1).split(" ");
            script_name = name[0];
            rec_script = true;
            //ScriptMap.put(script_name,"");
            return "Recording script: " + script_name;
        } else {
            return "No such command " + vallist_com.get(0);
        }
    }

    private String doActuatorstuff(List<String> vallist, List<String> vallist_com) {
        // Commands for automatic takeoff and landing
        if (vallist_com.get(0).equals("act.ls")) {
            return "act.takeoff\nact.land";
        } else if (vallist_com.get(0).equals("act.takeoff")){
            return takeoff();
        } else if (vallist_com.get(0).equals("act.land")){
            return landing();
        } else {
            return "No such command " + vallist_com.get(0);
        }
    }

    private String doOperatorstuff(List<String> vallist, List<String> vallist_com) {
        if (vallist_com.get(0).equals("operator.ls")) {
            return "operator.pos\noperator.battery";
        } else if (vallist_com.get(0).equals("operator.pos")){
            Tutorial.getRCGPS();
            String str = String.valueOf(Tutorial.getRC_lat()) + " " + String.valueOf(Tutorial.getRC_lon());
            return str;
        } else {
            return "No such command " + vallist_com.get(0);
        }
    }
    private String doPosstuff(List<String> vallist, List<String> vallist_com) {
        //Commands regarding copter positioning
        if (vallist_com.get(0).equals("pos.ls")) {
            return "pos\npos.angle\n";
        } else if (vallist_com.get(0).equals("pos")){
            if (!Tutorial.isAircraftConnected()) {
                return "Aircraft not connected";
            } else {
                String pos = position();
                try {
                    List<String> coor = new ArrayList<>(Arrays.asList(pos.split(" ")));
                    return coor.get(0) + " " + coor.get(1) + " " + coor.get(5);
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage());
                    return "Couldn't get Coordinates";
                    //return position();
                }
            }
        } else if (vallist_com.get(0).equals("pos.angle")){
            if (!Tutorial.isAircraftConnected()) {
                return "No aircraft connected";
            } else {
                return Tutorial.getAngles();
            }
        } else {
            return "No such command " + vallist_com.get(0);
        }
    }

    private String doListenStuff(List<String> vallist, List<String> vallist_com){
        if (vallist_com.get(0).equals("listen.ls")) {
            return "listen\nlisten <Task> X (where X is seconds between updates)\nlisten stop ";
        } else if (vallist_com.get(0).equals("listen")){
            String task = vallist_com.get(1);
            if (vallist_com.get(1).equals("stop")) {
                stop_listen = true;
                val = true;
                return "stopped listening";
            } else {
                long time = Long.parseLong(vallist_com.get(2));
                Thread listen = new Thread(new ListenThread(time, task));
                //Thread listen = new Thread(new ListenThread(time, task,val));
                //Thread listen = new Thread(new Listen(time, task, parent, handler));
                listen.start();
                return "Started listening";
            }
        } else {
            return "No such command " + vallist_com.get(0);
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
            int a=0;
            //serversocket.close(); // HOX!!!
        } catch (Exception e) { // IOException
            e.printStackTrace();
        }
    }

    protected BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            updateTitleBar();
            setCamera();
            isFlying = Tutorial.checkIfFlying();
            if (isFlying) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        flightstatus.setText("Land");
                    }
                });
            } else {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        flightstatus.setText("Takeoff");
                    }
                });
            }
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
                getBatteryStatus();
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

    public void setCamera(){
        DJIBaseProduct product = Tutorial.getProductInstance();
        if (product == null || !product.isConnected()) {
            Log.e(TAG, "NO product connected");
        } else {
            DJICamera camera = product.getCamera();
            if (camera == null) {
                Log.e(TAG, "Coudn't get camera");
            } else {
                camera.setDJICameraReceivedVideoDataCallback(mReceivedVideoDataCallBack);
                camera.setDJICameraGeneratedNewMediaFileCallback(mGeneratedPhotoCallBack);
            }
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Log.e(TAG, "onSurfaceTextureAvailable");
        if (mCodecManager == null) {
            mCodecManager = new DJICodecManager(this, surface, width, height);
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        Log.e(TAG, "onSurfaceTextureSizeChanged");
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        Log.e(TAG, "onSurfaceTextureDestroyed");
        if (mCodecManager != null) {
            mCodecManager.cleanSurface();
            mCodecManager = null;
        }
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) { }


    class VideoConnection implements Runnable{

        //The connection to clients who want the video stream
        @Override
        public void run() {
            try {
                //String host = "192.168.1.52";
                int port = 8080; // 1234
                //Socket socket = null;

                final DatagramSocket datasocket = new DatagramSocket(port);
                mReceivedVideoDataCallBack = new DJICamera.CameraReceivedVideoDataCallback() {
                    @Override
                    public void onResult(byte[] videoBuffer, int size) {
                        if (mCodecManager != null) {
                            // Send the raw H264 video data to codec manager for decoding
                            //mCodecManager.sendDataToDecoder(videoBuffer, size);
                            try {
                                //sendall("ei toimi");
                                sendVideo2(videoBuffer, size, datasocket);

                            } catch (Exception e) {

                                Log.e(TAG,e.getMessage());
                                e.printStackTrace();
                            }
                        } else {
                            showToast("CodecManager is null");
                            Log.e(TAG, "mCodecManager is null");
                        }
                    }
                };

                mGeneratedPhotoCallBack = new DJICamera.CameraGeneratedNewMediaFileCallback() {
                    @Override
                    public void onResult(DJIMedia djiMedia) {
                        File dir = new File(Environment.DIRECTORY_PICTURES+File.separator+"DJI_images"+File.separator);
                        String date = djiMedia.getCreatedDate();
                        djiMedia.fetchMediaData(dir, date, new DJIMediaManager.CameraDownloadListener<String>() {
                            @Override
                            public void onStart() { }
                            @Override
                            public void onRateUpdate(long l, long l1, long l2) { }
                            @Override
                            public void onProgress(long l, long l1) { }
                            @Override
                            public void onSuccess(String s) {
                                showToast("Image Downloaded");
                            }
                            @Override
                            public void onFailure(DJIError djiError) {
                                showToast(djiError.getDescription());
                            }
                        });
                    }
                };

            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "Socket error");
            }
        }

        public void sendVideo2(byte[] buffer, int size, DatagramSocket socket2) throws Exception{
            //Send the video data through a UDP port to clients
            // iframe for the codec (Inspire 1)
            String path = "/home/juuso/Documents/V2.4.0/Android Studio/AppTwo/DJISDKLIB/src/main/res/raw/iframe_1280_ins.h264";
            File file = new File (path);
            //Socket socket = clients.get(0).getSocket();
            for (CommunicationThread client : clients) {
                InetAddress adr = client.getAddr();
                DatagramPacket packet = new DatagramPacket(buffer, size, adr, 8080);
                if (counter == 0) {
                    byte[] iframe = new byte[(int) file.length()];
                    DatagramPacket ipacket = new DatagramPacket(iframe, (int) file.length(), adr, 8080);
                    socket2.send(ipacket);
                    counter++; //
                }
                if (counter == 10) {
                    counter = 0;
                }
                socket2.send(packet);
            }
        }
    }


    public void showToast(final String msg) {


        handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    Toast.makeText(ServerActivity.this, msg, Toast.LENGTH_LONG).show(); // LENGTH_SHORT
                } catch (NullPointerException e) {
                    Log.e(TAG, "Toast error msg: " + e.getMessage());
                }
            }
        });

        /*
        Message message = handler.obtainMessage(); // testaa !!
        message.sendToTarget();

        parent.runOnUiThread(new Runnable() {
            public void run() {
                try { // ServerActivity.this
                    Toast.makeText(parent.getBaseContext(), msg, Toast.LENGTH_LONG).show(); // LENGTH_SHORT
                } catch (NullPointerException e) {
                    Log.e(TAG, "Toast error msg: " + e.getMessage());
                }
            }
        });
        */
    }

    public void takePhoto(){
        try {
            DJIBaseProduct product = Tutorial.getProductInstance();
            DJICamera camera = product.getCamera();
            DJICameraSettingsDef.CameraMode cameraMode = DJICameraSettingsDef.CameraMode.ShootPhoto;
            DJICameraSettingsDef.CameraShootPhotoMode photoMode = DJICameraSettingsDef.CameraShootPhotoMode.Single;
            camera.startShootPhoto(photoMode, new DJIBaseComponent.DJICompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError == null) {
                        showToast("Photo taken");
                    } else {
                        showToast(djiError.getDescription());
                        Log.e(TAG, djiError.getDescription());
                    }
                }
            });
        } catch (Exception e) {
            showToast(e.getMessage());
            Log.e(TAG, e.getMessage());
        }
    }


    private void startRec(){
        try {
            DJIBaseProduct product = Tutorial.getProductInstance();
            DJICamera camera = product.getCamera();
            DJICameraSettingsDef.CameraMode cameraMode = DJICameraSettingsDef.CameraMode.RecordVideo;
            //DJICameraSettingsDef.CameraShootPhotoMode photoMode = DJICameraSettingsDef.;
            camera.setCameraMode(cameraMode, new DJIBaseComponent.DJICompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError != null){
                        showToast(djiError.getDescription());
                    }
                }
            });
            camera.startRecordVideo(new DJIBaseComponent.DJICompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError != null){
                        showToast(djiError.getDescription());
                    }
                }
            });
        } catch (Exception e) {
            showToast(e.getMessage());
        }
    }

    private void stopRec(){
        try {
            DJIBaseProduct product = Tutorial.getProductInstance();
            DJICamera camera = product.getCamera();
            DJICameraSettingsDef.CameraMode cameraMode = DJICameraSettingsDef.CameraMode.RecordVideo;
            //DJICameraSettingsDef.CameraShootPhotoMode photoMode = DJICameraSettingsDef.;

            camera.stopRecordVideo(new DJIBaseComponent.DJICompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError != null) {
                        showToast(djiError.getDescription());
                    }
                }
            });

        } catch (Exception e) {
            showToast(e.getMessage());
        }
    }



    private void downloadMedia(){
        final DJIMediaManager manager = Tutorial.getProductInstance().getCamera().getMediaManager();
        manager.setCameraModeMediaDownload(new DJIBaseComponent.DJICompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                if (djiError == null) {
                    manager.fetchMediaList(new DJIMediaManager.CameraDownloadListener<ArrayList<DJIMedia>>() {
                        @Override
                        public void onStart() { }
                        @Override
                        public void onRateUpdate(long l, long l1, long l2) { }
                        @Override
                        public void onProgress(long l, long l1) { }
                        @Override
                        public void onSuccess(ArrayList<DJIMedia> djiMedias) {
                            for (int i=0;i<djiMedias.size();i++) {
                                File dir = new File(Environment.DIRECTORY_PICTURES+File.separator+"DJI_images"+File.separator);
                                String date = djiMedias.get(i).getCreatedDate();
                                djiMedias.get(i).fetchMediaData(dir, date, new DJIMediaManager.CameraDownloadListener<String>() {
                                    @Override
                                    public void onStart() { }
                                    @Override
                                    public void onRateUpdate(long l, long l1, long l2) { }
                                    @Override
                                    public void onProgress(long l, long l1) { }
                                    @Override
                                    public void onSuccess(String s) { showToast("Image Downloaded"); }
                                    @Override
                                    public void onFailure(DJIError djiError) { showToast(djiError.getDescription()); }
                                });
                            }
                        }
                        @Override
                        public void onFailure(DJIError djiError) {
                            showToast("Couldn't set media download mode: "+djiError.getDescription());
                        }
                    });
                } else {

                }
            }
        });
        //CameraMode mode = CameraMode.MediaDownload;
    }

    public class ListenThread implements Runnable{
        /*Thread for the listen command, repeats the given command until the stop command is issued*/
        private long time;
        private String task;
        private boolean run=false;

        //public ListenThread(){ }

        public ListenThread(long aika, String taski){
            this.time = aika;
            this.task = taski;
        }

        public ListenThread(Long aika,String taski, boolean val) {
            this.time = aika;
            this.task = taski;
            this.run = val;
        }


        @Override
        public void run() {
            //List<String> list = new ArrayList<>(Arrays.asList(task.split(" ")));
            while (!stop_listen) { // run // !stop_listen
                // Do commands
                // and
                // something
                parserReal(task,1);

                // wait for the user assigned time
                try {
                    Thread.sleep(time*1000); // 1000
                } catch(InterruptedException e) {
                    showToast(e.getMessage());
                    Thread.currentThread().interrupt();
                }
            }
            stop_listen = false;
            Thread.currentThread().interrupt();
        }
        public void setRun(final boolean val){
            run = val;
        }
    }
}
