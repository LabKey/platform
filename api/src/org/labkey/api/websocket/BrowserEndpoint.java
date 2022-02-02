/*
 * Copyright (c) 2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.websocket;

import org.apache.logging.log4j.Logger;
import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.util.logging.LogHelper;

import javax.servlet.ServletException;
import javax.servlet.http.HttpSession;
import javax.websocket.ClientEndpoint;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.HandshakeResponse;
import javax.websocket.MessageHandler;
import javax.websocket.PongMessage;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpointConfig;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

// moved from https://github.com/LabKey/rstudio/blob/release18.3/src/org/labkey/rstudio/shiny/BrowserEndpoint.java

public abstract class BrowserEndpoint extends Endpoint
{
    static final Logger LOG = LogHelper.getLogger(BrowserEndpoint.class, "Server side websocket communication status");

    protected Session browserSession;
    ServerEndpoint serverEndpoint = null;

    private void close()
    {
        try
        {
            if (null != browserSession && browserSession.isOpen())
                browserSession.close();
        }
        catch (IOException io)
        {
            LOG.info("error closing websocket", io);
        }
    }

    public static class _Configurator extends ServerEndpointConfig.Configurator
    {
        @Override
        public void modifyHandshake(ServerEndpointConfig config, HandshakeRequest request, HandshakeResponse response)
        {
            HttpSession httpSession = (HttpSession) request.getHttpSession();
            User user = SecurityManager.getSessionUser(httpSession);
            if (user != null && !user.isActive())
            {
                httpSession.invalidate();
                user = null;
            }

            config.getUserProperties().put("requestHeaders", request.getHeaders());
            if (httpSession != null)
                config.getUserProperties().put("httpSession", httpSession);

            config.getUserProperties().put("userId", null == user ? 0 : user.getUserId());
            config.getUserProperties().put("attributes", AuthenticationManager.getAuthenticationAttributes(httpSession));
        }
    }

    public abstract String getWSRemoteUri(Session session, EndpointConfig endpointConfig) throws URISyntaxException, MalformedURLException, ServletException;

    @Override
    public void onOpen(Session session, EndpointConfig endpointConfig)
    {
        try
        {
            String uri = getWSRemoteUri(session, endpointConfig);
            LOG.debug("BrowserEndpoint.onOpen( " + session.getRequestURI() + " -> " + uri);
            Map<String, List<String>> requestHeaders = (Map<String, List<String>>) endpointConfig.getUserProperties().get("requestHeaders");
            this.browserSession = session;
            this.serverEndpoint = new ServerEndpoint(new URI(uri), requestHeaders, endpointConfig.getUserProperties());

            // wire up message handlers
            new _Forwarder(browserSession, serverEndpoint.serverSession, "BrowserEndpoint");
        }
        catch (URISyntaxException | IOException | DeploymentException | ServletException ex)
        {
            LOG.debug("BrowserEndpoint.onOpen", ex);
            UnexpectedException.rethrow(ex);
        }
    }

    @Override
    public void onClose(Session session, CloseReason closeReason)
    {
        LOG.debug("BrowserEndpoint.onClose()");
        if (null != serverEndpoint)
            serverEndpoint.close();
    }

    @Override
    public void onError(Session session, Throwable throwable)
    {
        LOG.error("BrowserEndpoint.onError()", throwable);
    }


    public abstract Map<String,List<String>> prepareProxyHeaders(URI remoteURI, Map<String,List<String>> requestHeaders, Map<String,Object> properties);

    @ClientEndpoint
    class ServerEndpoint extends Endpoint
    {
        final Session serverSession;

        /**
         *  headers are from the original upgrade request
         *  properties contains other configuration set in Configurator.modifyHandshake()
         */
        ServerEndpoint(URI remoteURI, Map<String,List<String>> requestHeaders, Map<String,Object> properties) throws DeploymentException, IOException
        {
            final Map<String,List<String>> proxyHeaders = prepareProxyHeaders(remoteURI, requestHeaders, properties);

            WebSocketContainer clientEndPoint = ContainerProvider.getWebSocketContainer();
            ClientEndpointConfig config = ClientEndpointConfig.Builder.create()
                    .configurator(new ClientEndpointConfig.Configurator()
                    {
                        @Override
                        public void beforeRequest(Map<String, List<String>> headers)
                        {
                            headers.putAll(proxyHeaders);
                        }
                    })
                    .build();
            serverSession = clientEndPoint.connectToServer(this, config, remoteURI);

            new _Forwarder(serverSession, browserSession, "ServerEndpoint");
        }

        private void close()
        {
            try
            {
                if (null != serverSession && serverSession.isOpen())
                    serverSession.close();
            }
            catch (IOException io)
            {
                LOG.info("Exception closing websocket session", io);
            }
        }

        @Override
        public void onError(Session session, Throwable throwable)
        {
            LOG.error("ServerEndpoint.onError()", throwable);
            super.onError(session, throwable);
        }

        @Override
        public void onOpen(Session session, EndpointConfig endpointConfig)
        {
            LOG.trace("ServerEndpoint.onOpen()");
        }

        @Override
        public void onClose(Session session, CloseReason closeReason)
        {
            BrowserEndpoint.this.close();
            super.onClose(session, closeReason);
        }
    }

    static class _Forwarder
    {
        final Session from;
        final Session to;
        final String debugName;

        _Forwarder(Session fromSession, Session toSession, String name)
        {
            this.from = fromSession;
            this.to = toSession;
            this.debugName = name;

            // NOTE: using lambda for addMessageHandler() seems to break tomcat's type inspection
            from.addMessageHandler(new MessageHandler.Partial<String>()
            {
                final StringBuffer partial = new StringBuffer();
                @Override
                public void onMessage(String s, boolean last)
                {
                    if (!last)
                    {
                        partial.append(s);
                        return;
                    }
                    if (partial.length() > 0)
                    {
                        partial.append(s);
                        s = partial.toString();
                        partial.setLength(0);
                    }
                    try
                    {
                        synchronized (to)
                        {
                            if (to.isOpen())
                                to.getBasicRemote().sendText(s);
                        }
                        LOG.trace(debugName + ".onMessage(" + s + ")");
                    }
                    catch (IOException x)
                    {
                        try { from.close(); } catch (IOException io) {/* */}
                        try { to.close(); } catch (IOException io) {/* */}
                        LOG.warn("websocket proxy exception", x);
                    }
                }
            });
            from.addMessageHandler(new MessageHandler.Whole<ByteBuffer>()
            {
                @Override
                public void onMessage(ByteBuffer byteBuffer)
                {
                    try
                    {
                        synchronized (to)
                        {
                            if (to.isOpen())
                                to.getBasicRemote().sendBinary(byteBuffer);
                        }
                        LOG.trace(debugName + ".onMessage(<binary>)");
                    }
                    catch (IOException x)
                    {
                        try { from.close(); } catch (IOException io) {/* */}
                        try { to.close(); } catch (IOException io) {/* */}
                        LOG.warn("websocket proxy exception", x);
                    }
                }
            });
            from.addMessageHandler(new MessageHandler.Whole<PongMessage>()
            {
                @Override
                public void onMessage(PongMessage pongMessage)
                {
                    try
                    {
                        synchronized (to)
                        {
                            if (to.isOpen())
                                to.getBasicRemote().sendPong(pongMessage.getApplicationData());
                        }
                        LOG.trace(debugName + ".pong()");
                    }
                    catch (IOException x)
                    {
                        try { from.close(); } catch (IOException io) {/* */}
                        try { to.close(); } catch (IOException io) {/* */}
                        LOG.warn("websocket proxy exception", x);
                    }
                }
            });
        }
    }
}
