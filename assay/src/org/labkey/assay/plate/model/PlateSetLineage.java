package org.labkey.assay.plate.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.assay.plate.PlateSet;
import org.labkey.api.assay.plate.PlateSetEdge;
import org.labkey.api.query.ValidationException;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Stack;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlateSetLineage
{
    private List<PlateSetEdge> _edges = Collections.emptyList();
    private Map<Integer, PlateSet> _plateSets = Collections.emptyMap();
    private Integer _root;
    private final Integer _seed;

    public PlateSetLineage(@NotNull Integer seed)
    {
        _seed = seed;
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

    /**
     * Returns a Map<Integer, PlateSet> containing the PlateSet for the given plateSetId as well as all the PlateSets
     * for the descendents of the given plateSetId.
     * @param plateSetId the plateSetId to return with descendents
     * @return Map<Integer, PlateSet>
     */
    @JsonIgnore
    public Map<Integer, PlateSet> getPlateSetAndDescendents(Integer plateSetId)
    {
        Map<Integer, PlateSet> allPlateSets = new HashMap<>();
        allPlateSets.put(plateSetId, _plateSets.get(plateSetId));
        Set<Integer> parents = new HashSet<>(Arrays.asList(plateSetId));

        while (!parents.isEmpty())
        {
            Set<Integer> children = new HashSet<>();

            for (PlateSetEdge edge : _edges)
            {
                if (parents.contains(edge.getFromPlateSetId()))
                {
                    Integer to = edge.getToPlateSetId();
                    children.add(to);
                    allPlateSets.put(to, _plateSets.get(to));
                }
            }

            parents = children;
        }

        return allPlateSets;
    }

    /**
     * Returns a string "lineage path" that expresses all plate sets along the path between the seed
     * plate set and the root plate set for this lineage. This path is the Row IDs of each plate set along the path
     * from root to seed read left to right separated by "/".
     * Example:
     * seed: 19
     * root: 4
     * ancestors: 12, 16
     * path: "/4/12/16/19/"
     */
    public @NotNull String getSeedPath() throws ValidationException
    {
        if (_edges.isEmpty() || _plateSets.isEmpty() || _root == null || _seed.equals(_root))
            return "/" + _seed + "/";

        Integer target = _seed;
        Stack<Integer> stack = new Stack<>();
        stack.push(target);

        while (!target.equals(_root))
        {
            final Integer currentTarget = target;
            Optional<PlateSetEdge> edge = _edges.stream().filter(e -> currentTarget.equals(e.getToPlateSetId())).findFirst();

            if (edge.isEmpty())
                throw new ValidationException(String.format("Failed to find edge to plate set Row ID (%d).", target));

            target = edge.get().getFromPlateSetId();
            stack.push(target);
        }

        StringBuilder path = new StringBuilder("/");
        while (!stack.isEmpty())
            path.append(stack.pop()).append("/");
        return path.toString();
    }
}
