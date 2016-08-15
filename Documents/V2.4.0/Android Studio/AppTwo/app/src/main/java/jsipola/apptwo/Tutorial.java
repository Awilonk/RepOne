package jsipola.apptwo;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import dji.sdk.AirLink.DJIAirLink;
import dji.sdk.AirLink.DJIWiFiLink;
import dji.sdk.FlightController.DJICompass;
import dji.sdk.FlightController.DJIFlightController;
import dji.sdk.FlightController.DJIFlightControllerDataType;
import dji.sdk.Gimbal.DJIGimbal;
import dji.sdk.Products.DJIAircraft;
import dji.sdk.RemoteController.DJIRemoteController;
import dji.sdk.SDKManager.DJISDKManager;
import dji.sdk.base.DJIBaseComponent;
import dji.sdk.base.DJIBaseComponent.DJIComponentListener;
import dji.sdk.base.DJIBaseProduct;
import dji.sdk.base.DJIBaseProduct.DJIBaseProductListener;
import dji.sdk.base.DJIBaseProduct.DJIComponentKey;
import dji.sdk.base.DJIError;
import dji.sdk.base.DJISDKError;

/**
 *  Reference: https://github.com/DJI-Mobile-SDK/Android-FPVDemo/blob/master/FPV-Demo/FPV-Demo-AS/app/src/main/java/com/dji/fpvtutorial/FPVTutorialActivity.java
 */

public class Tutorial extends Application{

    private static final String TAG = Tutorial.class.getName();

    public static final String FLAG_CONNECTION_CHANGE = "Connection changed";

    public static String wifiSS;
    static float pitch;
    static float yaw;
    static float roll;

    public static double latRC;
    public static double lonRC;

    private static DJIBaseProduct mProduct;

    private Handler mHandler;

    /**
     * This function is used to get the instance of DJIBaseProduct.
     * If no product is connected, it returns null.
     */
    public static synchronized DJIBaseProduct getProductInstance() {
        if (null == mProduct) {
            mProduct = DJISDKManager.getInstance().getDJIProduct();
        }
        return mProduct;
    }

    public static boolean isAircraftConnected() {
        DJIBaseProduct product = getProductInstance();
        return product != null && product instanceof DJIAircraft;
    }

    public static synchronized DJIAircraft getAircraftInstance() {
        if (!isAircraftConnected()) return null;
        return (DJIAircraft) getProductInstance();
    }

    public static synchronized DJIFlightControllerDataType.DJIFlightControllerCurrentState getState(){
        DJIAircraft Air = getAircraftInstance();
        DJIFlightController mFlight = Air.getFlightController();
        return mFlight.getCurrentState();
    }

    public static String getAngles(){
        DJIFlightControllerDataType.DJIFlightControllerCurrentState state = getState();
        DJIFlightControllerDataType.DJIAttitude att = state.getAttitude();
        double pitch = att.pitch;
        double yaw = att.yaw;
        double roll = att.roll;
        return "Copter: pitch: "+pitch+" yaw: "+yaw+" roll: "+roll+" in degrees";
    }

