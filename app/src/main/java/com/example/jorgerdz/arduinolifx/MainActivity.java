package com.example.jorgerdz.arduinolifx;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.UUID;

import lifx.java.android.client.LFXClient;
import lifx.java.android.entities.LFXHSBKColor;
import lifx.java.android.entities.LFXTypes;
import lifx.java.android.light.LFXLight;
import lifx.java.android.light.LFXLightCollection;
import lifx.java.android.light.LFXTaggedLightCollection;
import lifx.java.android.network_context.LFXNetworkContext;

public class MainActivity extends Activity implements LFXLight.LFXLightListener, LFXLightCollection.LFXLightCollectionListener, LFXNetworkContext.LFXNetworkContextListener
{
    private static final String TAG = "bluetooth2";

    Button btnOn, btnOff,btnLights, btnBind, btnRed, btnBlue, btnGreen, btnWhite, btnOrange;
    TextView txtArduino;
    Handler h;

    Boolean bind = false;
    float hue = 0f;
    float saturation = 0f;


    final int RECIEVE_MESSAGE = 1;        // Status  for Handler
    private BluetoothAdapter btAdapter = null;
    private BluetoothSocket btSocket = null;
    private StringBuilder sb = new StringBuilder();

    private ConnectedThread mConnectedThread;
    private WifiManager.MulticastLock ml = null;

    private LFXNetworkContext networkContext;


    // SPP UUID service
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // MAC-address of Bluetooth module (you must edit this line)
    private static String address = "20:14:10:15:31:21";
    private LFXLight room;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        btnOn = (Button) findViewById(R.id.btnOn);                  // button LED ON
        btnOff = (Button) findViewById(R.id.btnOff);                // button LED OFF
        btnLights = (Button) findViewById(R.id.btnLights);
        btnBind = (Button) findViewById(R.id.btnBind);
        btnRed = (Button) findViewById(R.id.btnRed);
        btnWhite = (Button) findViewById(R.id.btnWhite);
        btnBlue = (Button) findViewById(R.id.btnBlue);
        btnGreen = (Button) findViewById(R.id.btnGreen);
        btnOrange = (Button) findViewById(R.id.btnOrange);
        txtArduino = (TextView) findViewById(R.id.txtArduino);      // for display the received data from the Arduino

        // A Multicast lock should be acquired, as some phones disable UDP broadcast / recieve
        WifiManager wifi;
        wifi = (WifiManager) getSystemService( Context.WIFI_SERVICE);
        ml = wifi.createMulticastLock( "lifx_samples_tag");
        ml.acquire();

        networkContext = LFXClient.getSharedInstance( getApplicationContext()).getLocalNetworkContext();
        networkContext.connect();

        networkContext.addNetworkContextListener(this);

        h = new Handler() {
            public void handleMessage(android.os.Message msg) {
                switch (msg.what) {
                    case RECIEVE_MESSAGE:                                                   // if receive message
                        byte[] readBuf = (byte[]) msg.obj;
                        String strIncom = new String(readBuf, 0, msg.arg1);                 // create string from bytes array
                        sb.append(strIncom);                                                // append string
                        int endOfLineIndex = sb.indexOf("\r\n");                            // determine the end-of-line
                        if (endOfLineIndex > 0) {                                            // if end-of-line,
                            String sbprint = sb.substring(0, endOfLineIndex);               // extract string
                            sb.delete(0, sb.length());                                      // and clear
                            txtArduino.setText("Data: " + sbprint);
                            receiveMessage(sbprint);
                        }

                        break;
                }
            };
        };

        btnLights.setEnabled(false);

        btAdapter = BluetoothAdapter.getDefaultAdapter();       // get Bluetooth adapter
        checkBTState();

