/*
 * Copyright (c) 2020-2026 LabKey Corporation
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
package org.labkey.api.exp.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties("systemFields")
public class DataClassDomainKindProperties
{
    private long rowId;
    private String lsid;
    private int domainId;
    private String name;
    private String description;
    private String nameExpression;
    private Long sampleType;
    private String category;
    private boolean _strictFieldValidation = true; // Set as false to skip validation check in ExperimentServiceImpl.createDataClass (used in Rlabkey labkey.domain.createAndLoad)
    private List<String> excludedContainerIds;

    @JsonDeserialize(using = ImportAliasesDeserializer.class)
    @JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
    private Map<String, Map<String, Object>> importAliases;

    public DataClassDomainKindProperties()
    {}

    public DataClassDomainKindProperties(@Nullable ExpDataClass dc)
    {
        if (dc == null)
            return;

        this.rowId = dc.getRowId();
        this.lsid = dc.getLSID();
        this.name = dc.getName();
        this.nameExpression = dc.getNameExpression();
        this.category = dc.getCategory();

        this.description = dc.getDescription();
        if (this.description == null && dc.getDomain() != null)
            this.description = dc.getDomain().getDescription();

        if (dc.getSampleType() != null)
        {
            this.sampleType = dc.getSampleType().getRowId();
            assert 0 != this.sampleType;
        }

        if (dc.getDomain() != null)
            this.domainId = dc.getDomain().getTypeId();

        try
        {
            this.importAliases = dc.getImportAliasMap();
        }
        catch (IOException e)
        {
            throw new RuntimeException("Unable to parse parent alias mappings: ", e);
        }
    }

    public long getRowId()
    {
        return rowId;
    }

    public void setRowId(long rowId)
    {
        this.rowId = rowId;
    }

    public String getLsid()
    {
        return lsid;
    }

    public void setLsid(String lsid)
    {
        this.lsid = lsid;
    }

    public String getName()
    {
        return StringUtils.trimToNull(name);
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getDescription()
    {
        return StringUtils.trimToNull(description);
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public String getNameExpression()
    {
        return StringUtils.trimToNull(nameExpression);
    }

    public void setNameExpression(String nameExpression)
    {
        this.nameExpression = nameExpression;
    }

    public Long getSampleType()
    {
        return sampleType;
    }

    public void setSampleType(Long sampleType)
    {
        assert null == sampleType || 0 < sampleType;
        this.sampleType = sampleType;
    }

    @Deprecated // Left in place for now, until domain templates get cleaned up (e.g., media-base.template.xml)
    public Long getSampleSet()
    {
        return sampleType;
    }

    @Deprecated // Left in place for now, until domain templates get cleaned up (e.g., media-base.template.xml)
    public void setSampleSet(Long sampleType)
    {
        assert 0 != sampleType;
        this.sampleType = sampleType;
    }

    public String getCategory()
    {
        return StringUtils.trimToNull(category);
    }

    public void setCategory(String category)
    {
        this.category = category;
    }

    public int getDomainId()
    {
        return domainId;
    }

    public void setDomainId(int domainId)
    {
        this.domainId = domainId;
    }

    public boolean isStrictFieldValidation()
    {
        return _strictFieldValidation;
    }

    public void setStrictFieldValidation(boolean strictFieldValidation)
    {
        _strictFieldValidation = strictFieldValidation;
    }

    public void setImportAliases(Map<String, Map<String, Object>> importAliases)
    {
        this.importAliases = importAliases;
    }

    @JsonIgnore
    @Nullable
    public Map<String, String> getImportAliasesMap()
    {
        Map<String, Map<String, Object>> importAliases = getImportAliases();
        if (importAliases == null)
            return null;

        Map<String, String> aliases = new HashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : importAliases.entrySet())
        {
            aliases.put(entry.getKey(), (String) entry.getValue().get("inputType"));
        }
        return Collections.unmodifiableMap(aliases);
    }

    public Map<String, Map<String, Object>> getImportAliases()
    {
        return this.importAliases;
    }

    public List<String> getExcludedContainerIds()
    {
        return excludedContainerIds;
    }

    public void setExcludedContainerIds(List<String> excludedContainerIds)
    {
        this.excludedContainerIds = excludedContainerIds;
    }

    public Map<String, Object> getAuditRecordMap()
    {
        Map<String, Object> map = new LinkedHashMap<>();
        // skip Name and Description since it's general domain property
        if (!StringUtils.isEmpty(getNameExpression()))
            map.put("NameExpression", getNameExpression());
        String importAliasStr = ExperimentJSONConverter.getImportAliasStringVal(getImportAliases());
        if (!StringUtils.isEmpty(importAliasStr))
            map.put("ImportAlias", importAliasStr);
        if (!StringUtils.isEmpty(getCategory()))
            map.put("Category", getCategory());
        if (getSampleType() != null)
            map.put("SampleType", getSampleType());

        return map;
    }


}
