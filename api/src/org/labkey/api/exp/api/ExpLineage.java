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

import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.assay.AssayProtocolSchema;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayService;
import org.labkey.api.exp.Identifiable;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.util.Pair;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

/**
 * Created by Nick Arnold on 2/11/2016.
 */
public class ExpLineage
{
    private Set<Identifiable> _seeds;
    private Set<ExpData> _datas;
    private Set<ExpMaterial> _materials;
    private Set<ExpRun> _runs;
    private Set<Identifiable> _objects;
    private Set<Edge> _edges;

    // constructed in processNodes
    private Map<String, Identifiable> _nodes;
    // constructed in processNodeEdges
    private Map<String, Pair<Set<Edge>, Set<Edge>>> _nodesAndEdges;

    public ExpLineage(Set<Identifiable> seeds, Set<ExpData> data, Set<ExpMaterial> materials, Set<ExpRun> runs, Set<Identifiable> objects, Set<Edge> edges)
    {
        _seeds = seeds;
        _datas = data;
        _materials = materials;
        _runs = runs;
        _objects = objects;
        _edges = edges;
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
     * @return
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
        if (_nodes == null)
        {
            _nodes = new HashMap<>();

            for (Identifiable seed : _seeds)
                _nodes.put(seed.getLSID(), seed);

            for (ExpData node : _datas)
                _nodes.put(node.getLSID(), node);

            for (ExpMaterial node : _materials)
                _nodes.put(node.getLSID(), node);

            for (ExpRun node : _runs)
                _nodes.put(node.getLSID(), node);

            for (Identifiable node : _objects)
                _nodes.put(node.getLSID(), node);
        }

        return _nodes;
    }

    /**
     * Create map from node LSID to the set of (parent, child) edges.
     */
    private Map<String, Pair<Set<Edge>, Set<Edge>>> processNodeEdges()
    {
        if (_nodesAndEdges == null)
        {
            _nodesAndEdges = new HashMap<>();

            for (Edge edge : _edges)
            {
                if (!_nodesAndEdges.containsKey(edge.parent))
                    _nodesAndEdges.put(edge.parent, Pair.of(new HashSet<>(), new HashSet<>()));

                if (!_nodesAndEdges.containsKey(edge.child))
                    _nodesAndEdges.put(edge.child, Pair.of(new HashSet<>(), new HashSet<>()));

                // The edges parent now has a child of edge.second
                if (!edge.parent.equals(edge.child)) // node cannot parent itself
                    _nodesAndEdges.get(edge.parent).second.add(edge);

                // The edges child now has a parent of edge.first
                if (!edge.child.equals(edge.parent)) // node cannot child itself
                    _nodesAndEdges.get(edge.child).first.add(edge);
            }
        }

        return _nodesAndEdges;
    }

    private Pair<Set<ExpLineage.Edge>, Set<ExpLineage.Edge>> nodeEdges(Identifiable node)
    {
        Map<String, Identifiable> nodes = processNodes();
        String nodeLsid = node.getLSID();
        if (!nodes.containsKey(nodeLsid))
            throw new IllegalArgumentException("node not in lineage");

        Map<String, Pair<Set<ExpLineage.Edge>, Set<ExpLineage.Edge>>> edges = processNodeEdges();
        Pair<Set<ExpLineage.Edge>, Set<ExpLineage.Edge>> nodeEdges = edges.get(nodeLsid);
        return nodeEdges;
    }

    /** Get the set of directly connected parents for the node. */
    public Set<Identifiable> getNodeParents(Identifiable node)
    {
        var nodeEdges = nodeEdges(node);
        if (nodeEdges == null)
            return Collections.emptySet();

        Map<String, Identifiable> nodes = processNodes();
        Set<ExpLineage.Edge> inputEdges = nodeEdges.getKey();
        return inputEdges.stream().map(e -> nodes.get(e.parent)).collect(Collectors.toSet());
    }

    /** Get the set of directly connected children for the node. */
    public Set<Identifiable> getNodeChildren(Identifiable node)
    {
        var nodeEdges = nodeEdges(node);
        if (nodeEdges == null)
            return Collections.emptySet();

        Map<String, Identifiable> nodes = processNodes();
        Set<ExpLineage.Edge> outputEdges = nodeEdges.getValue();
        return outputEdges.stream().map(e -> nodes.get(e.child)).collect(Collectors.toSet());
    }

