package org.labkey.api.reports;

import javax.script.ScriptContext;
import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionBindingListener;


public class RConnectionHolder implements HttpSessionBindingListener
{
    String _connectionId;
    RserveScriptEngine _engine;

    public RConnectionHolder(String connectionId, RserveScriptEngine engine)
    {
        _connectionId = connectionId;
        _engine = engine;
    }

    public void valueBound(HttpSessionBindingEvent httpSessionBindingEvent)
    {
        // Do nothing
    }

    public void valueUnbound(HttpSessionBindingEvent httpSessionBindingEvent)
    {
        _engine.closeConnection(_connectionId);
        _engine.getContext().getBindings(ScriptContext.ENGINE_SCOPE).remove(RserveScriptEngine.R_CONNECTION_ID);
    }

    public String getConnectionId()
    {
        return _connectionId;
    }
}