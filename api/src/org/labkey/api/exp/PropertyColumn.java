/*
 * Copyright (c) 2005-2008 Fred Hutchinson Cancer Research Center
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
package org.labkey.api.exp;

import org.apache.commons.lang.StringUtils;
import org.labkey.api.data.*;
import org.labkey.api.query.PdLookupForeignKey;
import org.labkey.api.view.HttpView;


/**
 * User: migra
 * Date: Sep 20, 2005
 * Time: 9:15:38 AM
 */
public class PropertyColumn extends LookupColumn
{
    protected PropertyDescriptor pd;
    protected String containerId;

    static private ColumnInfo getColumnInfo(String fullyQualifiedColumnName)
    {
        int ichFirstDot = fullyQualifiedColumnName.indexOf(".");
        int ichSecondDot = fullyQualifiedColumnName.indexOf(".", ichFirstDot + 1);
        String strSchema = fullyQualifiedColumnName.substring(0, ichFirstDot);
        String strTable = fullyQualifiedColumnName.substring(ichFirstDot + 1, ichSecondDot);
        String strColumn = fullyQualifiedColumnName.substring(ichSecondDot + 1);
        DbSchema schema = DbSchema.get(strSchema);
        TableInfo table = schema.getTable(strTable);
        ColumnInfo column = table.getColumn(strColumn);
        return column;
    }

    public PropertyColumn(PropertyDescriptor pd, TableInfo tinfoParent, String parentLsidColumn, String containerId)
    {
        this(pd, tinfoParent.getColumn(parentLsidColumn), containerId);
    }

    public PropertyColumn(PropertyDescriptor pd, ColumnInfo lsidColumn, String containerId)
    {
        super(lsidColumn, OntologyManager.getTinfoObject().getColumn("ObjectURI"), OntologyManager.getTinfoObjectProperty().getColumn(getPropertyCol(pd)), false);
        setName(ColumnInfo.legalNameFromName(pd.getName()));
        setAlias(ColumnInfo.legalNameFromName(pd.getName()));
        setNullable(!pd.isRequired());
        String description = pd.getDescription();
        if (null == description && null != pd.getConceptURI())
        {
            PropertyDescriptor concept = OntologyManager.getPropertyDescriptor(pd.getConceptURI(), pd.getContainer());
            if (null != concept)
                description = concept.getDescription();
        }
        setDescription(description);
        setCaption(pd.getLabel() == null ? getName() : pd.getLabel());
        this.pd = pd;
        setSqlTypeName(getPropertySqlType(OntologyManager.getSqlDialect()));
        String format = StringUtils.trimToNull(pd.getFormat());
        if (null != format)
            setFormatString(format);
        
        setInputType(pd.getPropertyType().getInputType());

        this.containerId = containerId;

        // TODO: pass in the user as an argument. I'd do this now,
        // but we're late in the cycle for 8.2. See bug 6130
        if (HttpView.hasCurrentView())
        {
            setFk(new PdLookupForeignKey(HttpView.currentContext().getUser(), pd));
        }
    }


    public SQLFragment getValueSql()
    {
        String cast = getPropertySqlCastType();
        SQLFragment sql = new SQLFragment("\n(SELECT ");
        if (pd.getPropertyType() == PropertyType.BOOLEAN)
        {
            sql.append("CASE FloatValue WHEN 1.0 THEN 1 ELSE 0 END");
        }
        else
        {
            sql.append(getPropertyCol(pd));
        }
        sql.append("\nFROM exp.ObjectProperty WHERE exp.ObjectProperty.PropertyId = " + pd.getPropertyId());
        sql.append("\nAND exp.ObjectProperty.ObjectId = " + getTableAlias() + ".ObjectId)");
        if (null != cast)
        {
            sql.insert(0, "CAST(");
            sql.append(" AS " + cast + ")");
        }

        return sql;
    }


    private String getPropertySqlType(SqlDialect dialect)
    {
        return dialect.sqlTypeNameFromSqlType(getPropertySqlTypeInt());
    }

    
    private int getPropertySqlTypeInt()
    {
        return pd.getPropertyType().getSqlType();
    }


    static private String getPropertyCol(PropertyDescriptor pd)
    {
        switch (pd.getPropertyType().getStorageType())
        {
            case 's':
                return "StringValue";
            case 'f':
                return "FloatValue";
            case 'd':
                return "DateTimeValue";
            default:
                throw new IllegalStateException("Bad storage type");
        }
    }


    private String getPropertySqlCastType()
    {
        PropertyType pt = pd.getPropertyType();
        if (PropertyType.DOUBLE == pt || PropertyType.DATE_TIME == pt)
            return null;
        else if (PropertyType.INTEGER == pt)
            return "INT";
        else if (PropertyType.BOOLEAN == pt)
            return getParentTable().getSqlDialect().getBooleanDatatype();
        else
            return "VARCHAR(" + ObjectProperty.STRING_LENGTH + ")";
    }


    public PropertyDescriptor getPropertyDescriptor()
    {
        return pd;
    }

    public String getPropertyURI()
    {
        return getPropertyDescriptor().getPropertyURI();
    }

    public SQLFragment getJoinCondition()
    {
        SQLFragment strJoinNoContainer = super.getJoinCondition();
        if (containerId == null)
        {
            return strJoinNoContainer;
        }

        strJoinNoContainer.append(" AND " + getTableAlias() + ".Container = '" + containerId + "'");
        return strJoinNoContainer;
    }

    public String getTableAlias()
    {
        if (containerId == null)
            return super.getTableAlias();
        return super.getTableAlias() + "_C";
    }

    public String getInputType()
    {
        if (pd.getPropertyType() == PropertyType.FILE_LINK || pd.getPropertyType() == PropertyType.ATTACHMENT)
            return "file";
        else
            return super.getInputType();
    }
}
