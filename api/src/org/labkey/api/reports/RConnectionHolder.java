/*
 * Copyright (c) 2012 LabKey Corporation
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