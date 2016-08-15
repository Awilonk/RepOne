package jsipola.apptwo;


import android.content.Context;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Created by juuso on 18.7.2016.
 * Methods provided by Antti Tikanmäki in Python, translated to Java by Juuso Sipola
 */
public class CoordConv { /* extends ServerActivity*/

    public static String filename="";
    FileOutputStream out;
    BufferedWriter writer;
    File file;

    private ServerActivity parent;
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

    public CoordConv(){
    }

    public CoordConv(final ServerActivity activity){
        this.parent = activity;
    }

    public String to_latlon(double easting, double northing, int zone_number, String zone_letter, String northern) {
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

    protected String from_latlon(double latitude, double longitude){
        /*Converts GPS coordinates to ETRS89 coordinates*/
        String string = "";
        // test
        //sendall(String.valueOf(latitude));
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
                //Log.e(TAG, zoneLetters.get(key));
                //showToast(zoneLetters.get(key));
            }
        }
        return "None";
    }

    public void setFilename(final String s){
        filename = s;
    }

    public void saveToFile(String str) throws FileNotFoundException {
        try {
            //FileOutputStream out = openFileOutput(filename, Context.MODE_PRIVATE);
            out.write(str.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public void openFile(){
        try {
            out = parent.openFileOutput(filename, parent.getBaseContext().MODE_APPEND);
        } catch (FileNotFoundException e) {
            Log.e("CoordConv", "openFile error: " + e.getMessage());
        }
    }
    public void closeFile(){
        try {
            out.close();
        } catch (IOException e) {
            Log.e("CoordConv", "closeFile error: " + e.getMessage());
        }
    }

    public boolean makeWriter(){
        try {
            file = new File(parent.getBaseContext().getFilesDir(),filename + ".txt");
            writer = new BufferedWriter(new FileWriter(file,true));
            return true;
        } catch (IOException e) {
            Log.e("make buffwriter excep: ",e.getMessage());
            return false;
        }
    }
    public void writeToFile(String str){
        try {
            writer.write(str+"\n");
        } catch (IOException e) {
            Log.e("CoordConv","write to file error: "+e.getMessage());
        }
    }
    public void closeWriter(){
        try {
            writer.close();
            MediaScannerConnection.scanFile(parent,new String[]{file.toString()},null,null);
        } catch (IOException e) {
            Log.e("CoordConv","close file error: "+e.getMessage());
        }
    }
}
