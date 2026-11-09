/*
 * Copyright (c) 2024-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.assay.plate.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.assay.plate.PlateSet;
import org.labkey.api.assay.plate.PlateSetEdge;
import org.labkey.api.collections.LongHashMap;
import org.labkey.api.query.ValidationException;

import java.util.Arrays;
import java.util.Collections;
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
    private Map<Long, PlateSet> _plateSets = Collections.emptyMap();
    private Long _root;
    private final Long _seed;

    public PlateSetLineage(@NotNull Long seed)
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

    public Map<Long, PlateSet> getPlateSets()
    {
        return _plateSets;
    }

    public void setPlateSets(Map<Long, PlateSet> plateSets)
    {
        _plateSets = plateSets;
    }

    public Long getRoot()
    {
        return _root;
    }

    public void setRoot(Long root)
    {
        _root = root;
    }

    public long getSeed()
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
    public Map<Long, PlateSet> getPlateSetAndDescendents(Long plateSetId)
    {
        Map<Long, PlateSet> allPlateSets = new LongHashMap<>();
        allPlateSets.put(plateSetId, _plateSets.get(plateSetId));
        Set<Long> parents = new HashSet<>(Arrays.asList(plateSetId));

        while (!parents.isEmpty())
        {
            Set<Long> children = new HashSet<>();

            for (PlateSetEdge edge : _edges)
            {
                if (parents.contains(edge.getFromPlateSetId()))
                {
                    Long to = edge.getToPlateSetId();
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

        Long target = _seed;
        Stack<Long> stack = new Stack<>();
        stack.push(target);

        while (!target.equals(_root))
        {
            final Long currentTarget = target;
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
