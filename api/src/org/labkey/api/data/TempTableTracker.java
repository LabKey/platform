/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.apache.logging.log4j.Logger;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.ShutdownListener;
import org.labkey.api.util.logging.LogHelper;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.ref.Cleaner;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * User: Matthew
 * Date: May 4, 2006
 * Time: 7:27:50 PM
 */
public class TempTableTracker
{
    private static final Logger _log = LogHelper.getLogger(TempTableTracker.class, "Manages temp tables and their deletion");
    private static final String LOGFILE = "CPAS_sqlTempTables.log";
    private static final Map<String, TempTableTracker> createdTableNames = new TreeMap<>();
    private static final Cleaner cleaner = Cleaner.create();

    private static RandomAccessFile tempTableLog = null;

    private final String schemaName;
    private final String tableName;
    private final String qualifiedName;
    private final Cleaner.Cleanable cleanable;
    private final CleanupState state;

    private static class CleanupState implements Runnable
    {
        private final DbSchema schema;
        private final String tableName;
        private final String qualifiedName;
        private final String schemaName;
        private boolean deleted = false;

        private CleanupState(DbSchema schema, String tableName, String qualifiedName, String schemaName)
        {
            this.schema = schema;
            this.tableName = tableName;
            this.qualifiedName = qualifiedName;
            this.schemaName = schemaName;
        }

        @Override
        public void run()
        {
            if (!deleted)
            {
                _log.debug("Deleting table {}.{}", schema.getName(), tableName);
                schema.dropTableIfExists(tableName);

                deleted = true;
                untrack(qualifiedName, schemaName, tableName);
            }
        }
    }

    private TempTableTracker(DbSchema schema, String tableName, Object ref)
    {
        this.schemaName = schema.getName();
        this.tableName = tableName;
        this.qualifiedName = this.schemaName + "." + this.tableName;
        this.state = new CleanupState(schema, tableName, qualifiedName, schemaName);
        cleanable = cleaner.register(ref, state);
    }


    private TempTableTracker(String schemaName, String tableName, Object ref)
    {
        this(DbSchema.get(schemaName), tableName, ref);  // TODO: Treat as provisioned?
    } 

    private static final Object LOCK = new Object();
    private static boolean initialized = false;

    // make sure temp table tracker is initialized
    public static void init()
    {
        synchronized(LOCK)
        {
            if (!initialized)
            {
                initialized = true;
                synchronizeLog(true);
                purgeTempSchema();
            }
        }
    }


    public static TempTableTracker track(String name, Object ref)
    {
        init();
        return track(new TempTableTracker(DbSchema.getTemp(), name, ref));
    }


    private static TempTableTracker track(String schema, String name, Object ref)
    {
        init();
        return track(new TempTableTracker(schema, name, ref));
    }


    private static TempTableTracker track(TempTableTracker ttt)
    {
        _log.debug("track({})", ttt.qualifiedName);

        synchronized(createdTableNames)
        {
            TempTableTracker old = createdTableNames.get(ttt.qualifiedName);
            if (old != null)
                return old;
            createdTableNames.put(ttt.qualifiedName, ttt);
            appendToLog("+" + ttt.schemaName + "\t" + ttt.tableName + "\n");
            return ttt;
        }
    }

    public synchronized void delete()
    {
        cleanable.clean();
    }

    private static void untrack(String qualifiedName, String schemaName, String tableName)
    {
        _log.debug("untrack({})", qualifiedName);

        synchronized(createdTableNames)
        {
            createdTableNames.remove(qualifiedName);
            appendToLog("-" + schemaName + "\t" + tableName + "\n");

            if (createdTableNames.isEmpty() || System.currentTimeMillis() > lastSync + CacheManager.DAY)
                synchronizeLog(false);
        }
    }


    private static void appendToLog(String str)
    {
        if (null != tempTableLog)
        {
            try
            {
                tempTableLog.writeUTF(str);
            }
            catch (IOException x)
            {
             _log.error("could not write to " + LOGFILE, x);
            }
        }
    }


    static long lastSync = System.currentTimeMillis();


    private static void purgeTempSchema()
    {
        _log.debug("purgeTempSchema");
        // consider: test to see if any of the schemas have different scope (dbName) than core schema
        synchronized(createdTableNames)
        {
            SqlDialect dialect = CoreSchema.getInstance().getSchema().getSqlDialect();
            dialect.purgeTempSchema(createdTableNames);
        }
    }


    static void synchronizeLog(boolean loadFile)
    {
        synchronized(createdTableNames)
        {
            try
            {
                if (null == tempTableLog)
                    tempTableLog = new RandomAccessFile(FileUtil.appendName(FileUtil.getTempDirectory(), LOGFILE), "rwd");

                if (loadFile)
                {
                    _log.debug("load log file");
                    TreeSet<String> logEntries = new TreeSet<>();

                    try
                    {
                        tempTableLog.seek(0);
                        while (tempTableLog.getFilePointer() < tempTableLog.length())
                        {
                            String s = tempTableLog.readUTF().trim();
                            if (s.charAt(0) == '+')
                                logEntries.add(s.substring(1));
                            else if (s.charAt(0) == '-')
                                logEntries.remove(s.substring(1));
                        }
                    }
                    catch (Exception x)
                    {
                        _log.error("error reading file '" + LOGFILE + "'", x);
                    }

                    Object noref = new Object();
                    for (String s : logEntries)
                    {
                        try
                        {
                            String[] parts = s.split("\t");
                            if (parts.length != 2)
                                continue;
                            String schemaName = parts[0].trim();
                            String tableName = parts[1].trim();
                            if (schemaName.isEmpty() || tableName.isEmpty())
                                continue;
                            track(schemaName, tableName, noref);
                        }
                        catch (IllegalArgumentException x)
                        {
                            _log.error("bad log entry", x);
                        }
                    }
                }

                //
                // Rewrite file
                //
                // we could create new file and rename, but this doesn't have to be that robust
                //
                _log.debug("rewrite log file");
                tempTableLog.seek(0);
                tempTableLog.setLength(0);
                for (TempTableTracker ttt : createdTableNames.values())
                {
                    if (!ttt.state.deleted)
                        appendToLog("+" + ttt.schemaName + "\t" + ttt.tableName + "\n");
                }
            }
            catch (IOException x)
            {
                _log.error("could not create " + LOGFILE, x);
            }
            finally
            {
                lastSync = System.currentTimeMillis();
            }
        }
    }


    static final TempTableThread tempTableThread = new TempTableThread();

    public static class TempTableThread implements ShutdownListener
    {
        @Override
        public String getName()
        {
            return "Temp table cleanup";
        }

        @Override
        public void shutdownStarted()
        {
            synchronized(createdTableNames)
            {
                // Copy createdTableNames.values() to prevent ConcurrentModificationException
                for (TempTableTracker ttt : new ArrayList<>(createdTableNames.values()))
                {
                    ttt.state.run();
                }
            }

            synchronizeLog(false);
        }
    }


    public static ShutdownListener getShutdownListener()
    {
        return tempTableThread;
    }


    public static Logger getLogger()
    {
        return _log;
    }    
}
