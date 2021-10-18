/*
 * Copyright (c) 2005-2018 Fred Hutchinson Cancer Research Center
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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.BeanObjectFactory;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ColumnRenderPropertiesImpl;
import org.labkey.api.data.ConditionalFormat;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.ObjectFactory;
import org.labkey.api.data.ParameterDescription;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.Transient;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.IPropertyValidator;
import org.labkey.api.exp.property.Lookup;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.query.PdLookupForeignKey;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.UnexpectedException;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.labkey.api.exp.api.ExperimentJSONConverter.VOCABULARY_DOMAIN;

/**
 * Bean class for property types managed via the ontology system and stored in exp.PropertyDescriptor.
 * Most code should not reference PropertyDescriptors
 * directly but should instead use the wrapper {@link org.labkey.api.exp.property.DomainProperty}.
 * User: migra
 * Date: Aug 15, 2005
 */
public class PropertyDescriptor extends ColumnRenderPropertiesImpl implements ParameterDescription, Serializable, Cloneable
{                           
    private String _name;
    private String _storageColumnName;
    private int _propertyId;
    private Container _container;
    private Container _project;
    private String _lookupContainer;
    private String _lookupSchema;
    private String _lookupQuery;
    private boolean _mvEnabled;
    private String _mvIndicatorStorageColumnName;        // only valid if mvEnabled

    private static final Logger LOG = LogManager.getLogger(PropertyDescriptor.class);

    @Override
    public void checkLocked()
    {
        // pass
    }

    /** Entity id for the lookup's target container */
    public String getLookupContainer()
    {
        return _lookupContainer;
    }

    /** Entity id for the lookup's target container */
    public void setLookupContainer(String lookupContainer)
    {
        _lookupContainer = lookupContainer;
    }

    public String getLookupSchema()
    {
        return _lookupSchema;
    }

    public void setLookupSchema(String lookupSchema)
    {
        _lookupSchema = lookupSchema;
    }

    public String getLookupQuery()
    {
        return _lookupQuery;
    }

    public void setLookupQuery(String lookupQuery)
    {
        _lookupQuery = lookupQuery;
    }

    public Lookup getLookup()
    {
        Lookup ret = new Lookup();
        String containerId = getLookupContainer();
        ret.setQueryName(getLookupQuery());
        ret.setSchemaName(getLookupSchema());
        if (ret.getQueryName() == null || ret.getSchemaName() == null)
            return null;

        if (containerId != null)
        {
            Container container = ContainerManager.getForId(containerId);
            if (container == null)
                return null;

            ret.setContainer(container);
        }
        return ret;
    }

    public PropertyDescriptor()
    {
        // property descriptors default to nullable, while columninfos do not; assign explicitly, rather than in an
        // initializer, since the 'nullable' property is shared by both classes via ColumnRenderProperties
        _nullable = true;
    }

    public PropertyDescriptor(ColumnInfo col, Container c)
    {
        this(col.getPropertyURI(), PropertyType.getFromClass(col.getJavaClass()), col.getName(), c);
        setDescription(col.getDescription());
        setRequired(!col.isNullable());
        setHidden(col.isHidden());
        setShownInDetailsView(col.isShownInDetailsView());
        setShownInInsertView(col.isShownInInsertView());
        setShownInUpdateView(col.isShownInUpdateView());
        setDimension(col.isDimension());
        setMeasure(col.isMeasure());
        setLabel(col.getLabel());
        setFormat(col.getFormat());
        setScale(col.getScale());
    }

    public PropertyDescriptor(String propertyURI, PropertyType type, String name, Container container)
    {
        this(propertyURI, type, name, null, container);
    }

    public PropertyDescriptor(String propertyURI, PropertyType type, String name, String caption, Container container)
    {
        this();
        _propertyURI = propertyURI;
        _rangeURI = type.getTypeUri();
        _name = name;
        _label = caption;
        setContainer(container);
        setScale(type.getScale());
    }

    public int getPropertyId()
    {
        return _propertyId;
    }

    public void setPropertyId(int rowId)
    {
        _propertyId = rowId;
    }

    public void setPropertyURI(String propertyURI)
    {
        _propertyURI = propertyURI;
    }

    public void setName(String name)
    {
        _name = name;
    }

