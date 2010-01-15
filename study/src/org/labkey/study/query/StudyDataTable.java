/*
 * Copyright (c) 2010 LabKey Corporation
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

package org.labkey.study.query;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.property.SystemProperty;
import org.labkey.api.query.*;
import org.labkey.api.view.ActionURL;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.StudyController;

import java.util.Arrays;
import java.util.List;

/**
 * User: kevink
 * Date: Jan 11, 2010 4:31:18 PM
 *
 * Proof of concept.  Eventually just use DataSetDefinition.StudyDataTableInfo.
 */
public class StudyDataTable extends FilteredTable
{
    StudyQuerySchema _schema;

    public StudyDataTable(StudyQuerySchema schema)
    {
        super(StudySchema.getInstance().getTableInfoStudyData(), schema.getContainer());
        _schema = schema;

        ColumnInfo datasetColumn = new AliasedColumn(this, "DataSet", _rootTable.getColumn("DataSetId"));
        datasetColumn.setFk(new LookupForeignKey(getDataSetURL(), "datasetId", "datasetId", "Name") {
            public TableInfo getLookupTableInfo()
            {
                return new DataSetsTable(_schema);
            }
        });
        addColumn(datasetColumn);

        ColumnInfo participantIdColumn = new AliasedColumn(this, "ParticipantId", _rootTable.getColumn("ParticipantId"));
        participantIdColumn.setFk(new TitleForeignKey(getParticipantURL(), null, null, "participantId"));
        addColumn(participantIdColumn);

        ColumnInfo dateColumn = new AliasedColumn(this, "Date", _rootTable.getColumn("_visitdate"));
        addColumn(dateColumn);

        ColumnInfo createdColumn = new AliasedColumn(this, "Created", _rootTable.getColumn("Created"));
        addColumn(createdColumn);

        ColumnInfo modifiedColumn = new AliasedColumn(this, "Modified", _rootTable.getColumn("Modified"));
        addColumn(modifiedColumn);

        ColumnInfo lsidColumn = new AliasedColumn(this, "LSID", _rootTable.getColumn("lsid"));
        lsidColumn.setHidden(true);
        addColumn(lsidColumn);

        ColumnInfo sourceLsidColumn = new AliasedColumn(this, "Source LSID", _rootTable.getColumn("sourceLsid"));
        sourceLsidColumn.setHidden(true);
        addColumn(sourceLsidColumn);

        ColumnInfo sequenceNumColumn = new AliasedColumn(this, "SequenceNum", _rootTable.getColumn("SequenceNum"));
        sequenceNumColumn.setHidden(true);
        addColumn(sequenceNumColumn);

        ColumnInfo propertiesColumn = new AliasedColumn(this, "Properties", _rootTable.getColumn("lsid"));
        propertiesColumn.setIsUnselectable(true);
        List<PropertyDescriptor> pds = SystemProperty.getProperties();
        propertiesColumn.setFk(new PropertyForeignKey(pds.toArray(new PropertyDescriptor[pds.size()]), _schema));
        addColumn(propertiesColumn);

        setDefaultVisibleColumns(Arrays.asList(
                FieldKey.fromParts("DataSet"),
                FieldKey.fromParts("ParticipantId"),
                FieldKey.fromParts("Date"),
                FieldKey.fromParts("Properties", "Comment")
        ));
    }

    public ActionURL getParticipantURL()
    {
        return new ActionURL(StudyController.ParticipantAction.class, _schema.getContainer());
    }

    public ActionURL getDataSetURL()
    {
        return new ActionURL(StudyController.DatasetAction.class, _schema.getContainer());
    }

}
