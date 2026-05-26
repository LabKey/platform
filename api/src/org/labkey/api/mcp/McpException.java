package org.labkey.api.mcp;

// A special exception that MCP endpoints can throw when they want to provide guidance to the client without making
// it a big red error. The message will be extracted and sent as text to the client.
public class McpException extends RuntimeException
{
    public McpException(String message)
    {
        super(message);
    }
}