        btnOn.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                btnOn.setEnabled(false);
                mConnectedThread.write("1");    // Send "1" via Bluetooth
                //Toast.makeText(getBaseContext(), "Turn on LED", Toast.LENGTH_SHORT).show();
            }
        });

        btnOff.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                btnOff.setEnabled(false);
                mConnectedThread.write("0");    // Send "0" via Bluetooth
                //Toast.makeText(getBaseContext(), "Turn off LED", Toast.LENGTH_SHORT).show();
            }
        });

        btnLights.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if(room.getPowerState().equals(LFXTypes.LFXPowerState.ON)) {
                    room.setPowerState(LFXTypes.LFXPowerState.OFF);
                }else{
                    room.setPowerState(LFXTypes.LFXPowerState.ON);
                }
            }
        });

        btnBind.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                networkContext.getAllLightsCollection();
                room = networkContext.getAllLightsCollection().getLightWithDeviceID("D073D5019D87");
                if(room != null){
                    bind = true;
                    btnLights.setEnabled(true);
                    Toast.makeText(getBaseContext(),"Conectado!",Toast.LENGTH_SHORT);
                }
            }
        });

        btnRed.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                receiveMessage("2222");
                mConnectedThread.write("2222");
            }
        });

        btnBlue.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                receiveMessage("3333");
                mConnectedThread.write("3333");
            }
        });

        btnWhite.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                receiveMessage("4444");
                mConnectedThread.write("4444");
            }
        });

        btnGreen.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                receiveMessage("5555");
                mConnectedThread.write("5555");
            }
        });

        btnOrange.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                receiveMessage("6666");
                mConnectedThread.write("6666");
            }
        });
    }



    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
        if(Build.VERSION.SDK_INT >= 10){
            try {
                final Method  m = device.getClass().getMethod("createInsecureRfcommSocketToServiceRecord", new Class[] { UUID.class });
                return (BluetoothSocket) m.invoke(device, MY_UUID);
            } catch (Exception e) {
                Log.e(TAG, "Could not create Insecure RFComm Connection",e);
            }
        }
        return  device.createRfcommSocketToServiceRecord(MY_UUID);
    }


    private void receiveMessage(String message){
        if(isNumeric(message)) {
            if (Integer.parseInt(message) == 2222) {
                saturation = 1.0f;
                hue = 0f;
                setLights(800);
            } else if (Integer.parseInt(message) == 3333) {
                saturation = 1.0f;
                hue = 200f;
                setLights(800);
            } else if (Integer.parseInt(message) == 4444) {
                saturation = 0f;
                hue = 0f;
                setLights(800);
            } else if (Integer.parseInt(message) == 5555) {
                saturation = 1.0f;
                hue = 120f;
                setLights(800);
            } else if (Integer.parseInt(message) == 6666) {
                saturation = 1.0f;
                hue = 34f;
                setLights(800);
            } else if (isNumeric(message)) {
                setLights(Integer.parseInt(message));
            }
        }
    }

    public static boolean isNumeric(String str)
    {
        try
        {
            double d = Double.parseDouble(str);
        }
        catch(NumberFormatException nfe)
        {
            return false;
        }
        return true;
    }

    private void setLights(int intensity){

        if(intensity > 0 && intensity < 1300 && bind){
            if(intensity >= 0 && intensity < 200){
                room.setColor(LFXHSBKColor.getColor( hue, saturation, .05f, 1000));
            }else if(intensity >= 200 && intensity < 400){
                room.setColor(LFXHSBKColor.getColor( hue, saturation, 0.2f, 1000));
            }else if(intensity >= 400 && intensity < 600){
                room.setColor(LFXHSBKColor.getColor( hue, saturation, 0.4f, 1000));
            }else if(intensity >= 600 && intensity < 800){
                room.setColor(LFXHSBKColor.getColor( hue, saturation, 0.6f, 1000));
            }else if(intensity >= 800 && intensity < 1000){
                room.setColor(LFXHSBKColor.getColor( hue, saturation, 0.8f, 1000));
            }else if(intensity >= 1000 && intensity < 1300){
                room.setColor(LFXHSBKColor.getColor( hue, saturation, 1f, 1000));
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        networkContext.connect();

        Log.d(TAG, "...onResume - try connect...");

        // Set up a pointer to the remote node using it's address.
        BluetoothDevice device = btAdapter.getRemoteDevice(address);

        // Two things are needed to make a connection:
        //   A MAC address, which we got above.
        //   A Service ID or UUID.  In this case we are using the
        //     UUID for SPP.

        try {
            btSocket = createBluetoothSocket(device);
        } catch (IOException e) {
            errorExit("Fatal Error", "In onResume() and socket create failed: " + e.getMessage() + ".");
        }

        // Discovery is resource intensive.  Make sure it isn't going on
        // when you attempt to connect and pass your message.
        btAdapter.cancelDiscovery();

        // Establish the connection.  This will block until it connects.
        Log.d(TAG, "...Connecting...");
        try {
            btSocket.connect();
            Log.d(TAG, "....Connection ok...");
        } catch (IOException e) {
            try {
                btSocket.close();
            } catch (IOException e2) {
                errorExit("Fatal Error", "In onResume() and unable to close socket during connection failure" + e2.getMessage() + ".");
            }
        }

        // Create a data stream so we can talk to server.
        Log.d(TAG, "...Create Socket...");

        mConnectedThread = new ConnectedThread(btSocket);
        mConnectedThread.start();
    }

    @Override
    public void onPause() {
        super.onPause();

        networkContext.disconnect();

        Log.d(TAG, "...In onPause()...");

        try     {
            btSocket.close();
        } catch (IOException e2) {
            errorExit("Fatal Error", "In onPause() and failed to close socket." + e2.getMessage() + ".");
        }
    }

    private void checkBTState() {
        // Check for Bluetooth support and then check to make sure it is turned on
        // Emulator doesn't support Bluetooth and will return null
        if(btAdapter==null) {
            errorExit("Fatal Error", "Bluetooth not supported");
        } else {
            if (btAdapter.isEnabled()) {
                Log.d(TAG, "...Bluetooth ON...");
            } else {
                //Prompt user to turn on Bluetooth
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, 1);
            }
        }
    }

    private void errorExit(String title, String message){
        Toast.makeText(getBaseContext(), title + " - " + message, Toast.LENGTH_LONG).show();
        finish();
    }

    @Override
    public void lightCollectionDidAddLight(LFXLightCollection lightCollection, LFXLight light) {

    }

    @Override
    public void lightCollectionDidRemoveLight(LFXLightCollection lightCollection, LFXLight light) {

    }

    @Override
    public void lightCollectionDidChangeLabel(LFXLightCollection lightCollection, String label) {

    }

    @Override
    public void lightCollectionDidChangeColor(LFXLightCollection lightCollection, LFXHSBKColor color) {

    }

    @Override
    public void lightCollectionDidChangeFuzzyPowerState(LFXLightCollection lightCollection, LFXTypes.LFXFuzzyPowerState fuzzyPowerState) {

    }

    @Override
    public void lightDidChangeLabel(LFXLight light, String label) {

    }

    @Override
    public void lightDidChangeColor(LFXLight light, LFXHSBKColor color) {

    }

    @Override
    public void lightDidChangePowerState(LFXLight light, LFXTypes.LFXPowerState powerState) {

    }

    @Override
    public void networkContextDidConnect(LFXNetworkContext networkContext) {

    }

    @Override
    public void networkContextDidDisconnect(LFXNetworkContext networkContext) {

    }

    @Override
    public void networkContextDidAddTaggedLightCollection(LFXNetworkContext networkContext, LFXTaggedLightCollection collection) {

    }

    @Override
    public void networkContextDidRemoveTaggedLightCollection(LFXNetworkContext networkContext, LFXTaggedLightCollection collection) {

    }

    private class ConnectedThread extends Thread {
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[256];  // buffer store for the stream
            int bytes; // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);        // Get number of bytes and message in "buffer"
                    h.obtainMessage(RECIEVE_MESSAGE, bytes, -1, buffer).sendToTarget();     // Send to message queue Handler
                } catch (IOException e) {
                    break;
                }
            }
        }

        /* Call this from the main activity to send data to the remote device */
        public void write(String message) {
            Log.d(TAG, "...Data to send: " + message + "...");
            byte[] msgBuffer = message.getBytes();
            try {
                mmOutStream.write(msgBuffer);
            } catch (IOException e) {
                Log.d(TAG, "...Error data send: " + e.getMessage() + "...");
            }
        }
    }
}