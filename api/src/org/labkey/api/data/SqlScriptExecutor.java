/*
 * Copyright (c) 2008-2013 LabKey Corporation
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
import org.labkey.api.module.ModuleContext;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: adam
 * Date: Nov 24, 2008
 * Time: 3:22:16 PM
 */
public class SqlScriptExecutor
{
    private static final Logger _log = Logger.getLogger(SqlScriptExecutor.class);

    private final String _sql;
    private final Pattern _splitPattern;
    private final Pattern _procPattern;
    private final DbSchema _schema;
    private final @Nullable UpgradeCode _upgradeCode;
    private final ModuleContext _moduleContext;
    private final @Nullable Connection _conn;


    /**
     * Splits a SQL string into blocks and executes each block, one at a time. Blocks are determined in a dialect-specific
     * way, using splitPattern and procPattern.
     *
     * @param sql The SQL string to split and execute
     * @param splitPattern Dialect-specific regex pattern for splitting normal SQL statements into blocks. Null means no need to split.
     * @param procPattern Dialect-specific regex pattern for finding executeJavaCode and bulkImport procedure calls in the SQL. See SqlDialect.getSqlScriptProcPattern() for details.
     * @param schema Current schema. Null is allowed for testing purposes.
     * @param upgradeCode Implementation of UpgradeCode that provides methods for executeJavaCode to run
     * @param moduleContext Current ModuleContext
     * @param conn Connection to use, if non-null
     */
    public SqlScriptExecutor(String sql, @Nullable Pattern splitPattern, @NotNull Pattern procPattern, @Nullable DbSchema schema, @Nullable UpgradeCode upgradeCode, ModuleContext moduleContext, @Nullable Connection conn)
    {
        _sql = sql;
        _splitPattern = splitPattern;
        _procPattern = procPattern;
        _schema = schema;
        _upgradeCode = upgradeCode;
        _moduleContext = moduleContext;
        _conn = conn;
    }

    public void execute()
    {
        for (SqlScriptExecutor.Block block : getBlocks())
            block.execute();
    }

    private Collection<Block> getBlocks()
    {
        StringBuilder stripped = stripComments(_sql);

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

                Block block = null != m.group(2) ? new JavaCodeBlock(m.group(0), m.group(3)) : new BulkImportBlock(m.group(0), m.group(5), m.group(6), m.group(7));
                blocks.add(block);

                start = m.end();             // TODO: plus 1?
            }

            if (start < trimmed.length())
                blocks.add(new Block(trimmed.substring(start, trimmed.length())));
        }

        return blocks;
    }

    // Strip all comments from the script -- PostgreSQL JDBC driver goes berserk if it sees ; or ? inside a comment
    private StringBuilder stripComments(String sql)
    {
        StringBuilder sb = new StringBuilder(sql.length());
        int j = 0;

        while (j < sql.length())
        {
            char c = sql.charAt(j);
            String twoChars = null;
            int end = j + 1;

            if (j < (sql.length() - 1))
                twoChars = sql.substring(j, j + 2);

            if ('\'' == c)
            {
                end = sql.indexOf('\'', j + 1) + 1;

                if (0 == end)
                    _log.error("No quote termination char");
                else
                    sb.append(sql.substring(j, end));
            }
            else if ("/*".equals(twoChars))
            {
                end = sql.indexOf("*/", j + 2) + 2;  // Skip comment completely

                if (1 == end)
                    _log.error("No comment termination char");
            }
            else if ("--".equals(twoChars))
            {
                end = sql.indexOf("\n", j + 2);  // Skip comment but leave the cr

                if (0 == end)
                    end = sql.length();
            }
            else
                sb.append(c);

            j = end;
        }

        return sb;
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
            if (_sql.length() > 0 && null != _schema)
            {
                new SqlExecutor(_schema.getScope(), _conn).execute(_sql);
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

            try
            {
                if (_upgradeCode == null)
                {
                    throw new IllegalArgumentException("The " + _moduleContext.getName() + " module does not have an UpgradeCode implementation");
                }
                assert null != _methodName;

                // Make sure cached database meta data reflects all previously executed SQL
                CacheManager.clearAllKnownCaches();
                Method method = _upgradeCode.getClass().getMethod(_methodName, ModuleContext.class);
                String displayName = method.getDeclaringClass().getSimpleName() + "." + method.getName() + "(ModuleContext moduleContext)";

                if (method.isAnnotationPresent(DeferredUpgrade.class))
                {
                    _log.info("Adding deferred upgrade task to execute " + displayName);
                    _moduleContext.addDeferredUpgradeTask(method);
                }
                else
                {
                    _log.info("Executing " + displayName);
                    method.invoke(_upgradeCode, _moduleContext);
                }

                // Just to be safe
                CacheManager.clearAllKnownCaches();
            }
            catch (NoSuchMethodException e)
            {
                throw new RuntimeException("Can't find method " + _methodName + "(ModuleContext moduleContext) on class " + _upgradeCode.getClass().getName(), e);
            }
            catch (InvocationTargetException | IllegalAccessException e)
            {
                throw new RuntimeException("Can't invoke method " + _methodName + "(ModuleContext moduleContext) on class " + _upgradeCode.getClass().getName(), e);
            }
        }
    }


    public static final AtomicLong BULK_IMPORT_EXECUTION_COUNT = new AtomicLong();

    private class BulkImportBlock extends Block
    {
        private final String _schemaName;
        private final String _tableName;
        private final String _filename;

        private BulkImportBlock(String sql, String schemaName, String tableName, String filename)
        {
            super(sql);
            _schemaName = schemaName;  // Note: Use schemaName, not _schema... these might not match
            _tableName = tableName;
            _filename = filename;
        }

        @Override
        public void execute()
        {
            super.execute();

            // TODO:
            // Determine module (based on _schemaName)
            // Get resource... Resource r = module.getModuleResource("/schemas/" + filename); r.getInputStream()...
            // Stream resource into table
            // SQL Server: SET IDENTIFY ON/OFF?

            // TODO: Delete everything below.
            // For temporary junit test -- just count number of successful "executions" and verify the test parameters
            BULK_IMPORT_EXECUTION_COUNT.incrementAndGet();
            assert "test".equals(_schemaName);
            assert "TestTable".equals(_tableName);
            assert "test.xls".equals(_filename);
        }
    }
}
