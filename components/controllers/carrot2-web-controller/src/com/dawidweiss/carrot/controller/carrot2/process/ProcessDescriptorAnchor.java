

/*
 * Carrot2 Project
 * Copyright (C) 2002-2003, Dawid Weiss
 * Portions (C) Contributors listen in carrot2.CONTRIBUTORS file.
 * All rights reserved.
 * 
 * Refer to full text of the licence "carrot2.LICENCE" in the root folder
 * of CVS checkout or at: 
 * http://www.cs.put.poznan.pl/dweiss/carrot2.LICENCE
 */


package com.dawidweiss.carrot.controller.carrot2.process;


import java.io.*;


interface ProcessDescriptorAnchor
{
    /**
     * Indicates whether process descriptor is up-to-date since last openStream() call.
     */
    public boolean isUpToDate();


    /**
     * Returns a stream to process descriptor data and resets the up-to-date flag.
     */
    public InputStream openStream();
}
