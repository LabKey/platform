/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.api.data;

import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.PropertyForeignKey;

/**
 * User: jgarms
 * Date: Jan 8, 2009
 */
public class QCDisplayColumnFactory implements DisplayColumnFactory
{
    private final ColumnInfo qcColumn;
    public static final String RAW_VALUE_SUFFIX = "RawValue";
    public static final String QC_INDICATOR_SUFFIX = "QCIndicator";

    public QCDisplayColumnFactory(ColumnInfo qcColumn)
    {
        this.qcColumn = qcColumn;
    }

    public DisplayColumn createRenderer(ColumnInfo colInfo)
    {
        return new QCDisplayColumn(colInfo, qcColumn);
    }

    /**
     * For creating QC columns from ontology manager
     */
    public static ColumnInfo addQCColumns(FilteredTable table, ColumnInfo valueColumn, DomainProperty property, ColumnInfo colObjectId)
    {
        ColumnInfo qcColumn = new ExprColumn(table,
                property.getName() + QC_INDICATOR_SUFFIX,
                PropertyForeignKey.getValueSql(colObjectId.getValueSql(ExprColumn.STR_TABLE_ALIAS), property.getQCValueSQL(), property.getPropertyId(), false),
                property.getSqlType());
        
        qcColumn.setNullable(true);

        table.addColumn(qcColumn);

        valueColumn.setQcColumnName(qcColumn.getName());

        return addQCColumn(table, valueColumn, qcColumn);
    }

    /**
     * Returns the new combined column, with qc states replacing null
     */
    public static ColumnInfo addQCColumn(FilteredTable table, ColumnInfo valueColumn, ColumnInfo qcColumn)
    {
        assert valueColumn.isQcEnabled() : "Attempt to add a QC column without QC enabled";
        assert valueColumn.getQcColumnName().equals(qcColumn.getName()) : "Column QC name mismatch. valueColumn.getQcColumnName='" + valueColumn.getQcColumnName() + "'; qcColumn.getName()='" + qcColumn.getName() + "'.";

        AliasedColumn rawValueCol = new AliasedColumn(table, valueColumn.getName() + RAW_VALUE_SUFFIX, valueColumn);
        table.addColumn(rawValueCol);

        valueColumn.setDisplayColumnFactory(new QCDisplayColumnFactory(qcColumn));
        //valueColumn.setUserEditable(false);

        return valueColumn;
    }
}