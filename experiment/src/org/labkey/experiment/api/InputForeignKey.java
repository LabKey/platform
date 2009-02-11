/*
 * Copyright (c) 2006-2009 LabKey Corporation
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
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.exp.query.ExpProtocolApplicationTable;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;

import java.util.*;

/**
 * This class is a foreign key which has columns for data and material inputs of various types.
 */
public class InputForeignKey extends LookupForeignKey
{
    private final ExpSchema _schema;
    private final ExpProtocol.ApplicationType _type;
    private final ContainerFilter _filter;
    private Set<String> _dataInputs;
    private Set<String> _materialInputs;

    public InputForeignKey(ExpSchema schema, ExpProtocol.ApplicationType type, ContainerFilter filter)
    {
        super(null);
        _schema = schema;
        _type = type;
        _filter = filter;
    }


    public TableInfo getLookupTableInfo()
    {
        ExpProtocolApplicationTable ret = ExperimentService.get().createProtocolApplicationTable(ExpSchema.TableType.ProtocolApplications.toString(), "InputLookup", _schema);
        ret.setContainerFilter(_filter, _schema.getUser());
        SamplesSchema samplesSchema = _schema.getSamplesSchema();
        for (String role : getDataInputs())
        {
            ret.safeAddColumn(ret.createDataInputColumn(role, _schema, role));
        }
        for (String role : getMaterialInputs())
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

    private Set<String> getDataInputs()
    {
        if (_dataInputs == null)
        {
            _dataInputs = ExperimentService.get().getDataInputRoles(_schema.getContainer(), _type);
        }
        return _dataInputs;
    }

    private Set<String> getMaterialInputs()
    {
        if (_materialInputs == null)
        {
            _materialInputs = ExperimentService.get().getMaterialInputRoles(_schema.getContainer(), _type);
        }
        return _materialInputs;
    }
}
