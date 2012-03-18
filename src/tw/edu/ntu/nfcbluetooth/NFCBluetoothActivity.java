package tw.edu.ntu.nfcbluetooth;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcAdapter.CreateNdefMessageCallback;
import android.nfc.NfcEvent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.text.format.Formatter;
import android.util.Log;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class NFCBluetoothActivity extends Activity implements CreateNdefMessageCallback{
	
	
	public static final String TAG = "NFCBluetooth";
	//Get Image
	protected static final int CHOOSE_FILE_RESULT_CODE = 20;
	public Uri uri;	
	//NFC
    private NfcAdapter mNfcAdapter;
    private NdefMessage mMessage;
    //Bluetooth
    private BluetoothAdapter mBTAdapter;
    private String des_mac_address;
    private static final String NAME = "NFCBluetooth";
    private static final UUID MY_UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");
    
    TextView tv;
    int BUFFER_SIZE = 65536;
    
    Handler handler = new Handler() {

		@Override
		public void handleMessage(Message msg) {
			// TODO Auto-generated method stub
			super.handleMessage(msg);
			if(msg!=null)
			{
				if(msg.getData().getString("type")!=null)
				{
					if(msg.getData().getString("type").equals("succeed")){
						Intent intent = new Intent();
			            intent.setAction(android.content.Intent.ACTION_VIEW);
			            String file_uri = msg.getData().getString("file_uri");
			            intent.setDataAndType(Uri.parse("file://" + file_uri), "image/*");
			            NFCBluetoothActivity.this.startActivity(intent);
					}
					
				}
				else{
					tv = (TextView)findViewById(R.id.teststring);
					tv.append((String) msg.obj);
				}
				
			}
		}
	};
    
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        
        Intent intent = getIntent();
        String action = intent.getAction();
        
        // Allow user to pick an image from Gallery
        if(!"android.nfc.action.NDEF_DISCOVERED".equals(action))
        {
        	Intent getImageintent = new Intent(Intent.ACTION_GET_CONTENT);
        	getImageintent.setType("image/*");
        	startActivityForResult(getImageintent, CHOOSE_FILE_RESULT_CODE);
        }
        
        
        // Check for available NFC Adapter
        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (mNfcAdapter == null) {
                  Toast.makeText(this, "NFC is not available", Toast.LENGTH_LONG).show();
                  finish();
                  return;
        }
        // Register NFC callback
        mNfcAdapter.setNdefPushMessageCallback(this, this);
        
        mBTAdapter = (BluetoothAdapter) BluetoothAdapter.getDefaultAdapter();
        
        //server
        new Thread(new Runnable() {
			public void run() {
				// TODO Auto-generated method stub
				try {
					BluetoothServerSocket sSocket = mBTAdapter.listenUsingRfcommWithServiceRecord(NAME, MY_UUID);
					if(sSocket != null){
						Message msg = new Message();
						msg.obj = "Open\n";
						handler.sendMessage(msg);
					}
					while (true) {
						BluetoothSocket client = sSocket.accept();
						Message msg = new Message();
						msg.obj = ""+client.getRemoteDevice().getAddress()+" connected\n";
						handler.sendMessage(msg);
						
		
						OutputStream stream = client.getOutputStream();
		                ContentResolver cr = NFCBluetoothActivity.this.getContentResolver();
		                InputStream is = null;
		                try {
		                    is = cr.openInputStream(NFCBluetoothActivity.this.uri);
		                } catch (FileNotFoundException e) {
		                    Log.d(NFCBluetoothActivity.TAG, e.toString());
		                }
		                NFCBluetoothActivity.copyFile(is, stream);
		                
		               
						client.close();
						Message msg2 = new Message();
						msg2.obj = "socket to client close";
						handler.sendMessage(msg2);
										
						
					}
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}).start();
        
        
    }
    
    
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User has picked an image. Transfer it to group owner i.e peer using
    	if (resultCode==RESULT_OK)
    	{
    		this.uri = data.getData();
    		
            InputStream imageStream;
			try {
				imageStream = getContentResolver().openInputStream(uri);
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
            try {
            	ImageView imgview = (ImageView)findViewById(R.id.imageView1);
            	imgview.setImageBitmap(decodeUri(this.uri));
				
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    		
    		
    	}
    		
              
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // Check to see that the Activity started due to an Android Beam
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(getIntent().getAction())) {
            processIntent(getIntent());
        }
    }
    
    @Override
    public void onNewIntent(Intent intent) {
        // onResume gets called after this to handle the intent
        setIntent(intent);
    }
    
    
    void processIntent(Intent intent) {      
    	Parcelable[] rawMsgs = intent
				.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
    	
    	NdefMessage[] msgs;
		if (rawMsgs != null) {
			
			msgs = new NdefMessage[rawMsgs.length];
			for (int i = 0; i < rawMsgs.length; i++) {
				msgs[i] = (NdefMessage) rawMsgs[i];
			}
			getMACAddress(msgs);
		}
		
		
    }
    
    // Get Bluetooth MAC Address from NFC msg.
  	private void getMACAddress(NdefMessage[] msgs) {
  		String mac = new String(msgs[0].getRecords()[0].getPayload());
  		this.des_mac_address = mac;
  		
  		this.connectTransfer(mac);
  	}
  	
  	private void connectTransfer(String mac_address)
  	{
  		new Thread(new Runnable() {
		
			public void run() {
				
				try {
					for (int i = 0; i < 1; i++) {
						long start, end;
						Message msg = new Message();
						msg.obj = "start to connect\n";
						handler.sendMessage(msg);

						BluetoothSocket socket = mBTAdapter.getRemoteDevice(NFCBluetoothActivity.this.des_mac_address).
													createRfcommSocketToServiceRecord(MY_UUID);
						socket.connect();
						start = System.currentTimeMillis();
						
						
						final File f = new File(Environment.getExternalStorageDirectory() + "/"
		                        + NFCBluetoothActivity.this.getPackageName() + "/wifip2pshared-" + System.currentTimeMillis()
		                        + ".jpg");
					
						File dirs = new File(f.getParent());
		                if (!dirs.exists())
		                    dirs.mkdirs();
		                f.createNewFile();
	
		                Log.d(NFCBluetoothActivity.TAG, "server: copying files " + f.toString());
		                InputStream inputstream = socket.getInputStream();
		                copyFile(inputstream, new FileOutputStream(f));
		                
		                end = System.currentTimeMillis();
			            //be careful
		                Message msg2 = new Message();
						msg2.obj = "server socket close\n";
						handler.sendMessage(msg2);
		                
						socket.close();
						
						Message msg3 = new Message();
						Bundle data = new Bundle();
						data.putString("type", "succeed");
						data.putString("file_uri", f.getAbsolutePath());
						msg3.setData(data);
						handler.sendMessage(msg3);
						
						
						// be careful
						Thread.sleep(5000);
					}
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}).start();
  		
  		
  	}
  
    
    public NdefMessage createNdefMessage(NfcEvent event) {
		// Create an NDEF message with device Bluetooth MAC address

    	this.des_mac_address = mBTAdapter.getAddress();
    	
		try {
			byte[] byteMAC = this.des_mac_address.getBytes();
			mMessage = new NdefMessage(new NdefRecord[] { new NdefRecord(
					NdefRecord.TNF_MIME_MEDIA, "text/plain".getBytes(),
					new byte[] {}, byteMAC) });
		} catch (java.lang.NoClassDefFoundError e) {
			Toast.makeText(this, "No NFC Device", Toast.LENGTH_LONG).show();
		}
    	
		return mMessage;
	}
    
    private Bitmap decodeUri(Uri selectedImage) throws FileNotFoundException {

        // Decode image size
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(getContentResolver().openInputStream(selectedImage), null, o);

        // The new size we want to scale to
        final int REQUIRED_SIZE = 140;

        // Find the correct scale value. It should be the power of 2.
        int width_tmp = o.outWidth, height_tmp = o.outHeight;
        int scale = 1;
        while (true) {
            if (width_tmp / 2 < REQUIRED_SIZE
               || height_tmp / 2 < REQUIRED_SIZE) {
                break;
            }
            width_tmp /= 2;
            height_tmp /= 2;
            scale *= 2;
        }

        // Decode with inSampleSize
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = scale;
        return BitmapFactory.decodeStream(getContentResolver().openInputStream(selectedImage), null, o2);

    }
    
    public static boolean copyFile(InputStream inputStream, OutputStream out) {
        byte buf[] = new byte[1024];
        int len;
        try {
            while ((len = inputStream.read(buf)) != -1) {
                out.write(buf, 0, len);

            }
            out.close();
            inputStream.close();
        } catch (IOException e) {
            Log.d(NFCBluetoothActivity.TAG, e.toString());
            return false;
        }
        return true;
    }
    
}