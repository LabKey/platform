/*
 * Copyright (c) 2007-2013 LabKey Corporation
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

package org.labkey.api.study.assay;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.query.ExpRunTable;
import org.labkey.api.query.*;
import org.labkey.api.study.query.ProtocolFilteredObjectTable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Used only for migrating data out of OntologyManager into hard tables at upgrade time.
 * 
 * User: brittp
 * Date: Jul 6, 2007
 * Time: 5:35:15 PM
 */
@Deprecated
/* package */ class RunDataTable extends FilteredTable<AssayProtocolSchema>
{
    private Domain _resultsDomain;

    public RunDataTable(final AssayProtocolSchema schema, boolean forUpgrade)
    {
        super(new ProtocolFilteredObjectTable(schema, schema.getProtocol().getLSID()), schema);
        setDescription("Contains all of the results (and may contain raw data as well) for the " + schema.getProtocol().getName() + " assay definition");
        List<FieldKey> visibleColumns = new ArrayList<FieldKey>();
        ColumnInfo objectIdColumn = addWrapColumn(_rootTable.getColumn("ObjectId"));
        objectIdColumn.setKeyField(true);
        ColumnInfo column = wrapColumn("Properties", _rootTable.getColumn("ObjectId"));
        column.setKeyField(false);
        column.setIsUnselectable(true);
        _resultsDomain = schema.getProvider().getResultsDomain(schema.getProtocol());
        DomainProperty[] resultsDPs = _resultsDomain.getProperties();
        QcAwarePropertyForeignKey fk = new QcAwarePropertyForeignKey(resultsDPs, this, schema);
        fk.setParentIsObjectId(true);
        fk.addDecorator(new SpecimenPropertyColumnDecorator(schema.getProvider(),schema.getProtocol(), schema));

        Set<String> hiddenCols = new HashSet<String>();
        for (PropertyDescriptor pd : fk.getDefaultHiddenProperties())
            hiddenCols.add(pd.getName());

        FieldKey dataKeyProp = new FieldKey(null, column.getName());
        for (DomainProperty lookupCol : resultsDPs)
        {
            if (!lookupCol.isHidden() && !hiddenCols.contains(lookupCol.getName()))
                visibleColumns.add(new FieldKey(dataKeyProp, lookupCol.getName()));
        }
        column.setFk(fk);
        addColumn(column);

        // TODO - we should have a more reliable (and speedier) way of identifying just the data rows here
        SQLFragment dataRowClause = new SQLFragment("ObjectURI LIKE '%.DataRow-%'");
        addCondition(dataRowClause, FieldKey.fromParts("ObjectURI"));

        ExprColumn dataColumn = new ExprColumn(this, "DataId", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".DataId"), JdbcType.INTEGER);
        addColumn(dataColumn);

        if (!forUpgrade)
        {
            ExprColumn runColumn = new ExprColumn(this, "Run", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".RunID"), JdbcType.INTEGER);
            runColumn.setFk(new LookupForeignKey("RowID")
            {
                public TableInfo getLookupTableInfo()
                {
                    ExpRunTable expRunTable = AssayService.get().createRunTable(schema.getProtocol(), schema.getProvider(), schema.getUser(), schema.getContainer());
                    expRunTable.setContainerFilter(getContainerFilter());
                    return expRunTable;
                }
            });
            addColumn(runColumn);
        }

        Domain runDomain = schema.getProvider().getRunDomain(schema.getProtocol());
        for (DomainProperty prop : runDomain.getProperties())
        {
            if (!prop.isHidden())
                visibleColumns.add(FieldKey.fromParts("Run", prop.getName()));
        }

        for (DomainProperty prop : schema.getProvider().getBatchDomain(schema.getProtocol()).getProperties())
        {
            if (!prop.isHidden())
                visibleColumns.add(FieldKey.fromParts("Run", AssayService.BATCH_COLUMN_NAME, prop.getName()));
        }

        if (!forUpgrade)
        {
            Set<String> studyColumnNames = schema.addCopiedToStudyColumns(this, false);
            for (String columnName : studyColumnNames)
            {
                visibleColumns.add(new FieldKey(null, columnName));
            }
        }

        setDefaultVisibleColumns(visibleColumns);
    }

    public Domain getDomain()
    {
        return _resultsDomain;
    }

    @Override
    public String toString()
    {
        return "RunDataTable";
    }

}
