/*
 * Copyright (c) 2018-2019 LabKey Corporation
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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.gwt.user.client.rpc.IsSerializable;
import lombok.Getter;
import lombok.Setter;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.gwt.client.util.PropertyUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GWTDomain<FieldType extends GWTPropertyDescriptor> implements IsSerializable
{
    private String _ts;
    @Getter @Setter private int domainId;
    @Getter @Setter private String name;
    @Getter @Setter private String domainURI;
    @Getter @Setter private String domainKindName;
    @Getter @Setter private String description;
    @Getter @Setter private String container;
    @Getter @Setter private boolean allowFileLinkProperties;
    @Getter @Setter private boolean allowAttachmentProperties;
    @Getter @Setter private boolean allowFlagProperties;
    @Getter @Setter private boolean allowTextChoiceProperties;
    @Getter @Setter private boolean allowMultiChoiceProperties;
    @Getter @Setter private boolean allowSampleSubjectProperties;
    @Getter @Setter private boolean allowTimepointProperties;
    @Getter @Setter private boolean allowUniqueConstraintProperties;
    @Getter @Setter private boolean allowCalculatedFields;
    @Getter @Setter private boolean showDefaultValueSettings;
    private DefaultValueType defaultDefaultValueType = null;
    private DefaultValueType[] defaultValueOptions = new DefaultValueType[0];
    private List<FieldType> fields = new ArrayList<>();
    private List<FieldType> standardFields = null;
    private List<FieldType> calculatedFields = null;
    @Getter @Setter private List<GWTIndex> indices = new ArrayList<>();
    private String defaultValuesURL = null;
    private Set<String> mandatoryPropertyDescriptorNames = new HashSet<>();
    private Set<String> reservedFieldNames = new HashSet<>();
    private Set<String> reservedFieldNamePrefixes = new HashSet<>();
    private Set<String> phiNotAllowedFieldNames = new HashSet<>();
    private Set<String> excludeFromExportFieldNames = new HashSet<>();
    @Getter @Setter private boolean provisioned = false;
    @Getter @Setter private List<String> disabledSystemFields;

    // schema,query,template are not part of the domain, but it's handy to pass
    // these values to the PropertiedEditor along with the GWTDomain.
    // NOTE queryName is not necessarily == name
    @Getter @Setter private String schemaName = null;
    @Getter @Setter private String queryName = null;
    @Getter @Setter private String templateDescription = null; // null if no template
    @Getter @Setter private String instructions = null;
    @Getter @Setter private boolean supportsPhiLevel = false;

    public GWTDomain()
    {
    }

    // deep clone constructor
    public GWTDomain(GWTDomain<FieldType> src)
    {
        _ts = src._ts;
        this.domainId = src.domainId;
        this.name = src.name;
        this.domainURI = src.domainURI;
        this.domainKindName = src.domainKindName;
        this.description = src.description;
        this.disabledSystemFields = src.disabledSystemFields;
        this.container = src.container;
        this.allowFileLinkProperties = src.allowFileLinkProperties;
        this.allowAttachmentProperties = src.allowAttachmentProperties;
        this.allowFlagProperties = src.allowFlagProperties;
        this.allowTextChoiceProperties = src.allowTextChoiceProperties;
        this.allowMultiChoiceProperties = src.allowMultiChoiceProperties;
        this.allowSampleSubjectProperties = src.allowSampleSubjectProperties;
        this.allowTimepointProperties = src.allowTimepointProperties;
        this.allowUniqueConstraintProperties = src.allowUniqueConstraintProperties;
        this.allowCalculatedFields = src.allowCalculatedFields;
        this.showDefaultValueSettings = src.showDefaultValueSettings;
        this.defaultDefaultValueType = src.defaultDefaultValueType;
        this.defaultValueOptions = src.defaultValueOptions;
        this.defaultValuesURL = src.defaultValuesURL;
        this.provisioned = src.provisioned;
        this.supportsPhiLevel = src.supportsPhiLevel;

        if (src.indices != null)
        {
            for (int i = 0; i < src.indices.size(); i++)
                this.indices.add(src.indices.get(i).copy());
        }

        // include all fields here (standard and calculated) in the copy
        if (src.getFields(true) == null)
            return;
        for (int i=0 ; i<src.getFields(true).size() ; i++)
            this.fields.add((FieldType)src.getFields(true).get(i).copy());

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
        this.instructions = src.instructions;

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

    @JsonIgnore
    public List<FieldType> getFields(boolean includeCalculated)
    {
        if (includeCalculated)
            return fields;
        else
            return getFields();
    }

    public List<FieldType> getFields()
    {
        if (standardFields == null)
            standardFields = fields.stream().filter(f -> f.getValueExpression() == null).toList();
        return standardFields;
    }

    public List<FieldType> getCalculatedFields()
    {
        if (calculatedFields == null)
            calculatedFields = fields.stream().filter(f -> f.getValueExpression() != null).toList();
        return calculatedFields;
    }

    public void setFields(List<FieldType> list)
    {
        fields = list;

        // reset the cached lists of fields so they will be recalculated on next call to getters
        standardFields = null;
        calculatedFields = null;
    }

    public FieldType getFieldByName(String name)
    {
        for (FieldType field : getFields(true))
        {
            if (field.getName() != null && field.getName().equalsIgnoreCase(name))
                return field;
        }
        return null;
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
        this.mandatoryPropertyDescriptorNames = new HashSet<>();
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
        this.reservedFieldNames = new HashSet<>();
        for (String s : reservedFieldNames)
        {
            this.reservedFieldNames.add(s.toLowerCase());
        }
    }

    public Set<String> getReservedFieldNamePrefixes()
    {
        return this.reservedFieldNamePrefixes;
    }

    public void setReservedFieldNamePrefixes(Set<String> prefixes)
    {
        this.reservedFieldNamePrefixes = new HashSet<>(prefixes);
    }
    /**
     *
     * @param excludeFromExportFieldNames These fields will be suppressed from the export field list. Primary use case is to not export List key fields.
     */
    public void setExcludeFromExportFieldNames(Set<String> excludeFromExportFieldNames)
    {
        this.excludeFromExportFieldNames = new HashSet<>();
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
        this.phiNotAllowedFieldNames = new HashSet<>();
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
}
