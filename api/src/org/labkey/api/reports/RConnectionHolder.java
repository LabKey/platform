/*
 * Copyright (c) 2012-2014 LabKey Corporation
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
package org.labkey.api.reports;

import javax.script.ScriptException;
import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionBindingListener;
import org.rosuda.REngine.Rserve.RConnection;
import java.util.HashSet;

//
// This object and its held RConnection will outlive the underlying ScriptEngine
//
public class RConnectionHolder implements HttpSessionBindingListener
{
    RConnection _connection;
    boolean _inUse;
    Object _clientContext; // client supplied value to help identify a report session
    String _reportSessionId;
    static final HashSet<String> _reportSessions = new HashSet<>();
    // list of functions in this session that can be called directly by the executeFunction method
    HashSet<String> _callableFunctions = new HashSet<>();

    public RConnectionHolder(String reportSessionId, Object clientContext)
    {
        _clientContext = clientContext;
        _reportSessionId = reportSessionId;
        synchronized(_reportSessions)
        {
            _reportSessions.add(reportSessionId);
        }
    }

    protected void finalize()
    {
        close();
    }

    public static HashSet<String> getReportSessions()
    {
        return _reportSessions;
    }

    public void valueBound(HttpSessionBindingEvent httpSessionBindingEvent)
    {
        // Do nothing
    }

    public void valueUnbound(HttpSessionBindingEvent httpSessionBindingEvent)
    {
        close();
        synchronized (_reportSessions)
        {
            if (_reportSessions.contains(_reportSessionId))
                _reportSessions.remove(_reportSessionId);
        }
    }

    public RConnection getConnection()
    {
        return _connection;
    }

    public void setConnection(RConnection connection)
    {
        if (_connection != connection)
        {
            close();
            _connection = connection;
        }
    }

    public void addCallableFunctions(HashSet<String> functionList)
    {
        for(String function : functionList)
        {
            if (!_callableFunctions.contains(function))
                _callableFunctions.add(function);
        }
    }

    public boolean isFunctionCallable(String function)
    {
        return _callableFunctions.contains(function);
    }

    public synchronized void acquire() throws ScriptException
    {
        if (!isInUse())
            setInUse(true);
        else
            throw new ScriptException("The report session is currently in use");
    }

    public synchronized void release()
    {
        setInUse(false);
    }

    public boolean isInUse()
    {
        return _inUse;
    }

    public Object getClientContext()
    {
        return _clientContext;
    }

    public String getReportSessionId()
    {
        return _reportSessionId;
    }

    public void setInUse(boolean value)
    {
        _inUse = value;
    }

    private void close()
    {
        if (_connection != null)
        {
            if (_connection.isConnected())
            {
                _connection.close();
            }
            _connection = null;
        }
    }
}