package yu_zhang.wasn_ncu.rfdataverifier;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.StrictMode;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.LinearLayout;
import android.view.ViewGroup;
import android.nfc.tech.NfcV;
import android.graphics.Bitmap;

import android.support.v4.content.res.ResourcesCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.concurrent.ExecutionException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class A_Main extends AppCompatActivity
{
    NfcAdapter mNFCAdapter = null;
    protected PendingIntent PIntent = null;
    private IntentFilter F_list[];

    private Tag Tag;
    private NdefMessage NDEFMESSAGE;

    TextView print_no , print_TID , print_TSG , print_TData , Oper_msg;

    // Connection Informaion to the Database
    EditText ip1, ip2, ip3, ip4, port = null;

    // Operation Arguments
    String db_host = "" , uniqueId = "" , V_SPSignature = "";
    int Rows = 0 , currentOper = 0;
    List<String> TID_list = new ArrayList<String>();         // Tag ID
    List<String> SPRK_list = new ArrayList<String>();   // SP Reader Key
    List<String> SPSG_list = new ArrayList<String>();   // SP Signature
    List<String> RNSP_list = new ArrayList<String>();   // SP Random Number
    List<String> CSP_list = new ArrayList<String>();    // SP Counter
    List<String> Data_list = new ArrayList<String>();   // Data
    List<String> TSG_list = new ArrayList<String>();    // Tag Signature
    List<String> RNT_list = new ArrayList<String>();         // Tag Random Number
    List<String> CT_list = new ArrayList<String>();          // Tag Counter

    List<View> View_list = new ArrayList<View>();

    private MyThread thread;
    private Object syncToken;

    // Application Arguments
    private String result;
    private boolean DBaccessed = false;
    private boolean silent = false , writeProtect = false , canWrite = false , canRead = false , wSuccess = false , rSuccess = false , ERROR = false;
    final Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.a_main);

        this.ip1 =(EditText) findViewById( R.id.ip1 );
        this.ip2 =(EditText) findViewById( R.id.ip2 );
        this.ip3 =(EditText) findViewById( R.id.ip3 );
        this.ip4 =(EditText) findViewById( R.id.ip4 );
        this.port = (EditText) findViewById( R.id.port );

        this.Oper_msg = (TextView) findViewById( R.id.MSG );

        this.setupNFC();

        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build());
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .penaltyLog()
                .penaltyDeath()
                .build());
    }

    private void dataInitialization()
    {
        uniqueId = "";          // Data to Write the tag
        Rows = 0;
        currentOper = 0;
        TID_list.clear();       // Tag ID
        SPRK_list.clear();      // SP Reader Key
        SPSG_list.clear();      // SP Signature
        RNSP_list.clear();      // SP Random Number
        CSP_list.clear();       // SP Counter
        Data_list.clear();      // Data
        TSG_list.clear();       // Tag Signature
        RNT_list.clear();       // Tag Random Number
        CT_list.clear();        // Tag Counter

        for( int i = 0 ; i < View_list.size() ; i++ )
        {
            View child = View_list.get( i );
            ((ViewGroup) child.getParent()).removeView( child );
        }

        View_list.clear();

        result = "";
        DBaccessed = false;
        silent = false;
        writeProtect = false;
        canWrite = false;
        canRead = false;
        wSuccess = false;
        rSuccess = false;
        ERROR = false;

        this.Oper_msg.setText( "" );
    }

    private void setupNFC()
    {
        mNFCAdapter = NfcAdapter.getDefaultAdapter(this);

        try
        {
            if( !mNFCAdapter.isEnabled() )
                Toast.makeText(this, "Please Activate NFC!", Toast.LENGTH_LONG).show();
            else
            {
                Toast.makeText( this , "NFC!" , Toast.LENGTH_LONG ).show();

                this.PIntent = PendingIntent.getActivity( this , 0 , new Intent( this , this.getClass() ).addFlags( Intent.FLAG_ACTIVITY_SINGLE_TOP ) , 0 );

                IntentFilter tagDetected = new IntentFilter( this.mNFCAdapter.ACTION_TAG_DISCOVERED );
                IntentFilter ndefDected = new IntentFilter( this.mNFCAdapter.ACTION_NDEF_DISCOVERED );
                IntentFilter techDected = new IntentFilter( this.mNFCAdapter.ACTION_TECH_DISCOVERED );

                this.F_list = new IntentFilter[]{ tagDetected , ndefDected , techDected };

                // Close the reader first!
                this.canWrite = false;
                this.canRead = false;
            }
        }
        catch( NullPointerException NPE)
        {
            Oper_msg.setText( "No NFC on this device!" );
            Toast.makeText( this.getApplicationContext() , "No NFC on this device!" , Toast.LENGTH_LONG ).show();
        }
    }

    public void connectToDB( View V ) throws Exception
    {
        db_host = ip1.getText().toString() + "." + ip2.getText().toString() + "."
                + ip3.getText().toString() + "." + ip4.getText().toString() + ":" + port.getText().toString();
        toast( db_host );

        try
        {
            result = call_database.executeQuery( db_host, "SELECT * FROM `Operation`" );

            /*
                SQL 結果有多筆資料時使用JSONArray
                只有一筆資料時直接建立JSONObject物件
                JSONObject jsonData = new JSONObject(result);
            */
            JSONArray jsonArray = new JSONArray(result);
            this.Rows = jsonArray.length();
            for(int i = 0; i < Rows; i++)
            {
                JSONObject jsonData = jsonArray.getJSONObject(i);
                try
                {
                    LinearLayout myRoot = (LinearLayout) findViewById( R.id.root );
                    View child = getLayoutInflater().inflate( R.layout.m_card , null );

                    print_no = (TextView) child.findViewById( R.id.print_no );
                    print_TID = (TextView) child.findViewById( R.id.print_TID );
                    print_TSG = (TextView) child.findViewById( R.id.print_TSG );
                    print_TData = (TextView) child.findViewById( R.id.print_TData );

                    TID_list.add( jsonData.getString( "Tag_ID" ).toString() );
                    SPRK_list.add( jsonData.getString( "SP_RK" ).toString() );
                    SPSG_list.add( jsonData.getString( "SP_Signature" ).toString() );
                    RNSP_list.add( jsonData.getString( "RanNum_SP" ).toString() );
                    CSP_list.add( jsonData.getString( "Counter_SP" ).toString() );
                    Data_list.add( jsonData.getString( "Data" ).toString() );
                    TSG_list.add( jsonData.getString( "Tag_Signature" ).toString() );
                    RNT_list.add( jsonData.getString( "RanNum_Tag" ).toString() );
                    CT_list.add( jsonData.getString( "Counter_Tag" ).toString() );

                    print_no.setText( CSP_list.get( i ) );
                    print_TID.setText( jsonData.getString( "Tag_ID" ).toString() );
                    print_TSG.setText( jsonData.getString( "Tag_Signature" ).toString() );
                    print_TData.setText( jsonData.getString( "Data" ).toString() );

                    Log.d( "Data Check :" , CSP_list.get( i ).toString() );

                    View_list.add( child );

                    myRoot.addView( child );
                }
                catch ( Exception e) { toast( "Input Error!!" ); }
            }

            this.Oper_msg.setText( "There are " + this.Rows + " data on the DB." );

            DBaccessed = true;
        }
        catch(Exception e)
        {
            Log.e("log_tag", e.toString());
        }

    }

    public void verify( View V )
    {
        verifying();
    }

    private void verifying()
    {
        if ( !this.DBaccessed ) this.Oper_msg.setText( "Please Access to DB first!" );
        else
        {
//            for (int i = 0; i < this.Rows; i++) {
            if( this.currentOper == this.Rows )
            {
                if (!ERROR)
                {
                    this.Oper_msg.setText("Done Verifying. The Data are All VALID!");
                }
            }
            else
            {
                if (!this.CSP_list.get( this.currentOper ).equals(this.CT_list.get( this.currentOper ) ) )
                {
                    //this.Oper_msg.setText( this.CSP_list.get( i ) + " " + this.CSP_list.get( i + 1 ) + " " + this.CT_list.get( i ) + " " + this.CT_list.get( i + 1 ) );

                    this.Oper_msg.setText("The Counters (Csp , Ct) are not Matched! The Data is INVALID!!");
                    ERROR = true;
                    return;
                }
                else
                {
                    this.Oper_msg.setText("The Counters (Csp , Ct) are Matched! Start Verifying Data " + (this.currentOper + 1));

                    // Start Write......
                    this.canWrite = true;
                    this.canRead = false;
                }




                //            WriteResponse wr = writeTag( Tag );
                //            String message = (wr.getStatus() == 1 ? "Success!\n" : "Failed!\n") + wr.getMessage();
                //            if( wr.getStatus() == 1 )   wSuccess = true;
                //            else                        wSuccess = false;
                //            final A_Main myActivity = this;
                //
                //            syncToken = new Object();
                //            thread = new MyThread( syncToken );
                //            thread.start();
                //            try
                //            {
                //                Thread.sleep(3000);
                //            }
                //            catch (InterruptedException e)
                //            {
                //                e.printStackTrace();
                //            }
                //            synchronized( syncToken )
                //            {
                //                thread.setText( "Iteration " + i );
                //                syncToken.notify();
                //            }


                //            r_callHandler();
//              }
            }
        }
    }

    public void clear( View V )
    {
        this.dataInitialization();
    }

    private void toast(String text)
    {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private void handleIntent( Intent intent )
    {
//        synchronized( thread )
//        {
//            thread.notifyAll();

/* =============================================================================================== */
            // TODO: handle Intent
            String action = intent.getAction();

            if( this.mNFCAdapter.ACTION_TAG_DISCOVERED.equals( action ) )
            {
                if( this.currentOper == this.Rows )
                {
                    if (!ERROR)
                    {
                        this.Oper_msg.setText("Done Verifying. The Data are All VALID!");
                    }
                }

                Tag tag;
                if( !this.canWrite && !this.canRead )
                    return;
                else
                {
                    tag = intent.getParcelableExtra( this.mNFCAdapter.EXTRA_TAG );
                    this.Oper_msg.setText( "TAG!" );
                }

                if( this.canRead )
                {
                    NdefReaderTask NRT = new NdefReaderTask( this , this.handler , null );
                    NRT.execute( tag );
                }
                if( this.canWrite )
                {
                    // validate that this tag can be written
                    if( supportedTechs( tag.getTechList() ) )
                    {
                        // check if tag is writable (to the extent that we can
                        if( writableTag( tag ) )
                        {
                            //writeTag here
                            WriteResponse wr = writeTag( getTagAsNdef() , tag );
                            String message = (wr.getStatus() == 1 ? "Success!\n" : "Failed!\n");
                            if( wr.getStatus() == 1 )
                            {
                                wSuccess = true;
                                this.Oper_msg.setText( message + "Please now access the Verifier" );

                                // Stop Write, Start Read......
                                this.canWrite = false;
                                this.canRead = true;
                            }
                            else
                            {
                                wSuccess = false;
                                this.Oper_msg.setText( message + "Please try again!" );
                            }
                        }
                        else    this.Oper_msg.setText( "This tag is not writable" );
                    }
                    else    this.Oper_msg.setText( "This tag type is not supported" );
                }
            }
/* =============================================================================================== */

//        }
    }

    // For Write (Preparing the Message to Write)
    private NdefMessage getTagAsNdef()
    {
        int Csp = Integer.parseInt( this.CSP_list.get( currentOper ) );

        // ***** The Operation H( Rk | Data | Csp | Nsp ) *****
        if ( Csp < 10 )
            uniqueId = this.SPRK_list.get( this.currentOper ) + this.Data_list.get( this.currentOper ) + "0" + Csp + this.RNSP_list.get( this.currentOper );
        else if ( Csp > 99)
            uniqueId = this.SPRK_list.get( this.currentOper ) + this.Data_list.get( this.currentOper ) + "99" + this.RNSP_list.get( this.currentOper );
        else
            uniqueId = this.SPRK_list.get( this.currentOper ) + this.Data_list.get( this.currentOper ) + Csp + this.RNSP_list.get( this.currentOper );

        Log.d( "V SPSG' (non-MD5)" , uniqueId );
        V_SPSignature = uniqueId = MD5.getMD5( uniqueId );
        Log.d( "V SPSG' (MD5ed)" , V_SPSignature );
        Log.d( "V SPSG (MD5ed)" , this.SPSG_list.get( this.currentOper ) );

        // Verifying the SP SG
        if( !V_SPSignature.equals( this.SPSG_list.get( this.currentOper ) ) )
        {
            this.Oper_msg.setText( "The SP SG are not Matched! The Data is INVALID!!" );
            this.ERROR = true;
            return null;
        }

        int Ct = Integer.parseInt( this.CT_list.get( this.currentOper ) );

        if( Ct < 10 )
            uniqueId = uniqueId + "0" + Ct;
        else if( Ct > 99 )
            uniqueId = uniqueId + "99";
        else
            uniqueId = uniqueId + Ct;

        int Nt = Integer.parseInt( this.RNT_list.get( this.currentOper ) );

        if( Nt < 10 )
            uniqueId = uniqueId + "00" + Nt;
        else if( Nt >= 10 && Nt < 100 )
            uniqueId = uniqueId + "0" + Nt;
        else
            uniqueId = uniqueId + Nt;

        byte[] uriField = uniqueId.getBytes( Charset.forName("UTF-8") );

        // add 1 for the URI Prefix
        byte[] payload = new byte[ (uriField.length + 1) ];

        System.arraycopy( uriField , 0 , payload , 1 , uriField.length ); // appends URI to payload

        NdefRecord rtdUriRecord = new NdefRecord( NdefRecord.TNF_WELL_KNOWN , NdefRecord.RTD_TEXT , new byte[ 0 ] , payload );
        return new NdefMessage( new NdefRecord[] { rtdUriRecord });
    }

    public WriteResponse writeTag( NdefMessage message , Tag tag )
    {
        int size = message.toByteArray().length;
        String mess = "";

        try
        {
            Ndef ndef = Ndef.get(tag);
            if( ndef != null )
            {
                ndef.connect();
                if( !ndef.isWritable() )
                    return new WriteResponse( 0 , "Tag is read-only" );

                if( ndef.getMaxSize() < size )
                {
                    mess = "Tag capacity is " + ndef.getMaxSize() + " bytes, message is " + size + " bytes.";
                    return new WriteResponse( 0 , mess );
                }
                ndef.writeNdefMessage( message );

                if( writeProtect ) ndef.makeReadOnly();

                mess = "Wrote message to pre-formatted tag.";
                return new WriteResponse( 1 , mess );
            }
            else
            {
                NdefFormatable format = NdefFormatable.get( tag );
                if( format != null )
                {
                    try
                    {
                        format.connect();
                        format.format( message );
                        mess = "Formatted tag and wrote message";
                        return new WriteResponse( 1 , mess );
                    }
                    catch( IOException e )
                    {
                        mess = "Failed to format tag.";
                        return new WriteResponse( 0 , mess );
                    }
                }
                else
                {
                    mess = "Tag doesn't support NDEF.";
                    return new WriteResponse( 0 , mess );
                }
            }
        }
        catch( Exception e )
        {
            mess = "Failed to write tag";
            return new WriteResponse( 0 , mess );
        }
    }

//    public synchronized WriteResponse writeTag( Tag tag )
//    {
//        try
//        {
//            this.NDEFMESSAGE = getTagAsNdef();
//
//            // It means that the SP SG verification is not passed, so the data is invalid.
//            if ( this.NDEFMESSAGE == null ) return new WriteResponse( 0 , "Failed" );
//            else
//            {
//                this.Oper_msg.setText( "Read the Tag..." );
//                this.Tag = tag;
//
//                boolean result = new WriteVicinityTagTask().execute().get( 5000 , TimeUnit.MILLISECONDS );
//                int status = result ? 1 : 0;
//                return new WriteResponse( status , "Result" );
//            }
//        }
//        catch (InterruptedException e)
//        {
//            e.printStackTrace();
//            return new WriteResponse( 0 , "Failed" );
//        }
//        catch (ExecutionException e)
//        {
//            e.printStackTrace();
//            return new WriteResponse( 0 , "Failed" );
//        }
//        catch ( TimeoutException e )
//        {
//            e.printStackTrace();
//            return new WriteResponse( 0 , "Failed" );
//        }
//    }

    private boolean writableTag( Tag tag )
    {
        try
        {
            Ndef ndef = Ndef.get( tag );
            if( ndef != null )
            {
                ndef.connect();
                if( !ndef.isWritable() )
                {
                    Toast.makeText( this , "Tag is read-only." , Toast.LENGTH_SHORT ).show();
                    ndef.close();
                    return false;
                }
                ndef.close();
                return true;
            }
        }
        catch( Exception e )
        {   Toast.makeText( this , "Failed to read tag" , Toast.LENGTH_SHORT ).show();  }
        return false;
    }

    public static boolean supportedTechs( String[] techs )
    {
        for( String tech : techs )
        {
            if( tech.equals( "android.nfc.tech.MifareUltralight" ) )
                return true;
            if(tech.equals( "android.nfc.tech.IsoDep" ) )
                return true;
            if( tech.equals( "android.nfc.tech.NfcA" ) )
                return true;
            if( tech.equals( "android.nfc.tech.NfcF" ) )
                return true;
            if( tech.equals( "android.nfc.tech.Ndef" ) || tech.equals( "android.nfc.tech.NdefFormatable" ) )
                return true;
        }
        return false;
    }

    private class WriteVicinityTagTask extends AsyncTask<Void, Void, Boolean>
    {
        @Override
        protected void onPostExecute(Boolean result)
        {
            super.onPostExecute(result);
            String message = (result) ? "WRITE OK" : "ERROR";
            Toast.makeText(A_Main.this, message , Toast.LENGTH_SHORT).show();
        }

        @Override
        protected Boolean doInBackground(Void... params)
        {
            System.out.println( "2. Call Write: " + NDEFMESSAGE.toString() ); // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!

            int size = NDEFMESSAGE.toByteArray().length;
            String mess = "";

            try
            {
                Ndef ndef = Ndef.get( Tag );
                if( ndef != null )
                {
                    ndef.connect();
                    if( !ndef.isWritable() )
                        return false;

                    if( ndef.getMaxSize() < size )
                    {
                        mess = "Tag capacity is " + ndef.getMaxSize() + " bytes, message is " + size + " bytes.";
                        return false;
                    }
                    ndef.writeNdefMessage( NDEFMESSAGE );

                    if( writeProtect ) ndef.makeReadOnly();

                    mess = "Wrote message to pre-formatted tag.";
                    return true;
                }
                else
                {
                    NdefFormatable format = NdefFormatable.get( Tag );
                    if( format != null )
                    {
                        try
                        {
                            format.connect();
                            format.format( NDEFMESSAGE );
                            mess = "Formatted tag and wrote message";
                            return true;
                        }
                        catch( IOException e )
                        {
                            mess = "Failed to format tag.";
                            return false;
                        }
                    }
                    else
                    {
                        mess = "Tag doesn't support NDEF.";
                        return false;
                    }
                }
            }
            catch( Exception e )
            {
                mess = "Failed to write tag";
                return false;
            }
        }
    }

    private class WriteResponse
    {
        int status;
        String message;

        public WriteResponse( int Status , String Message )
        {
            this.status = Status;
            this.message = Message;
        }

        public int getStatus()
        {
            return status;
        }

        public String getMessage()
        {
            return message;
        }
    }

    /**
     * Background task for reading the data. Do not block the UI thread while reading.
     *
     * @author Ralf Wondratschek
     *
     */
    private class NdefReaderTask extends AsyncTask<Tag , Void , String>
    {
        private Context context;
        private Handler handler;
        private String msg = null;

        public NdefReaderTask( Context context , Handler handler , String data )
        {
            this.context = context;
            this.handler = handler;
            this.msg = data;
        }

        @Override
        protected String doInBackground( Tag... params )
        {
            Tag tag = params[ 0 ];

            Ndef ndef = Ndef.get( tag );
            // NDEF is not supported by this Tag.
            if( ndef == null )  return null;

            NdefMessage ndefMessage = ndef.getCachedNdefMessage();
            NdefRecord[] records = ndefMessage.getRecords();

            String result = "";
            for( NdefRecord ndefRecord : records )
            {
                try {  result = readText( ndefRecord );  }
                catch( UnsupportedEncodingException e )
                {  Log.e( "nfcproject" , "Unsupported Encoding" , e );  }
            }
            if( !result.equals( "" ) )  return result;
            else                        return null;
        }

        /**
         * See NFC forum specification for "Text Record Type Definition" at 3.2.1
         *
         * http://www.nfc-forum.org/specs/
         *
         * bit_7 defines encoding
         * bit_6 reserved for future use, must be 0
         * bit_5..0 length of IANA language code
         */
        private String readText( NdefRecord record ) throws UnsupportedEncodingException
        {
            byte[] payload = record.getPayload();
            String textEncoding = "UTF-8";

//			// Get the Text Encoding
//			textEncoding = ((payload[ 0 ] & 128) == 0) ? "UTF-8" : "UTF-16";
            int languageCodeLength = 0;
            try
            {
                // Get the Language Code
                languageCodeLength = payload[ 0 ] & 0063;
            }
            catch( Exception exp )
            {   return "Message format ERROR!"; }

            // Get the Text
            msg = new String( payload , (languageCodeLength + 1) , (payload.length - languageCodeLength - 1) , Charset.forName( textEncoding ) );

//            String body = new String(msg);
//            if ( body.length() == 25 )
//            {
//                if( !body.equals( TSG_list.get( currentOper ) ) )
//                {
//                    Oper_msg.setText( "The Tag SG are not Matched! The Data is INVALID!!" );
//                    ERROR = true;
//                }
//            }
//
//            rSuccess = true;

            return msg;
        }

        @Override
        protected void onPostExecute(String result)
        {
            if (result != null)
            {
                canWrite = false;
                canRead = false;

                rSuccess = true;

                System.out.println( "TSG Operation No. : " + currentOper );
                System.out.println( "TSG': " + result );
                System.out.println( "TSG: " + TSG_list.get( currentOper ) );

                if( !result.toString().contains( TSG_list.get( currentOper ) ) )
                {
                    Oper_msg.setText( "The Tag SG are not Matched! The Data is INVALID!!" );
                    print_no = (TextView) View_list.get(currentOper).findViewById(R.id.print_no);
                    print_no.setBackgroundDrawable(getResources().getDrawable(R.drawable.circular_error));
                    ERROR = true;
                }
                else
                {
                    Oper_msg.setText( "The Tag SG are Matched! The Data is VALID!!" );

                    print_no = (TextView) View_list.get(currentOper).findViewById(R.id.print_no);
                    print_no.setBackgroundDrawable(getResources().getDrawable(R.drawable.circular_success));
                    currentOper++;

                    if( currentOper < Rows )    verifying();
                }
            }
        }
    }


    @Override
    protected void onPause()
    {
        super.onPause();
        if( this.mNFCAdapter != null )
            this.mNFCAdapter.disableForegroundDispatch( this );
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        /**
         * It's important, that the activity is in the foreground (resumed). Otherwise
         * an IllegalStateException is thrown.
         */
        if( this.mNFCAdapter != null )
            this.mNFCAdapter.enableForegroundDispatch(this, this.PIntent, this.F_list, null);
    }

    @Override
    protected void onNewIntent( Intent intent )
    {
        /**
         * This method gets called, when a new Intent gets associated with the current activity instance.
         * Instead of creating a new activity, onNewIntent will be called. For more information have a look
         * at the documentation.
         *
         * In our case this method gets called, when the user attaches a Tag to the device.
         */
        handleIntent( intent );
    }

}
