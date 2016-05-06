package jsipola.apptwo;

import android.app.Application;

import android.graphics.SurfaceTexture;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;

import dji.sdk.Camera.DJICamera;
import dji.sdk.Codec.DJICodecManager;

public class Stream extends Application {
    int counter = 0;
    protected DJICamera.CameraReceivedVideoDataCallback mReceivedVideoDataCallBack = null;
    Socket socket;
    private static final String TAG = Stream.class.getName();

    protected DJICodecManager mCodecManager = null;

    @Override
    public void onCreate() {
        super.onCreate();
        String host = "192.168.1.52";
        int port = 1234;
        Socket socket = null;
        try {
            socket = new Socket(InetAddress.getByName(host),port);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // The callback for receiving the raw H264 video data for camera live view
        final Socket finalSocket = socket;
        mReceivedVideoDataCallBack = new DJICamera.CameraReceivedVideoDataCallback() {

            @Override
            public void onResult(byte[] videoBuffer, int size) {
                if (mCodecManager != null) {
                    // Send the raw H264 video data to codec manager for decoding
                    //mCodecManager.sendDataToDecoder(videoBuffer, size);
                    try {
                        sendVideo(videoBuffer, finalSocket);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    Log.e(TAG, "mCodecManager is null");
                }
            }
        };

    }


    public void sendVideo(byte[] buffer, Socket socket) throws Exception{

        String path = "/home/juuso/Documents/V2.4.0/Android Studio/AppTwo/DJISDKLIB/src/main/res/raw/iframe_1280_ins.h264";
        File file = new File (path);
        byte [] iframe  = new byte [(int)file.length()];
        OutputStream out = socket.getOutputStream();
        if (counter == 0) {
            out.write(iframe);
            counter++;
        }
        out.write(buffer);


        //ParcelFileDescriptor pdf = ParcelFileDescriptor.fromSocket(socket);

    }

    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Log.e(TAG, "onSurfaceTextureAvailable");
        if (mCodecManager == null) {
            mCodecManager = new DJICodecManager(this, surface, width, height);
        }
    }


}
