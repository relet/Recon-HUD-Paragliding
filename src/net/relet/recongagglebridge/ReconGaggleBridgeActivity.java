package net.relet.recongagglebridge;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Set; 

import com.reconinstruments.ReconSDK.*;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice; 
import android.bluetooth.BluetoothSocket;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.RectF;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;

public class ReconGaggleBridgeActivity extends Activity implements IReconEventListener, IReconDataReceiver, Runnable
{
	 private static final String TAG = ReconGaggleBridgeActivity.class.getSimpleName();
	 ReconSDKManager mDataManager    = ReconSDKManager.Initialize(this);

	 private BluetoothDevice device;
	 // What device type do we look for? Currently hard coded.
	 private static final int CLASS_FLYNET        = 7936;
	 private static final int CLASS_HTC_SENSATION = 524; // an HTC sensation
	 private static final int myClass =  CLASS_FLYNET; // a FlyNet vario
	  	 
	 public static final String  RECON_DATA_BUNDLE  = "RECON_DATA_BUNDLE";
	 private int reconFlags = ReconEvent.TYPE_ALTITUDE|ReconEvent.TYPE_TIME|ReconEvent.TYPE_TEMPERATURE|ReconEvent.TYPE_SPEED|ReconEvent.TYPE_LOCATION;

	 // Commands recognized by the FlyNet device
	 private static final String CMD_PRESSURE    = "_PRS";
	 private static final String CMD_BATTERY     = "_BAT";
   @SuppressWarnings("unused")
   private static final String CMD_DEVICENAME  = "_USR";

	 private VarioView surface;
	 private VarioData   data = new VarioData();

