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
    private Map<Long, List<Long>> _protocolPlateSets = Collections.emptyMap();
    // A map of Plate Set ID to Plate Set
    private Map<Long, PlateSet> _plateSets = Collections.emptyMap();

    public PlateSetAssays()
    {
    }

    public Map<Long, List<Long>> getProtocolPlateSets()
    {
        return _protocolPlateSets;
    }

    public void setProtocolPlateSets(Map<Long, List<Long>> protocolPlateSets)
    {
        _protocolPlateSets = protocolPlateSets;
    }

    public Map<Long, PlateSet> getPlateSets()
    {
        return _plateSets;
    }

    public void setPlateSets(Map<Long, PlateSet> plateSets)
    {
        _plateSets = plateSets;
    }
}
