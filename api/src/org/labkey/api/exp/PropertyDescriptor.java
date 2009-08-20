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

import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.data.Container;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.ColumnRenderProperties;
import org.labkey.api.query.PdLookupForeignKey;
import org.labkey.api.security.User;
import org.labkey.api.gwt.client.DefaultValueType;

import java.io.Serializable;

/**
 * User: migra
 * Date: Aug 15, 2005
 * Time: 2:41:47 PM
 */
public class PropertyDescriptor extends ColumnRenderProperties implements Serializable, Cloneable
{
    private int propertyId;
    private String propertyURI;
    private String ontologyURI;
    private String rangeURI;
    private String conceptURI;
//    private PropertyDescriptor concept;
    private PropertyType propertyType;
    private String searchTerms;
    private String semanticType;
    private String format;
    private Container container;
    private Container project;
    private boolean required;
    private String lookupContainer;
    private String lookupSchema;
    private String lookupQuery;
    private boolean mvEnabled;
    private DefaultValueType _defaultValueType;

    public String getLookupContainer()
    {
        return lookupContainer;
    }

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
//        assert MemTracker.put(this);
    }

    public PropertyDescriptor(ColumnInfo col, Container c)
    {
        this(col.getPropertyURI(), PropertyType.getFromClass(col.getJavaClass()).getTypeUri(), col.getName(), c);
        setDescription(col.getDescription());
        setRequired(!col.isNullable());
        setHidden(col.isHidden());
        setLabel(col.getLabel());
        setFormat(col.getFormatString());
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
    }

    public int getPropertyId()
    {
        return propertyId;
    }

    public void setPropertyId(int rowId)
    {
        this.propertyId = rowId;
    }

    public String getPropertyURI()
    {
        return propertyURI;
    }

    public void setPropertyURI(String propertyURI)
    {
        this.propertyURI = propertyURI;
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

    public String getOntologyURI()
    {
        return ontologyURI;
    }

    public void setOntologyURI(String ontologyURI)
    {
        this.ontologyURI = ontologyURI;
    }

    public String getRangeURI()
    {
        return rangeURI;
    }

    public void setRangeURI(String dataTypeURI)
    {
        this.rangeURI = dataTypeURI;
    }

    public PropertyType getPropertyType()
    {
        if (null == propertyType)
            propertyType = PropertyType.getFromURI(getConceptURI(), rangeURI);

        return propertyType;
    }

    public String getConceptURI()
    {
        return conceptURI;
    }

    public void setConceptURI(String conceptURI)
    {
        this.conceptURI = conceptURI;
    }

    public String getSearchTerms()
    {
        return searchTerms;
    }

    public void setSearchTerms(CharSequence searchTerms)
    {
        this.searchTerms = searchTerms == null ? null : searchTerms.toString();
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

    public String getFormat()
    {
        return format;
    }

    public void setFormat(String format)
    {
        this.format = format;
    }

    public Container getContainer() {
        return container;
    }

    public void setContainer(Container container) {
    //    if (container.equals(ContainerManager.getRoot()))
    //        container=ContainerManager.getSharedContainer();

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


    public boolean isRequired()
    {
        return required;
    }

    public void setRequired(boolean required)
    {
        this.required = required;
    }

    public boolean isMvEnabled()
    {
        return mvEnabled;
    }

    public void setMvEnabled(boolean mvEnabled)
    {
        this.mvEnabled = mvEnabled;
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

    public ColumnInfo createColumnInfo(TableInfo baseTable, String lsidCol, User user)
    {
        ColumnInfo info = new PropertyColumn(this, baseTable, lsidCol, getContainer().getId(), user);
        if (getLookupQuery() != null)
            info.setFk(new PdLookupForeignKey(user, this));
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
}


