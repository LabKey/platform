/*
 * Copyright (c) 2016-2017 LabKey Corporation
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
import org.labkey.api.util.Pair;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Created by Nick Arnold on 2/11/2016.
 */
public class ExpLineage
{
    private ExpProtocolOutput _seed;
    private Set<ExpData> _datas;
    private Set<ExpMaterial> _materials;
    private Set<ExpRun> _runs;
    private Set<Edge> _edges;

    public ExpLineage(ExpProtocolOutput seed)
    {
        this(seed, Collections.emptySet(), Collections.emptySet(), Collections.emptySet(), Collections.emptySet());
    }

    public ExpLineage(ExpProtocolOutput seed, Set<ExpData> data, Set<ExpMaterial> materials, Set<ExpRun> runs, Set<Edge> edges)
    {
        _seed = seed;
        _datas = data;
        _materials = materials;
        _runs = runs;
        _edges = edges;
    }

    public Set<ExpData> getDatas()
    {
        return _datas;
    }

    public Set<ExpMaterial> getMaterials()
    {
        return _materials;
    }

    /**
     * Create map from node LSID to ExpObject.
     */
    private Map<String, ExpObject> processNodes()
    {
        Map<String, ExpObject> nodes = new HashMap<>();

        nodes.put(_seed.getLSID(), _seed);

        for (ExpObject node : _datas)
            nodes.put(node.getLSID(), node);

        for (ExpObject node : _materials)
            nodes.put(node.getLSID(), node);

        for (ExpObject node : _runs)
            nodes.put(node.getLSID(), node);

        return nodes;
    }

    /**
     * Create map from node LSID to the set of (parent, child) edges.
     */
    private Map<String, Pair<Set<Edge>, Set<Edge>>> processEdges()
    {
        Map<String, Pair<Set<Edge>, Set<Edge>>> nodes = new HashMap<>();

        for (Edge edge : _edges)
        {
            if (!nodes.containsKey(edge.parent))
                nodes.put(edge.parent, Pair.of(new HashSet<>(), new HashSet<>()));

            if (!nodes.containsKey(edge.child))
                nodes.put(edge.child, Pair.of(new HashSet<>(), new HashSet<>()));

            // The edges parent now has a child of edge.second
            if (!edge.parent.equals(edge.child)) // node cannot parent itself
                nodes.get(edge.parent).second.add(edge);

            // The edges child now has a parent of edge.first
            if (!edge.child.equals(edge.parent)) // node cannot child itself
                nodes.get(edge.child).first.add(edge);
        }

        return nodes;
    }

    /**
     * Find all child and grandchild samples Samples that are direct descendants of the input seed,
     * ignoring any sample children derived from ExpData children.
     */
    public Set<ExpMaterial> findRelatedChildSamples()
    {
        Map<String, ExpObject> nodes = processNodes();
        Map<String, Pair<Set<ExpLineage.Edge>, Set<ExpLineage.Edge>>> edges = processEdges();
        return findRelatedChildSamples(_seed, nodes, edges);
    }

