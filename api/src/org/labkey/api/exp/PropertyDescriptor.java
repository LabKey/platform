/*
 * Copyright (c) 2005-2014 Fred Hutchinson Cancer Research Center
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.*;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.query.PdLookupForeignKey;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.UnexpectedException;

import java.io.Serializable;
import java.util.Map;

/**
 * User: migra
 * Date: Aug 15, 2005
 * Time: 2:41:47 PM
 */
public class PropertyDescriptor extends ColumnRenderProperties implements ParameterDescription, Serializable, Cloneable
{                           
    private String name;
    private String storageColumnName;
    private int propertyId;
    private String ontologyURI;
    private String searchTerms;
    private String semanticType;
    private Container container;
    private Container project;
    private String lookupContainer;
    private String lookupSchema;
    private String lookupQuery;
    private boolean mvEnabled;

    /** Entity id for the lookup's target container */
    public String getLookupContainer()
    {
        return lookupContainer;
    }

    /** Entity id for the lookup's target container */
    public void setLookupContainer(String lookupContainer)
    {
        this.lookupContainer = lookupContainer;
    }

    public String getLookupSchema()
    {
        return lookupSchema;
    }

    public void setLookupSchema(String lookupSchema)
    {
        this.lookupSchema = lookupSchema;
    }

    public String getLookupQuery()
    {
        return lookupQuery;
    }

    public void setLookupQuery(String lookupQuery)
    {
        this.lookupQuery = lookupQuery;
    }

    public PropertyDescriptor()
    {
        // property descriptors default to nullable, while columninfos do not; assign explicitly, rather than in an
        // initializer, since the 'nullable' property is shared by both classes via ColumnRenderProperties
        this.nullable = true;
    }

    public PropertyDescriptor(ColumnInfo col, Container c)
    {
        this(col.getPropertyURI(), PropertyType.getFromClass(col.getJavaClass()).getTypeUri(), col.getName(), c);
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

    public PropertyDescriptor(String propertyURI, String rangeURI, String name, Container container)
    {
        this(propertyURI, rangeURI, name, null, container);
    }

    public PropertyDescriptor(String propertyURI, String rangeURI, String name, String caption, Container container)
    {
        this();
        this.propertyURI = propertyURI;
        this.rangeURI = rangeURI;
        this.name = name;
        this.label = caption;
        setContainer(container);
        if (PropertyType.STRING.getTypeUri().equals(rangeURI))
            setScale(PropertyStorageSpec.DEFAULT_SIZE);       // Make sure to set default scale
    }

    public int getPropertyId()
    {
        return propertyId;
    }

    public void setPropertyId(int rowId)
    {
        this.propertyId = rowId;
    }

    public void setPropertyURI(String propertyURI)
    {
        this.propertyURI = propertyURI;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    @Override
    public String getName()
    {
        if (null == name && null != propertyURI)
        {
            int pos;
            pos = propertyURI.lastIndexOf("#");
            if (pos < 0)
                pos = propertyURI.lastIndexOf(":");

            if (pos >= 0)
                name = PageFlowUtil.decode(propertyURI.substring(pos + 1));
        }

        return name;
    }

    public String getStorageColumnName()
    {
        return storageColumnName;
    }

    public void setStorageColumnName(String storageColumnName)
    {
        this.storageColumnName = storageColumnName;
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

    public String getOntologyURI()
    {
        return ontologyURI;
    }

    public void setOntologyURI(String ontologyURI)
    {
        this.ontologyURI = ontologyURI;
    }

    public void setRangeURI(String dataTypeURI)
    {
        this.rangeURI = dataTypeURI;
    }

    public void clearPropertyType()
    {
        this.propertyType = null;
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
        this.conceptURI = conceptURI;
    }

    public String getSearchTerms()
    {
        return searchTerms;
    }

    public void setSearchTerms(String searchTerms)
    {
        this.searchTerms = searchTerms;
    }

    public String getNonBlankCaption()
    {
        if (label == null || "".equals(label.trim()))
        {
            return getName();
        }
        return label;
    }

    public String getSemanticType()
    {
        return semanticType;
    }

    public void setSemanticType(String semanticType)
    {
        this.semanticType = semanticType;
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
        return propertyURI + " name=" + name + " project="+  (project == null ? "null" : project.getPath()) + " container="+  (container==null ? "null" : container.getPath()) + " label=" + label + " range=" + rangeURI + " concept=" + conceptURI;
    }

    public Container getContainer() {
        return container;
    }

    public void setContainer(Container container) {
        this.container = container;
        if (null==project)
            project=container.getProject();
        if (null==project)
            project=container;
    }

    public Container getProject() {
        return project;
    }

    public void setProject(Container proj) {
        this.project = proj;
    }

    @Override
    public int getSqlTypeInt()
    {
        return getPropertyType().getSqlType();
    }

    @NotNull
    @Override
    public JdbcType getJdbcType()
    {
        return getPropertyType().getJdbcType();
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

    public boolean isMvEnabled()
    {
        return mvEnabled;
    }

    public void setMvEnabled(boolean mvEnabled)
    {
        this.mvEnabled = mvEnabled;
    }

    /** Need the string version of this method because it's called by reflection and must match by name */
    public String getImportAliases()
    {
        return ColumnRenderProperties.convertToString(getImportAliasSet());
    }

    /** Need the string version of this method because it's called by reflection and must match by name */
    public void setImportAliases(String importAliases)
    {
        this.importAliases = ColumnRenderProperties.convertToSet(importAliases);
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

    public ColumnInfo createColumnInfo(TableInfo baseTable, String lsidCol, User user, Container container)
    {
        ColumnInfo info = new PropertyColumn(this, baseTable, lsidCol, container, user, false);
        if (getLookupQuery() != null)
            info.setFk(new PdLookupForeignKey(user, this, container));
        return info;
    }

    public int hashCode()
    {
        return new Integer(getPropertyId()).hashCode();
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
    public void copyTo(ColumnRenderProperties to)
    {
        super.copyTo(to);
        if (to instanceof PropertyDescriptor)
        {
            PropertyDescriptor toPD = (PropertyDescriptor)to;
            toPD.container = container; // ?
            toPD.project = project; // ?
            toPD.ontologyURI = ontologyURI;
            toPD.searchTerms = searchTerms;
            toPD.semanticType = semanticType;
            toPD.lookupContainer = lookupContainer;
            toPD.lookupSchema = lookupSchema;
            toPD.lookupQuery = lookupQuery;
            toPD.mvEnabled = mvEnabled;
        }
    }

    static
    {
        ObjectFactory.Registry.register(PropertyDescriptor.class,
            new BeanObjectFactory<PropertyDescriptor>(PropertyDescriptor.class)
            {
                @Override
                public @NotNull Map<String, Object> toMap(PropertyDescriptor bean, @Nullable Map<String, Object> m)
                {
                    m = super.toMap(bean, m);
                    Object o = m.get("URL");
                    if (o instanceof StringExpression)
                        m.put("URL", o.toString());
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


    // ParameterDescription

    @Override
    public String getURI()
    {
        return getPropertyURI();
    }
}


