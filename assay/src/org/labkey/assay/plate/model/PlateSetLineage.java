package org.labkey.assay.plate.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.labkey.api.assay.plate.PlateSet;
import org.labkey.api.assay.plate.PlateSetEdge;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlateSetLineage
{
    private List<PlateSetEdge> _edges = Collections.emptyList();
    private Map<Integer, PlateSet> _plateSets = Collections.emptyMap();
    private Integer _root;
    private Integer _seed;

    public PlateSetLineage()
    {
    }

    public List<PlateSetEdge> getEdges()
    {
        return _edges;
    }

    public void setEdges(List<PlateSetEdge> edges)
    {
        _edges = edges;
    }

    public Map<Integer, PlateSet> getPlateSets()
    {
        return _plateSets;
    }

    public void setPlateSets(Map<Integer, PlateSet> plateSets)
    {
        _plateSets = plateSets;
    }

    public Integer getRoot()
    {
        return _root;
    }

    public void setRoot(Integer root)
    {
        _root = root;
    }

    public Integer getSeed()
    {
        return _seed;
    }

    public void setSeed(Integer seed)
    {
        _seed = seed;
    }
}