   private Calendar calendar = Calendar.getInstance();
   private SimpleDateFormat dateformat = new SimpleDateFormat("HH:mm");
    /* Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState)  
  {
        super.onCreate(savedInstanceState);        
        setContentView(R.layout.main);
         
        // we use main screen as listeners and menu. Subscreens (activities) display history data for particular recon event
       	try {
          mDataManager.registerListener(this, reconFlags);
        } catch (InstantiationException ex) {
          Log.e(TAG, "GEN1 " + ex.getMessage() );
        } 
        
        // ----------------------------------
        surface    = (VarioView) findViewById(R.id.surface);
        //statusText = (TextView) findViewById(R.id.statusText);
  }

  @Override
  public void onDestroy() {
    mDataManager.unregisterListener(reconFlags); 
    super.onDestroy();        
  }
    
  @Override 
  public void onStart()
  {
  	super.onStart();
  	
    this.device = findDevice();
  	// connect bluetooth devices // broken until the permissions are fixed
    new Thread(this).start(); //start bluetooth connection to Gaggle and/or FlyNet 

  	mDataManager.receiveFullUpdate(this);
   
  }

  // event change notifier callback
	public void onDataChanged(ReconEvent event, Method m)
	{	
		if (event.getType() == ReconEvent.TYPE_ALTITUDE)
		{
		  try {
		    Float value = (Float)(m.invoke(event));
		    if (value != null) data.altitude.set( value.floatValue() );
      } catch (InvocationTargetException ex) {
        Log.e(TAG, "GEN2 " + ex.getMessage() );
      } catch (IllegalAccessException ex) {
        Log.e(TAG, "GEN3 " + ex.getMessage() );
		  }
		} else if (event.getType() == ReconEvent.TYPE_TIME) {
        Log.d(TAG, "Time broadcast received.");
        /* just there to update the view */
    } else if (event.getType() == ReconEvent.TYPE_TEMPERATURE) {
      Log.d(TAG, "Time broadcast received.");
      /* just there to update the view */
    } else if (event.getType() == ReconEvent.TYPE_SPEED) {
      try {
        Float value = (Float)(m.invoke(event));
        if (value != null) data.speed.set( value.floatValue() );
      } catch (InvocationTargetException ex) {
        Log.e(TAG, "GEN2 " + ex.getMessage() );
      } catch (IllegalAccessException ex) {
        Log.e(TAG, "GEN3 " + ex.getMessage() );
      }
    } else if (event.getType() == ReconEvent.TYPE_LOCATION) {
      Log.d(TAG, "Location broadcast received.");
      /* just there to update the view */
		}
		updateView();
	}
	
	private void updateView() {
	  SurfaceHolder holder = surface.getHolder();
	  Canvas canvas = holder.lockCanvas();
	  if (canvas == null) {
	    Log.d(TAG, "Surface not initialized");
	  } else {
      Log.d(TAG, "Surface initialized ok");
	  
  	  float w = canvas.getWidth(),
  	        h = canvas.getHeight(), 
  	        cx = w/2,
  	        cy = h/2;
  	  
  	  Paint p = new Paint();
      p.setTextAlign(Align.CENTER);
      p.setTextSize(24);
      
      p.setColor(Color.RED);      
      canvas.drawOval(new RectF(cx - 75, cy - 75, cx + 75, cy + 75), p);
      p.setColor(Color.BLACK);      
      canvas.drawOval(new RectF(cx - 50, cy - 50, cx + 50, cy + 50), p);
      
      double bear = -data.bearing.getCurrent() / 180 * Math.PI - Math.PI / 2;
      canvas.drawLine(cx, cy, cx + (float)(75 * Math.cos(bear)), cy + (float)(75 * Math.sin(bear)), p);
  	  p.setColor(Color.WHITE);
      canvas.drawText((int)Math.floor(data.altitude.getCurrent()) + "m", cx, cy+12, p);
      canvas.drawText(dateformat.format(calendar.getTime()), 30, 20, p);
      canvas.drawText((int)Math.floor(data.speed.getCurrent()) + "", cx+95, cy+12, p);
      p.setTextSize(12);
      canvas.drawText("kmh", cx+135, cy+12, p);
  	  
      double vario = data.altitude.getChange();
      p.setTextSize(16);
      canvas.drawText(String.format("%.1f", vario), cx-95, cy+12, p);
      long bars = Math.min(Math.max(Math.round(vario),0),5);
      p.setColor(Color.GREEN);
      for (long bar = 0; bar < bars; bar++) {
        canvas.drawRect(cx - 110, cy - bar * 10 - 17, cx - 78, cy - bar * 10 - 10, p);
      }
      bars = Math.min(Math.max(Math.round(vario),-5),0);
      Log.d(TAG, "Displaying " + bars + "bars");
      p.setColor(Color.RED);
      for (long bar = 0; bar > bars; bar--) {
        canvas.drawRect(cx - 110, cy - bar * 10 + 27, cx - 78, cy - bar * 10 + 20, p);
      }
      
      
  	  holder.unlockCanvasAndPost(canvas);
	  }
	}

  @Override
  public void onFullUpdateCompleted(int status, ArrayList<ReconDataResult> results) {
    Log.d(TAG, "onFullUpdateCompleted status " + status + " ; " + results);
    
    if (status != ReconSDKManager.STATUS_OK)
    {
      Log.e(TAG, "GEN5 Communication failure with recon unit." + status);  
      return;
    }
    
    ReconAltitude alt = (ReconAltitude)results.get(0).arrItems.get(0);
    data.altitude.set(alt.GetAltitude());

    ReconSpeed spd = (ReconSpeed)results.get(1).arrItems.get(0);
    data.speed.set(spd.GetSpeed());

    ReconLocation loc = (ReconLocation)results.get(5).arrItems.get(0);
    if ((loc != null) && (loc.GetLocation() != null) && loc.GetLocation().hasBearing()) 
      data.bearing.set(loc.GetLocation().getBearing());
    
    updateView();
  }

  @Override
  public void onReceiveCompleted(int status, ReconDataResult result) {
    Log.d(TAG, "onReceiveCompleted status " + status + " ; " + result);
    if (status != ReconSDKManager.STATUS_OK)
    {
      Log.e(TAG, "GEN4 Communication failure with recon unit.");  
      return;
    }

    ReconAltitude alt = (ReconAltitude)result.arrItems.get(0);
    data.altitude.set(alt.GetAltitude());
    
    updateView();
  }
	
  private static BluetoothDevice findDevice() {
    Log.d(TAG, "FindDevice called.");
    BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
    Log.d(TAG,"FindDevice called, bluetooth is enabled: "+adapter.isEnabled());
    if (adapter != null && adapter.isEnabled()) {
      Set<BluetoothDevice> pairedDevices = adapter.getBondedDevices();

      for (BluetoothDevice device : pairedDevices) {
        Log.d(TAG,
            "Paired with " + device.getName() + "@" + device.getAddress() + " which has device ID " + device.getBluetoothClass().getDeviceClass());
        if ((device.getBluetoothClass().getDeviceClass() == myClass)) {
          Log.d(TAG,
              "Connected to " + device.getName() + "@" + device.getAddress() + " which has device ID " + device.getBluetoothClass().getDeviceClass());
          return device;
        }
      }
    }

    return null;
  }

  /** The background thread that talks to device */
  @Override
  public void run() {
    BluetoothSocket socket = null;
      
    do {
      try {
        while (device == null) {
          try {
            Thread.sleep(50);
          }  catch (InterruptedException ex) {
          }
        }
        
        //socket = device.createRfcommSocketToServiceRecord(uuid); /* NOTE - does not work in Android 2.1/2.2 */
        BluetoothDevice hxm = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(device.getAddress());
        Method m;
        try {
          m = hxm.getClass().getMethod("createRfcommSocket", new Class[]{int.class});
          socket = (BluetoothSocket)m.invoke(hxm, Integer.valueOf(1));
        } catch (Exception e) {
          Log.d(TAG, "Error while creating socket", e);
        }
      
        if (socket != null) {
        // Connect the device through the socket. This will block
        // until it succeeds or throws an exception
          socket.connect();
    
          // Read messages
          BufferedReader reader = new BufferedReader(new InputStreamReader(
              socket.getInputStream()));
    
          String line;
          while ((line = reader.readLine()) != null)
            if (myClass == CLASS_FLYNET ) {
              handleMessage(line);
            } else {
              Log.d(TAG, "Bluetooth message receive: "+line);
            }
    
          reader.close();
    
          socket.close();
          socket = null;
        }
      } catch (IOException connectException) {
        // close the socket and get out
        Log.d(TAG, "Error while connecting", connectException);
        try {
          if (socket != null)
            socket.close();
        } catch (IOException closeException) {
          // Ignore errors on close
        }
      }
    } while (true);
  }

  
  private void handleMessage(String m) {
    if (m.length()>4) {
      String cmd = m.substring(0,4);
      if (cmd.equals(CMD_PRESSURE)) {
        // "_PRS 17CBA\n" corresponds to 0x17CBA Pa
        data.pressure.set( Integer.parseInt(m.substring(5,10), 16) / 100.d );
        //altitude = SensorManager.getAltitude(reference, pressure);
        //regression.addSample(System.currentTimeMillis(), altitude);
        //Log.d(TAG, "-> pressure = " + pressure + "\t altitude = "+altitude);
      } else 
      if (cmd.equals(CMD_BATTERY)) {
        // "_BAT 9\n" corresponds to 90%
        // "_BAT *\n" signals charging status
        if (m.charAt(5) == '*') {
          //isCharging = true;
        } else {
          //batPercentage = Integer.parseInt(m.substring(5,6), 16) / 16.f;
          //isCharging = false; // FIXME - may need a timeout if it actually alternates with the * message when charging
        }
      }       
      // Tell the GUI/audio vario we have new state
      updateView();
    }
  }


}
