/*
 * Carrot2 project.
 *
 * Copyright (C) 2002-2019, Dawid Weiss, Stanisław Osiński.
 * All rights reserved.
 *
 * Refer to the full license file "carrot2.LICENSE"
 * in the root folder of the repository checkout or at:
 * https://www.carrot2.org/carrot2.LICENSE
 */
package org.carrot2.dcs.servlets;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.atomic.AtomicInteger;
import javax.servlet.http.HttpServletResponse;
import org.assertj.core.api.Assertions;
import org.carrot2.dcs.client.ClusterResponse;
import org.carrot2.dcs.client.ErrorResponse;
import org.carrot2.math.mahout.Arrays;
import org.junit.Test;

public class ClusterServletTest extends AbstractServletTest {
  @Test
  public void testSimpleRequest() throws Exception {
    verifyRequest("simple.request.json", "simple.response.json");
  }

  @Test
  public void testTemplateRequest() throws Exception {
    setupMockTemplates("template1.json", "template2.json");

    when(request.getParameter(ClusterServlet.PARAM_TEMPLATE)).thenReturn("template1");
    verifyRequest("template.request.json", "template.response.json");
  }

  @Test
  public void testAttrInRequest() throws Exception {
    verifyRequest("attrInRequest.request.json", "attrInRequest.response.json");
  }

  @Test
  public void testAttrInTemplate() throws Exception {
    setupMockTemplates("template1.json", "template2.json");

    when(request.getParameter(ClusterServlet.PARAM_TEMPLATE)).thenReturn("template2");
    verifyRequest("attrInTemplate.request.json", "attrInTemplate.response.json");
  }

  @Test
  public void testInvalidValueAttr() throws Exception {
    verifyInvalidRequest(
        HttpServletResponse.SC_BAD_REQUEST,
        "invalidValueAttr.request.json",
        "invalidValueAttr.response.json");
  }

  @Test
  public void testExtraUnusedAttr() throws Exception {
    verifyInvalidRequest(
        HttpServletResponse.SC_BAD_REQUEST,
        "extraUnusedAttr.request.json",
        "extraUnusedAttr.response.json");
  }

  private void verifyInvalidRequest(
      int expectedStatus, String requestResource, String responseResource) throws Exception {
    String requestData = resourceString(requestResource);
    log.debug("Request: " + requestData);

    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    when(response.getWriter()).thenReturn(pw);
    when(request.getInputStream()).thenReturn(new StringServletInputStream(requestData));

    AtomicInteger returnedStatus = new AtomicInteger();
    doAnswer(
            (a) -> {
              returnedStatus.set(a.getArgument(0));
              return null;
            })
        .when(response)
        .sendError(anyInt(), anyString());

    ClusterServlet servlet = new ClusterServlet();
    servlet.init(config);
    servlet.doPost(request, response);
    pw.flush();

    // Verify status.
    Assertions.assertThat(returnedStatus.get()).isEqualTo(expectedStatus);

    // Verify against expected response.
    String content = sw.toString();
    log.debug("Actual response: " + content);

    // Clear the stack trace since it's apt to change.
    ObjectMapper om = new ObjectMapper();

    ErrorResponse errorResponse = om.readValue(content, ErrorResponse.class);
    errorResponse.stacktrace = "<removed>";

    DefaultPrettyPrinter pp = new DefaultPrettyPrinter();
    pp.indentArraysWith(new DefaultIndenter("  ", DefaultIndenter.SYS_LF));
    content = om.writer().with(pp).writeValueAsString(errorResponse);
    Assertions.assertThat(content).isEqualToIgnoringNewLines(resourceString(responseResource));
  }

  private void verifyRequest(String requestResource, String responseResource) throws Exception {
    String requestData = resourceString(requestResource);
    log.debug("Request: " + requestData);

    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    when(response.getWriter()).thenReturn(pw);
    when(request.getInputStream()).thenReturn(new StringServletInputStream(requestData));

    doAnswer(
            (a) -> {
              throw new RuntimeException(
                  "Unexpected sendError(): " + Arrays.toString(a.getArguments()));
            })
        .when(response)
        .sendError(anyInt(), anyString());

    ClusterServlet servlet = new ClusterServlet();
    servlet.init(config);
    servlet.doPost(request, response);
    pw.flush();

    // Verify against expected response.
    String content = sw.toString();
    log.debug("Actual response: " + content);
    Assertions.assertThat(content).isEqualToIgnoringNewLines(resourceString(responseResource));

    // And try parsing against the client model.
    ObjectMapper om = new ObjectMapper();
    om.readValue(content, ClusterResponse.class);
  }
}