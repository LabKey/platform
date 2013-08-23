/*
 * Copyright (c) 2005-2013 Fred Hutchinson Cancer Research Center
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

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.User;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: arauch
 * Date: Jun 14, 2005
 * Time: 2:56:01 PM
 */
public class SqlScriptRunner
{
    private static final Logger _log = Logger.getLogger(SqlScriptRunner.class);
    private static final List<SqlScript> _remainingScripts = new ArrayList<>();
    private static final Object SCRIPT_LOCK = new Object();

    private static String _currentModuleName = null;

    public static List<SqlScript> getRunningScripts(@Nullable String moduleName)
    {
        synchronized (SCRIPT_LOCK)
        {
            if (null != moduleName && moduleName.equals(_currentModuleName))
                return new ArrayList<>(_remainingScripts);
            else
                return Collections.emptyList();
        }
    }


    public static String getCurrentModuleName()
    {
        synchronized (SCRIPT_LOCK)
        {
            return _currentModuleName;
        }
    }


    // Throws SQLException only if getRunScripts() fails -- script failures are handled more gracefully
    public static void runScripts(Module module, @Nullable User user, List<SqlScript> scripts) throws SQLException, SqlScriptException
    {
        _log.info("Running " + scripts.toString());

        synchronized(SCRIPT_LOCK)
        {
            assert _remainingScripts.isEmpty();
            _remainingScripts.addAll(scripts);
            _currentModuleName = module.getName();
        }

        for (SqlScript script : scripts)
        {
            SqlScriptManager.runScript(user, script, ModuleLoader.getInstance().getModuleContext(module), null);

            synchronized(SCRIPT_LOCK)
            {
                _remainingScripts.remove(script);
                SCRIPT_LOCK.notifyAll();
            }
        }

        // Clear caches to invalidate previously loaded meta data (upgrade scripts we just ran may have changed them)
        // and invalidate potentially stale objects that have been cached.
        CacheManager.clearAllKnownCaches();

        synchronized(SCRIPT_LOCK)
        {
            _currentModuleName = null;
        }
    }


    // Returns all the existing scripts matching schemaName that have not been run
    public static List<SqlScript> getNewScripts(SqlScriptProvider provider, @Nullable DbSchema schema) throws SQLException
    {
        List<SqlScript> allScripts;

        try
        {
            allScripts = provider.getScripts(schema);
        }
        catch(SqlScriptException e)
        {
            throw new RuntimeException(e);
        }

        List<SqlScript> newScripts = new ArrayList<>();
        Set<SqlScript> runScripts = SqlScriptManager.getRunScripts(provider);

        for (SqlScript script : allScripts)
            if (!runScripts.contains(script))
                newScripts.add(script);

        return newScripts;
    }


    public static List<SqlScript> getRecommendedScripts(SqlScriptProvider provider, double from, double to) throws SQLException, SqlScriptException
    {
        List<SqlScript> scripts = new ArrayList<>();

        for (DbSchema schema : provider.getSchemas())
        {
            List<SqlScript> newScripts = getNewScripts(provider, schema);
            scripts.addAll(getRecommendedScripts(newScripts, from, to));
        }

        return scripts;
    }


    // Get the recommended scripts from a given collection of scripts
    public static List<SqlScript> getRecommendedScripts(Collection<SqlScript> schemaScripts, double from, double to)
    {
        // Create a map of SqlScript objects.  For each fromVersion, store only the script with the highest toVersion
        Map<Double, SqlScript> m = new HashMap<>();

        for (SqlScript script : schemaScripts)
        {
            if (script.getFromVersion() >= from && script.getToVersion() <= to)
            {
                SqlScript current = m.get(script.getFromVersion());

                if (null == current || script.getToVersion() > current.getToVersion())
                    m.put(script.getFromVersion(), script);
            }
        }

        List<SqlScript> scripts = new ArrayList<>();

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


    public interface SqlScript extends Comparable<SqlScript>
    {
        public String getSchemaName();
        public double getFromVersion();
        public double getToVersion();
        public String getContents();
        public String getErrorMessage();
        public String getDescription();
        public SqlScriptProvider getProvider();
        public boolean isValidName();
        public boolean isIncremental();
    }


    public interface SqlScriptProvider
    {
        public @NotNull Collection<DbSchema> getSchemas() throws SqlScriptException;
        public @NotNull List<SqlScript> getScripts(@Nullable DbSchema schema) throws SqlScriptException;
        public SqlScript getScript(String description);
        public String getProviderName();
        public @NotNull List<SqlScript> getDropScripts() throws SqlScriptException;
        public @NotNull List<SqlScript> getCreateScripts() throws SqlScriptException;
        public UpgradeCode getUpgradeCode();
    }
}
