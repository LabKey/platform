/*
 * Copyright (c) 2005-2011 Fred Hutchinson Cancer Research Center
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

import org.labkey.api.data.*;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.property.IPropertyValidator;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.PdLookupForeignKey;
import org.labkey.api.security.User;
import org.labkey.api.study.assay.FileLinkDisplayColumn;

import java.util.List;
import java.util.Map;


/**
 * User: migra
 * Date: Sep 20, 2005
 * Time: 9:15:38 AM
 */
public class PropertyColumn extends LookupColumn
{
    protected PropertyDescriptor _pd;
    protected Container _container;
    protected boolean _parentIsObjectId = false;
    private final boolean _joinOnContainer;

    public PropertyColumn(PropertyDescriptor pd, TableInfo tinfoParent, String parentLsidColumn, Container container, User user, boolean joinOnContainer)
    {
        this(pd, tinfoParent.getColumn(parentLsidColumn), container, user, joinOnContainer);
    }

    /**
     * @param container container in which the query is going to be executed. Used optionally as a join condition, and
     * to construct the appropriate TableInfo for lookups.
     * @param joinOnContainer when creating the join as a LookupColumn, whether or not to also match based on container 
     */
    public PropertyColumn(final PropertyDescriptor pd, final ColumnInfo lsidColumn, final Container container, User user, boolean joinOnContainer)
    {
        super(lsidColumn, OntologyManager.getTinfoObject().getColumn("ObjectURI"), OntologyManager.getTinfoObjectProperty().getColumn(getPropertyCol(pd)));
        _joinOnContainer = joinOnContainer;
        setName(pd.getName());
        setAlias(ColumnInfo.legalNameFromName(pd.getName()));

        _pd = pd;
        _container = container;
        
        copyAttributes(user, this, pd, _container);
        setSqlTypeName(getPropertySqlType(pd,OntologyManager.getSqlDialect()));

        // Swap out the renderer for file properties
        if (pd.getPropertyType() == PropertyType.FILE_LINK)
        {
            setDisplayColumnFactory(new DisplayColumnFactory()
            {
                public DisplayColumn createRenderer(ColumnInfo colInfo)
                {
                    return new FileLinkDisplayColumn(colInfo, pd, container, lsidColumn.getFieldKey());
                }
            });
        }
    }


    public static void copyAttributes(User user, ColumnInfo to, PropertyDescriptor pd, Container container)
    {
        // ColumnRenderProperties
        pd.copyTo(to);

        to.setNullable(!pd.isRequired());
        to.setHidden(pd.isHidden());
        String description = pd.getDescription();
        if (null == description && null != pd.getConceptURI())
        {
            PropertyDescriptor concept = OntologyManager.getPropertyDescriptor(pd.getConceptURI(), pd.getContainer());
            if (null != concept)
                description = concept.getDescription();
        }
        to.setDescription(description);
        to.setLabel(pd.getLabel() == null ? ColumnInfo.labelFromName(pd.getName()) : pd.getLabel());

        if (pd.getPropertyType() == PropertyType.MULTI_LINE)
        {
            to.setDisplayColumnFactory(new DisplayColumnFactory() {
                public DisplayColumn createRenderer(ColumnInfo colInfo)
                {
                    DataColumn dc = new DataColumn(colInfo);
                    dc.setPreserveNewlines(true);
                    return dc;
                }
            });
        }

        if (pd.getLookupSchema() != null && pd.getLookupQuery() != null && user != null)
            to.setFk(new PdLookupForeignKey(user, pd, container));

        to.setDefaultValueType(pd.getDefaultValueTypeEnum());
        to.setConditionalFormats(PropertyService.get().getConditionalFormats(pd));

        to.setPropertyURI(pd.getPropertyURI());
    }


    // select the mv column instead
    public void setMvIndicatorColumn(boolean mv)
    {
        super.setMvIndicatorColumn(mv);
        setSqlTypeName(getSqlDialect().sqlTypeNameFromSqlType(PropertyType.STRING.getSqlType()));
    }


    public void setParentIsObjectId(boolean id)
    {
        _parentIsObjectId = id;
    }
    

    public SQLFragment getValueSql(String tableAlias)
    {
        String cast = getPropertySqlCastType();
        SQLFragment sql = new SQLFragment("(SELECT ");
        if (isMvIndicatorColumn())
        {
            sql.append("MvIndicator");
        }
        else if (_pd.getPropertyType() == PropertyType.BOOLEAN)
        {
            sql.append("CASE FloatValue WHEN 1.0 THEN 1 ELSE 0 END");
        }
        else
        {
            sql.append(getPropertyCol(_pd));
        }
        sql.append(" FROM exp.ObjectProperty WHERE exp.ObjectProperty.PropertyId = " + _pd.getPropertyId());
        sql.append(" AND exp.ObjectProperty.ObjectId = ");
        if (_parentIsObjectId)
            sql.append(_foreignKey.getValueSql(tableAlias));
        else
            sql.append(getTableAlias(tableAlias) + ".ObjectId");
        sql.append(")");
        if (null != cast)
        {
            sql.insert(0, "CAST(");
            sql.append(" AS " + cast + ")");
        }

        return sql;
    }

    @Override
    public void declareJoins(String baseAlias, Map<String, SQLFragment> map)
    {
        if (!_parentIsObjectId)
            super.declareJoins(baseAlias, map);
    }


    private static String getPropertySqlType(PropertyDescriptor pd, SqlDialect dialect)
    {
        return dialect.sqlTypeNameFromSqlType(pd.getPropertyType().getSqlType());
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
        if (isMvIndicatorColumn())
            return null;
        PropertyType pt = _pd.getPropertyType();
        if (PropertyType.DOUBLE == pt || PropertyType.DATE_TIME == pt)
            return null;
        else if (PropertyType.INTEGER == pt)
            return "INT";
        else if (PropertyType.BOOLEAN == pt)
            return getParentTable().getSqlDialect().getBooleanDataType();
        else
            return "VARCHAR(" + ObjectProperty.STRING_LENGTH + ")";
    }


    public PropertyDescriptor getPropertyDescriptor()
    {
        return _pd;
    }

    public String getPropertyURI()
    {
        return getPropertyDescriptor().getPropertyURI();
    }

    public String getConceptURI()
    {
        return getPropertyDescriptor().getConceptURI();
    }

    public SQLFragment getJoinCondition(String tableAliasName)
    {
        SQLFragment strJoinNoContainer = super.getJoinCondition(tableAliasName);
        if (_container == null || !_joinOnContainer)
        {
            return strJoinNoContainer;
        }

        strJoinNoContainer.append(" AND ");
        strJoinNoContainer.append(getParentTable().getContainerFilter().getSQLFragment(getParentTable().getSchema(), new SQLFragment(tableAliasName + ".Container"), _container));

        return strJoinNoContainer;
    }

    public String getTableAlias(String baseAlias)
    {
        if (_container == null)
            return super.getTableAlias(baseAlias);
        return super.getTableAlias(baseAlias) + "_C";
    }

    public String getInputType()
    {
        if (_pd.getPropertyType() == PropertyType.FILE_LINK || _pd.getPropertyType() == PropertyType.ATTACHMENT)
            return "file";
        else
            return super.getInputType();
    }

    @Override
    public Class getJavaClass()
    {
        if (isMvIndicatorColumn())
        {
            return String.class;
        }
        return _pd.getPropertyType().getJavaType();
    }

    @Override
    public List<? extends IPropertyValidator> getValidators()
    {
        return PropertyService.get().getPropertyValidators(_pd);
    }
}
