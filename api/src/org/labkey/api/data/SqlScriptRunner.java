/*
 * Copyright (c) 2003-2005 Fred Hutchinson Cancer Research Center
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

package org.labkey.api.data;

import org.apache.commons.collections.MultiMap;
import org.apache.commons.collections.map.MultiValueMap;
import org.apache.log4j.Logger;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;

import java.sql.SQLException;
import java.util.*;

/**
 * User: arauch
 * Date: Jun 14, 2005
 * Time: 2:56:01 PM
 */
public class SqlScriptRunner
{
    /**
     * Key modules can use in moduleContext properties to indicate whether scripts were run.
     */
    public static String SCRIPTS_RUN_KEY = "scriptsRun";
    private static Logger _log = Logger.getLogger(SqlScriptRunner.class);

    private static BackgroundScriptRunner WORKER;
    private static final List<SqlScript> _scriptsToRun = new ArrayList<SqlScript>();
    private static final Set<String> _previousProviders = new HashSet<String>();
    private static final Object SCRIPT_LOCK = new Object();


    // Wait for a single script to finish or timeout, whichever comes first
    // Specify 0 to wait indefinitely until current script finishes
    public static boolean waitForScriptToFinish(int timeout) throws InterruptedException
    {
        synchronized (SCRIPT_LOCK)
        {
            if (_scriptsToRun.isEmpty())
            {
                return true;
            }
            SCRIPT_LOCK.wait(timeout);

            return _scriptsToRun.isEmpty();
        }
    }


    // Wait indefinitely until all scripts finish running
    public static void waitForScriptsToFinish() throws InterruptedException
    {
        while (!waitForScriptToFinish(0));
    }


    public static List<SqlScript> getRunningScripts()
    {
        synchronized (SCRIPT_LOCK)
        {
            return new ArrayList<SqlScript>(_scriptsToRun);
        }
    }

    public static Exception getException()
    {
        if (null != WORKER)
            return WORKER.getException();
        else
            return null;
    }


    // TODO: Should move next two methods to Controller... but Controller is not accessible outside module
    public static ActionURL getDefaultURL(ActionURL returnURL, String moduleName, double fromVersion, double toVersion, boolean express)
    {
        return getURL(returnURL, moduleName, null, fromVersion, toVersion, express);
    }

    public static ActionURL getURL(ActionURL returnURL, String moduleName, String schemaName, double fromVersion, double toVersion, boolean express)
    {
        ActionURL url = new ActionURL();

        if (express)
        {
            url.setAction("runRecommended");
            url.addParameter("finish", "1");
        }
        else
            url.setAction("showList");

        url.setPageFlow("admin-sql");
        url.setExtraPath(null);
        url.addParameter("moduleName", moduleName);

        if (null != schemaName)
            url.addParameter("schemaName", schemaName);

        url.addParameter("from", String.valueOf(fromVersion));
        url.addParameter("to", String.valueOf(toVersion));
        url.addParameter("uri", returnURL.getLocalURIString());

        return url;
    }


    // Returns all the existing scripts matching schemaName that have not been run
    public static List<SqlScript> getNewScripts(SqlScriptProvider provider, String schemaName) throws SQLException
    {
        List<SqlScript> allScripts;

        try
        {
            allScripts = provider.getScripts(schemaName);
        }
        catch(SqlScriptException e)
        {
            throw new RuntimeException(e);
        }

        List<SqlScript> newScripts = new ArrayList<SqlScript>();
        Set<SqlScript> runScripts = SqlScriptManager.getRunScripts(provider);

        for (SqlScript script : allScripts)
            if (!runScripts.contains(script))
                newScripts.add(script);

        return newScripts;
    }


    public static List<SqlScript> getRecommendedScripts(SqlScriptProvider provider, String schemaName, double from, double to) throws SQLException
    {
        List<SqlScript> newScripts = getNewScripts(provider, schemaName);
        MultiMap mm = new MultiValueMap();

        for (SqlScript script : newScripts)
            mm.put(script.getSchemaName(), script);

        List<SqlScript> scripts = new ArrayList<SqlScript>();
        String[] schemaNames = ((Set<String>)mm.keySet()).toArray(new String[0]);
        Arrays.sort(schemaNames, String.CASE_INSENSITIVE_ORDER);
        for (String name : schemaNames)
            scripts.addAll(getRecommendedScripts((Collection<SqlScript>) mm.get(name), from, to));

        return scripts;
    }


    // Get the recommended scripts from a given collection of scripts
    public static List<SqlScript> getRecommendedScripts(Collection<SqlScript> schemaScripts, double from, double to)
    {
        // Create a map of SqlScript objects.  For each fromVersion, store only the script with the highest toVersion
        Map<Double, SqlScript> m = new HashMap<Double, SqlScript>();

        for (SqlScript script : schemaScripts)
        {
            if (script.getFromVersion() >= from && script.getToVersion() <= to)
            {
                SqlScript current = m.get(script.getFromVersion());

                if (null == current || script.getToVersion() > current.getToVersion())
                    m.put(script.getFromVersion(), script);
            }
        }

        List<SqlScript> scripts = new ArrayList<SqlScript>();

        while (true)
        {
            SqlScript nextScript = getNearestFrom(m, from);

            if (null == nextScript)
                break;

            from = nextScript.getToVersion();
            scripts.add(nextScript);
        }

        return scripts;
    }


