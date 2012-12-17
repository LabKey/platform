/*
 * Copyright (c) 2012 LabKey Corporation
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
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.security.User;

import java.io.File;
import java.util.Date;

/**
 *
 * User: jeckels
 * Date: 11/7/12
 */
public class TableUpdaterFileMoveListener implements FileMoveListener
{
    private static final Logger LOG = Logger.getLogger(TableUpdaterFileMoveListener.class);

    private final TableInfo _table;
    private final String _pathColumn;
    private final PathGetter _pathGetter;

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
                return f.toURI().toString();
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

    public TableUpdaterFileMoveListener(TableInfo table, String pathColumn, PathGetter pathGetter)
    {
        _table = table;
        _pathColumn = pathColumn;
        _pathGetter = pathGetter;
    }

    @Override
    public void fileMoved(@NotNull File srcFile, @NotNull File destFile, @Nullable User user, @Nullable Container container)
    {
        String srcPath = _pathGetter.get(srcFile);
        String destPath = _pathGetter.get(destFile);

        DbSchema schema = _table.getSchema();
        SqlDialect dialect = schema.getSqlDialect();

        // Build up SQL that can be used for both the file and any children
        SQLFragment sharedSQL = new SQLFragment("UPDATE ");
        sharedSQL.append(_table);
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

        int rows = new SqlExecutor(schema).execute(singleEntrySQL);
        LOG.info("Updated " + rows + " row in " + _table + " for move from " + srcFile + " to " + destFile);

        // Skip attempting to fix up child paths if we know that the entry is a file. If it's not (either it's a
        // directory or it doesn't exist), then try to fix up child records
        if (!destFile.isFile())
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
            LOG.info("Updated " + childRows + " child paths in " + _table + " rows for move from " + srcFile + " to " + destFile);
        }
    }
}
