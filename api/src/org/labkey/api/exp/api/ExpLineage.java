package org.labkey.api.exp.api;

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
    private Set<Pair<String, String>> _edges;

    public ExpLineage(ExpProtocolOutput seed)
    {
        this(seed, Collections.emptySet(), Collections.emptySet(), Collections.emptySet());
    }

    public ExpLineage(ExpProtocolOutput seed, Set<ExpData> data, Set<ExpMaterial> materials, Set<Pair<String, String>> edges)
    {
        _seed = seed;
        _datas = data;
        _materials = materials;
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

    private Map<String, Pair<Set<String>, Set<String>>> processEdges(Set<Pair<String, String>> edges) throws IllegalStateException
    {
        Map<String, Pair<Set<String>, Set<String>>> nodes = new HashMap<>();

        // TODO: Replace this prefix filtering with something more robust and that captures all non-data/non-material types
        // This loop is used solely to remap relationships. Edges can contain non-data/non-material types so this loop
        // "looks through" those types to resolve parent/child relationships of datas/materials.
        // Input: MatA -> Run1 -> MatB
        // Output: MatA -> MatB
        String prefix = "urn:lsid:labkey.com:Run.Folder";
        Map<String, Pair<Set<String>, Set<String>>> invalidEdges = new HashMap<>();
        for (Pair<String, String> edge : edges)
        {
            if (edge.first.equals(edge.second))
                continue;

            // an edge where the parent is invalid
            if (edge.first.indexOf(prefix) == 0)
            {
                if (!invalidEdges.containsKey(edge.first))
                    invalidEdges.put(edge.first, Pair.of(new HashSet<>(), new HashSet<>()));

                invalidEdges.get(edge.first).second.add(edge.second);
            }

            // an edge where the child is invalid
            if (edge.second.indexOf(prefix) == 0)
            {
                if (!invalidEdges.containsKey(edge.second))
                    invalidEdges.put(edge.second, Pair.of(new HashSet<>(), new HashSet<>()));

                invalidEdges.get(edge.second).first.add(edge.first);
            }
        }

        for (Pair<String, String> edge : edges)
        {
            if (!invalidEdges.containsKey(edge.first) && !nodes.containsKey(edge.first))
                nodes.put(edge.first, Pair.of(new HashSet<>(), new HashSet<>()));

            if (!invalidEdges.containsKey(edge.second) && !nodes.containsKey(edge.second))
                nodes.put(edge.second, Pair.of(new HashSet<>(), new HashSet<>()));

            // The edges parent now has a child of edge.second
            if (!invalidEdges.containsKey(edge.first) && !edge.first.equals(edge.second)) // node cannot parent itself
            {
                if (invalidEdges.containsKey(edge.second))
                    nodes.get(edge.first).second.addAll(invalidEdges.get(edge.second).second);
                else
                    nodes.get(edge.first).second.add(edge.second);
            }

            // The edges child now has a parent of edge.first
            if (!invalidEdges.containsKey(edge.second) && !edge.second.equals(edge.first)) // node cannot child itself
            {
                if (invalidEdges.containsKey(edge.first))
                    nodes.get(edge.second).first.addAll(invalidEdges.get(edge.first).first);
                else
                    nodes.get(edge.second).first.add(edge.first);
            }
        }

        return nodes;
    }

    public JSONObject toJSON()
    {
        Map<String, Object> values = new HashMap<>();

        JSONObject nodes = new JSONObject();
        Map<String, Object> json;

        for (Map.Entry<String, Pair<Set<String>, Set<String>>> node : processEdges(_edges).entrySet())
        {
            json = new JSONObject();
            json.put("parents", node.getValue().first.toArray());
            json.put("children", node.getValue().second.toArray());

            nodes.put(node.getKey(), json);
        }

//        for (ExpData output : getDatas())
//        {
//            json = new HashMap<>();
//            json.put("dataClass", output.getDataClass());
//            json.put("LSID", output.getLSID());
//            json.put("created", output.getCreated());
//            json.put("createdBy", output.getCreatedBy());
//            json.put("modified", output.getModified());
//            json.put("modifiedBy", output.getModifiedBy());
//
//            nodes.put(output.getLSID(), json);
//        }
//
//        for (ExpMaterial output : getMaterials())
//        {
//            json = new HashMap<>();
//            json.put("LSID", output.getLSID());
//            json.put("created", output.getCreated());
//            json.put("createdBy", output.getCreatedBy());
//            json.put("modified", output.getModified());
//            json.put("modifiedBy", output.getModifiedBy());
//
//            nodes.put(output.getLSID(), json);
//        }

        values.put("seed", _seed.getLSID());
        values.put("nodes", nodes);

        return new JSONObject(values);
    }
}
