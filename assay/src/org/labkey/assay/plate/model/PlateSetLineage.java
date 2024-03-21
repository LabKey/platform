package org.labkey.assay.plate.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.labkey.api.assay.plate.PlateSet;
import org.labkey.api.assay.plate.PlateSetEdge;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    /**
     * Returns a Map<Integer, PlateSet> containing the PlateSet for the given plateSetId as well as all the PlateSets
     * for the descendents of the given plateSetId.
     * @param plateSetId the plateSetId to return with descendents
     * @return Map<Integer, PlateSet>
     */
    public Map<Integer, PlateSet> getPlateSetAndDescendents(Integer plateSetId) {
        Map<Integer, PlateSet> allPlateSets = new HashMap<>();
        allPlateSets.put(plateSetId, _plateSets.get(plateSetId));
        Set<Integer> parents = new HashSet<>(Arrays.asList(plateSetId));

        while (!parents.isEmpty())
        {
            Set<Integer> children = new HashSet<>();

            for (PlateSetEdge edge : _edges)
            {
                if (parents.contains(edge.getFromPlateSetId())) {
                    Integer to = edge.getToPlateSetId();
                    children.add(to);
                    allPlateSets.put(to, _plateSets.get(to));
                }
            }

            parents = children;
        }

        return allPlateSets;
    }
}
