/*
 * Copyright (c) 2015 LabKey Corporation
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
package org.labkey.api.study.assay.matrix;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.CrosstabDimension;
import org.labkey.api.data.CrosstabMeasure;
import org.labkey.api.data.CrosstabMember;
import org.labkey.api.data.CrosstabSettings;
import org.labkey.api.data.CrosstabTable;
import org.labkey.api.data.CrosstabTableInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.security.User;
import org.labkey.api.study.assay.AssayProtocolSchema;
import org.labkey.api.study.assay.AssayProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class AbstractMatrixProtocolSchema extends AssayProtocolSchema
{

    private final String _dataBySampleTableName;
    private final String _dataTableName;

    public AbstractMatrixProtocolSchema(User user, Container container, @NotNull AssayProvider provider,
                                        @NotNull ExpProtocol protocol, @Nullable Container targetStudy,
                                        @NotNull String dataSampleTableName, @NotNull String dataTableName)
    {
        super(user, container, provider, protocol, targetStudy);
        _dataBySampleTableName = dataSampleTableName;
        _dataTableName = dataTableName;
    }
    public String getDataTableName()
    {
        return _dataTableName;
    }

    public String getDataBySampleTableName()
    {
        return _dataBySampleTableName;
    }

    public abstract List<Map> getDistinctSampleIds();

    public abstract TableInfo getDataTableInfo();

    @Override
    public FilteredTable createDataTable(boolean includeCopiedToStudyColumns)
    {
       return null;
    }

    public TableInfo createTable(String name, String rowAxisId, String colAxisId, String valueMeasureName, String title)
    {
        if (name.equals(getDataBySampleTableName()))
        {
            return getDataBySampleTable(rowAxisId, colAxisId, valueMeasureName, title); //TODO: change
        }

        return super.createTable(name);
    }

    public CrosstabTableInfo getDataBySampleTable(String rowAxisId, String colAxisId, String valueMeasureName, String title)
    {
        CrosstabSettings settings = new CrosstabSettings(getDataTableInfo());
        CrosstabTable cti;
        CrosstabDimension rowDim = settings.getRowAxis().addDimension(FieldKey.fromParts(rowAxisId));
        CrosstabDimension colDim = settings.getColumnAxis().addDimension(FieldKey.fromParts(colAxisId));
        CrosstabMeasure valueMeasure = settings.addMeasure(FieldKey.fromParts(valueMeasureName), CrosstabMeasure.AggregateFunction.MIN, valueMeasureName);

        List<FieldKey> defaultCols = new ArrayList<>();
        ArrayList<CrosstabMember> members = new ArrayList<>();

        List<Map> distinctSampleIds = getDistinctSampleIds();

        for (Map sample : distinctSampleIds)
        {
            members.add(new CrosstabMember(sample.get(colAxisId), colDim, (String) sample.get("Name")));
        }

        defaultCols.add(FieldKey.fromParts(valueMeasure.getName()));
        defaultCols.add(rowDim.getFieldKey());
        defaultCols.add(colDim.getFieldKey());

        cti = new CrosstabTable(settings, members);
        cti.setDefaultVisibleColumns(defaultCols);
        cti.setTitle(title);

        return cti;
    }

    public Set<String> getTableNames()
    {
        Set<String> result = super.getTableNames();
        result.add(_dataBySampleTableName);
        return result;
    }

}
