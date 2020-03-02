package org.labkey.api.exp.api;

public class DataClassDomainKindProperties
{
    private int rowId;
    private String lsid;
    private int domainId;
    private String name;
    private String description;
    private String nameExpression;
    private Integer sampleSet;
    private String category;

    public DataClassDomainKindProperties()
    {}

    public DataClassDomainKindProperties(ExpDataClass dc)
    {
        this.rowId = dc.getRowId();
        this.lsid = dc.getLSID();
        this.name = dc.getName();
        this.nameExpression = dc.getNameExpression();
        this.category = dc.getCategory();

        this.description = dc.getDescription();
        if (this.description == null && dc.getDomain() != null)
            this.description = dc.getDomain().getDescription();

        if (dc.getSampleSet() != null)
            this.sampleSet = dc.getSampleSet().getRowId();
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

    public String getNameExpression()
    {
        return nameExpression;
    }

    public void setNameExpression(String nameExpression)
    {
        this.nameExpression = nameExpression;
    }

    public Integer getSampleSet()
    {
        return sampleSet;
    }

    public void setSampleSet(Integer sampleSet)
    {
        this.sampleSet = sampleSet;
    }

    public String getCategory()
    {
        return category;
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
