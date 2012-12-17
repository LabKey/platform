/*
 * Copyright (c) 2008-2012 LabKey Corporation
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
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
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
    private final Pattern _executeJavaCodePattern;
    private final DbSchema _schema;
    private final UpgradeCode _upgradeCode;
    private final ModuleContext _moduleContext;
    @Nullable private final Connection _conn;

    public SqlScriptExecutor(String sql, @Nullable Pattern splitPattern, @NotNull Pattern executeJavaCodePattern, @Nullable DbSchema schema, @Nullable UpgradeCode upgradeCode, ModuleContext moduleContext, @Nullable Connection conn)
    {
        _sql = sql;
        _splitPattern = splitPattern;
        _executeJavaCodePattern = executeJavaCodePattern;
        _schema = schema;
        _upgradeCode = upgradeCode;
        _moduleContext = moduleContext;
        _conn = conn;
    }

    public void execute() throws SQLException
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

        Collection<Block> blocks = new ArrayList<Block>(sqlBlocks.size());

        for (String sqlBlock : sqlBlocks)
        {
            String trimmed = sqlBlock.trim();
            Matcher m = _executeJavaCodePattern.matcher(trimmed);
            int start = 0;

            while (m.find(start))
            {
                if (m.start() > start)
                    blocks.add(new Block(trimmed.substring(start, m.start())));          // TODO: -1 ?

                blocks.add(new JavaCodeBlock(trimmed.substring(m.start(), m.end()), m.group(1)));

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
        private String _sql;

        private Block(String sql)
        {
            _sql = sql.trim();
        }

        public void execute() throws SQLException
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
        private String _methodName;

        private JavaCodeBlock(String sql, String methodName)
        {
            super(sql);
            _methodName = methodName;
        }

        @Override
        public void execute() throws SQLException
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

                if (method.isAnnotationPresent(DeferredUpgrade.class))
                    _moduleContext.addDeferredUpgradeTask(method);
                else
                    method.invoke(_upgradeCode, _moduleContext);

                // Just to be safe
                CacheManager.clearAllKnownCaches();
            }
            catch (NoSuchMethodException e)
            {
                throw new RuntimeException("Can't find method " + _methodName + "(ModuleContext moduleContext) on class " + _upgradeCode.getClass().getName(), e);
            }
            catch (InvocationTargetException e)
            {
                throw new RuntimeException("Can't invoke method " + _methodName + "(ModuleContext moduleContext) on class " + _upgradeCode.getClass().getName(), e);
            }
            catch (IllegalAccessException e)
            {
                throw new RuntimeException("Can't invoke method " + _methodName + "(ModuleContext moduleContext) on class " + _upgradeCode.getClass().getName(), e);
            }
        }
    }
}
