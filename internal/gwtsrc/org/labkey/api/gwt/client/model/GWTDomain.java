/*
 * Copyright (c) 2007-2014 LabKey Corporation
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
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.gwt.client.util.PropertyUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
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
    private String container;
    private boolean allowFileLinkProperties;
    private boolean allowAttachmentProperties;
    private boolean allowFlagProperties = false;
    private DefaultValueType defaultDefaultValueType = null;
    private DefaultValueType[] defaultValueOptions = new DefaultValueType[0];
    private List<FieldType> fields = new ArrayList<FieldType>();
    private String defaultValuesURL = null;

    private Set<String> mandatoryPropertyDescriptorNames = new HashSet<String>();

    private Set<String> reservedFieldNames = new HashSet<String>();

    private Set<String> excludeFromExportFieldNames = new HashSet<String>();

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
        this.container = src.container;
        this.allowFileLinkProperties = src.allowFileLinkProperties;
        this.allowAttachmentProperties = src.allowAttachmentProperties;
        this.allowFlagProperties = src.allowFlagProperties;
        this.defaultDefaultValueType = src.defaultDefaultValueType;
        this.defaultValueOptions = src.defaultValueOptions;
        this.defaultValuesURL = src.defaultValuesURL;

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

        if (src.getExcludeFromExportFieldNames() != null)
        {
            this.getExcludeFromExportFieldNames().addAll(src.getExcludeFromExportFieldNames());
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

    public String getContainer()
    {
        return container;
    }

    public void setContainer(String container)
    {
        this.container = container;
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

    public boolean isAllowFlagProperties()
    {
        return allowFlagProperties;
    }

    public void setAllowFlagProperties(boolean allowFlagProperties)
    {
        this.allowFlagProperties = allowFlagProperties;
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
        return true;
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

    /**
     *  @param reservedFieldNames can't create new fields with these names
     */
    public void setReservedFieldNames(Set<String> reservedFieldNames)
    {
        this.reservedFieldNames = reservedFieldNames;
    }

    /**
     *
     * @param excludeFromExportFieldNames These fields will be suppressed from the export field list. Primary use case is to not export List key fields.
     */
    public void setExcludeFromExportFieldNames(Set<String> excludeFromExportFieldNames)
    {
        this.excludeFromExportFieldNames = new HashSet<String>();
        for (String excludeFromExportFieldName : excludeFromExportFieldNames)
        {
            this.excludeFromExportFieldNames.add(excludeFromExportFieldName.toLowerCase());
        }
    }

    public Set<String> getExcludeFromExportFieldNames()
    {
        return excludeFromExportFieldNames;
    }

    public boolean isExcludeFromExportField(FieldType field)
    {
        if (excludeFromExportFieldNames == null || field.getName() == null)
        {
            return false;
        }
        return excludeFromExportFieldNames.contains(field.getName().toLowerCase());
    }

    public DefaultValueType getDefaultDefaultValueType()
    {
        return defaultDefaultValueType;
    }

    public DefaultValueType[] getDefaultValueOptions()
    {
        return defaultValueOptions;
    }

    public void setDefaultValueOptions(DefaultValueType[] defaultOptions, DefaultValueType defaultDefault)
    {
        this.defaultDefaultValueType = defaultDefault;
        this.defaultValueOptions = defaultOptions;
    }

    public String getDefaultValuesURL()
    {
        if (defaultValuesURL == null)
            return PropertyUtil.getRelativeURL("setDefaultValuesList", "list");
        return defaultValuesURL;
    }

    public void setDefaultValuesURL(String defaultValuesURL)
    {
        this.defaultValuesURL = defaultValuesURL;
    }

}
