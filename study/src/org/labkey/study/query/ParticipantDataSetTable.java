/*
 * Copyright (c) 2006-2010 LabKey Corporation
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

import org.labkey.api.data.*;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.study.TimepointType;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.study.StudyService;
import org.labkey.study.StudySchema;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.StudyManager;

public class ParticipantDataSetTable extends VirtualTable
{
    StudyQuerySchema _schema;
    ColumnInfo _colParticipantId;

    public ParticipantDataSetTable(StudyQuerySchema schema, ColumnInfo colParticipantId)
    {
        super(StudySchema.getInstance().getSchema());
        _schema = schema;
        _colParticipantId = colParticipantId;
        for (DataSetDefinition dataset : _schema.getStudy().getDataSets())
        {
            String name = dataset.getLabel();
            if (name == null)
                continue;
            // if not keyed by Participant/SequenceNum it is not a lookup
            if (dataset.getKeyPropertyName() != null)
                continue;
            // duplicate labels! see BUG 2206
            if (getColumn(name) != null)
                continue;
            addColumn(createDataSetColumn(name, dataset));
        }
    }

    protected ColumnInfo createDataSetColumn(String name, final DataSetDefinition def)
    {
        ColumnInfo column;
        if (_colParticipantId == null)
        {
            column = new ColumnInfo(name, this);
            column.setSqlTypeName("VARCHAR");
        }
        else
        {
            column = new AliasedColumn(name, _colParticipantId);
        }
        column.setLabel(def.getLabel());

        if (def.isDemographicData()) // If it's demographic, there are no visits, so we can add the dataset fields directly
        {
            column.setFk(new AbstractForeignKey() {
                public ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
                {
                    TableInfo table = getLookupTableInfo();

                    if (table == null)
                        return null;

                    if (displayField == null)
                        return null;

                    return LookupColumn.create(parent, table.getColumn(StudyService.get().getSubjectColumnName(def.getContainer())), table.getColumn(displayField), true);
                }

                public TableInfo getLookupTableInfo()
                {
                    try
                    {
                        DataSetTable dsTable = new DataSetTable(_schema, def);
                        dsTable.hideParticipantLookups();
                        return dsTable;
                    }
                    catch (UnauthorizedException e)
                    {
                        return null;
                    }
                }

                public StringExpression getURL(ColumnInfo parent)
                {
                    return null;
                }
            });
            column.setIsUnselectable(true);
            return column;
        }
        else
        {
            column.setFk(new AbstractForeignKey()
            {
                public ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
                {
                    if (displayField == null)
                        return null;
                    ColumnInfo ret = new ParticipantVisitDataSetTable(_schema, def, parent).getColumn(displayField);
                    ret.setLabel(parent.getLabel() + " " + ret.getLabel());
                    return ret;
                }

                public TableInfo getLookupTableInfo()
                {
                    return new ParticipantVisitDataSetTable(_schema, def, null);
                }

                public StringExpression getURL(ColumnInfo parent)
                {
                    return null;
                }
            });
            column.setIsUnselectable(true);
            return column;
        }
    }
}
