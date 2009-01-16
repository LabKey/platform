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

package org.labkey.api.query;

import org.labkey.api.data.*;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.security.User;
import org.labkey.api.util.StringExpressionFactory;

import java.util.Map;
import java.util.TreeMap;

public class PropertyForeignKey extends AbstractForeignKey
{
    Map<String, PropertyDescriptor> _pdMap;
    private QuerySchema _schema;

    public PropertyForeignKey(Map<String, PropertyDescriptor> pds, QuerySchema schema)
    {
        _pdMap = pds;
        _schema = schema;
    }

    /**
     * Creates a virtual table with columns for each of the property descriptors.
     */
    public PropertyForeignKey(PropertyDescriptor[] pds, QuerySchema schema)
    {
        _pdMap = new TreeMap();
        for (PropertyDescriptor pd : pds)
        {
            _pdMap.put(pd.getName(), pd);
        }
        _schema = schema;
    }

    public ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
    {
        if (displayField == null)
            return null;
        PropertyDescriptor pd = _pdMap.get(displayField);
        if (pd == null)
        {
            pd = resolvePropertyDescriptor(displayField);
        }
        if (pd == null)
            return null;

        return constructColumnInfo(parent, decideColumnName(parent, displayField, pd), pd);
    }

    protected String decideColumnName(ColumnInfo parent, String displayField, PropertyDescriptor pd)
    {
        return parent.getName() + "$P" + pd.getPropertyId();
    }

    static public SQLFragment getValueSql(PropertyType type)
    {
        switch (type)
        {
        case INTEGER:
            return new SQLFragment("CAST(exp.ObjectProperty.FloatValue AS INTEGER)");
        case BOOLEAN:
            return new SQLFragment("CAST((CASE exp.ObjectProperty.FloatValue WHEN 1.0 THEN 1 ELSE 0 END) AS " + ExperimentService.get().getSchema().getSqlDialect().getBooleanDatatype() + ")");
        case DOUBLE:
            return new SQLFragment("exp.ObjectProperty.FloatValue");
        case DATE_TIME:
            return new SQLFragment("exp.ObjectProperty.DateTimeValue");
        case ATTACHMENT:
        case FILE_LINK:
        case RESOURCE:
        case STRING:
        case MULTI_LINE:
        case XML_TEXT:
            return new SQLFragment("exp.ObjectProperty.StringValue");
        }
        return new SQLFragment("exp.ObjectProperty.StringValue");
    }

    static public SQLFragment getQCValueSQL()
    {
        return new SQLFragment("exp.ObjectProperty.QcValue");
    }

    static public SQLFragment getValueSql(ColumnInfo parent, SQLFragment value, int propertyId, boolean parentIsLSID)
    {
        return getValueSql(parent.getValueSql(), value, propertyId, parentIsLSID);
    }

    static public SQLFragment getValueSql(SQLFragment sqlParentColumn, SQLFragment value, int propertyId, boolean parentIsLSID)
    {
        SQLFragment sql = new SQLFragment();
        sql.append("(SELECT ");
        sql.append(value);
        sql.append(" FROM exp.ObjectProperty ");
        if (parentIsLSID)
        {
            sql.append("\nINNER JOIN exp.Object ON exp.Object.ObjectId = exp.ObjectProperty.ObjectId");
            sql.append("\nWHERE exp.Object.ObjectURI = ");
            sql.append(sqlParentColumn);
            sql.append("\nAND exp.ObjectProperty.PropertyId = " + propertyId);
            sql.append(")");
        }
        else
        {
            sql.append("WHERE PropertyId = " + propertyId + " AND ObjectId = ");
            sql.append(sqlParentColumn);
            sql.append(")");
        }
        return sql;

    }

    protected SQLFragment getValueSql(ColumnInfo parent, PropertyDescriptor pd)
    {
        return getValueSql(parent, getValueSql(pd.getPropertyType()), pd.getPropertyId(), false);
    }

    protected ColumnInfo constructColumnInfo(ColumnInfo parent, String name, PropertyDescriptor pd)
    {
        ColumnInfo ret;
        if (parent == null)
        {
            ret = new ColumnInfo(name);
        }
        else
        {
            ExprColumn expr = new ExprColumn(parent.getParentTable(), name, getValueSql(parent, pd), pd.getPropertyType().getSqlType(), parent);
            ret = expr;
        }
        if (pd.getLabel() != null)
            ret.setCaption(pd.getLabel());
        else
            ret.setCaption(ColumnInfo.captionFromName(pd.getName()));
        if (pd.getFormat() != null)
            ret.setFormatString(pd.getFormat());
        ret.setFk(new PdLookupForeignKey(_schema.getUser(), pd));
        return ret;
    }

    public TableInfo getLookupTableInfo()
    {
        VirtualTable ret = new VirtualTable(ExperimentService.get().getSchema());
        for (Map.Entry<String, PropertyDescriptor> entry : _pdMap.entrySet())
        {
            ColumnInfo column = constructColumnInfo(null, entry.getKey(), entry.getValue());
            if (column != null)
            {
                column.setParentTable(ret);
                ret.addColumn(column);
            }
        }
        return ret;
    }

    public StringExpressionFactory.StringExpression getURL(ColumnInfo parent)
    {
        return null;
    }

    /**
     * Override this method to allow properties which might not have been
     * known in advance, and in {@link #_pdMap}.
     */
    protected PropertyDescriptor resolvePropertyDescriptor(String name)
    {
        return null;
    }

    static public void initColumn(User user, ColumnInfo column, PropertyDescriptor pd)
    {
        column.setReadOnly(false);
        if (pd.getLabel() != null)
            column.setCaption(pd.getLabel());
        else
            column.setCaption(ColumnInfo.captionFromName(pd.getName()));
        if (pd.getFormat() != null)
            column.setFormatString(pd.getFormat());
        column.setNullable(!pd.isRequired());
        column.setFk(new PdLookupForeignKey(user, pd));
    }
}
