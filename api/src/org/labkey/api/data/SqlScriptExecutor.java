/*
 * Copyright (c) 2008-2017 LabKey Corporation
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
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.DataIteratorUtil;
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

import java.io.BufferedInputStream;
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
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * Executes a single module upgrade SQL script, including finding calls into Java code that are embedded using
 * stored-procedure style syntax.
 * User: adam
 * Date: Nov 24, 2008
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
    private final @NotNull String _literalTrue;

    /**
     * Splits a SQL string into blocks and executes each block, one at a time. Blocks are determined in a dialect-specific
     * way, using splitPattern and procPattern.
     * @param sql The SQL string to split and execute
     * @param splitPattern Dialect-specific regex pattern for splitting normal SQL statements into blocks. Null means no need to split.
     * @param procPattern Dialect-specific regex pattern for finding executeJavaCode and bulkImport procedure calls in the SQL. See SqlDialect.getSqlScriptProcPattern() for details.
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
        getBlocks().forEach(block -> {
            synchronized (ModuleLoader.SCRIPT_RUNNING_LOCK)  // Prevent deadlocks between script running and initial user, #26165
            {
                block.execute();
            }
        });
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

                Block block = null != m.group(2) ? new JavaCodeBlock(m.group(0), m.group(3)) : new BulkImportBlock(m.group(0), m.group(5), m.group(6), m.group(7), m.group(8));
                blocks.add(block);

                start = m.end();             // TODO: plus 1?
            }

            if (start < trimmed.length())
                blocks.add(new Block(trimmed.substring(start, trimmed.length())));
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

            Method method;

            try
            {
                if (_upgradeCode == null)
                {
                    throw new IllegalArgumentException("The " + _moduleContext.getName() + " module does not have an UpgradeCode implementation");
                }
                assert null != _methodName;

                method = _upgradeCode.getClass().getMethod(_methodName, ModuleContext.class);
            }
            catch (NoSuchMethodException e)
            {
                throw new RuntimeException("Can't find method " + _methodName + "(ModuleContext moduleContext) on class " + _upgradeCode.getClass().getName(), e);
            }

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
    }


    private class BulkImportBlock extends Block
    {
        private final String _schemaName;
        private final String _tableName;
        private final String _filename;
        private final boolean _preserveEmptyString;

        private BulkImportBlock(String sql, @NotNull String schemaName, @NotNull String tableName, @NotNull String filename, @Nullable String preserveEmptyString)
        {
            super(sql);
            _schemaName = schemaName;  // Note: Use schemaName, not _schema... these might not match
            _tableName = tableName;
            _filename = filename;
            _preserveEmptyString = _literalTrue.equals(preserveEmptyString);
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
            final String fullTableName = _schemaName + "." + _tableName;
            try
            {
                BatchValidationException errors = new BatchValidationException();
                DataIteratorContext dix = new DataIteratorContext(errors);
                dix.setInsertOption(QueryUpdateService.InsertOption.IMPORT);
                Map<Enum, Object> options = new HashMap<>();
                options.put(QueryUpdateService.ConfigParameters.PreserveEmptyString, true);
                dix.setConfigParameters(options);

                // TARGET TABLE
                // Make sure cached database meta data reflects all previously executed SQL
                CacheManager.clearAllKnownCaches();
                DbSchema schema = DbSchema.get(_schemaName, DbSchemaType.Bare);
                TableInfo dbTable = schema.getTable(_tableName);
                if (null == dbTable)
                    throw new IllegalStateException("Table not found for data loading: " + fullTableName);

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

                // I'm not sure ExcelLoader closes its input stream, so let's just make sure
                try (InputStream is = r.getInputStream())
                {
                    if (null == is)
                        throw new IllegalStateException("Could not open resource: " + r.getPath());
                    InputStream buffStream = new BufferedInputStream(is, 64 * 1024);

                    // DataLoader.get().createLoader() doesn't work, because the loader factories are not registered yet
                    if (contentType.startsWith("text"))
                    {
                        loader = new TabLoader(new InputStreamReader(buffStream, StringUtilsLabKey.DEFAULT_CHARSET), true, null, false);
                        ((TabLoader)loader).setUnescapeBackslashes(false);
                    }
                    else if (contentType.contains("excel") || contentType.contains("spreadsheet"))
                        loader = new ExcelLoader(buffStream, true, null);
                    // NOTE: this makes the assumption all gzip files are text format!
                    else if (contentType.contains("gzip"))
                    {
                        loader = new TabLoader(new InputStreamReader(new GZIPInputStream(buffStream, 64 * 1024), StringUtilsLabKey.DEFAULT_CHARSET), true, null, false);
                        ((TabLoader)loader).setUnescapeBackslashes(false);
                    }
                    else
                        throw new IllegalStateException("Unrecognized data file format for file: " + r.getPath());

                    loader.setThrowOnErrors(true);
                    loader.setPreserveEmptyString(_preserveEmptyString);
                    DataIteratorUtil.copy(dix, loader, dbTable, null, null);
                    if (errors.hasErrors())
                    {
                        // Errors on inserting into target. Data was read successfully.
                        StringBuilder msg = new StringBuilder().append("Error for table: ").append(fullTableName).append(" inserting from data file: ").append(path).append(errors.getMessage());
                        int rowNumber = errors.getLastRowError().getRowNumber() + 1;
                        if (rowNumber > 0)
                            msg.append(" in file row ").append(rowNumber).append(" (including header row)");
                        throw new IllegalStateException(msg.toString(), errors);
                    }
                }
            }
            catch (IOException|BatchValidationException|IllegalArgumentException x)
            {
                // This is an error on reading the file. It would be nice to know if a given line/column had a problem, but the loaders don't bubble that info up. (yet)
                throw new RuntimeException("Error for table: " + fullTableName + " reading from data file: " + path, x);
            }
        }
    }
}
