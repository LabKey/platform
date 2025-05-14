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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Executes a single module upgrade SQL script, including finding calls into Java code that are embedded using
 * stored-procedure style syntax.
 * User: adam
 * Date: Nov 24, 2008
 */
public class SqlScriptExecutor
{
    private static final Logger _log = LogManager.getLogger(SqlScriptExecutor.class);

    private final String _sql;
    private final Pattern _splitPattern;
    private final Pattern _procPattern;
    private final DbSchema _schema;
    private final @Nullable UpgradeCode _upgradeCode;
    private final ModuleContext _moduleContext;
    private final @Nullable Connection _conn;
    private final @NotNull String _literalTrue;

    /**
     * Splits a SQL string into blocks and executes each block, one at a time. Blocks are determined in a dialect-specific
     * way, using splitPattern and procPattern.
     * @param sql The SQL string to split and execute
     * @param splitPattern Dialect-specific regex pattern for splitting normal SQL statements into blocks. Null means no need to split.
     * @param procPattern Dialect-specific regex pattern for finding executeJavaCode procedure calls in the SQL. See SqlDialect.getSqlScriptProcPattern() for details.
     * @param schema Current schema. Null is allowed for testing purposes.
     * @param upgradeCode Implementation of UpgradeCode that provides methods for executeJavaCode to run
     * @param moduleContext Current ModuleContext
     * @param conn Connection to use, if non-null
     * @param literalTrue String value of boolean true for the sql dialect
     */
    public SqlScriptExecutor(String sql, @Nullable Pattern splitPattern, @NotNull Pattern procPattern, @Nullable DbSchema schema, @Nullable UpgradeCode upgradeCode, ModuleContext moduleContext, @Nullable Connection conn, @NotNull String literalTrue)
    {
        _sql = sql;
        _splitPattern = splitPattern;
        _procPattern = procPattern;
        _schema = schema;
        _upgradeCode = upgradeCode;
        _moduleContext = moduleContext;
        _conn = conn;
        _literalTrue = literalTrue;
    }

    public void execute()
    {
        // Prevent deadlocks between script running and initial user, #26165, but do it for the full script as a whole, #39377
        synchronized (ModuleLoader.SCRIPT_RUNNING_LOCK)
        {
            getBlocks().forEach(Block::execute);
        }
    }

    private Collection<Block> getBlocks()
    {
        // Strip all comments from the script -- PostgreSQL JDBC driver goes berserk if it sees ; or ? inside a comment
        StringBuilder stripped = new SqlScanner(_sql).stripComments();

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

            Method method;

            try
            {
                if (_upgradeCode == null)
                {
                    throw new IllegalArgumentException("The " + _moduleContext.getName() + " module does not have an UpgradeCode implementation");
                }
                assert null != _methodName;

                method = _upgradeCode.getClass().getMethod(_methodName, ModuleContext.class);

                String displayName = method.getDeclaringClass().getSimpleName() + "." + method.getName() + "(ModuleContext moduleContext)";

                Runnable runnable = () -> {
                    // Make sure cached database meta data reflects all previously executed SQL
                    CacheManager.clearAllKnownCaches();

                    try
                    {
                        method.invoke(_upgradeCode, _moduleContext);
                    }
                    catch (InvocationTargetException | IllegalAccessException e)
                    {
                        throw new RuntimeException("Can't invoke method " + method.getName() + "(ModuleContext moduleContext) on class " + _upgradeCode.getClass().getName(), e);
                    }
                    finally
                    {
                        // Just to be safe
                        CacheManager.clearAllKnownCaches();
                    }
                };

                if (method.isAnnotationPresent(DeferredUpgrade.class))
                {
                    _log.info("Adding deferred upgrade to execute " + displayName);
                    _moduleContext.addDeferredUpgradeRunnable(displayName, runnable);
                }
                else
                {
                    _log.info("Executing " + displayName);
                    runnable.run();
                }
            }
            catch (NoSuchMethodException e)
            {
                // Give the upgrade code a chance to recognize something that doesn't map to a Java method
                _upgradeCode.fallthroughHandler(_methodName);
            }
        }
    }
}
