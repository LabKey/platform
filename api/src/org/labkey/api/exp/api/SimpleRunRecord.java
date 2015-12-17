package org.labkey.api.exp.api;

import org.labkey.api.exp.api.ExpMaterial;

import java.util.Map;

/**
 * Created by klum on 11/25/2015.
 */
public interface SimpleRunRecord
{
    public Map<ExpMaterial, String> getInputMaterialMap();
    public Map<ExpMaterial, String> getOutputMaterialMap();
    public Map<ExpData, String> getInputDataMap();
    public Map<ExpData, String> getOutputDataMap();
}
