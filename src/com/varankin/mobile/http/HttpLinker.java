/*
 * HttpLinker.java
 * Created on April 19, 2006, 2:42 PM
 * Copyright 2006 Nikolai Varankine. All rights reserved.
 *
 * This interface outlines call back routines used by 
 * HttpLinkMonitor class.
 */

package com.varankin.mobile.http;

import com.varankin.mobile.Dispatcher;
import javax.microedition.lcdui.*;
import javax.microedition.io.*;

/**
 * @author  Nikolai Varankine
 */
public interface HttpLinker 
{
    public void completed( byte[] a_reply, int a_reply_size, HttpConnection a_conn );
    public void interrupted( Exception a_problem );
    public Dispatcher getDispatcher();
}
