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
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Small class with only the properties and fields (de)serialized to the DomainKindProperties (avoids hassle of pasting @JsonIgnores all over three classes.
 */
public class SampleTypeDomainKindProperties implements Cloneable
{
    public SampleTypeDomainKindProperties()
    {
    }

    public SampleTypeDomainKindProperties(ExpSampleType st)
    {
        if (st != null)
        {
            this.name = st.getName();
            this.nameExpression = st.getNameExpression();
            this.aliquotNameExpression = st.getAliquotNameExpression();
            this.labelColor = st.getLabelColor();
            this.metricUnit = st.getMetricUnit();
            this.domainId = st.getDomain().getTypeId();
            this.rowId = st.getRowId();
            this.lsid = st.getLSID();
            this.description = st.getDescription();
            this.idCols = Collections.emptyList();
            this.autoLinkTargetContainerId = null != st.getAutoLinkTargetContainer() ? st.getAutoLinkTargetContainer().getId() : "";
            this.autoLinkCategory = st.getAutoLinkCategory();
            this.category = st.getCategory();
            if (st.hasIdColumns())
            {
                this.idCols = st.getIdCols().stream().map(col -> col.getPropertyId()).collect(Collectors.toList());
            }

            try
            {
                this.importAliases = st.getImportAliasMap();
            }
            catch (IOException e)
            {
                throw new RuntimeException("Unable to parse parent alias mappings: ", e);
            }
        }
    }

    private String nameExpression;
    private String aliquotNameExpression;
    private String labelColor;
    private String metricUnit;

    private long rowId;
    private int domainId;
    private String lsid;
    private List<Integer> idCols;
    private String autoLinkTargetContainerId;
    private String autoLinkCategory;
    private Integer parentCol;
    private String category;
    private List<String> excludedContainerIds;
    private List<String> excludedDashboardContainerIds;

    //Ignored on import/save, use Domain.name & Domain.description instead
    private String name;
    private String description;

    @JsonDeserialize(using = ImportAliasesDeserializer.class)
    @JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
    private Map<String, Map<String, Object>> importAliases;


    public void setIdCols(List<Integer> idCols)
    {
        this.idCols = idCols;
    }

    public List<Integer> getIdCols()
    {
        return this.idCols;
    }


    public void setDescription(String description)
    {
        this.description = description;
    }

    public String getDescription()
    {
        return this.description;
    }

    public void setLsid(String lsid)
    {
        this.lsid = lsid;
    }

    public String getLsid()
    {
        return this.lsid;
    }

    public void setDomainId(int domainId)
    {
        this.domainId = domainId;
    }

    public int getDomainId()
    {
        return this.domainId;
    }

    public void setRowId(int rowId)
    {
        this.rowId = rowId;
    }

    public long getRowId()
    {
        return this.rowId;
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

    public void setNameExpression(String nameExpression)
    {
        this.nameExpression = nameExpression;
    }

    public String getNameExpression()
    {
        return this.nameExpression;
    }

    public String getAliquotNameExpression()
    {
        return this.aliquotNameExpression;
    }

    public void setAliquotNameExpression(String nameExpression)
    {
        this.aliquotNameExpression = nameExpression;
    }

    public void setLabelColor(String labelColor)
    {
        this.labelColor = labelColor;
    }

    public String getLabelColor()
    {
        return this.labelColor;
    }

    public String getMetricUnit()
    {
        return metricUnit;
    }

    public void setMetricUnit(String metricUnit)
    {
        this.metricUnit = metricUnit;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getName()
    {
        return this.name;
    }

    @JsonIgnore
    public JSONObject toJSONObject()
    {
        return new JSONObject(this);
    }

    public String getAutoLinkTargetContainerId()
    {
        return this.autoLinkTargetContainerId;
    }

    public void setAutoLinkTargetContainerId(String autoLinkTargetContainerId)
    {
        this.autoLinkTargetContainerId = autoLinkTargetContainerId;
    }

    public String getAutoLinkCategory()
    {
        return autoLinkCategory;
    }

    public void setAutoLinkCategory(String autoLinkCategory)
    {
        this.autoLinkCategory = autoLinkCategory;
    }

    public Integer getParentCol()
    {
        return parentCol;
    }

    public void setParentCol(Integer parentCol)
    {
        this.parentCol = parentCol;
    }

    public String getCategory()
    {
        return category;
    }

    public void setCategory(String category)
    {
        this.category = category;
    }

    public List<String> getExcludedContainerIds()
    {
        return excludedContainerIds;
    }

    public void setExcludedContainerIds(List<String> excludedContainerIds)
    {
        this.excludedContainerIds = excludedContainerIds;
    }

    public List<String> getExcludedDashboardContainerIds()
    {
        return excludedDashboardContainerIds;
    }

    public void setExcludedDashboardContainerIds(List<String> excludedDashboardContainerIds)
    {
        this.excludedDashboardContainerIds = excludedDashboardContainerIds;
    }

    public Map<String, Object> getAuditRecordMap()
    {
        Map<String, Object> map = new LinkedHashMap<>();
        // skip Name and Description since it's general domain property
        if (!StringUtils.isEmpty(getNameExpression()))
            map.put("NameExpression", getNameExpression());
        if (!StringUtils.isEmpty(getAliquotNameExpression()))
            map.put("AliquotNameExpression", getAliquotNameExpression());
        if (!StringUtils.isEmpty(getLabelColor()))
            map.put("LabelColor", getLabelColor());
        if (!StringUtils.isEmpty(getMetricUnit()))
            map.put("MetricUnit", getMetricUnit());
        String importAliasStr = ExperimentJSONConverter.getImportAliasStringVal(getImportAliases());
        if (!StringUtils.isEmpty(importAliasStr))
            map.put("ImportAlias", importAliasStr);
        if (!StringUtils.isEmpty(getAutoLinkTargetContainerId()))
            map.put("AutoLinkTargetContainerId", getAutoLinkTargetContainerId());
        if (!StringUtils.isEmpty(getAutoLinkCategory()))
            map.put("AutoLinkCategory", getAutoLinkCategory());
        if (!StringUtils.isEmpty(getCategory()))
            map.put("Category", getCategory());

        return map;
    }
}