    public static String calibrate(){
        try {
            DJIAircraft Air = getAircraftInstance();
            DJIFlightController mFLight = Air.getFlightController();
            DJICompass compass = mFLight.getCompass();
            compass.startCompassCalibration(new DJIBaseComponent.DJICompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {

                }
            });
            while (compass.isCalibrating()) {
                try {
                    Thread.sleep(1000);
                } catch(InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return "Compass calibrated";
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    public static String getGimbalAngles(){
        try {
            DJIAircraft Air = getAircraftInstance();
            if (Air == null) {
                return "Aircraft product null";
            } else {
                DJIGimbal gimbal = Air.getGimbal();
                if (gimbal == null) {
                    return "Gimbal null";
                } else {
                    DJIFlightControllerDataType.DJIAttitude attia = getAircraftInstance().getFlightController().getCurrentState().getAttitude();
                    final double yawC = attia.yaw;
                    gimbal.setGimbalStateUpdateCallback(new DJIGimbal.GimbalStateUpdateCallback() {
                        @Override
                        public void onGimbalStateUpdate(DJIGimbal djiGimbal, DJIGimbal.DJIGimbalState djiGimbalState) {
                            DJIGimbal.DJIGimbalAttitude atti = djiGimbalState.getAttitudeInDegrees();
                            pitch = atti.pitch;
                            yaw = (float) (atti.yaw /* - yawC */); // kato myöhemmin !!!!
                            roll = atti.roll;
                        }
                    });
                    return "Gimbal: pitch: " + pitch + " yaw: " + yaw + " roll: " + roll + " in degrees relative to the aircraft";
                }
            }
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    public static String moveGimbal(final List<String> rotationData, String mode) {
        if (!isAircraftConnected()) {
            return "No aircraft connected";
        } else {
            try {
                DJIGimbal.DJIGimbalAngleRotation yawRot = null;
                DJIGimbal.DJIGimbalAngleRotation rollRot = null;
                DJIGimbal.DJIGimbalAngleRotation pitchRot = null;

                final DJIGimbal gimbal = getAircraftInstance().getGimbal();
                if (gimbal == null) {
                    return "Gimbal null";
                } else {
                    pitchRot = new DJIGimbal.DJIGimbalAngleRotation(true, Float.parseFloat(rotationData.get(1)), DJIGimbal.DJIGimbalRotateDirection.Clockwise);
                    rollRot = new DJIGimbal.DJIGimbalAngleRotation(true, Float.parseFloat(rotationData.get(2)), DJIGimbal.DJIGimbalRotateDirection.CounterClockwise);
                    yawRot = new DJIGimbal.DJIGimbalAngleRotation(true, Float.parseFloat(rotationData.get(3)), DJIGimbal.DJIGimbalRotateDirection.CounterClockwise);

/*            try {
                pitchRot = new DJIGimbal.DJIGimbalAngleRotation(true, Float.parseFloat(rotationData.get(1)), DJIGimbal.DJIGimbalRotateDirection.Clockwise);
            } catch (Exception e) { pitchRot = null; }
            try {
                rollRot = new DJIGimbal.DJIGimbalAngleRotation(true, Float.parseFloat(rotationData.get(2)), DJIGimbal.DJIGimbalRotateDirection.CounterClockwise);
            } catch (Exception e) { rollRot = null; }
            try {
                if (mode.equals("spin")) {
                    yawRot = new DJIGimbal.DJIGimbalAngleRotation(true, 360, DJIGimbal.DJIGimbalRotateDirection.CounterClockwise);
                } else {
                    yawRot = new DJIGimbal.DJIGimbalAngleRotation(true, Float.parseFloat(rotationData.get(3)), DJIGimbal.DJIGimbalRotateDirection.CounterClockwise);
                }
            } catch (Exception e) { yawRot = null; }
*/
                    final DJIGimbal.DJIGimbalAngleRotation finalYawRot = yawRot;
                    final DJIGimbal.DJIGimbalAngleRotation finalPitchRot = pitchRot;
                    final DJIGimbal.DJIGimbalAngleRotation finalRollRot = rollRot;
                    gimbal.rotateGimbalByAngle(DJIGimbal.DJIGimbalRotateAngleMode.AbsoluteAngle, finalPitchRot, finalRollRot, finalYawRot, new DJIBaseComponent.DJICompletionCallback() {
                        @Override
                        public void onResult(DJIError djiError) {
                            //do nothing
                        }
                    });

                /*
                gimbal.setGimbalStateUpdateCallback(new DJIGimbal.GimbalStateUpdateCallback() {
                    @Override
                    public void onGimbalStateUpdate(DJIGimbal djiGimbal, DJIGimbal.DJIGimbalState djiGimbalState) {
                        djiGimbal.rotateGimbalByAngle(DJIGimbal.DJIGimbalRotateAngleMode.AbsoluteAngle, finalPitchRot, finalRollRot, finalYawRot, new DJIBaseComponent.DJICompletionCallback() {
                            @Override
                            public void onResult(DJIError djiError) {
                                //do nothing
                            }
                        });
                    }
                });
                */
                    return "Gimbal moving";
                }
            } catch (Exception e) {
                return "Couldn't rotate gimbal";
            }
        }
    }

    public static String resetGimbal(){
        try {
            final DJIGimbal gimbal = getAircraftInstance().getGimbal();
            gimbal.resetGimbal(new DJIBaseComponent.DJICompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    // do nothing
                }
            });
            /*
            gimbal.setGimbalStateUpdateCallback(new DJIGimbal.GimbalStateUpdateCallback() {
                @Override
                public void onGimbalStateUpdate(DJIGimbal djiGimbal, DJIGimbal.DJIGimbalState djiGimbalState) {
                    djiGimbal.resetGimbal(new DJIBaseComponent.DJICompletionCallback() {
                        @Override
                        public void onResult(DJIError djiError) {

                        }
                    });
                }
            });
            */
            return "Gimbal returned to original position";
        } catch (Exception e) {
            return "Couldn't reset gimbal position";
        }
    }

    public static void getRCGPS(){
        //final String latRC= null;
        DJIAircraft Air = Tutorial.getAircraftInstance();
        DJIRemoteController remote = Air.getRemoteController();
        remote.setGpsDataUpdateCallback(new DJIRemoteController.RCGpsDataUpdateCallback() {
            @Override
            public void onGpsDataUpdate(DJIRemoteController djiRemoteController, DJIRemoteController.DJIRCGPSData djircgpsData) {
                latRC = djircgpsData.latitude;
                lonRC = djircgpsData.longitude;
            }
        });

        //return "";
    }

    public static double getRC_lat() {
        return latRC;
    }

    public static double getRC_lon() {
        return lonRC;
    }

    public static void getSSID() {
        try {
            DJIBaseProduct prod = getProductInstance();
            //DJIAircraft Air = getAircraftInstance();

            //DJIAirLink link = Air.getAirLink();
            DJIAirLink link2 = prod.getAirLink();
            //DJIWiFiLink wifiLink = link.getWiFiLink();
            DJIWiFiLink wifiLink = link2.getWiFiLink();
            if (!(wifiLink==null)) {
                wifiLink.getWiFiSSID(new DJIBaseComponent.DJICompletionCallbackWith<String>() {
                    @Override
                    public void onSuccess(String s) {
                        wifiSS = s;
                    }

                    @Override
                    public void onFailure(DJIError djiError) {
                        wifiSS = "couldn't get ssid";
                    }
                });
            } else {
                wifiSS = "Wifilink null";
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            wifiSS = e.getMessage();
        }
    }

    public static boolean checkIfFlying(){
        try {
            DJIFlightControllerDataType.DJIFlightControllerCurrentState state = getState();
            if (state.isFlying()) {
                return true;
            } else {
                return false;
            }
        } catch (NullPointerException e) {
            Log.e(TAG,"Couldn't get flying status");
            return false;
        }
    }



    @Override
    public void onCreate() {
        super.onCreate();
        mHandler = new Handler(Looper.getMainLooper());
        //This is used to start SDK services and initiate SDK.
        DJISDKManager.getInstance().initSDKManager(this, mDJISDKManagerCallback);
    }

    /**
     * When starting SDK services, an instance of interface DJISDKManager.DJISDKManagerCallback will be used to listen to
     * the SDK Registration result and the product changing.
     */
    private DJISDKManager.DJISDKManagerCallback mDJISDKManagerCallback = new DJISDKManager.DJISDKManagerCallback() {

        //Listens to the SDK registration result
        //@Override
        public void onGetRegisteredResult(DJISDKError error) {
            if(error == DJISDKError.REGISTRATION_SUCCESS) {
                //DJISDKManager.getInstance().startConnectionToProduct();
                boolean conne = DJISDKManager.getInstance().startConnectionToProduct();
                Log.e("TAG", "Connection to Product: "+String.valueOf(conne));
            } else {
                Handler handler = new Handler(Looper.getMainLooper());
                handler.post(new Runnable() {

                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), "register sdk fails, check network is available", Toast.LENGTH_LONG).show();
                    }
                });

            }
            Log.e("TAG", error.getDescription());
        }

        @Override
        public void onGetRegisteredResult(DJIError djiError) {
            if (djiError == DJISDKError.REGISTRATION_SUCCESS){
                DJISDKManager.getInstance().startConnectionToProduct();
            } else {
                Log.e(TAG, "Connection failed");
            }
        }

        //Listens to the connected product changing, including two parts, component changing or product connection changing.
        @Override
        public void onProductChanged(DJIBaseProduct oldProduct, DJIBaseProduct newProduct) {

            mProduct = newProduct;
            if(mProduct != null) {
                mProduct.setDJIBaseProductListener(mDJIBaseProductListener);
            }

            notifyStatusChange();
        }
    };

    private DJIBaseProductListener mDJIBaseProductListener = new DJIBaseProductListener() {

        @Override
        public void onComponentChange(DJIComponentKey key, DJIBaseComponent oldComponent, DJIBaseComponent newComponent) {

            if(newComponent != null) {
                newComponent.setDJIComponentListener(mDJIComponentListener);
            }
            notifyStatusChange();
        }

        @Override
        public void onProductConnectivityChanged(boolean isConnected) {

            notifyStatusChange();
        }

    };

    private DJIComponentListener mDJIComponentListener = new DJIComponentListener() {

        @Override
        public void onComponentConnectivityChanged(boolean isConnected) {
            notifyStatusChange();
        }

    };

    private void notifyStatusChange() {
        mHandler.removeCallbacks(updateRunnable);
        mHandler.postDelayed(updateRunnable, 500);
    }

    private Runnable updateRunnable = new Runnable() {

        @Override
        public void run() {
            Intent intent = new Intent(FLAG_CONNECTION_CHANGE);
            sendBroadcast(intent);
        }
    };

}