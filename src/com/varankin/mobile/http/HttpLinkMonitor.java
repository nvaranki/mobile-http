/*
 * HttpLinkMonitor.java
 * Created on April 19, 2006, 12:26 PM
 * Copyright 2006 Nikolai Varankine. All rights reserved.
 *
 * This class implements dialog starting download process, 
 * indication of progress and analysis of experienced problems.
 */

package com.varankin.mobile.http;

import com.varankin.mobile.Dispatcher;
import javax.microedition.lcdui.*;
import javax.microedition.io.*;
import java.util.Hashtable;

/**
 * @author  Nikolai Varankine
 */
public class HttpLinkMonitor extends Form implements CommandListener 
{
    public  Gauge m_progress; // threads update its value, supposed inside 0..100
    private Command m_command_stop;
    private HttpLink m_link;
    private HttpLinker m_callback;
    private Dispatcher m_parent;
    private Exception m_problem;
    
    /** Creates a new instance of HttpLinkMonitor */
    public HttpLinkMonitor( String a_method, String a_url, 
        Hashtable a_parameters, Hashtable a_options, 
        Item a_contents, HttpLinker a_callback )
    {
        this( a_method, a_url, a_parameters, a_options, 
            new Item[] { a_contents }, a_callback );
    }
    public HttpLinkMonitor( String a_method, String a_url, 
        Hashtable a_parameters, Hashtable a_options, 
        Item[] a_contents, HttpLinker a_callback )
    {
        super( null );
        m_callback = a_callback;
        m_parent = m_callback.getDispatcher();
        m_progress = new Gauge( m_parent.getString( a_callback, "Progress" ), false, 100, 0 );
        m_progress.setValue( 0 );
        m_command_stop = new Command( m_parent.getString( this, "Menu.Stop" ), Command.STOP, 1 );
        
        // insert GUI elements and show up
        setTitle( m_parent.getString( this, "Title" ) );
        if( a_contents != null ) 
            for( int c = 0; c < a_contents.length; c++ ) append( a_contents[c] );
        append( m_progress );
        addCommand( m_command_stop );

        // Set up this form to listen to command events
        setCommandListener( this );

        // start connection as separate thread immediately
        m_link = new HttpLink( a_method, a_url, a_parameters, a_options, m_progress, m_callback );
        m_link.start();
    }
    
    /**
     * Called when user action should be handled
     */
    public void commandAction( Command a_command, Displayable a_displayable ) 
    {
        if( a_command == m_command_stop ) 
        {
            // place stop indicator in HttpLink, no promise it will terminate instantly
            m_link.terminate();
            // return back to form, link becomes unlinked and unhandled but still working - BAD (Java)
            m_callback.interrupted( new Exception ( m_parent.getString( this, "Cancelled" ) ) );
        }
    }
    
}
