//
// ========================================================================
// Copyright (c) 1995-2022 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under the
// terms of the Eclipse Public License v. 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
// which is available at https://www.apache.org/licenses/LICENSE-2.0.
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.eclipse.jetty.ee9.servlet;

import jakarta.servlet.Servlet;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.ee9.nested.HttpChannel;
import org.eclipse.jetty.logging.StacklessLogging;
import org.eclipse.jetty.server.LocalConnector;
import org.eclipse.jetty.server.Server;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;

public class ServletRequestStateTest
{
    private static final Logger LOG = LoggerFactory.getLogger(ServletRequestStateTest.class);

    private Server _server;
    private LocalConnector _connector;
    private StacklessLogging _stackless;
    private ServletContextHandler _context;

    private static Exception exception;

    @BeforeEach
    public void init() throws Exception
    {
        _server = new Server();
        _connector = new LocalConnector(_server);
        _server.addConnector(_connector);

        _context = new ServletContextHandler(ServletContextHandler.NO_SECURITY | ServletContextHandler.NO_SESSIONS);

        _server.setHandler(_context);

        _context.setContextPath("/");
        _context.addServlet(DefaultServlet.class, "/");
        _context.addServlet(FlushThenSendErrorServlet.class, "/flushsenderror/*");
        _context.addServlet(FlushThenResetServlet.class, "/flushreset/*");
        _context.addServlet(FlushThenResetBufferServlet.class, "/flushresetbuffer/*");

        _server.start();
        _stackless = new StacklessLogging(ServletHandler.class);
        exception = null;

    }

    @AfterEach
    public void destroy() throws Exception
    {
        _stackless.close();
        _server.stop();
        _server.join();
    }

    @Test
    public void illegalStateExceptionOnSendErrorAfterFlush() throws Exception
    {
        try (StacklessLogging stackless = new StacklessLogging(HttpChannel.class))
        {
            String response = _connector.getResponse("GET /flushsenderror/foo HTTP/1.0\r\n\r\n");
            assertThat(response, Matchers.containsString("HTTP/1.1 200 OK"));
            assertThat(response, Matchers.containsString("All good"));
            assertThat(exception, Matchers.notNullValue());
            assertThat(exception, Matchers.instanceOf(IllegalStateException.class));
        }
    }

    @Test
    public void illegalStateExceptionOnResetAfterFlush() throws Exception
    {
        try (StacklessLogging stackless = new StacklessLogging(HttpChannel.class))
        {
            String response = _connector.getResponse("GET /flushreset/foo HTTP/1.0\r\n\r\n");
            assertThat(response, Matchers.containsString("HTTP/1.1 200 OK"));
            assertThat(response, Matchers.containsString("All good"));
            assertThat(exception, Matchers.notNullValue());
            assertThat(exception, Matchers.instanceOf(IllegalStateException.class));
        }
    }

    @Test
    public void illegalStateExceptionOnResetBufferAfterFlush() throws Exception
    {
        try (StacklessLogging stackless = new StacklessLogging(HttpChannel.class))
        {
            String response = _connector.getResponse("GET /flushresetbuffer/foo HTTP/1.0\r\n\r\n");
            assertThat(response, Matchers.containsString("HTTP/1.1 200 OK"));
            assertThat(response, Matchers.containsString("All good"));
            assertThat(exception, Matchers.notNullValue());
            assertThat(exception, Matchers.instanceOf(IllegalStateException.class));
        }
    }

    public static class FlushThenSendErrorServlet extends HttpServlet implements Servlet
    {

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
        {
            response.getWriter().print("All good");
            response.flushBuffer();
            try
            {
                response.sendError(403, "never here");
            }
            catch (IllegalStateException e)
            {
                ServletRequestStateTest.exception = e;
            }
        }
    }

    public static class FlushThenResetServlet extends HttpServlet implements Servlet
    {

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
        {
            response.getWriter().print("All good");
            response.flushBuffer();
            try
            {
                response.reset();
            }
            catch (IllegalStateException e)
            {
                ServletRequestStateTest.exception = e;
            }
        }
    }

    public static class FlushThenResetBufferServlet extends HttpServlet implements Servlet
    {

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
        {
            response.getWriter().print("All good");
            response.flushBuffer();
            try
            {
                response.resetBuffer();
            }
            catch (IllegalStateException e)
            {
                ServletRequestStateTest.exception = e;
            }
        }
    }
}
