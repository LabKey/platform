/*
 * Copyright (c) 2010-2016 LabKey Corporation
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
import org.labkey.api.query.*;
import org.labkey.api.study.StudyService;
import org.labkey.api.view.ActionURL;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.StudyImpl;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: kevink
 * Date: Jan 11, 2010 4:31:18 PM
 *
 * Provides a Query-ized TableInfo that contains every row from every dataset in the study.
 * Eventually merge with StudyUnionTableInfo.
 */
public class StudyDataTable extends BaseStudyTable
{
    public StudyDataTable(StudyQuerySchema schema)
    {
        super(schema, StudySchema.getInstance().getTableInfoStudyData(schema.getStudy(), schema.getUser()));

        List<FieldKey> defaultColumns = new LinkedList<>();

        ColumnInfo datasetColumn = new AliasedColumn(this, "DataSet", _rootTable.getColumn("DataSet"));
        datasetColumn.setFk(new LookupForeignKey(getDatasetURL(), "entityId", "entityId", "Name") {
            public TableInfo getLookupTableInfo()
            {
                return new DatasetsTable(_userSchema);
            }
        });
        addColumn(datasetColumn);
        defaultColumns.add(FieldKey.fromParts("DataSet"));

        addFolderColumn();
        addStudyColumn();

        String subjectColName = StudyService.get().getSubjectColumnName(getContainer());
        ColumnInfo participantIdColumn = new AliasedColumn(this, subjectColName, _rootTable.getColumn("participantid"));
        participantIdColumn.setFk(new QueryForeignKey(_userSchema, null, StudyService.get().getSubjectTableName(getContainer()), subjectColName, null));
        addColumn(participantIdColumn);
        defaultColumns.add(FieldKey.fromParts(subjectColName));

        ColumnInfo dateColumn = new AliasedColumn(this, "Date", _rootTable.getColumn("_visitdate"));
        addColumn(dateColumn);
        defaultColumns.add(FieldKey.fromParts("Date"));

        ColumnInfo createdColumn = new AliasedColumn(this, "Created", _rootTable.getColumn("Created"));
        addColumn(createdColumn);
        ColumnInfo createdByColumn = new AliasedColumn(this, "CreatedBy", _rootTable.getColumn("CreatedBy"));
        addColumn(createdByColumn);


        ColumnInfo modifiedColumn = new AliasedColumn(this, "Modified", _rootTable.getColumn("Modified"));
        addColumn(modifiedColumn);
        ColumnInfo modifiedByColumn = new AliasedColumn(this, "ModifiedBy", _rootTable.getColumn("ModifiedBy"));
        addColumn(modifiedByColumn);

        ColumnInfo lsidColumn = new AliasedColumn(this, "LSID", _rootTable.getColumn("lsid"));
        lsidColumn.setHidden(true);
        addColumn(lsidColumn);

        ColumnInfo qcStateColumn = new AliasedColumn(this, "QCState", _rootTable.getColumn("qcstate"));
        // isHidden doesn't get propagated like the other properties, so preserve its value separately
        qcStateColumn.setHidden(_rootTable.getColumn("qcstate").isHidden());
        addColumn(qcStateColumn);

        ColumnInfo sourceLsidColumn = new AliasedColumn(this, "Source LSID", _rootTable.getColumn("sourceLsid"));
        sourceLsidColumn.setHidden(true);
        addColumn(sourceLsidColumn);

        ColumnInfo sequenceNumColumn = new AliasedColumn(this, "SequenceNum", _rootTable.getColumn("SequenceNum"));
        sequenceNumColumn.setHidden(true);
        sequenceNumColumn.setMeasure(false);
        addColumn(sequenceNumColumn);

        // Find a name for the extra key column that doesn't conflict with any existing columns
        String columnName = "AdditionalKey";
        int suffix = 2;
        while (getColumn(columnName) != null)
        {
            columnName = "AdditionalKey" + (suffix++);
        }
        ColumnInfo additionalKeyColumn = wrapColumn(columnName, _rootTable.getColumn("_key"));
        addColumn(additionalKeyColumn);
        defaultColumns.add(additionalKeyColumn.getFieldKey());
        
        StudyImpl study = _userSchema.getStudy();
        if (study != null)
        {
            Set<PropertyDescriptor> pds = study.getSharedProperties();
            for (PropertyDescriptor pd : pds)
            {
                // Avoid double-adding columns with the same name but different property descriptors
                if (getColumn(pd.getName()) == null)
                {
                    addWrapColumn(_rootTable.getColumn(pd.getName()));
                    defaultColumns.add(FieldKey.fromParts(pd.getName()));
                }
            }
        }

        setDefaultVisibleColumns(defaultColumns);

        setDescription("Contains one row for every row in every dataset in this folder with the columns that are common " +
                "across all the datasets");

        Map<String, String> params = new HashMap<>();
        params.put("lsid", "LSID");
        params.put("datasetId", "Dataset");
        setDetailsURL(new DetailsURL(new ActionURL(StudyController.DatasetDetailRedirectAction.class, getContainer()), params));
    }

    public ActionURL getDatasetURL()
    {
        return new ActionURL(StudyController.DatasetAction.class, _userSchema.getContainer());
    }
}
