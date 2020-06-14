package org.labkey.api.exp.api;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

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
}