    /**
     * Find all child and grandchild samples that are direct descendants of the input seed,
     * ignoring any sample children derived from ExpData or other Identifiable children.
     */
    public Set<ExpMaterial> findRelatedChildSamples(ExpRunItem seed)
    {
        if (!_seeds.contains(seed))
            throw new UnsupportedOperationException();

        Map<String, Identifiable> nodes = processNodes();
        Map<String, Pair<Set<ExpLineage.Edge>, Set<ExpLineage.Edge>>> edges = processNodeEdges();
        return findRelatedChildSamples(seed, nodes, edges);
    }

    private Set<ExpMaterial> findRelatedChildSamples(ExpRunItem seed, Map<String, Identifiable> nodes, Map<String, Pair<Set<Edge>, Set<Edge>>> edges)
    {
        if (edges.size() == 0)
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
            Set<ExpLineage.Edge> childEdges = edges.containsKey(lsid) ? edges.get(lsid).second : Collections.emptySet();
            for (ExpLineage.Edge edge : childEdges)
            {
                String childLsid = edge.child;
                Identifiable child = nodes.get(childLsid);
                if (child instanceof ExpRun)
                {
                    if (!seen.contains(child))
                    {
                        stack.add(child);
                        seen.add(child);
                    }
                }
                else if (child instanceof ExpMaterial)
                {
                    if (!seen.contains(child))
                    {
                        stack.add(child);
                        seen.add(child);
                    }
                    materials.add((ExpMaterial)child);
                }
            }
        }

