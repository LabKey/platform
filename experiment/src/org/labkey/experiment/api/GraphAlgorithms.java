/*
 * Copyright (c) 2019 LabKey Corporation
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
package org.labkey.experiment.api;

import org.apache.commons.collections4.multimap.HashSetValuedHashMap;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.util.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class GraphAlgorithms <NodeName extends Comparable>
{
    private class Graph
    {
        Map<NodeName, AtomicInteger> in_count = new TreeMap<>();
        HashSetValuedHashMap<NodeName, NodeName> out_edges = new HashSetValuedHashMap<>();

        private void ensureNode(NodeName name)
        {
            if (!in_count.containsKey(name))
                in_count.put(name, new AtomicInteger(0));
        }

        void addEdge(Pair<NodeName, NodeName> edge)
        {
            ensureNode(edge.first);
            ensureNode(edge.second);
            out_edges.put(edge.first, edge.second);
            in_count.get(edge.second).incrementAndGet();
        }

        int getNodeCount()
        {
            return in_count.size();
        }

        int getIncomingEdgeCount(NodeName name)
        {
            AtomicInteger I = in_count.get(name);
            return null == I ? 0 : I.get();
        }

        void removeNode(NodeName a, @Nullable LinkedList<NodeName> q)
        {
            assert (getIncomingEdgeCount(a) == 0);
            var out = out_edges.get(a);
            if (null != out)
            {
                out.forEach(b ->
                {
                    if (0 == in_count.get(b).decrementAndGet())
                        if (null != q)
                            q.add(b);
                });
            }
            out_edges.remove(a);
            in_count.remove(a);
        }

        Collection<NodeName> getNodes()
        {
            return new ArrayList<>(in_count.keySet());
        }

        Collection<NodeName> getNodes(Predicate<NodeName> pred)
        {
            return in_count.keySet().stream().filter(pred).collect(Collectors.toList());
        }

        Set<Pair<NodeName, NodeName>> getEdges()
        {
            return getEdges(e->true);
        }

        Set<Pair<NodeName,NodeName>> getEdges(Predicate<Pair<NodeName,NodeName>> pred)
        {
            Set<Pair<NodeName,NodeName>> ret = new HashSet<>();
            in_count.keySet().forEach(a -> {
                var out = out_edges.get(a);
                if (null != out)
                {
                    out.stream().map(b -> new Pair<>(a,b))
                            .filter(pred)
                            .forEach(ret::add);
                }
            });
            return ret;
        }

        void dump()
        {
            getEdges(e -> {System.out.println("(" + e.first + "," + e.second + ")"); return true;});
        }
    }

    public Collection<Pair<NodeName,NodeName>> detectCycleInDirectedGraph(Collection<Pair<NodeName,NodeName>> edges)
    {
        Graph graph = new Graph();

        // create directed graph (non-symmetric edges)
        edges.forEach(graph::addEdge);

        LinkedList<NodeName> q = new LinkedList<>();

        // find nodes that have no incoming edges
        graph.getNodes().stream().filter(n -> graph.getIncomingEdgeCount(n) == 0)
            .forEach(q::add);

        while (!q.isEmpty())
        {
            NodeName n = q.removeFirst();
            graph.removeNode(n, q);
        }

        if (0 == graph.getNodeCount())
            return Collections.emptyList();

        // return all edges that participate in some cycle (there may be more than one disjoint cycle/circuit)
        Set<Pair<NodeName,NodeName>> set = graph.getEdges(edge->true);
        graph.dump();
        return set;
    }

    public static class TestCase extends Assert
    {
        private Pair<String,String> p(String a, String b)
        {
            return new Pair<>(a,b);
        }

        @Test
        public void no_cycle()
        {
            var edges = Arrays.asList(p("a","b"), p("b","c"), p("c","e"), p("d", "e"));
            var cycle = (new GraphAlgorithms<String>()).detectCycleInDirectedGraph(edges);
            assertTrue(cycle.isEmpty());
        }

        @Test
        public void cycle()
        {
            var edges = Arrays.asList(p("a","b"), p("b","c"), p("c","e"), p("d", "e"), p("e", "a"));
            var cycle = (new GraphAlgorithms<String>()).detectCycleInDirectedGraph(edges);
            assertFalse(cycle.isEmpty());
        }
    }
}
