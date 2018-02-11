/*
 * Copyright (c) 2005-2017 Fred Hutchinson Cancer Research Center
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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.LookupColumn;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.defaults.DefaultValueService;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.IPropertyValidator;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.PdLookupForeignKey;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.security.User;
import org.labkey.api.study.assay.FileLinkDisplayColumn;

import java.util.List;
import java.util.Map;


/**
 * {@link ColumnInfo} which is specified via an ontology-managed property, and may be persisted in exp.ObjectProperty or
 * in a hard table managed by {@link org.labkey.api.exp.api.StorageProvisioner}.
 * User: migra
 * Date: Sep 20, 2005
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
        setAlias(null);

        _pd = pd;
        _container = container;
        
        copyAttributes(user, this, pd, _container, lsidColumn.getFieldKey());
    }

    // We must have a DomainProperty in order to retrieve the default values. TODO: Transition more callers to pass in DomainProperty
    public static void copyAttributes(User user, ColumnInfo to, DomainProperty dp, Container container, FieldKey lsidColumnFieldKey)
    {
        copyAttributes(user, to, dp.getPropertyDescriptor(), container, null, null, null, lsidColumnFieldKey);
        Map<DomainProperty, Object> map = DefaultValueService.get().getDefaultValues(container, dp.getDomain(), user);

        Object value = map.get(dp);

        if (null != value)
            to.setDefaultValue(value.toString());
    }

    public static void copyAttributes(User user, ColumnInfo to, PropertyDescriptor pd, Container container, FieldKey lsidColumnFieldKey)
    {
        copyAttributes(user, to, pd, container, null, null, null, lsidColumnFieldKey);
    }

    public static void copyAttributes(User user, ColumnInfo to, PropertyDescriptor pd, Container container, SchemaKey schemaKey, String queryName, FieldKey pkFieldKey)
    {
        copyAttributes(user, to, pd, container, schemaKey, queryName, pkFieldKey, null);
    }

    private static void copyAttributes(User user, ColumnInfo to, final PropertyDescriptor pd, final Container container, final SchemaKey schemaKey, final String queryName, final FieldKey pkFieldKey, final FieldKey lsidColumnFieldKey)
    {
        // ColumnRenderProperties
        pd.copyTo(to);

        to.setRequired(pd.isRequired());
        to.setNullable(pd.isNullable());
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

        // TODO: Move FILE_LINK display column factory to ColumnInfo.DEFAULT_FACTORY
        if (pd.getPropertyType() == PropertyType.FILE_LINK)
        {
            if ((schemaKey != null && queryName != null && pkFieldKey != null) || lsidColumnFieldKey != null)
            {
                // Swap out the renderer for file properties
                if (schemaKey != null && queryName != null && pkFieldKey != null)
                    to.setDisplayColumnFactory(new FileLinkDisplayColumn.Factory(pd, container, schemaKey, queryName, pkFieldKey));
                else
                    to.setDisplayColumnFactory(new FileLinkDisplayColumn.Factory(pd, container, lsidColumnFieldKey));
            }
        }

        if (user != null && ((pd.getLookupSchema() != null && pd.getLookupQuery() != null) || pd.getConceptURI() != null))
            to.setFk(new PdLookupForeignKey(user, pd, container));

        to.setDefaultValueType(pd.getDefaultValueTypeEnum());
        to.setConditionalFormats(PropertyService.get().getConditionalFormats(pd));
        to.setValidators(PropertyService.get().getPropertyValidators(pd));
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
            sql.append("CASE WHEN FloatValue IS NULL THEN NULL WHEN FloatValue=1.0 THEN 1 ELSE 0 END");
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


    static private String getPropertyCol(PropertyDescriptor pd)
    {
        if (pd.getPropertyType() == null)
            throw new IllegalStateException("No storage type");

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
            return "VARCHAR(" + PropertyStorageSpec.DEFAULT_SIZE + ")";
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

    @Override
    protected SQLFragment getJoinCondition(String tableAliasName, ColumnInfo fk, ColumnInfo pk, boolean equalOrIsNull)
    {
        // hack: issue 16263, on sql server entityid is a uniqueidentifier type, we need to force a cast
        // when using an entityid to join to an objecturi
        boolean addEntityIdCast = StringUtils.equalsIgnoreCase("entityid", fk.getSqlTypeName()) &&
                StringUtils.equalsIgnoreCase("lsidtype", pk.getSqlTypeName()) &&
                getSqlDialect().isSqlServer();

        if (addEntityIdCast)
        {
            SQLFragment condition = new SQLFragment();
            if (equalOrIsNull)
                condition.append("(");

            SQLFragment fkSql = fk.getValueSql(tableAliasName);
                condition.append("CAST((").append(fkSql).append(") AS VARCHAR(36))");
            condition.append(" = ");

            SQLFragment pkSql = pk.getValueSql(getTableAlias(tableAliasName));
            condition.append(pkSql);

            if (equalOrIsNull)
            {
                condition.append(" OR (").append(fkSql).append(" IS NULL");
                condition.append(" AND ").append(pkSql).append(" IS NULL))");
            }

            return condition;
        }
        else
            return super.getJoinCondition(tableAliasName, fk, pk, equalOrIsNull);
    }

    public String getTableAlias(String baseAlias)
    {
        if (_container == null)
            return super.getTableAlias(baseAlias);
        return super.getTableAlias(baseAlias) + "_C";
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

    @NotNull
    @Override
    public List<? extends IPropertyValidator> getValidators()
    {
        return PropertyService.get().getPropertyValidators(_pd);
    }
}
