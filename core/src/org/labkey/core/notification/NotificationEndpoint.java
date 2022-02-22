/*
 * Copyright (c) 2017-2018 LabKey Corporation
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
package org.labkey.core.notification;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.security.User;
import org.labkey.api.util.MemTracker;
import org.labkey.api.security.SecurityManager;
import org.labkey.core.metrics.WebSocketConnectionManager;

import javax.servlet.http.HttpSession;
import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.HandshakeResponse;
import javax.websocket.OnMessage;
import javax.websocket.Session;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpoint;
import javax.websocket.server.ServerEndpointConfig;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

/**
 * WebSocket endpoint for simple browser notification/alerting
 */

@ServerEndpoint(value="/_websocket/notifications", configurator=NotificationEndpoint.Configurator.class)
public class NotificationEndpoint extends Endpoint
{
    static final Logger LOG = LogManager.getLogger(NotificationEndpoint.class);
    static final MultiValuedMap<Integer,NotificationEndpoint> endpointsMap = new ArrayListValuedHashMap<>();

    private Session session;
    private int userId;
    private boolean errored;

    public NotificationEndpoint()
    {
        // Issue 42452: Suppress overly verbose logging from Tomcat about WebSocket connections not closing in the ideal pattern
        // https://bz.apache.org/bugzilla/show_bug.cgi?id=59062
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger("org.apache.tomcat.websocket.server.WsRemoteEndpointImplServer");
        logger.setLevel(Level.WARNING);
    }

    @Override
    public void onOpen(Session session, EndpointConfig endpointConfig)
    {
        this.session = session;

        Integer id = (Integer)endpointConfig.getUserProperties().get("userId");
        this.userId = null==id ? 0 : id;

        LOG.debug(this.toString() + " onOpen");
        synchronized (endpointsMap)
        {
            if (this.userId > 0)
            {
                endpointsMap.put(this.userId, this);
                WebSocketConnectionManager.getInstance().incrementCounter(true);
                MemTracker.get().put(this);
            }
        }
    }


    @OnMessage
    public void incoming(String message)
    {
    }


    @Override
    public void onClose(Session session, CloseReason closeReason)
    {
        LOG.debug(this.toString() + " onClose: " + closeReason.toString());
        synchronized (endpointsMap)
        {
            endpointsMap.removeMapping(this.userId, this);
        }
        super.onClose(session, closeReason);
    }


    @Override
    public void onError(Session session, Throwable throwable)
    {
        this.errored = true;
        LOG.debug(this.toString() + " onError: " + throwable.getMessage());
        super.onError(session, throwable);
    }

    @Override
    public String toString()
    {
        return "[WebSocket userId=" + this.userId + "]";
    }

    public static class Configurator extends ServerEndpointConfig.Configurator
    {

        @Override
        public void modifyHandshake(ServerEndpointConfig config, HandshakeRequest request, HandshakeResponse response)
        {
            HttpSession session = (HttpSession) request.getHttpSession();
            User user = SecurityManager.getSessionUser(request);

            // config.getUserProperties() is backed by a ConcurrentHashMap which does not allow null keys or values.
            if (session != null)
                config.getUserProperties().put("httpSession", session);

            config.getUserProperties().put("userId", null == user ? 0 : user.getUserId());
        }
    }

    interface Fn
    {
        void apply() throws IOException, IllegalStateException;
    }

    private static List<NotificationEndpoint> getEndpoints(int userId)
    {
        NotificationEndpoint[] arr;
        synchronized (endpointsMap)
        {
            Collection<NotificationEndpoint> coll = endpointsMap.get(userId);
            // prune errored or closed endpoints
            coll.removeIf(e -> e.errored || !e.session.isOpen());
            arr = coll.toArray(new NotificationEndpoint[coll.size()]);
        }
        return Arrays.asList(arr);
    }

    private static List<NotificationEndpoint> getEndpoints(List<Integer> userIds)
    {
        List<NotificationEndpoint> endpoints = new ArrayList<>();
        synchronized (endpointsMap)
        {
            for (Integer userId : userIds)
            {
                Collection<NotificationEndpoint> coll = endpointsMap.get(userId);
                // prune errored or closed endpoints
                coll.removeIf(e -> e.errored || !e.session.isOpen());
                endpoints.addAll(coll);
            }
        }
        return endpoints;
    }

    // execute function in try/catch and close session on exception
    private boolean safely(Fn fn)
    {
        try
        {
            synchronized (this)
            {
                fn.apply();
                return true;
            }
        }
        catch (Exception x)
        {
            LOG.debug(toString() + ": " + x.getMessage());
            // NOTE: This NotificationEndpoint will be removed from the endpointsMap in onClose, called from WsSocket.close()
            // but remember it is a bad endpoint if someone tries to use it before the onClose method runs.
            this.errored = true;
            try { this.session.close(); } catch (IOException ex){}
        }
        return false;
    }

    private static void sendEvent(int userId, String eventName)
    {
        sendEvent(Collections.singletonList(userId), eventName);
    }

    private static void sendEvent(List<Integer> userIds, String eventName)
    {
        final String data = "{\"event\":\"" + eventName + "\"}";

        long count = 0;
        // do not refactor to use stream as it might result in deadlock
        for (NotificationEndpoint endpoint : getEndpoints(userIds))
        {
            endpoint.safely(() -> {
                endpoint.session.getBasicRemote().sendText(data);
                LOG.debug(endpoint.toString() + " sendText: " + eventName);
            });
            count++;
        }

        if (count == 0)
        {
            if (userIds.size() == 1)
                LOG.debug("WebSocket: no sessions to send for " + userIds.get(0) + ": " + eventName);
            else
                LOG.debug("WebSocket: no sessions to send:" + eventName);
        }

    }

    public static void sendEvent(int userId, Enum e)
    {
        sendEvent(userId, e.getClass().getCanonicalName() + "#" + e.name());
    }

    public static void sendEvent(int userId, Class clazz)
    {
        sendEvent(userId, clazz.getCanonicalName());
    }

    public static void sendEvent(List<Integer> userIds, Enum e)
    {
        sendEvent(userIds, e.getClass().getCanonicalName() + "#" + e.name());
    }

    public static void sendEvent(List<Integer> userIds, Class clazz)
    {
        sendEvent(userIds, clazz.getCanonicalName());
    }

    static
    {
        MemTracker.get().register(set ->
        {
            synchronized (endpointsMap)
            {
                set.addAll(endpointsMap.values());
            }
        });
    }
}
