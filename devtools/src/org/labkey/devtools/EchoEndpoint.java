package org.labkey.devtools;

import jakarta.websocket.OnMessage;
import jakarta.websocket.server.ServerEndpoint;

@ServerEndpoint("/org.labkey.devtools.EchoEndpoint")
public class EchoEndpoint
{
    @OnMessage(maxMessageSize = 10240)
    public String handleTextMessage(String message)
    {
        return message;
    }

    @OnMessage(maxMessageSize = 1024000)
    public byte[] handleBinaryMessage(byte[] buffer)
    {
        return buffer;
    }
}
