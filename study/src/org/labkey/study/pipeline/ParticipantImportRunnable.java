/*
 * Copyright (c) 2009-2026 LabKey Corporation
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

package org.labkey.study.pipeline;

import org.apache.logging.log4j.Logger;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TempTableInfo;
import org.labkey.api.data.TempTableWriter;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.specimen.SpecimenSchema;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.StudySchema;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.SQLException;
import java.util.Date;
import java.util.Map;

public class ParticipantImportRunnable extends DatasetImportRunnable
{
    private String _siteLookup = "RowId";

    ParticipantImportRunnable(Logger logger, StudyImpl study, DatasetDefinition ds, VirtualFile root, String fileName, AbstractDatasetImportTask.Action action, boolean deleteAfterImport, Date defaultReplaceCutoff, Map<String, String> columnMap)
    {
        super(logger, study, ds, root, fileName, action, deleteAfterImport, defaultReplaceCutoff, columnMap, null);
    }


    public void setSiteLookup(String lookup)
    {
        if (lookup.equalsIgnoreCase("siteid"))
            lookup = "RowId";
        _siteLookup = lookup;
    }


    @Override
    public void run()
    {
        try
        {
            _run();
        }
        catch (Exception x)
        {
            _logger.error("Unexpected error importing file: {}", _fileName, x);
        }
    }


    @Override
    public String validate()
    {
        String error = super.validate();
        if (error != null)
            return error;

        TableInfo site = SpecimenSchema.get().getTableInfoLocation(getDatasetDefinition().getContainer());
        ColumnInfo col = site.getColumn(_siteLookup);
        if (col == null || _siteLookup.toLowerCase().startsWith("is"))
            return "No such column in Site table: " + _siteLookup;

        return null;
    }


    public void _run() throws IOException, SQLException
    {
        TabLoader loader = null;
        Container container = getDatasetDefinition().getContainer();

        try
        {
            loader = new TabLoader(new BufferedReader(new InputStreamReader(_root.getInputStream(_fileName), StringUtilsLabKey.DEFAULT_CHARSET)), true);

            CaseInsensitiveHashMap<ColumnDescriptor> columnMap = new CaseInsensitiveHashMap<>();
            for (ColumnDescriptor c : loader.getColumns())
                columnMap.put(c.name, c);

            String subjectIdCol = StudyService.get().getSubjectColumnName(container);
            if (!columnMap.containsKey(subjectIdCol))
            {
                _logger.error("Dataset does not contain column {}.", subjectIdCol);
                return;
            }

            StudySchema schema = StudySchema.getInstance();

            TempTableWriter ttl = new TempTableWriter(loader);
            TempTableInfo tinfoTemp = ttl.loadTempTable();
            TableInfo site = SpecimenSchema.get().getTableInfoLocation(container);
            ColumnInfo siteLookup = site.getColumn(_siteLookup);

            // Merge uploaded data with Study tables

            SqlExecutor executor = new SqlExecutor(schema.getSchema());

            executor.execute(SQLFragment.unsafe("INSERT INTO ").append(schema.getTableInfoParticipant()).unsafeAppend(" (ParticipantId)\n" +
                    "SELECT ").appendIdentifier(subjectIdCol).unsafeAppend(" FROM ").append(tinfoTemp).unsafeAppend(" WHERE ").appendIdentifier(subjectIdCol).unsafeAppend(" NOT IN (SELECT ParticipantId FROM ").append(schema.getTableInfoParticipant()).unsafeAppend(")"));

            if (columnMap.containsKey("EnrollmentSiteId"))
            {
                executor.execute(SQLFragment.unsafe("UPDATE ").append(schema.getTableInfoParticipant()).unsafeAppend(" SET EnrollmentSiteId=").append(site).unsafeAppend(".RowId\n" +
                        "FROM ").append(tinfoTemp).unsafeAppend(" JOIN ").append(site).unsafeAppend(" ON ").append(tinfoTemp).unsafeAppend(".EnrollmentSiteId=").append(site).unsafeAppend(".").appendIdentifier(siteLookup.getSelectIdentifier()).unsafeAppend("\n" +
                        "WHERE ").append(schema.getTableInfoParticipant()).unsafeAppend(".ParticipantId = ").append(tinfoTemp).unsafeAppend(".").appendIdentifier(subjectIdCol));
            }
            if (columnMap.containsKey("CurrentSiteId"))
            {
                executor.execute(SQLFragment.unsafe("UPDATE ").append(schema.getTableInfoParticipant()).unsafeAppend(" SET CurrentSiteId=").append(site).unsafeAppend(".RowId\n" +
                        "FROM ").append(tinfoTemp).unsafeAppend(" JOIN ").append(site).unsafeAppend(" ON ").append(tinfoTemp).unsafeAppend(".CurrentSiteId=").append(site).unsafeAppend(".").appendIdentifier(siteLookup.getSelectIdentifier()).unsafeAppend("\n" +
                        "WHERE ").append(schema.getTableInfoParticipant()).unsafeAppend(".ParticipantId = ").append(tinfoTemp).unsafeAppend(".").appendIdentifier(subjectIdCol));
            }

            if (columnMap.containsKey("StartDate"))
            {
                executor.execute(SQLFragment.unsafe("UPDATE ").append(schema.getTableInfoParticipant()).unsafeAppend(" SET StartDate=").append(tinfoTemp).unsafeAppend(".StartDate\n" +
                        "FROM ").append(tinfoTemp).unsafeAppend(" \n" +
                        "WHERE ").append(schema.getTableInfoParticipant()).unsafeAppend(".ParticipantId = ").append(tinfoTemp).unsafeAppend(".").appendIdentifier(subjectIdCol));
            }
            tinfoTemp.delete();
        }
        finally
        {
            StudyManager.getInstance().clearParticipantCache(container);
            if (loader != null)
                loader.close();
        }
    }
}
