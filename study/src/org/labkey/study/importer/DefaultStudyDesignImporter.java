/*
 * Copyright (c) 2014 LabKey Corporation
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
package org.labkey.study.importer;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.ImportException;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.query.studydesign.DefaultStudyDesignTable;

import java.io.InputStream;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Created by klum on 1/24/14.
 */
public class DefaultStudyDesignImporter
{
    /**
     * Removes previous data for the specified table and container
     * @param ctx
     * @param tableInfo
     */
    protected void deleteData(StudyImportContext ctx, TableInfo tableInfo) throws ImportException
    {
        try {
            if (tableInfo instanceof FilteredTable)
            {
                Table.delete(((FilteredTable)tableInfo).getRealTable(), SimpleFilter.createContainerFilter(ctx.getContainer()));
            }
        }
        catch (SQLException e)
        {
            throw new ImportException(e.getMessage());
        }
    }

    protected void importTableData(StudyImportContext ctx, VirtualFile vf, TableInfo tableInfo,
                                                     @Nullable TransformBuilder transformBuilder,
                                                     @Nullable TransformHelper transformHelper) throws Exception
    {
        BatchValidationException errors = new BatchValidationException();
        if (null != tableInfo)
        {
            String fileName = getFileName(tableInfo);
            try (InputStream tsv = vf.getInputStream(fileName))
            {
                if (null != tsv)
                {
                    DataLoader loader = DataLoader.get().createLoader(fileName, null, tsv, true, null, TabLoader.TSV_FILE_TYPE);
                    QueryUpdateService qus = tableInfo.getUpdateService();

                    if (transformBuilder != null || transformHelper != null)
                    {
                        // optimally we would use ETL to convert imported FKs so that the relationships are intact, but since
                        // the underlying tableInfo's do not implement UpdateableTableInfo, we are forced to use the deprecated insertRows
                        // method on QueryUpdateService.

                        List<Map<String, Object>> rows = loader.load();
                        List<Map<String, Object>> insertedRows;

                        if (transformHelper != null)
                        {
                            if (!(transformHelper instanceof TransformHelper))
                                throw new ImportException("The specified transform helper does not implement the TransformHelper interface");
                            insertedRows = qus.insertRows(ctx.getUser(), ctx.getContainer(), transformHelper.transform(ctx, rows), errors, null);
                        }
                        else
                            insertedRows = qus.insertRows(ctx.getUser(), ctx.getContainer(), rows, errors, null);

                        if (transformBuilder != null)
                        {
                            if (!(transformBuilder instanceof TransformBuilder))
                                throw new ImportException("The specified transform builder does not implement the TransformBuilder interface");
                            transformBuilder.createTransformInfo(ctx, rows, insertedRows);
                        }
                    }
                    else
                    {
                        qus.importRows(ctx.getUser(), ctx.getContainer(), loader, errors, null);
                    }
                }
                else
                    ctx.getLogger().warn("Unable to open the file at: " + fileName);
            }
        }
        else
            ctx.getLogger().warn("NULL tableInfo passed into importTableData.");

        if (errors.hasErrors())
            throw new ImportException(errors.getMessage());
    }

    protected String getFileName(TableInfo tableInfo)
    {
        return tableInfo.getName().toLowerCase() + ".tsv";
    }

    /**
     * Interface which allows the transform to create and initialize the transform based on the original
     * and inserted data.
     */
    interface TransformBuilder
    {
        void createTransformInfo(StudyImportContext ctx, List<Map<String, Object>> origRows, List<Map<String, Object>> insertedRows) throws ImportException;
    }

    /**
     * Interface which allows the transform to update the original data before insertion
     */
    interface TransformHelper
    {
        List<Map<String, Object>> transform(StudyImportContext ctx, List<Map<String, Object>> origRows) throws ImportException;
    }
}
