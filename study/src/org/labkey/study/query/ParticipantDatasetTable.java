/*
 * Copyright (c) 2015-2019 LabKey Corporation
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

import org.labkey.api.data.AbstractForeignKey;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.LookupColumn;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.VirtualTable;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.TimepointType;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.study.StudySchema;
import org.labkey.study.model.DatasetDefinition;

public class ParticipantDatasetTable extends VirtualTable<StudyQuerySchema>
{
    ColumnInfo _colParticipantId;

    public ParticipantDatasetTable(StudyQuerySchema schema, ContainerFilter cf, ColumnInfo colParticipantId)
    {
        super(StudySchema.getInstance().getSchema(), null, schema, cf);
        _colParticipantId = colParticipantId;
        for (DatasetDefinition dataset : schema.getStudy().getDatasets())
        {
            // verify that the current user has permission to read this dataset (they may not if
            // advanced study security is enabled).
            if (!dataset.canRead(schema.getUser()))
                continue;

            String name = dataset.getName();
            if (name == null)
                continue;
            // if not keyed by Participant/SequenceNum it is not a lookup
            if (dataset.getKeyPropertyName() != null)
                continue;
            // duplicate labels! see BUG 2206
            if (getColumn(name) != null)
                continue;
            addColumn(createDatasetColumn(name, dataset));
        }
    }

    protected BaseColumnInfo createDatasetColumn(String name, final DatasetDefinition def)
    {
        BaseColumnInfo column;
        if (_colParticipantId == null)
        {
            column = new BaseColumnInfo(name, this, JdbcType.VARCHAR);
            column.setSqlTypeName("VARCHAR");
        }
        else
        {
            column = new AliasedColumn(name, _colParticipantId);
        }
        column.setLabel(def.getLabel());

        // If it's demographic or a continuous study, there are no visits, so we can add the dataset fields directly
        if (def.isDemographicData() || def.getStudy().getTimepointType() == TimepointType.CONTINUOUS)
        {
            column.setFk(new AbstractForeignKey(getUserSchema(), getContainerFilter())
            {
                @Override
                public ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
                {
                    TableInfo table = getLookupTableInfo();

                    if (table == null)
                        return null;

                    if (displayField == null)
                        return null;

                    return LookupColumn.create(parent, table.getColumn(StudyService.get().getSubjectColumnName(def.getContainer())), table.getColumn(displayField), false);
                }

                @Override
                public TableInfo getLookupTableInfo()
                {
                    try
                    {
                        return _userSchema.getDatasetTableForLookup(def, getContainerFilter());
                    }
                    catch (UnauthorizedException e)
                    {
                        return null;
                    }
                }

                @Override
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
            column.setFk(new AbstractForeignKey(getUserSchema(), getContainerFilter())
            {
                @Override
                public ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
                {
                    if (displayField == null)
                        return null;
                    var ret = new ParticipantVisitDatasetTable(_userSchema, getLookupContainerFilter(), def, parent).getMutableColumn(displayField);
                    if (ret == null)
                        return null;
                    ret.setLabel(parent.getLabel() + " " + ret.getLabel());
                    return ret;
                }

                @Override
                public TableInfo getLookupTableInfo()
                {
                    return new ParticipantVisitDatasetTable(_userSchema, getLookupContainerFilter(), def, null);
                }

                @Override
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
