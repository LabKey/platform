package org.labkey.assay.plate.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.labkey.api.assay.plate.PlateSet;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlateSetAssays
{
    // A map of Assay Protocol ID to Plate Set IDs
    private Map<Integer, List<Integer>> _protocolPlateSets = Collections.emptyMap();
    // A map of Plate Set ID to Plate Set
    private Map<Integer, PlateSet> _plateSets = Collections.emptyMap();

    public PlateSetAssays()
    {
    }

    public Map<Integer, List<Integer>> getProtocolPlateSets()
    {
        return _protocolPlateSets;
    }

    public void setProtocolPlateSets(Map<Integer, List<Integer>> protocolPlateSets)
    {
        _protocolPlateSets = protocolPlateSets;
    }

    public Map<Integer, PlateSet> getPlateSets()
    {
        return _plateSets;
    }

    public void setPlateSets(Map<Integer, PlateSet> plateSets)
    {
        _plateSets = plateSets;
    }
}
