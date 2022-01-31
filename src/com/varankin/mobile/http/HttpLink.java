/*
 * HttpLink.java
 * Created on April 19, 2006, 12:26 PM
 * Copyright 2006 Nikolai Varankine. All rights reserved.
 *
 * This class implements hidden process getting data over HTTP.
 */

package com.varankin.mobile.http;

import com.varankin.mobile.Dispatcher;
import java.io.*;
import java.util.Hashtable;
import java.util.Enumeration;
import javax.microedition.io.*;
import javax.microedition.lcdui.Gauge;

/**
 * @author  Nikolai Varankine
 */
public class HttpLink extends Thread
{
    public final static String ACCOUNT = "X-Account";
    public final static String PROTOCOL = "http:";
    public final static String ERROR_NO_MEMORY = "No memory to read from HTTP stream.";
    public static long MEMORY_GAIN_PERCENT = 20L; // can be adjusted before run() starts
    
    protected byte[] m_stream_data;
    protected int m_stream_bytes;
    
    private HttpLinker m_callback;
    private Gauge m_progress;
    private String m_url, m_method;
    private Hashtable m_options, m_parameters;
    private boolean m_terminate = false;
    
    /** Creates a new instance of HttpLink */
    public HttpLink( String a_method, String a_request, 
        Hashtable a_parameters, Hashtable a_options, 
        Gauge a_progress, HttpLinker a_callback )
    {
        super();
        m_method = a_method; m_url = a_request;
        m_parameters = a_parameters; m_options = a_options;
        m_progress = a_progress;
        m_callback = a_callback;
    }
    
    /** Body of the process */
    public void run()
    {
        HttpConnection conn = null;
        int http_rc = HttpConnection.HTTP_UNAVAILABLE;
        try
        {
            // pass through setup state
            conn = getHttpConnection();
            if( m_progress != null ) m_progress.setValue( 10 );

            // enter connected phase
            // Getting the response code will open the connection,
            // send the request, and read the HTTP response headers.
            if( ! m_terminate ) http_rc = conn.getResponseCode();
            if( m_progress != null ) m_progress.setValue( 20 );

            if( http_rc != HttpConnection.HTTP_OK )
            {
                String rm = conn.getResponseMessage();
                if( rm == null ) rm = "";
/* DEBUG
                for( int h = 0; ; h++ )
                {
                    String v = conn.getHeaderField(h), k = conn.getHeaderFieldKey(h);
                    if( v == null || k == null ) break;
                    else rm = rm + "\n" + k + "=" + v;
                }
                rm += "\nMessage(" + String.valueOf(m_stream_bytes) + ")=" 
                    + new String( m_stream_data, 0, m_stream_bytes );
/* DEBUG
                m_callback.getDispatcher().setCurrent( 
                    new javax.microedition.lcdui.Alert( null, rm, null, 
                    javax.microedition.lcdui.AlertType.CONFIRMATION), 
                    new javax.microedition.lcdui.Form(null) );
DEBUG */
                throw new Exception( String.valueOf( http_rc ) + ": " + rm );
            }

            // enter data transfer state
            if( ! m_terminate ) readStream( conn, 30, 60 );

            // done
            if( m_progress != null ) m_progress.setValue( 100 ); 
            // The HTTP response headers are stored until requested.
            if( ! m_terminate ) m_callback.completed( m_stream_data, m_stream_bytes, conn );
        }
        catch( Exception e )
        {
            m_callback.interrupted( e );
        }
        finally
        {
            if( conn != null ) try{ conn.close(); } catch( IOException ie ) {}
        }
    }
    
    private HttpConnection getHttpConnection() throws IOException
    {
        HttpConnection conn;
        StringBuffer qry = new StringBuffer();
        Enumeration k;

        // modify URL with parameters
        if( m_parameters != null ) for( k = m_parameters.keys(); k.hasMoreElements(); ) 
        {
            Object key = k.nextElement();
            qry.append( qry.length() == 0 ? "?" : "&" );
            qry.append( key.toString() );
            qry.append( "=" );
            qry.append( ( m_parameters.get( key ).toString() ).replace( ' ', '+' ) );
        }
        
        // enter setup state
        String url = m_url + hexQuoted( qry.toString() );
        //if( ! url.startsWith( PROTOCOL ) ) url = PROTOCOL + url; //TEMPORARILY
        conn = (HttpConnection)Connector.open( url, Connector.READ_WRITE, true );
        conn.setRequestMethod( m_method );
        
        // finish setup phase
        if( m_options != null ) for( k = m_options.keys(); k.hasMoreElements(); ) 
        {
            Object key = k.nextElement();
            conn.setRequestProperty( key.toString(), m_options.get( key ).toString() );
        }
        
        // manage identity in every transaction
        if( m_options == null || ! m_options.containsKey( ACCOUNT ) )
        {
            String account = m_callback.getDispatcher().registry.getValue( Dispatcher.RKEY_INIT );
            if( account != null ) conn.setRequestProperty( ACCOUNT, account );
        }

        // The Host field value MUST represent the naming authority of the origin 
        // server or gateway given by the original URL. All Internet-based HTTP/1.1 
        // servers MUST respond with a 400 (Bad Request) status code to any HTTP/1.1 
        // request message which lacks a Host header field. 
        /*if( conn.getRequestProperty( "Host" ) == null )
            conn.setRequestProperty("Host", "www.pogoda.by");*/ 

        // conn.setRequestProperty("User-Agent","Profile/MIDP-2.0 Configuration/CLDC-1.0"); //DEBUG
        //conn.setRequestProperty("Cache-Control", "no-transform, no-store"); 
        //conn.setRequestProperty("Pragma", "no-cache"); 
        //conn.setRequestProperty("HTTP-Version", "HTTP/1.1"); 
        //conn.setRequestProperty("x-wap-profile", "http://wap.samsungmobile.com/uaprof/x820_10.xml"); //DEBUG

        // some servers bounce back with no Accept
        if( conn.getRequestProperty( "Accept" ) == null )
            conn.setRequestProperty( "Accept", "*/*" );

        // HTTP/1.1 applications that do not support persistent connections MUST 
        // include the "close" connection option in every message. 
        conn.setRequestProperty( "Connnection", "close" ); 

//        conn.setRequestProperty( "TE", "chunked" );//"deflate,gzip" ); //DEBUG
//        conn.setRequestProperty( "Transfer-Encoding", "chunked" );//"deflate,gzip" ); //DEBUG
//        conn.setRequestProperty( "Wap-Connection", "Stack-Type=HTTP" ); //DEBUG
//        conn.setRequestProperty( "Accept-Charset", "*" ); //DEBUG
//        conn.setRequestProperty( "Accept-Language", "en" ); //DEBUG
//        conn.setRequestProperty( "Accept-Encoding", "gzip,deflate" ); //DEBUG
//        conn.setRequestProperty( "Host", "users.cosmostv.by" ); //DEBUG

        return conn;
    }
    
