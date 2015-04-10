/*
 * Copyright (c) 2008-2015 LabKey Corporation
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
import org.labkey.api.etl.DataIteratorContext;
import org.labkey.api.etl.DataIteratorUtil;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.reader.ExcelLoader;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.MimeMap;
import org.labkey.api.util.Path;
import org.labkey.api.util.StringUtilsLabKey;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
import java.util.zip.GZIPInputStream;

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

        private BulkImportBlock(String sql, @NotNull String schemaName, @NotNull String tableName, @NotNull String filename)
        {
            super(sql);
            _schemaName = schemaName;  // Note: Use schemaName, not _schema... these might not match
            _tableName = tableName;
            _filename = filename;
        }

        @Override
        public void execute()
        {
            // find module that owns this script (may not be the target schema)
            Module m = ModuleLoader.getInstance().getModuleForSchemaName(SqlScriptExecutor.this._schema.getName());
            if (null == m)
                throw new IllegalStateException("Module not found for schema: " + SqlScriptExecutor.this._schema.getName());

            Path path;
            if (_filename.contains("/"))
                path = Path.parse(_filename).normalize();
            else
                path = Path.parse("schemas/dbscripts/datafiles/" + _filename).normalize();

            try
            {
                BatchValidationException errors = new BatchValidationException();
                DataIteratorContext dix = new DataIteratorContext(errors);
                dix.setInsertOption(QueryUpdateService.InsertOption.IMPORT);

                // TARGET TABLE
                // Make sure cached database meta data reflects all previously executed SQL
                CacheManager.clearAllKnownCaches();
                DbSchema schema = DbSchema.get(_schemaName, DbSchemaType.Bare);
                TableInfo dbTable = schema.getTable(_tableName);
                if (null == dbTable)
                    throw new IllegalStateException("Table not found for data loading: " + _schemaName + "." + _tableName);

                for (ColumnInfo col : dbTable.getColumns())
                {
                    if (col.isAutoIncrement())
                    {
                        dix.setInsertOption(QueryUpdateService.InsertOption.IMPORT_IDENTITY);
                        dix.setSupportAutoIncrementKey(true);
                        break;
                    }
                }

                // SOURCE FILE
                Resource r = m.getModuleResource(path);
                if (null == r || !r.isFile())
                    throw new IllegalStateException("Data file not found for data loading: " + path.toString());
                String contentType = new MimeMap().getContentTypeFor(r.getName());
                DataLoader loader;

                // I'm not sure ExcelLoader closes it's input stream, so let's just make sure
                try (InputStream is = r.getInputStream())
                {
                    if (null == is)
                        throw new IllegalStateException("Could not open resource: " + r.getPath());

                    // DataLoader.get().createLoader() doesn't work, because the loader factories are not registered yet
                    if (contentType.startsWith("text"))
                        loader = new TabLoader(new InputStreamReader(is, StringUtilsLabKey.DEFAULT_CHARSET), true, null, false);
                    else if (contentType.contains("excel") || contentType.contains("spreadsheet"))
                        loader = new ExcelLoader(is, true, null);
                    // NOTE: this makes the assumption all gzip files are text format!
                    else if (contentType.contains("gzip"))
                        loader = new TabLoader(new InputStreamReader(new GZIPInputStream(is), StringUtilsLabKey.DEFAULT_CHARSET), true, null, false);
                    else
                        throw new IllegalStateException("Unrecognized data file format for file: " + r.getPath());

                    DataIteratorUtil.copy(dix, loader, dbTable, null, null);
                    if (errors.hasErrors())
                        throw new IllegalStateException("Error loading data file: " + r.getPath() + errors.getMessage(), errors);
                }
            }
            catch (IOException|BatchValidationException x)
            {
                throw new RuntimeException(x);
            }
        }
    }
}
