package org.labkey.core.notification;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.labkey.api.security.User;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.MemTrackerListener;

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
import java.util.Collection;
import java.util.Set;

/**
 * WebSocket endpoint for simple browser notification/alerting
 */

@ServerEndpoint(value="/_websocket/notifications", configurator=NotificationEndpoint.Configurator.class)
public class NotificationEndpoint extends Endpoint
{
    static final MultiValuedMap<Integer,NotificationEndpoint> endpointsMap = new ArrayListValuedHashMap<>();

    private Session session;
    private int userId;

    public NotificationEndpoint()
    {
    }

    @Override
    public void onOpen(Session session, EndpointConfig endpointConfig)
    {
        this.session = session;
        Integer id = (Integer)endpointConfig.getUserProperties().get("userId");
        this.userId = null==id ? 0 : id;
        synchronized (endpointsMap)
        {
            if (this.userId > 0)
            {
                endpointsMap.put(this.userId, this);
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
        synchronized (endpointsMap)
        {
            endpointsMap.removeMapping(this.userId, this);
        }
        super.onClose(session, closeReason);
    }


    @Override
    public void onError(Session session, Throwable throwable)
    {
        super.onError(session, throwable);
    }


    public static class Configurator extends ServerEndpointConfig.Configurator
    {

        @Override
        public void modifyHandshake(ServerEndpointConfig config, HandshakeRequest request, HandshakeResponse response)
        {
            User user = org.labkey.api.security.SecurityManager.getSessionUser((HttpSession) request.getHttpSession());
            config.getUserProperties().put("userId", null==user ? 0 : user.getUserId());
        }
    }


    private static void sendEvent(int userId, String eventName)
    {
        NotificationEndpoint[] arr;
        synchronized (endpointsMap)
        {
            Collection<NotificationEndpoint> coll = endpointsMap.get(userId);
            arr = coll.toArray(new NotificationEndpoint[coll.size()]);
        }
        for (NotificationEndpoint endpoint : arr)
        {
            try
            {
                synchronized (endpoint)
                {
                    endpoint.session.getBasicRemote().sendText("{\"event\":\"" + eventName + "\"}");
                }
            }
            catch (IOException|IllegalStateException x)
            {
                synchronized (endpointsMap)
                {
                    endpointsMap.removeMapping(userId, endpoint);
                    try { endpoint.session.close(); } catch (IOException ex){}
                }
            }
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