    private void readStream( HttpConnection a_conn, int a_percent_start, int a_percent_size ) 
        throws IOException
    {
        InputStream stream = a_conn.openInputStream();
        if( m_progress != null ) m_progress.setValue( a_percent_start );

        // Make input buffer
        long len = a_conn.getLength(); // unknown stream returns -1L
        if( len <= 0L ) 
        {
            // determine risk free memory gain
            Runtime.getRuntime().gc(); // by rumor, it helps :)
            len = Math.min( Runtime.getRuntime().freeMemory(), (long)Integer.MAX_VALUE );
            len = len * MEMORY_GAIN_PERCENT / 100L; 
        }
        try { m_stream_data = new byte[ (int)( Math.min( len, (long)Integer.MAX_VALUE ) ) ]; }
        catch( OutOfMemoryError e ) { throw new IOException( ERROR_NO_MEMORY ); }

        // read byte stream
        int k = m_stream_data.length*100/a_percent_size;
        m_stream_bytes = 0;
        try 
        {
            for( int actual = 0; actual != -1 && m_stream_bytes < m_stream_data.length && ! m_terminate; ) 
            {
                actual = stream.read( m_stream_data, m_stream_bytes, m_stream_data.length - m_stream_bytes );
                if( actual >= 0 ) m_stream_bytes += actual;
                if( m_progress != null ) m_progress.setValue( a_percent_start + m_stream_bytes * 100 / k );
            }
        }
        finally { stream.close(); }
    }

    public void terminate()
    {
        m_terminate = true;
    }

    public static String hexQuoted( String a_original )
    {
        StringBuffer urlb = new StringBuffer( a_original );
        
        for( int cp = 0; cp < urlb.length(); cp++ )
        {
            char cb = urlb.charAt( cp );
            String implant;

            if( cb < 0x80 )
            {
                continue;
            }
            else if( cb == ' ' ) 
            {
                urlb.deleteCharAt( cp );
                implant = hexQuoted( cb );
                urlb.insert( cp, implant );
                cp += implant.length();
                cp--;
            }
            else if( cb < 0x800 ) 
            {
                urlb.deleteCharAt( cp );
                implant = hexQuoted( 0xC0 | cb>>6 );
                urlb.insert( cp, implant );
                cp += implant.length();
                implant = hexQuoted( 0x80 | cb & 0x3F );
                urlb.insert( cp, implant );
                cp += implant.length();
                cp--;
            }
            else if ( cb < 0x10000 ) //?
            {
                urlb.deleteCharAt( cp );
                implant = hexQuoted( 0xE0 | cb>>12 );
                urlb.insert( cp, implant );
                cp += implant.length();
                implant = hexQuoted( 0x80 | cb>>6 & 0x3F );
                urlb.insert( cp, implant );
                cp += implant.length();
                implant = hexQuoted( 0x80 | cb    & 0x3F );
                urlb.insert( cp, implant );
                cp += implant.length();
                cp--;
            }
            else if( cb < 0x200000 ) //?
            {
                urlb.deleteCharAt( cp );
                implant = hexQuoted( 0xF0 | cb>>18 );
                urlb.insert( cp, implant );
                cp += implant.length();
                implant = hexQuoted( 0x80 | cb>>12 & 0x3F );
                urlb.insert( cp, implant );
                cp += implant.length();
                implant = hexQuoted( 0x80 | cb>>6  & 0x3F );
                urlb.insert( cp, implant );
                cp += implant.length();
                implant = hexQuoted( 0x80 | cb     & 0x3F );
                urlb.insert( cp, implant );
                cp += implant.length();
                cp--;
            }
        }
        
        return urlb.toString();
    }
    public static String hexQuoted( int a_char )
    {
        String hex = "00" + Integer.toHexString( a_char );
        return "%" + hex.substring( hex.length() - 2 );
    }
    
    public static long getLastModified( HttpConnection a_conn ) throws IOException
    {
        long modified = a_conn.getLastModified(); // nice decoding!
        if( modified == 0L ) // WAP gateway or device tricks?
        {
            String modified_msg = a_conn.getHeaderField( "last-modified" );
            if( modified_msg != null ) 
                try { modified = Long.parseLong( modified_msg )*1000L; }
                catch( NumberFormatException e ) { modified = 0L; }
        }
        return modified;
    }
}
