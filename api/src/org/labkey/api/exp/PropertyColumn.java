/*
 * Copyright (c) 2005-2009 Fred Hutchinson Cancer Research Center
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
import org.labkey.api.security.User;


/**
 * User: migra
 * Date: Sep 20, 2005
 * Time: 9:15:38 AM
 */
public class PropertyColumn extends LookupColumn
{
    protected PropertyDescriptor pd;
    protected String containerId;
    protected boolean _mvIndicator = false;

    public PropertyColumn(PropertyDescriptor pd, TableInfo tinfoParent, String parentLsidColumn, String containerId, User user)
    {
        this(pd, tinfoParent.getColumn(parentLsidColumn), containerId, user);
    }

    public PropertyColumn(PropertyDescriptor pd, ColumnInfo lsidColumn, String containerId, User user)
    {
        super(lsidColumn, OntologyManager.getTinfoObject().getColumn("ObjectURI"), OntologyManager.getTinfoObjectProperty().getColumn(getPropertyCol(pd)), false);
        setName(ColumnInfo.legalNameFromName(pd.getName()));
        setAlias(ColumnInfo.legalNameFromName(pd.getName()));
        setNullable(!pd.isRequired());
        setHidden(pd.isHidden());
        setShownInDetailsView(pd.isShownInDetailsView());
        setShownInInsertView(pd.isShownInInsertView());
        setShownInUpdateView(pd.isShownInUpdateView());
        String description = pd.getDescription();
        if (null == description && null != pd.getConceptURI())
        {
            PropertyDescriptor concept = OntologyManager.getPropertyDescriptor(pd.getConceptURI(), pd.getContainer());
            if (null != concept)
                description = concept.getDescription();
        }
        setDescription(description);
        setLabel(pd.getLabel() == null ? getName() : pd.getLabel());
        this.pd = pd;
        setSqlTypeName(getPropertySqlType(OntologyManager.getSqlDialect()));
        String format = StringUtils.trimToNull(pd.getFormat());
        if (null != format)
            setFormat(format);
        
        setInputType(pd.getPropertyType().getInputType());
        setInputLength(pd.getInputLength());
        setInputRows(pd.getInputRows());
        setURL(pd.getURL());
        setImportAliasesSet(pd.getImportAliasesSet());

        this.containerId = containerId;

        setFk(new PdLookupForeignKey(user, pd));

        setDefaultValueType(pd.getDefaultValueTypeEnum());
    }


    // select the mv column instead
    public void setMvIndicator(boolean mv)
    {
        _mvIndicator = mv;
        this.setSqlTypeName(getSqlDialect().sqlTypeNameFromSqlType(PropertyType.STRING.getSqlType()));
    }
    

    public SQLFragment getValueSql()
    {
        String cast = getPropertySqlCastType();
        SQLFragment sql = new SQLFragment("\n(SELECT ");
        if (_mvIndicator)
        {
            sql.append("MvIndicator");
        }
        else if (pd.getPropertyType() == PropertyType.BOOLEAN)
        {
            sql.append("CASE FloatValue WHEN 1.0 THEN 1 ELSE 0 END");
        }
        else
        {
            sql.append(getPropertyCol(pd));
        }
        sql.append(" FROM exp.ObjectProperty WHERE exp.ObjectProperty.PropertyId = " + pd.getPropertyId());
        sql.append(" AND exp.ObjectProperty.ObjectId = " + getTableAlias() + ".ObjectId)");
        if (null != cast)
        {
            sql.insert(0, "CAST(");
            sql.append(" AS " + cast + ")");
        }

        return sql;
    }


    public SQLFragment getValueSql(String tableAlias)
    {
        return getValueSql();
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

    public SQLFragment getJoinCondition(String tableAliasName)
    {
        SQLFragment strJoinNoContainer = super.getJoinCondition(tableAliasName);
        if (containerId == null)
        {
            return strJoinNoContainer;
        }

        strJoinNoContainer.append(" AND " + tableAliasName + ".Container = '" + containerId + "'");
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