    private Set<ExpMaterial> findRelatedChildSamples(ExpProtocolOutput seed, Map<String, ExpObject> nodes, Map<String, Pair<Set<Edge>, Set<Edge>>> edges)
    {
        if (edges.size() == 0)
            return Collections.emptySet();

        // walk from start through edges looking for all sample children, ignoring data children
        Set<ExpMaterial> materials = new HashSet<>();
        Queue<ExpObject> stack = new LinkedList<>();
        Set<ExpObject> seen = new HashSet<>();
        stack.add(seed);
        seen.add(seed);
        while (!stack.isEmpty())
        {
            ExpObject curr = stack.poll();
            String lsid = curr.getLSID();

            // Gather sample children
            Set<ExpLineage.Edge> childEdges = edges.containsKey(lsid) ? edges.get(lsid).second : Collections.emptySet();
            for (ExpLineage.Edge edge : childEdges)
            {
                String childLsid = edge.child;
                ExpObject child = nodes.get(childLsid);
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
    public Set<ExpData> findNearestParentDatas()
    {
        Map<String, ExpObject> nodes = processNodes();
        Map<String, Pair<Set<ExpLineage.Edge>, Set<ExpLineage.Edge>>> edges = processEdges();
        return findNearestParentDatas(_seed, nodes, edges);
    }

    private Set<ExpData> findNearestParentDatas(ExpProtocolOutput seed, Map<String, ExpObject> nodes, Map<String, Pair<Set<Edge>, Set<Edge>>> edges)
    {
        if (edges.size() == 0)
            return Collections.emptySet();

        // walk from start through edges looking for all sample children, stopping at first datas found
        Set<ExpData> datas = new HashSet<>();
        Queue<ExpObject> stack = new LinkedList<>();
        Set<ExpObject> seen = new HashSet<>();
        stack.add(seed);
        seen.add(seed);
        while (!stack.isEmpty())
        {
            ExpObject curr = stack.poll();
            String lsid = curr.getLSID();

            // Gather sample parents
            Set<ExpLineage.Edge> parentEdges = edges.containsKey(lsid) ? edges.get(lsid).first : Collections.emptySet();
            for (ExpLineage.Edge edge : parentEdges)
            {
                String parentLsid = edge.parent;
                ExpObject parent = nodes.get(parentLsid);
                if (parent instanceof ExpRun)
                {
                    if (!seen.contains(parent))
                    {
                        stack.add(parent);
                        seen.add(parent);
                    }
                }
                else if (parent instanceof ExpMaterial)
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
            }
        }

        return datas;
    }

    public JSONObject toJSON()
    {
        Map<String, ExpObject> nodeMeta = processNodes();
        Map<String, Object> values = new HashMap<>();
        JSONObject nodes = new JSONObject();

        if (_edges.isEmpty())
        {
            // just publish the seed
            nodes.put(_seed.getLSID(), publishNode(_seed, new JSONArray(), new JSONArray(), false, false));
        }
        else
        {
            final Map<String, Pair<Set<Edge>, Set<Edge>>> edges = processEdges();
            final Set<ExpMaterial> relatedChildSamples = findRelatedChildSamples(_seed, nodeMeta, edges);
            final Set<ExpData> nearestParentDatas = findNearestParentDatas(_seed, nodeMeta, edges);

            for (Map.Entry<String, Pair<Set<Edge>, Set<Edge>>> node : edges.entrySet())
            {
                JSONArray parents = new JSONArray();
                for (Edge edge : node.getValue().first)
                    parents.put(edge.toParentJSON());

                JSONArray children = new JSONArray();
                for (Edge edge : node.getValue().second)
                    children.put(edge.toChildJSON());

                ExpObject expObject = nodeMeta.get(node.getKey());
                boolean isRelatedSample = expObject instanceof ExpMaterial && relatedChildSamples.contains(expObject);
                boolean isNearestParentData = expObject instanceof ExpData && nearestParentDatas.contains(expObject);
                nodes.put(node.getKey(), publishNode(expObject, parents, children, isRelatedSample, isNearestParentData));
            }
        }

        values.put("seed", _seed.getLSID());
        values.put("nodes", nodes);

        return new JSONObject(values);
    }

    private JSONObject publishNode(ExpObject expObject, JSONArray parents, JSONArray children, boolean relatedChildSample, boolean nearestParentData)
    {
        JSONObject json = new JSONObject();

        json.put("parents", parents);
        json.put("children", children);

        if (expObject != null)
        {
            String cpasType = null;
            if (expObject instanceof ExpProtocolOutput)
            {
                cpasType = ((ExpProtocolOutput) expObject).getCpasType();
            }
            else if (expObject instanceof ExpRun)
            {
                cpasType = ((ExpRun)expObject).getProtocol().getLSID();
            }

            json.put("name", expObject.getName());
            json.put("url", expObject.detailsURL());
            json.put("rowId", expObject.getRowId());
            json.put("type", expObject.getLSIDNamespacePrefix());
            json.put("cpasType", cpasType);
            // EXPERIMENTAL: Include the relatedChildSample and nearestParentData in the lineage response.
            // CONSIDER: Maybe "relatedChildSample" should be "relatedChildSampleOf" with a value of the seed's LSID.  Same for "nearestParentData"
            if (expObject instanceof ExpMaterial)
                json.put("relatedChildSample", relatedChildSample);
            if (expObject instanceof ExpData)
                json.put("nearestParentData", nearestParentData);
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
