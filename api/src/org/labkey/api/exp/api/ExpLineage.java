package org.labkey.api.exp.api;

import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.util.Pair;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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

    public JSONObject toJSON()
    {
        Map<String, ExpObject> nodeMeta = processNodes();
        Map<String, Object> values = new HashMap<>();
        JSONObject nodes = new JSONObject();

        if (_edges.isEmpty())
        {
            // just publish the seed
            nodes.put(_seed.getLSID(), publishNode(_seed, new JSONArray(), new JSONArray()));
        }
        else
        {
            for (Map.Entry<String, Pair<Set<Edge>, Set<Edge>>> node : processEdges().entrySet())
            {
                JSONArray parents = new JSONArray();
                for (Edge edge : node.getValue().first)
                    parents.put(edge.toParentJSON());

                JSONArray children = new JSONArray();
                for (Edge edge : node.getValue().second)
                    children.put(edge.toChildJSON());

                nodes.put(node.getKey(), publishNode(nodeMeta.get(node.getKey()), parents, children));
            }
        }

        values.put("seed", _seed.getLSID());
        values.put("nodes", nodes);

        return new JSONObject(values);
    }

    private JSONObject publishNode(ExpObject expObject, JSONArray parents, JSONArray children)
    {
        JSONObject json = new JSONObject();

        json.put("parents", parents);
        json.put("children", children);

        if (expObject != null)
        {
            json.put("name", expObject.getName());
            json.put("url", expObject.detailsURL());
            json.put("rowId", expObject.getRowId());

            // TODO: Replace this casting with inclusion of cpastype for DataClasses
            String dataClass = null;
            if (expObject instanceof ExpData)
            {
                ExpData data = (ExpData) expObject;

                if (data.getDataClass() != null)
                    dataClass = data.getDataClass().getLSID();
            }

            json.put("type", expObject.getLSIDNamespacePrefix());
            json.put("dataClass", dataClass);
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
    }
}
