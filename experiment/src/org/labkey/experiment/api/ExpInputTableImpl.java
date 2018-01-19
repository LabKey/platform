/*
 * Copyright (c) 2010-2017 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.query.ExpInputTable;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.PageFlowUtil;

import java.util.Map;

/**
 * User: jeckels
 * Date: Jan 5, 2010
 */
public abstract class ExpInputTableImpl<C extends Enum> extends ExpTableImpl<C> implements ExpInputTable<C>
{
    private ExpRun _run;
    private ExpProtocol.ApplicationType _type;

    public ExpInputTableImpl(String name, TableInfo rootTable, UserSchema schema, AbstractProtocolOutputImpl objectType)
    {
        super(name, rootTable, schema, objectType);
    }

    public void setRun(ExpRun run, ExpProtocol.ApplicationType type)
    {
        _run = run;
        _type = type;
        applyFilters();
    }

    private void applyFilters()
    {
        clearConditions(FieldKey.fromParts("FolderRunType"));

        SQLFragment sqlFragment = new SQLFragment("(SELECT er.Container FROM ");
        sqlFragment.append(ExperimentServiceImpl.get().getTinfoExperimentRun(), "er");
        sqlFragment.append(", ");
        sqlFragment.append(ExperimentServiceImpl.get().getTinfoProtocolApplication(), "pa");
        sqlFragment.append(" WHERE er.RowId = pa.RunId AND pa.RowId = TargetApplicationId");
        if (_run != null)
        {
            sqlFragment.append(" AND er.RowId = ?");
            sqlFragment.add(_run.getRowId());
        }
        if (_type != null)
        {
            sqlFragment.append(" AND pa.CpasType = ?");
            sqlFragment.add(_type.toString());
        }
        sqlFragment.append(")");

        if (_run == null)
        {
            // We're not filtering to a single run, so we need to filter based on all of the containers that the user
            // has permission to see, subject to the container filter
            addCondition(getContainerFilter().getSQLFragment(getSchema(), sqlFragment, getContainer(), false), FieldKey.fromParts("FolderRunType"));
        }
        else
        {
            // We're filtering on a single run, so we can bypass some of the permissions checking based on whether
            // the user has permission to the run's container or not
            if (_run.getContainer().hasPermission(_userSchema.getUser(), ReadPermission.class))
            {
                // All we care is that the run exists and matches the TargetApplicationId
                sqlFragment.append(" IS NOT NULL ");
            }
            else
            {
                // Don't match anything, the user doesn't have permission. Shouldn't get this far anyway, but just
                // to play it safe.
                sqlFragment.append(" = NULL ");
            }
            addCondition(sqlFragment, FieldKey.fromParts("FolderRunType"));
        }
    }

    @Override
    protected void applyContainerFilter(ContainerFilter filter)
    {
        applyFilters();
    }

    @Override
    protected ColumnInfo resolveColumn(String name)
    {
        ColumnInfo col = super.resolveColumn(name);
        if (col != null)
            return col;

        return null;
    }

}
