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
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties("systemFields")
public class DataClassDomainKindProperties
{
    private int rowId;
    private String lsid;
    private int domainId;
    private String name;
    private String description;
    private String nameExpression;
    private Integer sampleType;
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
            this.sampleType = dc.getSampleType().getRowId();

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

    public int getRowId()
    {
        return rowId;
    }

    public void setRowId(int rowId)
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

    public Integer getSampleType()
    {
        return sampleType;
    }

    public void setSampleType(Integer sampleType)
    {
        this.sampleType = sampleType;
    }

    @Deprecated // Left in place for now, until domain templates get cleaned up (e.g., media-base.template.xml)
    public Integer getSampleSet()
    {
        return sampleType;
    }

    @Deprecated // Left in place for now, until domain templates get cleaned up (e.g., media-base.template.xml)
    public void setSampleSet(Integer sampleType)
    {
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
    public Map<String, String> getImportAliasesMap()
    {
        if (getImportAliases() == null)
            return null;

        Map<String, String> aliases = new HashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : getImportAliases().entrySet())
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
}
