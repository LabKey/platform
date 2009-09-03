/*
 * Copyright (c) 2009 LabKey Corporation
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

import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.StudySchema;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.TempTableLoader;
import org.labkey.api.data.Table;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.collections.CaseInsensitiveHashMap;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.sql.SQLException;

public class ParticipantImportRunnable extends DatasetImportRunnable
{
    private String _siteLookup = "RowId";

    ParticipantImportRunnable(AbstractDatasetImportTask task, DataSetDefinition ds, File tsv, AbstractDatasetImportTask.Action action, boolean deleteAfterImport, Map<String, String> columnMap)
    {
        super(task, ds, tsv, action, deleteAfterImport, columnMap);
    }


    public void setSiteLookup(String lookup)
    {
        if (lookup.toLowerCase().equals("siteid"))
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
            _task.logError("Unexpected error importing file: " + _tsv.getName(), x);
        }
    }


    @Override
    public String validate()
    {
        String error = super.validate();
        if (error != null)
            return error;

        TableInfo site = StudySchema.getInstance().getTableInfoSite();
        ColumnInfo col = site.getColumn(_siteLookup);
        if (col == null || _siteLookup.toLowerCase().startsWith("is"))
            return "No such column in Site table: " + _siteLookup;

        return null;
    }


    public void _run() throws IOException, SQLException
    {
        TempTableLoader loader = new TempTableLoader(_tsv, true);
        CaseInsensitiveHashMap<ColumnDescriptor> columnMap = new CaseInsensitiveHashMap<ColumnDescriptor>();
        for (ColumnDescriptor c : loader.getColumns())
            columnMap.put(c.name, c);

        if (!columnMap.containsKey("ParticipantId"))
        {
            _task.logError("Dataset does not contain column ParticipantId.");
            return;
        }

        StudySchema schema = StudySchema.getInstance();

        Table.TempTableInfo tinfoTemp = loader.loadTempTable(schema.getSchema());
        TableInfo site = StudySchema.getInstance().getTableInfoSite();
        ColumnInfo siteLookup = site.getColumn(_siteLookup);

        // Merge uploaded data with Study tables

        Table.execute(schema.getSchema(),
                "INSERT INTO " + schema.getTableInfoParticipant() + " (ParticipantId)\n" +
                "SELECT ParticipantId FROM " + tinfoTemp + " WHERE ParticipantId NOT IN (SELECT ParticipantId FROM " + schema.getTableInfoParticipant() + ")", null);

        if (columnMap.containsKey("EnrollmentSiteId"))
        {
            Table.execute(schema.getSchema(),
                "UPDATE " + schema.getTableInfoParticipant() + " SET EnrollmentSiteId=study.Site.RowId\n" +
                "FROM " + tinfoTemp + " JOIN study.Site ON " + tinfoTemp.toString() + ".EnrollmentSiteId=study.Site." + siteLookup.getSelectName() + "\n" +
                "WHERE " + schema.getTableInfoParticipant() + ".ParticipantId = " + tinfoTemp.toString() + ".ParticipantId", null);
        }
        if (columnMap.containsKey("CurrentSiteId"))
        {
            Table.execute(schema.getSchema(),
                "UPDATE " + schema.getTableInfoParticipant() + " SET CurrentSiteId=study.Site.RowId\n" +
                "FROM " + tinfoTemp + " JOIN study.Site ON " + tinfoTemp.toString() + ".CurrentSiteId=study.Site." + siteLookup.getSelectName() + "\n" +
                "WHERE " + schema.getTableInfoParticipant() + ".ParticipantId = " + tinfoTemp.toString() + ".ParticipantId", null);
        }

        if (columnMap.containsKey("StartDate"))
        {
            Table.execute(schema.getSchema(),
                "UPDATE " + schema.getTableInfoParticipant() + " SET StartDate=" + tinfoTemp.toString() + ".StartDate\n" +
                "FROM " + tinfoTemp + " \n" +
                "WHERE " + schema.getTableInfoParticipant() + ".ParticipantId = " + tinfoTemp.toString() + ".ParticipantId", null);
        }
        tinfoTemp.delete();
    }
}
