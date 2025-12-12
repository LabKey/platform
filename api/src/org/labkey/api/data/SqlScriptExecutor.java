/*
 * Copyright (c) 2012-2019 LabKey Corporation
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

import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.module.DefaultModule.UpgradeMethod;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.util.logging.LogHelper;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Executes a single module upgrade SQL script, including finding calls into Java code that are embedded using
 * stored-procedure style syntax.
 */
public class SqlScriptExecutor
{
    private static final Logger LOG = LogHelper.getLogger(SqlScriptExecutor.class, "Upgrade code invocations");

    private final String _scriptName;
    private final String _sql;
    private final Pattern _splitPattern;
    private final Pattern _procPattern;
    private final DbSchema _schema;
    private final ModuleContext _moduleContext;
    private final @Nullable Connection _conn;

    /**
     * Splits a SQL string into blocks and executes each block, one at a time. Blocks are determined in a dialect-specific
     * way, using splitPattern and procPattern.
     *
     * @param scriptName    Name of the SQL script for logging purposes
     * @param sql           The SQL string to split and execute
     * @param splitPattern  Dialect-specific regex pattern for splitting normal SQL statements into blocks. Null means no need to split.
     * @param procPattern   Dialect-specific regex pattern for finding executeJavaCode procedure calls in the SQL. See SqlDialect.getSqlScriptProcPattern() for details.
     * @param schema        Current schema. Null is allowed for testing purposes.
     * @param moduleContext Current ModuleContext
     * @param conn          Connection to use, if non-null
     */
    public SqlScriptExecutor(String scriptName, String sql, @Nullable Pattern splitPattern, @NotNull Pattern procPattern, @Nullable DbSchema schema, ModuleContext moduleContext, @Nullable Connection conn)
    {
        _scriptName = scriptName;
        _sql = sql;
        _splitPattern = splitPattern;
        _procPattern = procPattern;
        _schema = schema;
        _moduleContext = moduleContext;
        _conn = conn;
    }

    public void execute()
    {
        // Prevent deadlocks between script running and initial user, #26165, but do it for the full script as a whole, #39377
        synchronized (ModuleLoader.SCRIPT_RUNNING_LOCK)
        {
            getBlocks().forEach(Block::execute);
        }
    }

    private static final String START_ANNOTATION = "@SkipOnEmptySchemasBegin";
    private static final String END_ANNOTATION = "@SkipOnEmptySchemasEnd";

    private Collection<Block> getBlocks()
    {
        String sql;

        if (ModuleLoader.getInstance().shouldInsertData())
        {
            sql = _sql;
        }
        else
        {
            // Strip all statements (typically inserts) between the empty-schema START and END annotations, inclusive
            MutableBoolean skipping = new MutableBoolean(false);
            MutableInt lineCount = new MutableInt(0);
            sql = _sql.lines()
                .filter(line -> {
                    lineCount.increment();
                    if (line.contains(START_ANNOTATION))
                    {
                        if (skipping.booleanValue())
                            throw new IllegalStateException("Unexpected " + START_ANNOTATION + " at line " + lineCount.intValue());

                        skipping.setValue(true);
                    }

                    boolean ret = !skipping.booleanValue();

                    if (line.contains(END_ANNOTATION))
                    {
                        if (!skipping.booleanValue())
                            throw new IllegalStateException("Unexpected " + END_ANNOTATION + " at line " + lineCount.intValue());

                        skipping.setValue(false);
                    }

                    return ret;
                }
            )
            .collect(Collectors.joining("\n"));
        }

        // Strip all comments from the script -- PostgreSQL JDBC driver goes berserk if it sees ; or ? inside a comment
        StringBuilder stripped = new SqlScanner(sql).stripComments();

        Collection<String> sqlBlocks;

        if (null != _splitPattern)
        {
            sqlBlocks = Arrays.asList(_splitPattern.split(stripped));
        }
        else
        {
            sqlBlocks = Collections.singletonList(stripped.toString());
        }

        Collection<Block> blocks = new ArrayList<>(sqlBlocks.size());

        for (String sqlBlock : sqlBlocks)
        {
            String trimmed = sqlBlock.trim();
            Matcher m = _procPattern.matcher(trimmed);
            int start = 0;

            while (m.find(start))
            {
                if (m.start() > start)
                    blocks.add(new Block(trimmed.substring(start, m.start())));          // TODO: -1 ?

                Block block = new JavaCodeBlock(m.group(0), m.group(2));
                blocks.add(block);

                start = m.end();             // TODO: plus 1?
            }

            if (start < trimmed.length())
                blocks.add(new Block(trimmed.substring(start)));
        }

        return blocks;
    }

    public class Block
    {
        private final String _sql;

        private Block(String sql)
        {
            _sql = sql.trim();
        }

        public void execute()
        {
            // Null schema allowed for testing
            if (!_sql.isEmpty() && null != _schema)
            {
                new SqlExecutor(_schema.getScope(), _conn).execute(SQLFragment.unsafe(_sql));
            }
        }
    }

    private class JavaCodeBlock extends Block
    {
        private final String _methodName;

        private JavaCodeBlock(String sql, String methodName)
        {
            super(sql);
            _methodName = methodName;
        }

        @Override
        public void execute()
        {
            super.execute();

            UpgradeMethod upgradeMethod = new UpgradeMethod(_moduleContext, _scriptName, _methodName);

            // Make sure cached database metadata reflects all previously executed SQL. For example, core.UpgradeSteps
            // needs to appear as "in the physical database" before any deferred upgrade methods can be stashed.
            CacheManager.clearAllKnownCaches();

            try
            {
                // Retrieve Method in all cases (even deferred upgrade) to proactively validate
                Method method = upgradeMethod.getMethod();

                if (method.isAnnotationPresent(DeferredUpgrade.class))
                {
                    LOG.info("Adding deferred upgrade to execute {}", upgradeMethod.getDisplayName());
                    _moduleContext.addDeferredUpgradeMethod(upgradeMethod);
                }
                else
                {
                    LOG.info("Executing {}", upgradeMethod.getDisplayName());
                    _moduleContext.invokeUpgradeMethod(upgradeMethod);
                }
            }
            catch (NoSuchMethodException e)
            {
                // Give the upgrade code a chance to recognize something that doesn't map to a Java method.
                upgradeMethod.getUpgradeCode().fallthroughHandler(_methodName);
            }
            finally
            {
                // Just to be safe
                CacheManager.clearAllKnownCaches();
            }
        }
    }
}
