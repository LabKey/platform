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

package org.labkey.api.gwt.client.model;

import com.google.gwt.user.client.rpc.IsSerializable;

import java.util.*;

import org.labkey.api.gwt.client.util.StringProperty;
import org.labkey.api.gwt.client.DefaultValueType;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Apr 24, 2007
 * Time: 1:44:49 PM
 */
public class GWTDomain<FieldType extends GWTPropertyDescriptor> implements IsSerializable
{
    private int domainId;
    private String name;
    private String domainURI;
    private String description;
    private boolean allowFileLinkProperties;
    private boolean allowAttachmentProperties;
    private StringProperty defaultDefaultValueType = new StringProperty(DefaultValueType.FIXED_EDITABLE.name());
    private List<FieldType> fields = new ArrayList<FieldType>();

    private Set<String> mandatoryPropertyDescriptorNames = new HashSet<String>();

    private Set<String> reservedFieldNames = new HashSet<String>();

    public GWTDomain()
    {
    }

    // deep clone constructor
    public GWTDomain(GWTDomain<FieldType> src)
    {
        this.domainId = src.domainId;    
        this.name = src.name;
        this.domainURI = src.domainURI;
        this.description = src.description;
        this.allowFileLinkProperties = src.allowFileLinkProperties;
        this.allowAttachmentProperties = src.allowAttachmentProperties;
        if (src.getFields() == null)
            return;
        for (int i=0 ; i<src.getFields().size() ; i++)
            this.fields.add((FieldType)src.getFields().get(i).copy());

        if (src.mandatoryPropertyDescriptorNames != null)
        {
            this.mandatoryPropertyDescriptorNames.addAll(src.mandatoryPropertyDescriptorNames);
        }

        if (src.getReservedFieldNames() != null)
        {
            this.getReservedFieldNames().addAll(src.getReservedFieldNames());
        }
    }


    public int getDomainId()
    {
        return domainId;
    }

    public void setDomainId(int domainId)
    {
        this.domainId = domainId;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getDomainURI()
    {
        return domainURI;
    }

    public void setDomainURI(String domainURI)
    {
        this.domainURI = domainURI;
    }

    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public List<FieldType> getFields()
    {
        return fields;
    }

    public void setFields(List<FieldType> list)
    {
        fields = list;
    }

    public boolean isAllowFileLinkProperties()
    {
        return allowFileLinkProperties;
    }

    public void setAllowFileLinkProperties(boolean allowFileLinkProperties)
    {
        this.allowFileLinkProperties = allowFileLinkProperties;
    }

    public boolean isAllowAttachmentProperties()
    {
        return allowAttachmentProperties;
    }

    public void setAllowAttachmentProperties(boolean allowAttachmentProperties)
    {
        this.allowAttachmentProperties = allowAttachmentProperties;
    }

    /**
     * @return Indicates that the property can't be removed from the domain. The property may or may not be nullable.
     */
    public boolean isMandatoryField(FieldType field)
    {
        if (mandatoryPropertyDescriptorNames == null || field.getName() == null)
        {
            return false;
        }
        return mandatoryPropertyDescriptorNames.contains(field.getName().toLowerCase());
    }

    public boolean isEditable(FieldType field)
    {
        return !isMandatoryField(field);
    }

    /**
     * @param mandatoryFieldNames names of property descriptors that must be present in this domain.  Does not indicate that they must be non-nullable.
     */
    public void setMandatoryFieldNames(Set<String> mandatoryFieldNames)
    {
        this.mandatoryPropertyDescriptorNames = new HashSet<String>();
        for (String mandatoryPropertyDescriptor : mandatoryFieldNames)
        {
            this.mandatoryPropertyDescriptorNames.add(mandatoryPropertyDescriptor.toLowerCase());
        }
    }

    public Set<String> getReservedFieldNames()
    {
        return reservedFieldNames;
    }

    public void setReservedFieldNames(Set<String> reservedFieldNames)
    {
        this.reservedFieldNames = reservedFieldNames;
    }

    public String getDefaultDefaultValueType()
    {
        return defaultDefaultValueType.getString();
    }

    public void setDefaultDefaultValueType(String defaultDefaultValueType)
    {
        this.defaultDefaultValueType.set(defaultDefaultValueType);
    }
}
