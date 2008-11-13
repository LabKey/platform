/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

package org.labkey.experiment.api;

import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.exp.api.*;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.ColumnInfo;

import java.util.*;

/**
 * This class is a foreign key which has columns for data and material inputs of various types.
 */
public class InputForeignKey extends LookupForeignKey
{
    ExpSchema _schema;
    private final ContainerFilter _filter;
    Set<String> _dataInputs;
    Set<String> _materialInputs;

    public InputForeignKey(ExpSchema schema, ExpProtocol.ApplicationType type, ContainerFilter filter)
    {
        super(null);
        _schema = schema;
        _filter = filter;
        _dataInputs = ExperimentService.get().getDataInputRoles(schema.getContainer(), type);
        _materialInputs = ExperimentService.get().getMaterialInputRoles(schema.getContainer(), type);
    }

    public TableInfo getLookupTableInfo()
    {
        ExpProtocolApplicationTable ret = ExperimentService.get().createProtocolApplicationTable(ExpSchema.TableType.ProtocolApplications.toString(), "InputLookup", _schema);
        ret.setContainerFilter(_filter);
        SamplesSchema samplesSchema = _schema.getSamplesSchema();
        for (String role : _dataInputs)
        {
            ret.safeAddColumn(ret.createDataInputColumn(role, _schema, role));
        }
        for (String role : _materialInputs)
        {
            ExpSampleSet[] matchingSets = ExperimentService.get().getSampleSetsForRole(_schema.getContainer(), role);
            ExpSampleSet ss;
            if (matchingSets.length == 1)
            {
                ss = matchingSets[0];
            }
            else
            {
                ss = ExperimentService.get().lookupActiveSampleSet(_schema.getContainer());
            }
            ret.safeAddColumn(ret.createMaterialInputColumn(role, samplesSchema, ss, role));
        }
        return ret;
    }

    protected ColumnInfo getPkColumn(TableInfo table)
    {
        assert table instanceof ExpProtocolApplicationTable;
        ExpProtocolApplicationTable appTable = (ExpProtocolApplicationTable) table;
        return appTable.createColumn("~", ExpProtocolApplicationTable.Column.RowId);
    }
}
