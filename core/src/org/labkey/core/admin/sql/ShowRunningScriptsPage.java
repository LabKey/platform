/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

package org.labkey.core.admin.sql;

import org.labkey.api.jsp.JspBase;
import org.labkey.api.data.SqlScriptRunner;
import org.labkey.api.view.ActionURL;

import java.util.List;

/**
 * User: jeckels
 * Date: Apr 5, 2006
 */
public abstract class ShowRunningScriptsPage extends JspBase
{
    private List<SqlScriptRunner.SqlScript> _runningScripts;
    private ActionURL _waitForScriptsURL;
    private ActionURL _currentURL;

    public void setScripts(List<SqlScriptRunner.SqlScript> runningScripts)
    {
        _runningScripts = runningScripts;
    }

    public List<SqlScriptRunner.SqlScript> getScripts()
    {
        return _runningScripts;
    }

    public void setWaitForScriptsURL(ActionURL waitForScriptsURL)
    {
        _waitForScriptsURL = waitForScriptsURL;
    }

    public ActionURL getWaitForScriptsURL()
    {
        return _waitForScriptsURL;
    }

    public ActionURL getCurrentURL()
    {
        return _currentURL;
    }

    public void setCurrentURL(ActionURL currentURL)
    {
        _currentURL = currentURL;
    }
}
