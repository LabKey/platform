/*
 * Copyright (c) 2018-2019 LabKey Corporation
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

import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.UserSchema;

import static org.labkey.api.exp.query.ExpSchema.TableType.DataClasses;
import static org.labkey.api.exp.query.ExpSchema.TableType.SampleSets;

public abstract class ExpProtocolInputTableImpl<C extends Enum> extends ExpTableImpl<C>
{
    protected ExpProtocolInputTableImpl(String name, TableInfo rootTable, UserSchema schema, ContainerFilter cf)
    {
        super(name, rootTable, schema, null, cf);
    }

    @Override
    protected void applyContainerFilter(ContainerFilter filter)
    {
        // We don't have our own Container column, so filter based on the Container column of this ProtocolInput's protocol
        FieldKey containerFK = FieldKey.fromParts("Container");
        clearConditions(containerFK);
        SQLFragment sqlFragment = new SQLFragment("(SELECT p.Container FROM ");
        sqlFragment.append(ExperimentServiceImpl.get().getTinfoProtocol(), "p");
        sqlFragment.append(" WHERE p.RowId = ProtocolId)");

        addCondition(getContainerFilter().getSQLFragment(getSchema(), sqlFragment, false), containerFK);
    }

    protected MutableColumnInfo createRowIdColumn(String alias)
    {
        var col = wrapColumn(alias, _rootTable.getColumn("RowId"));
        col.setHidden(true);
        return col;
    }

    protected MutableColumnInfo createNameColumn(String alias)
    {
        return wrapColumn(alias, _rootTable.getColumn("Name"));
    }

    protected MutableColumnInfo createLsidColumn(String alias)
    {
        var col = wrapColumn(alias, _rootTable.getColumn("LSID"));
        col.setHidden(true);
        col.setShownInInsertView(false);
        col.setShownInUpdateView(false);
        col.setUserEditable(false);
        col.setCalculated(true);
        return col;
    }

    protected MutableColumnInfo createInputColumn(String alias)
    {
        var col = wrapColumn(alias, _rootTable.getColumn("input"));
        return col;
    }

    protected MutableColumnInfo createProtocolColumn(String alias)
    {
        var col = wrapColumn(alias, _rootTable.getColumn("protocolId"));
        col.setFk(getExpSchema().getProtocolForeignKey(getContainerFilter(), "RowId"));
        return col;
    }

    protected MutableColumnInfo createSampleSetColumn(String alias)
    {
        var col = wrapColumn(alias, _rootTable.getColumn("MaterialSourceId"));
        var fk = QueryForeignKey
                .from(getUserSchema(), getContainerFilter())
                .schema(ExpSchema.SCHEMA_NAME, getContainer())
                .to(SampleSets.name(), "RowId", null);
        col.setFk( fk );
        return col;
    }

    protected MutableColumnInfo createDataClassColumn(String alias)
    {
        var col = wrapColumn(alias, _rootTable.getColumn("DataClassId"));
        var fk = QueryForeignKey
                .from(getUserSchema(), getContainerFilter())
                .schema(ExpSchema.SCHEMA_NAME, getContainer())
                .to(DataClasses.name(), "RowId", null);
        col.setFk( fk );
        return col;
    }

    protected MutableColumnInfo createMinOccursColumn(String alias)
    {
        return wrapColumn(alias, _rootTable.getColumn("MinOccurs"));
    }

    protected MutableColumnInfo createMaxOccursColumn(String alias)
    {
        return wrapColumn(alias, _rootTable.getColumn("MaxOccurs"));
    }

}
