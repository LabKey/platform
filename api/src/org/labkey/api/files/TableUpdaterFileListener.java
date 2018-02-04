/*
 * Copyright (c) 2012-2015 LabKey Corporation
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
package org.labkey.api.files;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.security.User;
import org.labkey.api.util.FileUtil;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Set;

/**
 * FileListener implementation that can update tables that store file paths in various flavors (URI, standard OS
 * paths, etc).
 * User: jeckels
 * Date: 11/7/12
 */
public class TableUpdaterFileListener implements FileListener
{
    private static final Logger LOG = Logger.getLogger(TableUpdaterFileListener.class);

    public static final String TABLE_ALIAS = "x";

    private final TableInfo _table;
    private final SQLFragment _containerFrag;
    private final String _pathColumn;
    private final PathGetter _pathGetter;
    private final String _keyColumn;

    public interface PathGetter
    {
        /** @return the string that is expected to be the in database */
        public abstract String get(File f);
        /** @return the file path separator (typically '/' or '\' */
        public abstract String getSeparatorSuffix();
    }

    public enum Type implements PathGetter
    {
        /** Turns the file path into a "file:"-prefixed URI */
        uri
        {
            @Override
            public String get(File f)
            {
                return FileUtil.uriToString(f.toURI());
            }

            @Override
            public String getSeparatorSuffix()
            {
                return "/";
            }
        },

        /** Just uses getPath() to turn the File into a String */
        filePath
        {
            @Override
            public String get(File f)
            {
                return f.getPath();
            }

            @Override
            public String getSeparatorSuffix()
            {
                return File.separator;
            }
        },

        /** Just uses getPath() to turn the File into a String, but replaces all backslashes with forward slashes */
        filePathForwardSlash
        {
            @Override
            public String get(File f)
            {
                return f.getPath().replace('\\', '/');
            }

            @Override
            public String getSeparatorSuffix()
            {
                return "/";
            }
        }
    }

    public TableUpdaterFileListener(TableInfo table, String pathColumn, PathGetter pathGetter)
    {
        this(table, pathColumn, pathGetter, null, null);
    }

    public TableUpdaterFileListener(TableInfo table, String pathColumn, PathGetter pathGetter, @Nullable String keyColumn)
    {
        this(table, pathColumn, pathGetter, keyColumn, null);
    }

    public TableUpdaterFileListener(TableInfo table, String pathColumn, PathGetter pathGetter, @Nullable String keyColumn, @Nullable SQLFragment containerFrag)
    {
        _table = table;
        _pathColumn = pathColumn;
        _pathGetter = pathGetter;
        _keyColumn = keyColumn;
        _containerFrag = containerFrag;
    }

    @Override
    public String getSourceName()
    {
        StringBuilder name = new StringBuilder();
        name.append(_table.getSchema().getName()).append(".");
        name.append(_table.getName()).append(".");
        name.append(_pathColumn);
        return name.toString();
    }

    @Override
    public void fileCreated(@NotNull File created, @Nullable User user, @Nullable Container container)
    {
    }