    @Override
    public String getName()
    {
        if (null == _name && null != _propertyURI)
        {
            int pos;
            pos = _propertyURI.lastIndexOf("#");
            if (pos < 0)
                pos = _propertyURI.lastIndexOf(":");

            if (pos >= 0)
                _name = PageFlowUtil.decode(_propertyURI.substring(pos + 1));
        }

        return _name;
    }

    public String getStorageColumnName()
    {
        return _storageColumnName;
    }

    public void setStorageColumnName(String storageColumnName)
    {
        _storageColumnName = storageColumnName;
        if (_mvEnabled)
            _mvIndicatorStorageColumnName = makeMvIndicatorStorageColumnName();

    }

    public String getLegalSelectName(SqlDialect dialect)
    {
        return getLegalSelectNameFromStorageName(dialect, getStorageColumnName());
    }

    public static String getLegalSelectNameFromStorageName(SqlDialect dialect, String storageName)
    {
        String legalName = dialect.makeLegalIdentifier(storageName);
        if (storageName.equals(legalName))
            return storageName;
        if (dialect.isPostgreSQL())
            legalName = dialect.makeLegalIdentifier(storageName.toLowerCase());      // Our PG code deep down makes these lowercase, so we need to, too
        return legalName;

    }

    public void setRangeURI(String dataTypeURI)
    {
        _rangeURI = dataTypeURI;
    }

    public void clearPropertyType()
    {
        _propertyType = null;
    }

    @Override
    public String getInputType()
    {
        if (null == getPropertyType())
            return super.getInputType();

        return getPropertyType().getInputType();
    }

    public void setConceptURI(String conceptURI)
    {
        _conceptURI = conceptURI;
    }

    public DefaultValueType getDefaultValueTypeEnum()
    {
        return _defaultValueType;
    }

    public void setDefaultValueTypeEnum(DefaultValueType defaultValueType)
    {
        _defaultValueType = defaultValueType;
    }

    public String getDefaultValueType()
    {
        DefaultValueType type = getDefaultValueTypeEnum();
        return type != null ? type.name() : null;
    }

    public void setDefaultValueType(String defaultValueTypeName)
    {
        if (defaultValueTypeName != null)
            setDefaultValueTypeEnum(DefaultValueType.valueOf(defaultValueTypeName));
        else
            setDefaultValueTypeEnum(null);
    }

    @Override
    public String toString()
    {
        return _propertyURI + " name=" + _name + " project="+  (_project == null ? "null" : _project.getPath()) + " container="+  (_container ==null ? "null" : _container.getPath()) + " label=" + _label + " range=" + _rangeURI + " concept=" + _conceptURI;
    }

    public Container getContainer() {
        return _container;
    }

    public void setContainer(Container container) {
        _container = container;
        if (null== _project)
            _project =container.getProject();
        if (null== _project)
            _project =container;
    }

    public Container getProject() {
        return _project;
    }

    public void setProject(Container proj) {
        _project = proj;
    }

    @NotNull
    @Override
    public JdbcType getJdbcType()
    {
        PropertyType type = getPropertyType();
        if (type == null)
        {
            LOG.warn("Could not determine propertyType from RangeURI " + getRangeURI() + " and ConceptURI " + getConceptURI() + " for PropertyURI " + getPropertyURI() + ", defaulting to string");
            type = PropertyType.STRING;
        }
        return type.getJdbcType();
    }

    public void setJdbcType(JdbcType jdbcType, Integer size)
    {
        String rangeUri;
        if (jdbcType.isText())
        {
            rangeUri = PropertyType.STRING.getTypeUri();
        }
        else
        {
            rangeUri = PropertyType.getFromJdbcType(jdbcType).getTypeUri();
        }
        setRangeURI(rangeUri);
        setScale(size);
    }

    @Override
    public boolean isMvEnabled()
    {
        return _mvEnabled;
    }

    public void setMvEnabled(boolean mvEnabled)
    {
        if (_mvEnabled != mvEnabled)
        {
            if (mvEnabled && null != _storageColumnName)
                _mvIndicatorStorageColumnName = makeMvIndicatorStorageColumnName();
            else
                _mvIndicatorStorageColumnName = null;
            _mvEnabled = mvEnabled;
        }
    }

    private String makeMvIndicatorStorageColumnName()
    {
        return _storageColumnName + "_" + MvColumn.MV_INDICATOR_SUFFIX;
    }

    /** Need the string version of this method because it's called by reflection and must match by name */
    public String getImportAliases()
    {
        return ColumnRenderPropertiesImpl.convertToString(getImportAliasSet());
    }

