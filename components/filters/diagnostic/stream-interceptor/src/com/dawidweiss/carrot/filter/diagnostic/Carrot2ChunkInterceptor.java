

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


package com.dawidweiss.carrot.filter.diagnostic;


import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;


/**
 * This filter, when put in between two other filters, will intercept any POSTed Carrot2 data and
 * <em>copy it directly</em> to the output. It can be used in between other Carrot2-compliant
 * filters and it should be transparent to them.
 */
public class Carrot2ChunkInterceptor
    extends com.dawidweiss.carrot.filter.FilterRequestProcessor
{
    protected File outputDirectory;
    private static volatile int counter;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh-mm-ss");
    private final Logger log = Logger.getLogger(this.getClass());

    /**
     * Sets the servlet configuration. This method is invoked by template class instantiating the
     * request processor.
     */
    public void setServletConfig(ServletConfig servletConfig)
    {
        super.setServletConfig(servletConfig);
        initialize(servletConfig);
        log.info(
            "Carrot2 Stream interceptor will dump data to: " + outputDirectory.getAbsolutePath()
        );
    }


    protected void initialize(ServletConfig servletConfig)
    {
        String outputDir;

        if ((outputDir = servletConfig.getInitParameter("intercepted.streams.folder")) == null)
        {
            if ((outputDir = System.getProperty("catalina.base")) == null)
            {
                throw new java.lang.IllegalArgumentException(
                    "Could not locate output directory for intercepted streams."
                    + " catalina.base system property not available, intercepted.streams.folder servlet"
                    + " parameter not available."
                );
            }

            File logDir = new File(outputDir, "logs");

            if (logDir.exists() && logDir.isDirectory())
            {
                outputDir = logDir.getAbsolutePath();
            }
        }

        outputDirectory = new File(outputDir);

        if (!outputDirectory.exists() || !outputDirectory.isDirectory())
        {
            throw new IllegalArgumentException(
                "Output directory does not exist: " + outputDirectory.getAbsolutePath()
            );
        }
    }


    public void processFilterRequest(
        InputStream carrotData, HttpServletRequest request, HttpServletResponse response,
        Map paramsBeforeData
    )
        throws Exception
    {
        try
        {
            log.debug("Reading data...");

            byte [] bytes = org.put.util.io.FileHelper.readFully(carrotData);

            log.debug("Writing to output stream...");

            OutputStream os = response.getOutputStream();
            os.write(bytes);
            os.close();

            File streamName = getNextStreamFile();
            log.debug("Writing to intercepted stream: " + streamName);

            FileOutputStream fos = new FileOutputStream(streamName);
            fos.write(bytes);
            fos.close();

            log.debug("Finished.");
        }
        catch (IOException e)
        {
            log.error("Cannot intercept the stream.", e);
        }
    }


    protected File getNextStreamFile()
    {
        synchronized (this.getClass())
        {
            File f = new File(
                    outputDirectory.getAbsolutePath() + File.separatorChar + "carrot2-stream-"
                    + this.dateFormat.format(new Date()) + "-stream." + (counter++)
                );
            log.debug("Intercepting stream to: " + f.getAbsolutePath());

            return f;
        }
    }
}
