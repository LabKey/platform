package org.labkey.api.exp.api;

import org.labkey.api.util.GUID;
import org.labkey.api.util.Pair;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ProvenanceRecordingParams implements Serializable
{
    private GUID recordingId;
    private String name;
    private String description;
    private String runName;

    private List<String> predecessorSteps = Collections.emptyList();
    private String inputObjectUriProperty = ProvenanceService.PROVENANCE_INPUT_PROPERTY;
    private String outputObjectUriProperty = "lsid";
    private Map<String, Object> properties = Collections.emptyMap();
    // List of lsids
    private List<String> objectInputs = Collections.emptyList();
    // List of lsids
    private List<String> objectOutputs = Collections.emptyList();
    // from-to lsid pairs
    private List<Pair<String,String>> provenanceMap = Collections.emptyList();

    private List<ExpMaterial> materialInputs = Collections.emptyList();
    private List<ExpData> dataInputs = Collections.emptyList();

    private List<ExpMaterial> materialOutputs = Collections.emptyList();
    private List<ExpData> dataOutputs = Collections.emptyList();

    public GUID getRecordingId()
    {
        return recordingId;
    }

    public void setRecordingId(GUID recordingId)
    {
        this.recordingId = recordingId;
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

    public String getRunName()
    {
        return runName;
    }

    public void setRunName(String runName)
    {
        this.runName = runName;
    }

    public List<String> getPredecessorSteps()
    {
        return predecessorSteps;
    }

    public void setPredecessorSteps(List<String> predecessorSteps)
    {
        this.predecessorSteps = predecessorSteps;
    }

    public String getInputObjectUriProperty()
    {
        return inputObjectUriProperty;
    }

    public void setInputObjectUriProperty(String inputObjectUriProperty)
    {
        this.inputObjectUriProperty = inputObjectUriProperty;
    }

    public String getOutputObjectUriProperty()
    {
        return outputObjectUriProperty;
    }

    public void setOutputObjectUriProperty(String outputObjectUriProperty)
    {
        this.outputObjectUriProperty = outputObjectUriProperty;
    }

    public Map<String, Object> getProperties()
    {
        return properties;
    }

    public void setProperties(Map<String, Object> properties)
    {
        this.properties = properties;
    }

    public List<String> getObjectInputs()
    {
        return objectInputs;
    }

    public void setObjectInputs(List<String> objectInputs)
    {
        this.objectInputs = objectInputs;
    }

    public List<Pair<String, String>> getProvenanceMap()
    {
        return provenanceMap;
    }

    public void setProvenanceMap(List<Pair<String, String>> provenanceMap)
    {
        this.provenanceMap = provenanceMap;
    }

    public List<ExpMaterial> getMaterialInputs()
    {
        return materialInputs;
    }

    public void setMaterialInputs(List<ExpMaterial> materialInputs)
    {
        this.materialInputs = materialInputs;
    }

    public List<ExpData> getDataInputs()
    {
        return dataInputs;
    }

    public void setDataInputs(List<ExpData> dataInputs)
    {
        this.dataInputs = dataInputs;
    }

    public List<String> getObjectOutputs()
    {
        return objectOutputs;
    }

    public void setObjectOutputs(List<String> objectOutputs)
    {
        this.objectOutputs = objectOutputs;
    }

    public List<ExpMaterial> getMaterialOutputs()
    {
        return materialOutputs;
    }

    public void setMaterialOutputs(List<ExpMaterial> materialOutputs)
    {
        this.materialOutputs = materialOutputs;
    }

    public List<ExpData> getDataOutputs()
    {
        return dataOutputs;
    }

    public void setDataOutputs(List<ExpData> dataOutputs)
    {
        this.dataOutputs = dataOutputs;
    }
}
