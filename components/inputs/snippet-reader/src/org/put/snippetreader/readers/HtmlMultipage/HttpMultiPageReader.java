

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


package org.put.snippetreader.readers.HtmlMultipage;


import gnu.regexp.REException;

import java.io.*;
import java.util.*;

import org.apache.log4j.Logger;
import org.jdom.Element;
import org.put.snippetreader.util.ExtendedRegExp;
import org.put.util.io.FileHelper;
import org.put.util.net.http.FormParameters;
import org.put.util.net.http.HTTPFormSubmitter;
import org.put.util.xml.JDOMHelper;


/**
 * Submits a search query and reads as many pages as needed to reach the number of requested
 * results. The result snippets are NOT yet extracted from the page, only an estimation of their
 * number is taken into account.
 */
public class HttpMultiPageReader
{
    private static final Logger log = Logger.getLogger(HttpMultiPageReader.class);
    protected static final String PAGE_INFO = "pageinfo";
    protected static final String EXPECTED_RESULTS_PER_PAGE = PAGE_INFO + "/results-per-page";
    protected static final String RESULTS_MATCHED = PAGE_INFO
        + "/number-of-matched-documents/regexpression";
    protected static final String NO_RESULTS_MARKER = PAGE_INFO
        + "/no-results-marker/regexpression";
    protected static final String RESULTS_PER_PAGE = PAGE_INFO + "/expected-results-per-page";
    protected HTTPFormSubmitter submitter;
    protected FormParameters queryParameters;

    /**
     * It is ok to instantiate this class without arguments
     */
    public HttpMultiPageReader(HTTPFormSubmitter submitter, FormParameters queryParameters)
    {
        this.submitter = submitter;
        this.queryParameters = queryParameters;
    }

    public Enumeration getQueryResultsPages(
        String query, int resultsNeeded, String encoding, Element pageInfo
    )
        throws IOException, REException, Exception
    {
        String inputEncoding = encoding;
        String outputEncoding = encoding;

        try
        {
            // load the first page of the results.
            Map mappings = new HashMap();
            mappings.put("query.string", query);
            mappings.put("query.startFrom", "0");

            InputStream pageInputStream = submitter.submit(
                    queryParameters, mappings, outputEncoding
                );

            if (pageInputStream == null)
            {
                throw new IOException("Null returned from the submitter (HTTP request failed)");
            }

            // load the page entirely.
            byte [] pageBytes = FileHelper.readFully(pageInputStream);
            String page = new String(pageBytes, inputEncoding);

            // find out how many results there is on the page.
            ExtendedRegExp expr = new ExtendedRegExp(
                    JDOMHelper.getElement(RESULTS_MATCHED, pageInfo)
                );
            String resultsFound = expr.getProcessedMatch(page);
            Vector outputPages = new Vector();
            int resultsPerPage = Integer.parseInt(
                    JDOMHelper.getElement(RESULTS_PER_PAGE, pageInfo).getText()
                );

            if (resultsFound != null)
            {
                long results = Long.parseLong(resultsFound);

                if (resultsNeeded > results)
                {
                    resultsNeeded = (int) results;
                }

                InputStream pageStream;

                pageStream = new ByteArrayInputStream(pageBytes);

                int startFrom = 0;

                while (resultsNeeded > 0)
                {
                    outputPages.add(pageStream);

                    resultsNeeded = resultsNeeded - resultsPerPage;

                    if (resultsNeeded > 0)
                    {
                        // fetch next page of the results.
                        startFrom += resultsPerPage;
                        mappings.put("query.startFrom", Integer.toString(startFrom));
                        pageStream = submitter.submit(queryParameters, mappings, outputEncoding);
                    }
                }

                return outputPages.elements();
            }
            else
            {
                // no actual snippets marker. check whether no-results can be found.
                expr = new ExtendedRegExp(JDOMHelper.getElement(NO_RESULTS_MARKER, pageInfo));

                if (expr.getProcessedMatch(page) != null)
                {
                    log.debug("No results were found.");
                }
                else
                {
                    log.error(
                        "Page was not recognized. Neither results nor no-results tokens were found."
                    );
                    throw new Exception(
                        "Snippet parser problems. Please notify system administrator."
                    );
                }
            }
        }
        catch (UnsupportedEncodingException e)
        {
            throw new RuntimeException("Unsupported encoding.");
        }

        return null;
    }


    /**
     * Returns an InputStream to the collated results of a Query (pages are returned one after
     * another if more than one was needed to reach the required number of results). Some
     * information about the service must be provided pageInfo XML object. An example XML
     * structure for pageInfo could look as shown below.
     * <pre>
     * &lt;pageinfo&gt;
     *   &lt;expected-results-per-page&gt;50&lt;/expected-results-per-page&gt;
     * 
     *   &lt;!-- actual number of results found on a results page.
     *        This property MUST be found for the snippets to
     *        be extracted --&gt;
     *   &lt;number-of-matched-documents&gt;
     *       &lt;regexpression&gt;
     *           &lt;match&gt;&lt;![CDATA[of about[^S]*S]]&gt;&lt;/match&gt;
     *           &lt;replace regexp="[^0123456789]*" with="" /&gt;
     *       &lt;/regexpression&gt;
     *   &lt;/number-of-matched-documents&gt;
     * 
     *   &lt;!-- if no results number is found, the page is again checked whether
     *        it matched a 'no-results' page --&gt;
     *   &lt;no-results-marker&gt;
     *       &lt;regexpression&gt;
     *           &lt;match&gt;&lt;![CDATA[did not match any documents]]&gt;&lt;/match&gt;
     *       &lt;/regexpression&gt;
     *   &lt;/no-results-marker&gt;
     * &lt;/pageinfo&gt;
     * </pre>
     *
     * @param query A query string passed to the service.
     * @param resultsNeeded Number of results needed.
     * @param encoding Encoding of the output parameters and the input stream (for pattern matching
     *        only, the stream is not altered).
     * @param pageInfo A pageInfo XML DOM. Please refer to an example.
     *
     * @return An input stream to the collated result pages.
     *
     * @throws IOException Problems with reading/submitting the web page.
     * @throws REException Regular expression cannot be parsed.
     * @throws Exception If page-parsing problems occur.
     */
    public InputStream getQueryResults(
        String query, int resultsNeeded, String encoding, Element pageInfo
    )
        throws IOException, REException, Exception
    {
        Enumeration e = getQueryResultsPages(query, resultsNeeded, encoding, pageInfo);

        if (e != null)
        {
            return new SequenceInputStream(e);
        }
        else
        {
            return null;
        }
    }
}
