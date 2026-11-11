/*
 * Copyright (c) 2017-2026 LabKey Corporation
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
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.labkey.api.data.RequestCancellation;
import org.labkey.api.data.TransactionFilter;
import org.labkey.api.security.User;
import org.labkey.api.util.GUID;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.QuietCloser;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.websocket.WebSocketTracker;
import org.labkey.core.metrics.WebSocketConnectionManager;

import jakarta.servlet.http.HttpSession;
import jakarta.websocket.CloseReason;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.Session;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpoint;
import jakarta.websocket.server.ServerEndpointConfig;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;

import static org.labkey.api.util.IntegerUtils.asInteger;

/**
 * WebSocket endpoint for simple browser notification/alerting
 */

@ServerEndpoint(value="/_websocket/notifications", configurator=NotificationEndpoint.Configurator.class)
public class NotificationEndpoint extends Endpoint
{
    static final Logger LOG = LogManager.getLogger(NotificationEndpoint.class);
    static final MultiValuedMap<Integer,NotificationEndpoint> endpointsMap = new ArrayListValuedHashMap<>();
    private static final int MAX_CANCEL_REQUEST_IDS = 100;

    private Session session;
    private int userId;
    private String tabId; // The page that opened this socket, which sends the same id with the requests it may cancel
    private String sessionId; // The HTTP session the requests this socket may cancel must have been sent with
    private long openedAt;
    private boolean errored;
    private QuietCloser tracked;

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

        Integer id = asInteger(endpointConfig.getUserProperties().get("userId"));
        this.userId = null==id ? 0 : id;
        this.tabId = Objects.toString(endpointConfig.getUserProperties().get("tabId"), "");
        this.sessionId = endpointConfig.getUserProperties().get("httpSession") instanceof HttpSession httpSession ? httpSession.getId() : "";
        this.openedAt = System.currentTimeMillis();

        LOG.debug("{} onOpen", this);
        // endpointsMap holds only authenticated endpoints, but a guest's socket occupies a connection slot just the
        // same - on a public site they're nearly all guests. GH Issue 1574
        tracked = WebSocketTracker.opened();

        // Tomcat ignores @OnMessage on Endpoint subclasses
        session.addMessageHandler(new MessageHandler.Whole<String>()
        {
            @Override
            public void onMessage(String message)
            {
                incoming(message);
            }
        });

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


    /** {"type":"cancel","requestIds":[...]} cancels those in-flight requests from this socket's tab */
    private void incoming(String message)
    {
        if (!canCancel())
            return;

        try
        {
            JSONObject json = new JSONObject(message);
            JSONArray ids = json.optJSONArray("requestIds");
            if (!"cancel".equals(json.optString("type")) || ids == null || ids.isEmpty())
                return;

            if (ids.length() > MAX_CANCEL_REQUEST_IDS)
                LOG.debug("{} ignoring {} of {} request ids past the limit", this, ids.length() - MAX_CANCEL_REQUEST_IDS, ids.length());

            // Ids are client-generated UUIDs; anything else can't match a request and would only be logged as ignored
            List<String> requestIds = new ArrayList<>();
            for (int i = 0; i < Math.min(ids.length(), MAX_CANCEL_REQUEST_IDS); i++)
            {
                String requestId = ids.optString(i);
                if (GUID.isGUID(requestId))
                    requestIds.add(requestId);
            }
            if (!requestIds.isEmpty())
                TransactionFilter.cancelRequests(this.userId, this.sessionId, this.tabId, requestIds, RequestCancellation.Source.WS_MESSAGE);
        }
        catch (JSONException e)
        {
            LOG.debug("{} ignoring malformed message: {}", this, e.getMessage());
        }
    }

    private boolean canCancel()
    {
        return this.userId > 0 && !this.tabId.isEmpty() && !this.sessionId.isEmpty() && RequestCancellation.isCancelAbandonedRequestsEnabled();
    }


    @Override
    public void onClose(Session session, CloseReason closeReason)
    {
        LOG.debug("{} onClose: {}", this, closeReason.toString());
        synchronized (endpointsMap)
        {
            endpointsMap.removeMapping(this.userId, this);
        }
        if (null != tracked)
            tracked.close();
        if (canCancel())
        {
            int code = closeReason.getCloseCode().getCode();
            long lifetimeMs = System.currentTimeMillis() - this.openedAt;
            // An abnormal close near the load balancer's idle timeout means it killed a silent socket; errored means the server
            // closed it after a failed send; 1008 means Tomcat closed it because the HTTP session ended. The tab may still be
            // open and waiting, so leave its requests to the read-only timeout.
            if (this.errored || code == CloseReason.CloseCodes.CLOSED_ABNORMALLY.getCode() || code == CloseReason.CloseCodes.VIOLATED_POLICY.getCode())
            {
                LOG.info("websocket-close userId={} lifetimeMs={} code={} errored={} reason={}", this.userId, lifetimeMs, code, this.errored, closeReason.getReasonPhrase());
            }
            else
            {
                LOG.debug("websocket-close userId={} lifetimeMs={} code={} errored={} reason={}", this.userId, lifetimeMs, code, this.errored, closeReason.getReasonPhrase());
                // The page is gone, so nobody is left to read its results
                TransactionFilter.cancelRequests(this.userId, this.sessionId, this.tabId, null, RequestCancellation.Source.WS_CLOSE);
            }
        }
        super.onClose(session, closeReason);
    }


    @Override
    public void onError(Session session, Throwable throwable)
    {
        this.errored = true;
        LOG.debug("{} onError: {}", this, throwable.getMessage());
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

            List<String> tabIds = request.getParameterMap().get("tabId");
            config.getUserProperties().put("tabId", tabIds == null || tabIds.isEmpty() ? "" : tabIds.getFirst());
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
            arr = coll.toArray(new NotificationEndpoint[0]);
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
            LOG.debug("{}: {}", this, x.getMessage());
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
                LOG.debug("{} sendText: {}", endpoint, eventName);
            });
            count++;
        }

        if (count == 0)
        {
            if (userIds.size() == 1)
                LOG.debug("WebSocket: no sessions to send for {}: {}", userIds.getFirst(), eventName);
            else
                LOG.debug("WebSocket: no sessions to send:{}", eventName);
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
