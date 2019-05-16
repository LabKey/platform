/*
 * Copyright (c) 2018 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.query.ExpMaterialInputTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.UserSchema;

public abstract class ExpRunItemTableImpl<C extends Enum> extends ExpTableImpl<C>
{
    protected ExpRunItemTableImpl(String name, TableInfo rootTable, UserSchema schema, @Nullable ExpObjectImpl objectType, ContainerFilter cf)
    {
        super(name, rootTable, schema, objectType, cf);
    }

    /**
     * Create a column with a lookup to MaterialInput that is joined by materialId
     * and a targetProtocolApplication provided by the <code>protocolApplication</code> column.
     */
    protected BaseColumnInfo createEdgeColumn(String alias, C protocolAppColumn, ExpSchema.TableType lookupTable)
    {
        var col = wrapColumn(alias, _rootTable.getColumn("rowId"));
        LookupForeignKey fk = new LookupForeignKey()
        {
            @Override
            public @Nullable TableInfo getLookupTableInfo()
            {
                UserSchema schema = getUserSchema();
                ExpSchema expSchema = new ExpSchema(schema.getUser(), schema.getContainer());
                return expSchema.getTable(lookupTable);
            }
        };
        fk.addJoin(FieldKey.fromParts(protocolAppColumn.name()), ExpMaterialInputTable.Column.TargetProtocolApplication.name(), false);
        col.setFk(fk);
        col.setUserEditable(false);
        col.setReadOnly(true);
        col.setHidden(true);
        return col;
    }


    protected BaseColumnInfo createLineageColumn(ExpTableImpl table, String alias, boolean inputs)
    {
        var ret = table.wrapColumn(alias, table.getRealTable().getColumn("LSID"));
        ret.setFk(new LineageForeignKey(table, inputs));
        ret.setCalculated(true);
        ret.setUserEditable(false);
        ret.setReadOnly(true);
        ret.setShownInDetailsView(false);
        ret.setShownInInsertView(false);
        ret.setShownInUpdateView(false);
        ret.setIsUnselectable(true);
        ret.setHidden(true);
        ret.setConceptURI("http://www.labkey.org/exp/xml#" + alias);
        ret.setPropertyURI("http://www.labkey.org/exp/xml#" + alias);
        return ret;
    }

}
