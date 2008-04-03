package org.labkey.experiment.controllers.exp;

import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpData;

import java.util.Map;

/**
 * User: jeckels
* Date: Dec 18, 2007
*/
public class RunInputOutputBean
{
    private final Map<ExpMaterial, String> _materials;
    private final Map<ExpData, String> _datas;

    public RunInputOutputBean(Map<ExpMaterial, String> materials, Map<ExpData, String> datas)
    {
        _materials = materials;
        _datas = datas;
    }

    public Map<ExpMaterial, String> getMaterials()
    {
        return _materials;
    }

    public Map<ExpData, String> getDatas()
    {
        return _datas;
    }
}