        return materials;
    }

    /**
     * Find all parent ExpData that are parents of the seed, stopping at the first parent generation (no grandparents.)
     */
    public Set<ExpData> findNearestParentDatas(ExpRunItem seed)
    {
        if (!_seeds.contains(seed))
            throw new UnsupportedOperationException();

        Map<String, Identifiable> nodes = processNodes();
        Map<String, Pair<Set<ExpLineage.Edge>, Set<ExpLineage.Edge>>> edges = processNodeEdges();
        return findNearestParentDatas(seed, nodes, edges);
    }

    private Set<ExpData> findNearestParentDatas(ExpRunItem seed, Map<String, Identifiable> nodes, Map<String, Pair<Set<Edge>, Set<Edge>>> edges)
    {
        if (edges.size() == 0)
            return Collections.emptySet();

        // walk from start through edges looking for all sample children, stopping at first datas found
        Set<ExpData> datas = new HashSet<>();
        Queue<Identifiable> stack = new LinkedList<>();
        Set<Identifiable> seen = new HashSet<>();
        stack.add(seed);
        seen.add(seed);
        while (!stack.isEmpty())
        {
            Identifiable curr = stack.poll();
            String lsid = curr.getLSID();

            // Gather sample parents
            Set<ExpLineage.Edge> parentEdges = edges.containsKey(lsid) ? edges.get(lsid).first : Collections.emptySet();
            for (ExpLineage.Edge edge : parentEdges)
            {
                String parentLsid = edge.parent;
                Identifiable parent = nodes.get(parentLsid);
                if (parent instanceof ExpRun)
                {
                    if (!seen.contains(parent))
                    {
                        stack.add(parent);
                        seen.add(parent);
                    }
                }
                else if (parent instanceof ExpData)
                {
                    datas.add((ExpData)parent);
                }
                else // ExpMaterial or generic Identifiable
                {
                    if (!seen.contains(parent))
                    {
                        stack.add(parent);
                        seen.add(parent);
                    }
                }
            }
        }

        return datas;
    }

    public JSONObject toJSON(boolean requestedWithSingleSeed)
    {
        Map<String, Identifiable> nodeMeta = processNodes();
        Map<String, Object> values = new HashMap<>();
        JSONObject nodes = new JSONObject();

        if (_edges.isEmpty())
        {
            for (Identifiable seed : _seeds)
            {
                nodes.put(seed.getLSID(), nodeToJSON(seed, new JSONArray(), new JSONArray()));
            }
        }
        else
        {
            final Map<String, Pair<Set<Edge>, Set<Edge>>> edges = processNodeEdges();
            for (Map.Entry<String, Pair<Set<Edge>, Set<Edge>>> node : edges.entrySet())
            {
                JSONArray parents = new JSONArray();
                for (Edge edge : node.getValue().first)
                    parents.put(edge.toParentJSON());

                JSONArray children = new JSONArray();
                for (Edge edge : node.getValue().second)
                    children.put(edge.toChildJSON());

                Identifiable obj = nodeMeta.get(node.getKey());
                nodes.put(node.getKey(), nodeToJSON(obj, parents, children));
            }
        }

        // If the request was made with a single 'seed' property, use single 'seed' property in the response
        // otherwise, include an array of 'seed' regardless of the number of seed items.
        if (requestedWithSingleSeed)
        {
            assert _seeds.size() == 1;
            values.put("seed", _seeds.stream().findFirst().orElseThrow().getLSID());
        }
        else
        {
            values.put("seeds", _seeds.stream().map(Identifiable::getLSID).collect(toList()));
        }
        values.put("nodes", nodes);

        return new JSONObject(values);
    }

    private JSONObject nodeToJSON(Identifiable node, JSONArray parents, JSONArray children)
    {
        JSONObject json = new JSONObject();
        json.put("parents", parents);
        json.put("children", children);

        if (node != null)
        {
            json.put("name", node.getName());
            json.put("lsid", node.getLSID());
            json.put("type", node.getLSIDNamespacePrefix());

            // TODO: get rowId and maybe cpasType and schemaName/queryName for assay result row type

            if (node instanceof ExpObject)
            {
                json.put("rowId", ((ExpObject)node).getRowId());
                json.put("url", ((ExpObject)node).detailsURL());
            }

            if (node instanceof ExpMaterial)
            {
                ExpMaterial material = (ExpMaterial) node;
                json.put("cpasType", material.getCpasType());

                ExpSampleSet ss = material.getSampleSet();
                if (ss != null)
                {
                    json.put("schemaName", SamplesSchema.SCHEMA_NAME);
                    json.put("queryName", ss.getName());
                }
            }
            else if (node instanceof ExpData)
            {
                ExpData data = (ExpData) node;
                json.put("cpasType", data.getCpasType());

                ExpDataClass dc = data.getDataClass(null);
                if (dc != null)
                {
                    json.put("schemaName", "exp.data");
                    json.put("queryName", dc.getName());
                }
            }
            else if (node instanceof ExpRun)
            {
                ExpRun run = (ExpRun)node;

                ExpProtocol protocol = run.getProtocol();
                if (protocol != null)
                {
                    json.put("cpasType", protocol.getLSID());
                    AssayService assayService = AssayService.get();
                    if (assayService != null)
                    {
                        AssayProvider provider = assayService.getProvider(run);
                        if (provider != null)
                        {
                            SchemaKey schemaKey = AssayProtocolSchema.schemaName(provider, protocol);
                            json.put("schemaName", schemaKey.toString());
                            json.put("queryName", "Runs");
                        }
                    }
                }
            }
        }

        return json;
    }

    public static class Edge
    {
        public final String parent;
        public final String child;
        private String _role;

        public Edge(String parentLSID, String childLSID, String role)
        {
            parent = parentLSID;
            child = childLSID;
            _role = role;
        }

        public JSONObject toParentJSON()
        {
            JSONObject json = new JSONObject();
            json.put("lsid", parent);
            json.put("role", _role);
            return json;
        }

        public JSONObject toChildJSON()
        {
            JSONObject json = new JSONObject();
            json.put("lsid", child);
            json.put("role", _role);
            return json;
        }

        @Override
        public int hashCode()
        {
            int result = parent.hashCode();
            result = 31 * result + child.hashCode();
            result = 31 * result + _role.hashCode();
            return result;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Edge edge = (Edge) o;

            if (!parent.equals(edge.parent)) return false;
            if (!child.equals(edge.child)) return false;
            return _role.equals(edge._role);

        }

        @Override
        public String toString()
        {
            return "[" + parent + "] -(" + _role + ")-> [" + child + "]";
        }
    }
}
