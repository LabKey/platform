package org.labkey.api.exp.api;

import java.util.List;

public class RecordingOptions
{
    private String name;
    private String description;

    private List<Object> materialInputs;
    private List<Object> dataInputs;
    private List<String> objectInputs;

    public RecordingOptions(String name, String description, List<Object> materialInputs, List<Object> dataInputs, List<String> objectInputs)
    {
        this.name = name;
        this.description = description;
        this.materialInputs = materialInputs;
        this.dataInputs = dataInputs;
        this.objectInputs = objectInputs;
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

    public List<Object> getMaterialInputs()
    {
        return materialInputs;
    }

    public void setMaterialInputs(List<Object> materialInputs)
    {
        this.materialInputs = materialInputs;
    }

    public List<Object> getDataInputs()
    {
        return dataInputs;
    }

    public void setDataInputs(List<Object> dataInputs)
    {
        this.dataInputs = dataInputs;
    }

    public List<String> getObjectInputs()
    {
        return objectInputs;
    }

    public void setObjectInputs(List<String> objectInputs)
    {
        this.objectInputs = objectInputs;
    }
}
