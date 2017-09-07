/*
 * Copyright (c) 2007-2017 LabKey Corporation
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
import java.util.Collections;
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
    private String _ts;
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
    private List<GWTIndex> indices = new ArrayList<GWTIndex>();
    private String defaultValuesURL = null;

    private Set<String> mandatoryPropertyDescriptorNames = new HashSet<String>();

    private Set<String> reservedFieldNames = new HashSet<String>();
    private Set<String> phiNotAllowedFieldNames = new HashSet<String>();

    private Set<String> excludeFromExportFieldNames = new HashSet<String>();
    private boolean provisioned = false;

    // schema,query,template are not part of the domain, but it's handy to pass
    // these values to the PropertiedEditor along with the GWTDomain.
    // NOTE queryName is not necessarily == name
    private String schemaName=null;
    private String queryName=null;
    private String templateDescription=null; // null if no template

    public GWTDomain()
    {
    }

    // deep clone constructor
    public GWTDomain(GWTDomain<FieldType> src)
    {
        this._ts = src._ts;
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
        this.provisioned = src.isProvisioned();

        if (src.indices != null)
        {
            for (int i = 0; i < src.indices.size(); i++)
                this.indices.add(src.indices.get(i).copy());
        }

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

        this.schemaName = src.schemaName;
        this.queryName = src.queryName;
        this.templateDescription = src.templateDescription;

        if (src.getPhiNotAllowedFieldNames() != null)
        {
            this.getPhiNotAllowedFieldNames().addAll(src.getPhiNotAllowedFieldNames());
        }
    }

    //  String representation of database _ts (rowversion) column
    public void set_Ts(String ts)
    {
        _ts = ts;
    }
    public String get_Ts()
    {
        return _ts;
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

    public String getSchemaName()
    {
        return this.schemaName;
    }

    public void setSchemaName(String name)
    {
        this.schemaName = name;
    }

    public String getQueryName()
    {
        return this.queryName;
    }

    public void setQueryName(String name)
    {
        this.queryName = name;
    }

    public String getTemplateDescription()
    {
        return templateDescription;
    }

    public void setTemplateDescription(String templateDescription)
    {
        this.templateDescription = templateDescription;
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

    public List<GWTIndex> getIndices()
    {
        return indices;
    }

    public void setIndices(List<GWTIndex> indices)
    {
        this.indices = indices;
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
     * @return  Indicates that the property is not allowed to be set as PHI
     */
    public boolean allowsPhi(FieldType field)
    {
        return !(getPhiNotAllowedFieldNames() != null && field.getName() != null && getPhiNotAllowedFieldNames().contains(field.getName().toLowerCase()));
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

    /**
     * Get the list of property names that can't be removed from the domain.  The set of mandatory fields is not modifiable in the designer.
     */
    public Set<String> getMandatoryFieldNames()
    {
        if (this.mandatoryPropertyDescriptorNames == null)
            return Collections.emptySet();
        return Collections.unmodifiableSet(this.mandatoryPropertyDescriptorNames);
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
        this.reservedFieldNames = new HashSet<String>();
        for (String s : reservedFieldNames)
        {
            this.reservedFieldNames.add(s.toLowerCase());
        }
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

    public Set<String> getPhiNotAllowedFieldNames()
    {
        return phiNotAllowedFieldNames;
    }

    public void setPhiNotAllowedFieldNames(Set<String> phiNotAllowedFieldNames)
    {
        this.phiNotAllowedFieldNames = new HashSet<String>();
        for (String fieldName : phiNotAllowedFieldNames)
        {
            this.phiNotAllowedFieldNames.add(fieldName.toLowerCase());
        }
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

    /**
     * Flag indicating domain is provisioned
     * @return
     */
    public boolean isProvisioned()
    {
        return provisioned;
    }

    public void setProvisioned(boolean value)
    {
        this.provisioned = value;
    }

}
