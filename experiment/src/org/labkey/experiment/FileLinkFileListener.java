/*
 * Copyright (c) 2012-2013 LabKey Corporation
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
package org.labkey.experiment;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.files.FileListener;
import org.labkey.api.files.TableUpdaterFileListener;
import org.labkey.api.security.User;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Handles fixup of paths stored in OntologyManager for File Link fields.
 * User: jeckels
 * Date: 11/8/12
 */
public class FileLinkFileListener implements FileListener
{
    private static final Logger LOG = Logger.getLogger(FileLinkFileListener.class);

    @Override
    public void fileCreated(@NotNull File created, @Nullable User user, @Nullable Container container)
    {
    }

    @Override
    public void fileMoved(@NotNull File srcFile, @NotNull File destFile, @Nullable User user, @Nullable Container container)
    {
        updateObjectProperty(srcFile, destFile);

        updateHardTables(srcFile, destFile, user, container);
    }

    /** Migrate FileLink values stored in exp.ObjectProperty */
    private void updateObjectProperty(File srcFile, File destFile)
    {
        String srcPath = srcFile.getPath();
        String destPath = destFile.getPath();

        SqlDialect dialect = OntologyManager.getSqlDialect();

        // Build up SQL that can be used for both the file and any children
        SQLFragment sharedSQL = new SQLFragment("UPDATE ");
        sharedSQL.append(OntologyManager.getTinfoObjectProperty());
        sharedSQL.append(" SET StringValue = ");

        SQLFragment standardWhereSQL = new SQLFragment(" WHERE PropertyId IN (SELECT PropertyId FROM ");
        standardWhereSQL.append(OntologyManager.getTinfoPropertyDescriptor(), "pd");
        standardWhereSQL.append(" WHERE RangeURI = ?)");
        standardWhereSQL.add(PropertyType.FILE_LINK.getTypeUri());

        // Now build up the SQL to handle this specific path
        SQLFragment singleEntrySQL = new SQLFragment(sharedSQL);
        singleEntrySQL.append("? ");
        singleEntrySQL.add(destPath);
        singleEntrySQL.append(standardWhereSQL);
        singleEntrySQL.append(" AND StringValue = ?");
        singleEntrySQL.add(srcPath);

        int rows = new SqlExecutor(OntologyManager.getExpSchema()).execute(singleEntrySQL);
        LOG.info("Updated " + rows + " row in exp.ObjectProperty for move from " + srcFile + " to " + destFile);

        // Skip attempting to fix up child paths if we know that the entry is a file. If it's not (either it's a
        // directory or it doesn't exist), then try to fix up child records
        if (!destFile.isFile())
        {
            if (!srcPath.endsWith(File.separator))
            {
                srcPath = srcPath + File.separator;
            }
            if (!destPath.endsWith(File.separator))
            {
                destPath = destPath + File.separator;
            }

            // Make the SQL to handle children
            SQLFragment childPathsSQL = new SQLFragment(sharedSQL);
            childPathsSQL.append(dialect.concatenate(new SQLFragment("?", destPath), new SQLFragment(dialect.getSubstringFunction("StringValue", Integer.toString(srcPath.length() + 1), "5000"))));
            childPathsSQL.append(standardWhereSQL);
            childPathsSQL.append(" AND ");
            childPathsSQL.append(dialect.getStringIndexOfFunction(new SQLFragment("?", srcPath), new SQLFragment("StringValue")));
            childPathsSQL.append(" = 1");

            int childRows = new SqlExecutor(OntologyManager.getExpSchema()).execute(childPathsSQL);
            LOG.info("Updated " + childRows + " child paths in exp.ObjectProperty rows for move from " + srcFile + " to " + destFile);
        }
    }

    private void updateHardTables(File srcFile, File destFile, User user, Container container)
    {
        // Figure out all of the FileLink columns in hard tables managed by OntologyManager
        SQLFragment sql = new SQLFragment("SELECT dd.StorageTableName, dd.StorageSchemaName, pd.Name FROM ");
        sql.append(OntologyManager.getTinfoDomainDescriptor(), "dd");
        sql.append(", ");
        sql.append(OntologyManager.getTinfoPropertyDescriptor(), "pd");
        sql.append(", ");
        sql.append(OntologyManager.getTinfoPropertyDomain(), "m");
        sql.append(" WHERE dd.DomainId = m.DomainId AND pd.PropertyId = m.PropertyId AND pd.RangeURI = ? ");
        sql.add(PropertyType.FILE_LINK.getTypeUri());
        sql.append(" AND dd.StorageTableName IS NOT NULL AND dd.StorageSchemaName IS NOT NULL AND pd.Name IS NOT NULL");

        List<Map<String, Object>> rows = (List)new SqlSelector(OntologyManager.getExpSchema(), sql).getCollection(Map.class);
        for (Map<String, Object> row : rows)
        {
            // Find the DbSchema/TableInfo/ColumnInfo for the FileLink column
            DbSchema schema = DbSchema.get(row.get("StorageSchemaName").toString());
            if (schema != null)
            {
                TableInfo tableInfo = schema.getTable(row.get("StorageTableName").toString());
                if (tableInfo != null)
                {
                    ColumnInfo column = tableInfo.getColumn(row.get("Name").toString());
                    if (column != null)
                    {
                        // Migrate any paths that match this file move
                        TableUpdaterFileListener updater = new TableUpdaterFileListener(tableInfo, column.getColumnName(), TableUpdaterFileListener.Type.filePath);
                        updater.fileMoved(srcFile, destFile, user, container);
                    }
                }
            }
        }
    }
}