    @Override
    public void fileMoved(@NotNull File src, @NotNull File dest, @Nullable User user, @Nullable Container container)
    {
        String srcPath = _pathGetter.get(src);
        String destPath = _pathGetter.get(dest);

        DbSchema schema = _table.getSchema();
        SqlDialect dialect = schema.getSqlDialect();

        // Build up SQL that can be used for both the file and any children
        SQLFragment sharedSQL = new SQLFragment("UPDATE ");
        sharedSQL.append(_table);
        sharedSQL.append(_table.getSqlDialect().isSqlServer() ? " WITH (UPDLOCK)" : "");
        sharedSQL.append(" SET ");
        if (_table.getColumn("Modified") != null)
        {
            sharedSQL.append("Modified = ?, ");
            sharedSQL.add(new Date());
        }
        if (_table.getColumn("ModifiedBy") != null && user != null)
        {
            sharedSQL.append("ModifiedBy = ?, ");
            sharedSQL.add(user.getUserId());
        }
        sharedSQL.append(_table.getSqlDialect().makeLegalIdentifier(_pathColumn));
        sharedSQL.append(" = ");

        // Now build up the SQL to handle this specific path
        SQLFragment singleEntrySQL = new SQLFragment(sharedSQL);
        singleEntrySQL.append("? WHERE ");
        singleEntrySQL.append(_table.getSqlDialect().makeLegalIdentifier(_pathColumn));
        singleEntrySQL.append(" = ?");
        singleEntrySQL.add(destPath);
        singleEntrySQL.add(srcPath);

        int rows = -1;
        for (int retry=0 ; retry<2 ; retry++)
        {
            try
            {
                rows = new SqlExecutor(schema).execute(singleEntrySQL);
                break;
            }
            catch (RuntimeException x)
            {
                if (retry > 0 || _table.getSchema().getScope().isTransactionActive() || !SqlDialect.isTransactionException(x))
                    throw x;
            }
        }
        LOG.info("Updated " + rows + " row in " + _table + " for move from " + src + " to " + dest);

        // Skip attempting to fix up child paths if we know that the entry is a file. If it's not (either it's a
        // directory or it doesn't exist), then try to fix up child records
        if (!dest.isFile())
        {
            if (!srcPath.endsWith(_pathGetter.getSeparatorSuffix()))
            {
                srcPath = srcPath + _pathGetter.getSeparatorSuffix();
            }
            if (!destPath.endsWith(_pathGetter.getSeparatorSuffix()))
            {
                destPath = destPath + _pathGetter.getSeparatorSuffix();
            }

            // Make the SQL to handle children
            SQLFragment childPathsSQL = new SQLFragment(sharedSQL);
            childPathsSQL.append(dialect.concatenate(new SQLFragment("?", destPath), new SQLFragment(dialect.getSubstringFunction(_table.getSqlDialect().makeLegalIdentifier(_pathColumn), Integer.toString(srcPath.length() + 1), "5000"))));
            childPathsSQL.append(" WHERE ");
            childPathsSQL.append(dialect.getStringIndexOfFunction(new SQLFragment("?", srcPath), new SQLFragment(_table.getSqlDialect().makeLegalIdentifier(_pathColumn))));
            childPathsSQL.append(" = 1");

            int childRows = new SqlExecutor(schema).execute(childPathsSQL);
            LOG.info("Updated " + childRows + " child paths in " + _table + " rows for move from " + src + " to " + dest);
        }
    }

    @Override
    public Collection<File> listFiles(@Nullable Container container)
    {
        Set<String> columns = Collections.singleton(_pathColumn);
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(_pathColumn, null, CompareType.NONBLANK);
        if (container != null)
        {
            ColumnInfo containerColumn = _table.getColumn("container");
            if (containerColumn == null)
                containerColumn = _table.getColumn("folder");

            if (containerColumn != null)
                filter.addCondition(containerColumn, container.getEntityId());
            else
                filter.addCondition(new SimpleFilter.SQLClause("1 = 0", null));
        }

        Sort sort = new Sort(_pathColumn);
        TableSelector selector = new TableSelector(_table, columns, filter, sort);

        selector.setMaxRows(Table.ALL_ROWS);
        return selector.getArrayList(File.class);
    }

    @Override
    public SQLFragment listFilesQuery()
    {
        SqlDialect dialect = _table.getSqlDialect();
        SQLFragment selectFrag = new SQLFragment();
        selectFrag.append("SELECT\n");

        if (_containerFrag != null)
            selectFrag.append("(").append(_containerFrag).append(") AS Container,\n");
        else if (_table.getColumn("Container") != null)
            selectFrag.append("  Container,\n");
        else if (_table.getColumn("Folder") != null)
            selectFrag.append("  Folder AS Container,\n");
        else
            selectFrag.append("  NULL AS Container,\n");

        if (_table.getColumn("Created") != null)
            selectFrag.append("  Created,\n");
        else
            selectFrag.append("  NULL AS Created,\n");

        if (_table.getColumn("CreatedBy") != null)
            selectFrag.append("  CreatedBy,\n");
        else
            selectFrag.append("  NULL AS CreatedBy,\n");

        if (_table.getColumn("Modified") != null)
            selectFrag.append("  Modified,\n");
        else
            selectFrag.append("  NULL AS Modified,\n");

        if (_table.getColumn("ModifiedBy") != null)
            selectFrag.append("  ModifiedBy,\n");
        else
            selectFrag.append("  NULL AS ModifiedBy,\n");

        selectFrag.append("  ").append(dialect.makeLegalIdentifier(_pathColumn)).append(" AS FilePath,\n");

        if (_keyColumn != null)
            selectFrag.append("  ").append(dialect.makeLegalIdentifier(_keyColumn)).append(" AS SourceKey,\n");
        else
            selectFrag.append("  NULL AS SourceKey,\n");

        //selectFrag.append("  ? AS SourceName\n").add(getName());
        selectFrag.append("  ").append(_table.getSchema().getSqlDialect().getStringHandler().quoteStringLiteral(getSourceName())).append(" AS SourceName\n");

        selectFrag.append("FROM ").append(_table, TABLE_ALIAS).append("\n");
        selectFrag.append("WHERE ").append(dialect.makeLegalIdentifier(_pathColumn)).append(" IS NOT NULL\n");

        return selectFrag;
    }
}
