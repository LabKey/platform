/*
 * Copyright (c) 2016-2019 LabKey Corporation
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
package org.labkey.api.exp.api;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.exp.Identifiable;
import org.labkey.api.security.User;
import org.labkey.api.util.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

public class ExpLineage
{
    private final Set<Identifiable> _seeds;
    private final Set<ExpData> _datas;
    private final Set<ExpMaterial> _materials;
    private final Set<ExpRun> _runs;
    private final Set<Identifiable> _objects;
    private final Map<String, Identifiable> _nodes;
    private final Map<String, Edges> _nodesAndEdges;

    public ExpLineage(Set<Identifiable> seeds, Set<ExpData> data, Set<ExpMaterial> materials, Set<ExpRun> runs, Set<Identifiable> objects, Set<Edge> edges)
    {
        _seeds = seeds;
        _datas = data;
        _materials = materials;
        _runs = runs;
        _objects = objects;
        _nodes = processNodes();
        _nodesAndEdges = processEdges(edges);
    }

    /**
     * Get the starting seeds for the lineage.
     */
    public Set<Identifiable> getSeeds()
    {
        return _seeds;
    }

    /**
     * Get the ExpData parent or children encountered when traversing the lineage from one of the seeds.
     */
    public Set<ExpData> getDatas()
    {
        return _datas;
    }

    /**
     * Get the ExpMaterial parent or children encountered when traversing the lineage from one of the seeds.
     */
    public Set<ExpMaterial> getMaterials()
    {
        return _materials;
    }

    /**
     * Get the ExpRun parent or children encountered when traversing the lineage from one of the seeds.
     */
    public Set<ExpRun> getRuns()
    {
        return _runs;
    }

    /**
     * Get the non-Material and non-Data parent or children encountered when traversing the lineage from one of the seeds.
     */
    public Set<Identifiable> getObjects()
    {
        return _objects;
    }

    /**
     * Create map from node LSID to ExpObject.
     */
    private Map<String, Identifiable> processNodes()
    {
        var nodes = new HashMap<String, Identifiable>();

        for (Identifiable seed : _seeds)
            nodes.put(seed.getLSID(), seed);

        for (ExpData node : _datas)
            nodes.put(node.getLSID(), node);

        for (ExpMaterial node : _materials)
            nodes.put(node.getLSID(), node);

        for (ExpRun node : _runs)
            nodes.put(node.getLSID(), node);

        for (Identifiable node : _objects)
            nodes.put(node.getLSID(), node);

        return nodes;
    }

    public record Edges(Set<Edge> parents, Set<Edge> children)
    {
        public static Edges emptyEdges = new Edges(Collections.emptySet(), Collections.emptySet());

        public Edges()
        {
            this(new HashSet<>(), new HashSet<>());
        }
    }

    /**
     * Create map from node LSID to the set of (parent, child) edges.
     */
    public static Map<String, Edges> processEdges(Set<ExpLineage.Edge> edges)
    {
        var processedEdges = new HashMap<String, Edges>();

        for (var edge : edges)
        {
            processedEdges.computeIfAbsent(edge.parent, (r) -> new Edges());
            processedEdges.computeIfAbsent(edge.child, (r) -> new Edges());

            // The edges parent now has a child of edge.second
            if (!edge.parent.equals(edge.child)) // node cannot be a parent of itself
                processedEdges.get(edge.parent).children.add(edge);

            // The edges child now has a parent of edge.first
            if (!edge.child.equals(edge.parent)) // node cannot be a child of itself
                processedEdges.get(edge.child).parents.add(edge);
        }

        return processedEdges;
    }

    @Nullable
    private Edges nodeEdges(@NotNull Identifiable node)
    {
        String nodeLsid = node.getLSID();
        if (!_nodes.containsKey(nodeLsid))
            throw new IllegalArgumentException("node not in lineage");

        return _nodesAndEdges.get(nodeLsid);
    }

    /** Get the set of directly connected parents for the node. */
    public Set<Identifiable> getNodeParents(Identifiable node)
    {
        var nodeEdges = nodeEdges(node);
        if (nodeEdges == null)
            return Collections.emptySet();

        return nodeEdges.parents.stream().map(e -> _nodes.get(e.parent)).collect(Collectors.toSet());
    }

    /** Get the set of directly connected children for the node. */
    public Set<Identifiable> getNodeChildren(Identifiable node)
    {
        var nodeEdges = nodeEdges(node);
        if (nodeEdges == null)
            return Collections.emptySet();

        return nodeEdges.children.stream().map(e -> _nodes.get(e.child)).collect(Collectors.toSet());
    }

    public Set<Identifiable> findAncestorObjects(ExpRunItem seed, List<Pair<ExpLineageOptions.LineageExpType, String>> ancestorPaths, User user)
    {
        if (!_seeds.contains(seed))
            throw new UnsupportedOperationException();

        ExpLineageTree ancestorTree = ExpLineageTree.getAncestorTree(this, seed);
        return ExpLineageTree.getNodes(ancestorTree, ancestorPaths, user);
    }

    public List<ExpRunItem> findAncestorByType(ExpRunItem seed, @NotNull Pair<ExpLineageOptions.LineageExpType, String> ancestorType, User user)
    {
        if (!_seeds.contains(seed))
            throw new UnsupportedOperationException();

        ExpLineageTree ancestorTree = ExpLineageTree.getAncestorTree(this, seed);
        return ExpLineageTree.getNodes(ancestorTree, ancestorType, user);
    }

    /**
     * Find all child and grandchild samples that are direct descendants of the input seed,
     * ignoring any sample children derived from ExpData or other Identifiable children.
     */
    public Set<ExpMaterial> findRelatedChildSamples(ExpRunItem seed)
    {
        if (!_seeds.contains(seed))
            throw new UnsupportedOperationException();

        return findRelatedChildSamples(seed, _nodes);
    }

    private Set<ExpMaterial> findRelatedChildSamples(ExpRunItem seed, Map<String, Identifiable> nodes)
    {
        if (_nodesAndEdges.isEmpty())
            return Collections.emptySet();

        // walk from start through edges looking for all sample children, ignoring data children
        Set<ExpMaterial> materials = new HashSet<>();
        Queue<Identifiable> stack = new LinkedList<>();
        Set<Identifiable> seen = new HashSet<>();
        stack.add(seed);
        seen.add(seed);
        while (!stack.isEmpty())
        {
            Identifiable curr = stack.poll();
            String lsid = curr.getLSID();

            // Gather sample children
            Set<ExpLineage.Edge> childEdges = _nodesAndEdges.containsKey(lsid) ? _nodesAndEdges.get(lsid).children : Collections.emptySet();
            for (ExpLineage.Edge edge : childEdges)
            {
                Identifiable child = nodes.get(edge.child);
                if (child instanceof ExpRun run)
                {
                    if (!seen.contains(run))
                    {
                        stack.add(run);
                        seen.add(run);
                    }
                }
                else if (child instanceof ExpMaterial material)
                {
                    if (!seen.contains(material))
                    {
                        stack.add(material);
                        seen.add(material);
                    }
                    materials.add(material);
                }
            }
        }

        return materials;
    }

    /**
     * Find all ExpData children of the seed, stopping at the first child generation.
     */
    public @NotNull Set<ExpData> findNearestChildDatas(Identifiable seed)
    {
        return findNearestChildren(ExpData.class, seed);
    }

    /**
     * Find all ExpMaterial children of the seed, stopping at the first child generation.
     */
    public @NotNull Set<ExpMaterial> findNearestChildMaterials(Identifiable seed)
    {
        return findNearestChildren(ExpMaterial.class, seed);
    }

    /**
     * Find all ExpData or ExpMaterial children of the seed, stopping at the first child generation.
     */
    public @NotNull <T extends ExpRunItem> Set<T> findNearestChildren(Class<T> parentClazz, Identifiable seed)
    {
        return findNearest(false, parentClazz, null, seed, false);
    }

    /**
     * Find all ExpData parents of the seed, stopping at the first parent generation.
     */
    public @NotNull Set<ExpData> findNearestParentDatas(Identifiable seed)
    {
        return findNearestParents(ExpData.class, seed);
    }

    /**
     * Find all ExpMaterial parents of the seed, stopping at the first parent generation.
     */
    public @NotNull Set<ExpMaterial> findNearestParentMaterials(Identifiable seed)
    {
        return findNearestParents(ExpMaterial.class, seed);
    }

    /**
     * Find all ExpData or ExpMaterial parents of the seed, stopping at the first parent generation.
     */
    public @NotNull <T extends ExpRunItem> Set<T> findNearestParentMaterialsAndDatas(Identifiable seed)
    {
        return findNearest(true, null, null, seed, true);
    }

    /**
     * Find all ExpData or ExpMaterial parents of the seed, stopping at the first parent generation.
     */
    public @NotNull <T extends ExpRunItem> Set<T> findNearestParents(Class<T> parentClazz, Identifiable seed)
    {
        return findNearest(true, parentClazz, null, seed, false);
    }

    /**
     * Find all parents, of the given cpasType, of the seed, stopping at the first parent generation.
     */
    public @NotNull <T extends ExpRunItem> Set<T> findNearestParents(String cpasType, Identifiable seed)
    {
        return findNearest(true, null, cpasType, seed, false);
    }

    /**
     * Finds all children or parents of the given seed where those children or parents are of a specific type,
     * stopping at the first generation. The specific type is either a class (which extends ExpRunItem) or a
     * cpasType (SampleType or DataClass).
     */
    private @NotNull <T extends ExpRunItem> Set<T> findNearest(
        boolean findParents,
        @Nullable Class<T> clazz,
        @Nullable String cpasType,
        Identifiable seed,
        boolean findBothMaterialAndData
    )
    {
        assert cpasType != null || clazz == ExpMaterial.class || clazz == ExpData.class || findBothMaterialAndData;

        if (_nodesAndEdges.isEmpty())
            return Collections.emptySet();

        // walk from start through edges looking for all nearest nodes, stopping at first ones found
        Set<T> nearest = new HashSet<>();
        Queue<Identifiable> stack = new LinkedList<>();
        Set<Identifiable> seen = new HashSet<>();
        stack.add(seed);
        seen.add(seed);
        while (!stack.isEmpty())
        {
            Identifiable curr = stack.poll();
            String lsid = curr.getLSID();

            if (!_nodesAndEdges.containsKey(lsid))
                continue;

            Set<ExpLineage.Edge> edges;
            if (findParents)
                edges = _nodesAndEdges.get(lsid).parents;
            else
                edges = _nodesAndEdges.get(lsid).children;

            for (ExpLineage.Edge edge : edges)
            {
                String targetLsid = findParents ? edge.parent : edge.child;
                Identifiable target = _nodes.get(targetLsid);
                if (target instanceof ExpRun)
                {
                    if (!seen.contains(target))
                    {
                        stack.add(target);
                        seen.add(target);
                    }
                }
                else if (cpasType != null && target instanceof ExpRunItem item && cpasType.equals(item.getCpasType()))
                {
                    nearest.add((T) target);
                }
                else if ((clazz == ExpMaterial.class && target instanceof ExpMaterial) ||
                         (clazz == ExpData.class && target instanceof ExpData) ||
                        (findBothMaterialAndData && (target instanceof ExpMaterial || target instanceof ExpData)))
                {
                    nearest.add((T) target);
                }
                else // ExpMaterial or generic Identifiable
                {
                    if (!seen.contains(target))
                    {
                        stack.add(target);
                        seen.add(target);
                    }
                }
            }
        }

        return nearest;
    }

    public record Edge(String parent, String child)
    {
        private static final String NO_ROLE = "no role";

        public JSONObject toParentJSON()
        {
            return toJSON(parent);
        }

        public JSONObject toChildJSON()
        {
            return toJSON(child);
        }

        private JSONObject toJSON(String target)
        {
            JSONObject json = new JSONObject();
            json.put("lsid", target);
            json.put("role", NO_ROLE);
            return json;
        }

        @Override
        public String toString()
        {
            return "[" + parent + "] -(" + NO_ROLE + ")-> [" + child + "]";
        }
    }

    public static class ExpLineageTree
    {
        private final Identifiable _expObject;
        private final @NotNull List<ExpLineageTree> _children = new LinkedList<>();

        public Identifiable getExpObject()
        {
            return _expObject;
        }

        public @NotNull List<ExpLineageTree> getChildren()
        {
            return _children;
        }

        private ExpLineageTree(Identifiable expObject, List<ExpLineageTree> children)
        {
            _expObject = expObject;
            if (children != null)
                _children.addAll(children);
        }

        public static ExpLineageTree getAncestorTree(ExpLineage lineage, Identifiable seed)
        {
            Set<Identifiable> parentRuns = lineage.getNodeParents(seed);
            List<ExpLineageTree> children = new LinkedList<>();
            for (Identifiable run : parentRuns)
            {
                Set<Identifiable> parents = lineage.getNodeParents(run);
                for (Identifiable parent : parents)
                {
                    if (parent != null)
                        children.add(getAncestorTree(lineage, parent));
                }
            }

            return new ExpLineageTree(seed, children);
        }

        private static boolean isValidNode(Identifiable expObject, ExpLineageOptions.LineageExpType expType, String cpas, User user)
        {
            boolean isValidNode = false;

            switch (expType)
            {
                case Material:
                    if (expObject instanceof ExpMaterial material)
                    {
                        if (StringUtils.isEmpty(cpas))
                            isValidNode = true;
                        else
                        {
                            ExpSampleType sampleType = material.getSampleType();
                            if (sampleType != null)
                                isValidNode = sampleType.getLSID().equals(cpas);
                        }
                    }
                    break;
                case Data:
                    if (expObject instanceof ExpData data)
                    {
                        if (StringUtils.isEmpty(cpas))
                            isValidNode = true;
                        else
                        {
                            ExpDataClass dataClass = data.getDataClass(user);
                            if (dataClass != null)
                                isValidNode = dataClass.getLSID().equals(cpas);
                        }
                    }
                    break;
                default:
                    break;
            }

            return isValidNode;
        }

        public static Set<Identifiable> getNodes(ExpLineageTree tree, List<Pair<ExpLineageOptions.LineageExpType, String>> ancestorPaths, User user)
        {
            Set<Identifiable> targetNodes = new HashSet<>();
            if (ancestorPaths == null || ancestorPaths.isEmpty())
            {
                targetNodes.add(tree.getExpObject());
                return targetNodes;
            }

            Pair<ExpLineageOptions.LineageExpType, String> ancestorPath = ancestorPaths.get(0);
            ExpLineageOptions.LineageExpType expType = ancestorPath.first;
            String cpas = ancestorPath.second;

            for (ExpLineageTree child : tree.getChildren())
            {
                if (isValidNode(child.getExpObject(), expType, cpas, user))
                    targetNodes.addAll(getNodes(child, ancestorPaths.subList(1, ancestorPaths.size()), user));
            }

            return targetNodes;
        }

        public static List<ExpRunItem> getNodes(ExpLineageTree tree, @NotNull Pair<ExpLineageOptions.LineageExpType, String> ancestorType, User user)
        {
            ExpLineageOptions.LineageExpType expType = ancestorType.first;
            String cpas = ancestorType.second;

            List<ExpRunItem> targetNodes = new ArrayList<>();
            if (isValidNode(tree.getExpObject(), expType, cpas, user))
                targetNodes.add((ExpRunItem) tree.getExpObject());

            for (ExpLineageTree child : tree.getChildren())
                targetNodes.addAll(getNodes(child, ancestorType, user));

            return targetNodes;
        }
    }
}
