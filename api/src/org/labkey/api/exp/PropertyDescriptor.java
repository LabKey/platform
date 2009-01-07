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

import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.data.Container;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.PdLookupForeignKey;
import org.labkey.api.security.User;

import java.io.Serializable;

/**
 * User: migra
 * Date: Aug 15, 2005
 * Time: 2:41:47 PM
 */
public class PropertyDescriptor implements Serializable, Cloneable
{
    private int propertyId;
    private String propertyURI;
    private String ontologyURI;
    private String name;
    private String description;
    private String rangeURI;
    private String conceptURI;
//    private PropertyDescriptor concept;
    private PropertyType propertyType;
    private String label;
    private String searchTerms;
    private String semanticType;
    private String format;
    private Container container;
    private Container project;
    private boolean required;
    private String lookupContainer;
    private String lookupSchema;
    private String lookupQuery;
    private boolean qcEnabled;

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
        setLabel(col.getCaption());
        setFormat(col.getFormatString());
    }

    public PropertyDescriptor(String propertyURI, String rangeURI, String name, Container container)
    {
        this(propertyURI, rangeURI, name, null, container);
    }

    public PropertyDescriptor(String propertyURI, String rangeURI, String name, String label, Container container)
    {
        this();
        this.propertyURI = propertyURI;
        this.rangeURI = rangeURI;
        this.name = name;
        this.label = label;
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

    public void setName(String name)
    {
        this.name = name;
    }

    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
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

    public String getLabel()
    {
        return label;
    }

    public String getNonBlankLabel()
    {
        if (label == null || "".equals(label.trim()))
        {
            return getName();
        }
        return label;
    }

    public void setLabel(String label)
    {
        this.label = label;
    }

    public String getSemanticType()
    {
        return semanticType;
    }

    public void setSemanticType(String semanticType)
    {
        this.semanticType = semanticType;
    }


    @Override
    public String toString()
    {
        return propertyURI + " name=" + name + " project="+  project.getPath() + " container="+  container.getPath() + " label=" + label + " range=" + rangeURI + " concept=" + conceptURI;
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

    public boolean isQcEnabled()
    {
        return qcEnabled;
    }

    public void setQcEnabled(boolean qcEnabled)
    {
        this.qcEnabled = qcEnabled;
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
        ColumnInfo info = new PropertyColumn(this, baseTable, lsidCol, getContainer().getId());
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
        if (!(obj instanceof PropertyDescriptor))
            return false;
        // property descriptors that are not in the database are never equal:
        if (((PropertyDescriptor) obj).getPropertyId() == 0 || getPropertyId() == 0)
            return false;

        // two property descriptors are equal if they have the same row ID:
        return ((PropertyDescriptor) obj).getPropertyId() == getPropertyId();
    }
}