    /** Need the string version of this method because it's called by reflection and must match by name */
    public void setImportAliases(String importAliases)
    {
        super.setImportAliasesSet(ColumnRenderPropertiesImpl.convertToSet(importAliases));
    }

    @Override
    public final PropertyDescriptor clone()
    {
        try
        {
            return (PropertyDescriptor) super.clone();
        }
        catch (CloneNotSupportedException cnse)
        {
            throw UnexpectedException.wrap(cnse);
        }
    }

    public BaseColumnInfo createColumnInfo(TableInfo baseTable, String lsidCol, User user, Container container)
    {
        var info = new PropertyColumn(this, baseTable, lsidCol, container, user, false);
        if (getLookupQuery() != null || getConceptURI() != null)
        {
            assert null==baseTable.getUserSchema() || baseTable.getUserSchema().getUser() == user;
            assert null==baseTable.getUserSchema() || baseTable.getUserSchema().getContainer() == container;
            info.setFk(PdLookupForeignKey.create(baseTable.getUserSchema(), user, container, this));
        }
        return info;
    }

    public int hashCode()
    {
        return Integer.valueOf(getPropertyId()).hashCode();
    }

    public boolean equals(Object obj)
    {
        if (obj == this)
            return true;
        if (!(obj instanceof PropertyDescriptor))
            return false;
        // property descriptors that are not in the database are never equal:
        if (((PropertyDescriptor) obj).getPropertyId() == 0 || getPropertyId() == 0)
            return false;

        // two property descriptors are equal if they have the same row ID:
        return ((PropertyDescriptor) obj).getPropertyId() == getPropertyId();
    }

    @Override
    // TODO MutableColumnRenderProperties
    public void copyTo(ColumnRenderPropertiesImpl to)
    {
        super.copyTo(to);
        if (to instanceof PropertyDescriptor)
        {
            PropertyDescriptor toPD = (PropertyDescriptor)to;
            toPD._container = _container; // ?
            toPD._project = _project; // ?
            toPD._lookupContainer = _lookupContainer;
            toPD._lookupSchema = _lookupSchema;
            toPD._lookupQuery = _lookupQuery;
            toPD._mvEnabled = _mvEnabled;
        }
    }

    static
    {
        ObjectFactory.Registry.register(PropertyDescriptor.class,
            new BeanObjectFactory<>(PropertyDescriptor.class)
            {
                @Override
                public @NotNull Map<String, Object> toMap(PropertyDescriptor bean, @Nullable Map<String, Object> m)
                {
                    m = super.toMap(bean, m);
                    Object url = m.get("URL");
                    if (url instanceof StringExpression)
                        m.put("URL", url.toString());

                    Object textExpr = m.get("textExpression");
                    if (textExpr instanceof StringExpression)
                        m.put("textExpression", textExpr.toString());
                    return m;
                }
            }
    );
    }

    @Override
    public boolean isLookup()
    {
        return getLookupQuery() != null;
    }

    @Override
    public boolean isAutoIncrement()
    {
        return false;
    }

    @Transient
    public @NotNull Collection<? extends IPropertyValidator> getValidators()
    {
        return PropertyService.get().getPropertyValidators(this);
    }

    @Transient
    public @NotNull Collection<ConditionalFormat> getConditionalFormats()
    {
        return PropertyService.get().getConditionalFormats(this);
    }

    // ParameterDescription

    @Override
    public String getURI()
    {
        return getPropertyURI();
    }

    public String getMvIndicatorStorageColumnName()
    {
        return _mvIndicatorStorageColumnName;
    }

    // Should only be used during upgrade and bean
    public void setMvIndicatorStorageColumnName(String mvIndicatorStorageColumnName)
    {
        _mvIndicatorStorageColumnName = mvIndicatorStorageColumnName;
    }

    Boolean _vocabulary = null;

    /** @return true if this property is a member of a VocabularyDomainKind */
    @Transient
    public boolean isVocabulary()
    {
        if (_vocabulary == null)
        {
            List<Domain> domainsForPD = OntologyManager.getDomainsForPropertyDescriptor(getContainer(), this);
            _vocabulary = domainsForPD.stream()
                    .map(Domain::getDomainKind)
                    .filter(Objects::nonNull)
                    .anyMatch(d -> VOCABULARY_DOMAIN.equals(d.getKindName()));
        }
        return _vocabulary;
    }
}