    private static SqlScript getNearestFrom(Map<Double, SqlScript> m, double targetFrom)
    {
        SqlScript nearest = m.get(targetFrom);

        if (null == nearest)
        {
            double lowest = Double.MAX_VALUE;

            for (double from : m.keySet())
            {
                if (from >= targetFrom && from < lowest)
                    lowest = from;
            }

            nearest = m.get(lowest);
        }

        return nearest;
    }


    public static void runScripts(User user, List<SqlScript> scripts, SqlScriptProvider provider) throws SQLException
    {
        runScripts(user, scripts, provider, true);
    }


    // Throws SQLException only if getRunScripts() fails -- script failures are handled more gracefully
    public static void runScripts(User user, List<SqlScript> scripts, SqlScriptProvider provider, boolean allowMultipleProviderSubmits) throws SQLException
    {
        synchronized (SCRIPT_LOCK)
        {
            // No real synchronization before this point -- multiple requests/users will attempt to run conflicting
            // or duplicate scripts.  Check three conditions and reject the submission if any is true.  A rejection
            // will redirect the user to the status page.  See issue #4792 for more details.

            // Reject the submission if scripts are already running.
            if (!_scriptsToRun.isEmpty())
                return;

            // Reject the submission if any requested script has been run before on this server installation.
            Set<SqlScript> previouslyRunScripts = SqlScriptManager.getRunScripts(provider);
            for (SqlScript script : scripts)
                if (previouslyRunScripts.contains(script))
                    return;

            // Reject the submission if this provider has already run scripts during this server session (but allow
            // manual upgrade page to override this check).
            if (!allowMultipleProviderSubmits && _previousProviders.contains(provider.getProviderName()))
                return;
            _previousProviders.add(provider.getProviderName());

            _scriptsToRun.addAll(scripts);
            if (WORKER == null)
            {
                WORKER = new BackgroundScriptRunner(user);
                WORKER.start();
            }
            else
            {
                WORKER.setUser(user);    // Need to change from null to user in bootstrap case; at some point, we might support different admins doing parts of the upgrade
                WORKER.setException(null);
            }

            SCRIPT_LOCK.notifyAll();
        }
    }

    public static void stopBackgroundThread()
    {
        synchronized (SCRIPT_LOCK)
        {
            assert _scriptsToRun.isEmpty() : "There are still scripts to run!";
            if (WORKER != null)
            {
                WORKER.finish();
                WORKER = null;
            }
        }
    }

    private static class BackgroundScriptRunner extends Thread
    {
        private User _user;
        private Exception _scriptException = null;
        private volatile boolean _keepRunning = true;

        public BackgroundScriptRunner(User user)
        {
            super("Background SQL Script Runner");
            _user = user;
            setDaemon(true);
        }

        public void run()
        {
            while (_keepRunning)
            {
                SqlScript currentScript = null;
                synchronized(SCRIPT_LOCK)
                {
                    if (_scriptsToRun.isEmpty())
                    {
                        try
                        {
                            SCRIPT_LOCK.wait();
                        }
                        catch (InterruptedException e)
                        {
                            return;
                        }
                    }
                    if (!_scriptsToRun.isEmpty())
                    {
                        currentScript = _scriptsToRun.get(0);
                    }
                }

                if (currentScript != null)
                {
                    try
                    {
                        _log.info("Running " + currentScript.getDescription());
                        SqlScriptManager.runScript(_user, currentScript);
                    }
                    catch(Exception e)
                    {
                        // Stash any exception that occurs while running the script
                        _scriptException = e;
                    }
                    currentScript.afterScriptRuns();

                    synchronized(SCRIPT_LOCK)
                    {
                        // If we encounter an exception, stop running scripts
                        if (null != _scriptException)
                            _scriptsToRun.clear();
                        else
                            _scriptsToRun.remove(0);

                        SCRIPT_LOCK.notifyAll();
                    }
                }
            }
        }

        public User getUser()
        {
            return _user;
        }

        public void setUser(User user)
        {
            _user = user;
        }

        public Exception getException()
        {
            return _scriptException;
        }

        public void setException(Exception e)
        {
            _scriptException = e;
        }

        public void finish()
        {
            _keepRunning = false;
            interrupt();
            SCRIPT_LOCK.notifyAll();
        }
    }


    public static class SqlScriptException extends Exception
    {
        private String _filename;

        public SqlScriptException(Throwable cause, String filename)
        {
            super(cause);
            _filename = filename;
        }

        public SqlScriptException(String message, String filename)
        {
            super(message);
            _filename = filename;
        }

        public String getMessage()
        {
            if (getCause() == null)
            {
                return _filename + " : " + super.getMessage();
            }
            else
            {
                return _filename + " : " + getCause().getMessage();
            }
        }

        public String getFilename()
        {
            return _filename;
        }
    }


    public interface SqlScript
    {
        public String getSchemaName();
        public double getFromVersion();
        public double getToVersion();
        public String getContents();
        public String getErrorMessage();
        public String getDescription();
        public SqlScriptProvider getProvider();
        public void afterScriptRuns();
    }


    public interface SqlScriptProvider
    {
        public Set<String> getSchemaNames() throws SqlScriptException;
        public List<SqlScript> getScripts(String schemaName) throws SqlScriptException;
        public SqlScript getScript(String description);
        public String getProviderName();
    }
}
