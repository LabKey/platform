/*
 * Copyright (c) 2005-2014 Fred Hutchinson Cancer Research Center
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
package org.labkey.experiment;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.view.ActionURL;
import org.labkey.api.util.PageFlowUtil;
import org.apache.commons.lang3.StringUtils;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpProtocolApplication;
import org.labkey.api.exp.api.ExpData;

import java.util.*;
import java.io.PrintWriter;

/**
 * User: migra
 * Date: Jun 13, 2005
 * Time: 1:01:14 PM
 */
public class DotGraph
{
    PrintWriter pwOut;
    ActionURL urlBase;
    private static final String GROUP_ID_PREFIX = "Grp";
    private static final String PROTOCOLAPP_COLOR = "#F7F7A3";
    private static final String MATERIAL_COLOR = "#FFCC99";
    private static final String DATA_COLOR = "#BBE3E3";
    private static final String EXPRUN_COLOR = "#FF7F50";
    private static final String LINKEDRUN_COLOR = "#CDB79E";
    private static final String GROUP_COLOR = "#C0C0C0";
    private static final String LABEL_FONT = "helvetica";
    private static final int LABEL_DEFAULT_FONTSIZE = 30;
    private static final int LABEL_SMALL_FONTSIZE = 24;
    private static final int LABEL_CHAR_WIDTH = 20;

    SortedMap<Integer, DotNode> pendingMNodes;
    SortedMap<Integer, DotNode> pendingDNodes;
    SortedMap<Integer, DotNode> pendingProcNodes;
    Map<Integer, GroupedNode> groupMNodes;
    Map<Integer, GroupedNode> groupDNodes;
    Map<Integer, GroupedNode> groupPANodes;
    Map<Integer, DotNode> writtenMNodes;
    Map<Integer, DotNode> writtenDNodes;
    Map<Integer, DotNode> writtenProcNodes;
    Set<String> pendingConnects;
    Set<String> writtenConnects;
    Integer focusId = null;
    String objectType = null;

    // following are used in URLs to generate lineage graphs
    public static String TYPECODE_MATERIAL = "M";
    public static String TYPECODE_DATA = "D";
    public static String TYPECODE_PROT_APP = "A";

    public DotGraph(PrintWriter out, ActionURL url, boolean bSmallFonts)
    {
        pwOut = out;
        urlBase = url;
        pendingMNodes = new TreeMap<>();
        pendingDNodes = new TreeMap<>();
        pendingProcNodes = new TreeMap<>();
        groupMNodes = new HashMap<>();
        groupDNodes = new HashMap<>();
        groupPANodes = new HashMap<>();
        writtenMNodes = new HashMap<>();
        writtenDNodes = new HashMap<>();
        writtenProcNodes = new HashMap<>();
        pendingConnects = new HashSet<>();
        writtenConnects = new HashSet<>();

        if (bSmallFonts)
            pwOut.println("digraph G { node[fontname=\"" + LABEL_FONT + "\" fontsize=" + LABEL_SMALL_FONTSIZE + "]");
        else
            pwOut.println("digraph G { node[fontname=\"" + LABEL_FONT + "\" fontsize=" + LABEL_DEFAULT_FONTSIZE + "]");
    }

    public void setFocus(Integer focusid, String objtype)
    {
        focusId = focusid;
        objectType = objtype;
    }

    public void dispose()
    {
        flushPending();
        pwOut.println("}");
        pwOut = null;
    }

    public Integer getPAGroupId(Integer rowIdPA)
    {
        return getGroupId(rowIdPA, pendingProcNodes, writtenProcNodes);
    }

    public Integer getMGroupId(Integer rowIdM)
    {
        return getGroupId(rowIdM, pendingMNodes, writtenMNodes);
    }

    public Integer getDGroupId(Integer rowIdD)
    {
        return getGroupId(rowIdD, pendingDNodes, writtenDNodes);
    }

    public Integer getGroupId(Integer rowId, Map<Integer, DotNode> pendingNodes, Map<Integer, DotNode> writtenNodes)
    {
        DotNode node = null;
        if (pendingNodes.containsKey(rowId))
            node = pendingNodes.get(rowId);
        else if (writtenNodes.containsKey(rowId))
            node = writtenNodes.get(rowId);
        if (null == node)
            return null;
        if (node instanceof GroupedNode)
            return ((GroupedNode) node).gid;

        return null;
    }

    public void addStartingMaterial (ExpMaterial m, Integer groupId, Integer actionseq, Integer runId)
    {
        DotNode node = new MNode(m);
        node.setLink("resolveLSID", "type=material&lsid=" + PageFlowUtil.encode(m.getLSID()));

        if (null != focusId && objectType.equals(TYPECODE_MATERIAL) && focusId.intValue() == m.getRowId())
            node.setFocus(true);
        if (null != groupId)
        {
            node = addNodeToGroup(node, groupId, actionseq, groupMNodes);
            node.setLink("showGraphMoreList.view", "runId="+  runId
                    + "&objtype=" + TYPECODE_MATERIAL);
        }
        pendingMNodes.put(m.getRowId(), node);
    }

    public void addStartingData (ExpData d, Integer groupId, Integer actionseq, Integer runId)
    {
        DotNode node = new DNode(d);
        node.setLink("resolveLSID", "type=Data&lsid=" + d.getLSID());

        if (null != focusId && objectType.equals(TYPECODE_DATA) && focusId.intValue() == d.getRowId())
            node.setFocus(true);
        if (null != groupId)
        {
            node = addNodeToGroup(node, groupId, actionseq, groupDNodes);
            node.setLink("showGraphMoreList.view", "runId="+  runId
                    + "&objtype=" + TYPECODE_DATA);
        }
        pendingDNodes.put(d.getRowId(), node);
    }

    private GroupedNode addNodeToGroup(DotNode node, Integer groupId, Integer actionseq, Map<Integer, GroupedNode> groupNodes)
    {
        GroupedNode gnode;
        if (groupNodes.containsKey(groupId))
        {
            gnode = groupNodes.get(groupId);
            gnode.addNode(node);
        }
        else
        {
            if (null == actionseq) actionseq = 0;
            gnode = new GroupedNode(groupId, actionseq, node);
            groupNodes.put(groupId, gnode);
        }
        return gnode;
    }

    public void addMaterial(ExpMaterial m, Integer groupId, Integer actionseq, boolean output)
    {
        if (writtenMNodes.containsKey(m.getRowId()) || pendingMNodes.containsKey(m.getRowId()))
            return;
        DotNode node = new MNode(m);
        if (output)
        {
            node.setBold(true);
        }
        if (null != focusId && objectType.equals(TYPECODE_MATERIAL) && focusId.intValue() == m.getRowId())
            node.setFocus(true);
        if (null != groupId)
            node = addNodeToGroup(node, groupId, actionseq, groupMNodes);
        pendingMNodes.put(m.getRowId(), node);
    }

    public void addData(ExpData d, Integer groupId, Integer actionseq, boolean output)
    {
        if (writtenDNodes.containsKey(d.getRowId()) || pendingDNodes.containsKey(d.getRowId()))
            return;
        DotNode node = new DNode(d);
        if (output)
        {
            node.setBold(true);
        }
        if (null != focusId && objectType.equals(TYPECODE_DATA) && focusId.intValue() == d.getRowId())
            node.setFocus(true);
        if (null != groupId)
            node = addNodeToGroup(node, groupId, actionseq, groupDNodes);
        pendingDNodes.put(d.getRowId(), node);
    }

    public void addProtApp(Integer groupId, int rowId, String name, Integer actionseq)
    {
        if (writtenProcNodes.containsKey(rowId) || pendingProcNodes.containsKey(rowId))
            return;
        DotNode node = new PANode(rowId, name);
        if (null != focusId && objectType.equals(TYPECODE_PROT_APP) && focusId.intValue() == rowId)
            node.setFocus(true);
        if (null != groupId)
            node = addNodeToGroup(node, groupId, actionseq, groupPANodes);
        pendingProcNodes.put(rowId, node);
    }

    public void addOutputNode(Integer groupId, int rowId, String name, Integer actionseq)
    {
        if (writtenProcNodes.containsKey(rowId) || pendingProcNodes.containsKey(rowId))
            return;
        DotNode node = new OutputNode(rowId, name);
        if (null != groupId)
            node = addNodeToGroup(node, groupId, actionseq, groupPANodes);
        pendingProcNodes.put(rowId, node);
    }

    public void addExpRun(Integer runId, String name)
    {
        DotNode node = new ExpNode(runId, name);
        pendingProcNodes.put(runId, node);
    }

    public void addLinkedRun(Integer runId, String name)
    {
        DotNode node = new LinkedExpNode(runId, name);
        pendingProcNodes.put(runId, node);
    }

    public void connectMaterialToProtocolApp(Integer rowIdM, Integer rowIdPA, String label)
    {
        addConnectorObject(rowIdM, rowIdPA, pendingMNodes, writtenMNodes, pendingProcNodes, writtenProcNodes, label);
    }

    public void connectDataToProtocolApp(Integer rowIdD, Integer rowIdPA, String label)
    {
        addConnectorObject(rowIdD, rowIdPA, pendingDNodes, writtenDNodes, pendingProcNodes, writtenProcNodes, label);
    }

    public void connectProtocolAppToMaterial(Integer rowIdPA, Integer rowIdM)
    {
        addConnectorObject(rowIdPA, rowIdM, pendingProcNodes, writtenProcNodes, pendingMNodes, writtenMNodes, null);
    }

    public void connectProtocolAppToData(Integer rowIdPA, Integer rowIdD)
    {
        addConnectorObject(rowIdPA, rowIdD, pendingProcNodes, writtenProcNodes, pendingDNodes, writtenDNodes, null);
    }

    public void connectRunToMaterial(Integer runId, Integer rowIdM)
    {
        addConnectorObject(runId, rowIdM, pendingProcNodes, null, pendingMNodes, writtenMNodes, null);
    }

    public void connectRunToData(Integer runId, Integer rowIdD)
    {
        addConnectorObject(runId, rowIdD, pendingProcNodes, null, pendingDNodes, writtenDNodes, null);
    }

    public void connectMaterialToRun(Integer rowIdM, Integer runId, String label)
    {
        addConnectorObject(rowIdM, runId, pendingMNodes, writtenMNodes, pendingProcNodes, writtenProcNodes, label);
    }

    public void connectDataToRun(Integer rowIdD, Integer runId, String label)
    {
        addConnectorObject(rowIdD, runId, pendingDNodes, writtenDNodes, pendingProcNodes, writtenProcNodes, label);
    }

    private void addConnectorObject(Integer srcRow, Integer trgtRow,
                                    Map<Integer, DotNode> pendingSrcMap,
                                    @Nullable Map<Integer, DotNode> writtenSrcMap,
                                    Map<Integer, DotNode> pendingTrgtMap,
                                    Map<Integer, DotNode> writtenTrgtMap, @Nullable String label)
    {
        DotNode src = null;
        DotNode trgt = null;
        String connect = "";
        if (pendingSrcMap.containsKey(srcRow))
            src = pendingSrcMap.get(srcRow);
        else if ((null != writtenSrcMap) && writtenSrcMap.containsKey(srcRow))
            src = writtenSrcMap.get(srcRow);

        if (pendingTrgtMap.containsKey(trgtRow))
            trgt = pendingTrgtMap.get(trgtRow);
        else if ((null != writtenTrgtMap) && writtenTrgtMap.containsKey(trgtRow))
            trgt = writtenTrgtMap.get(trgtRow);
        if (null != src)
            connect += src.key;
        connect += " -> ";
        if (null != trgt)
        {
            if (null == trgt.shape)  // it's an output node, drawn just as an arrow to a label
            {
                String outnodekey = src.key + "out";
                connect += outnodekey + " [arrowhead = diamond] ";
                connect += "\n" + outnodekey + "[shape=plaintext label=\"Output\"]";
            }
            else
                connect += trgt.key + "[arrowsize = 2]";
        }
        if (label != null && !(src instanceof GroupedNode) && !(trgt instanceof GroupedNode))
        {
            connect += " [ style=\"setlinewidth(3)\" label = \"" + escape(label) + "\" fontname=\"" + LABEL_FONT + "\" fontsize=" + LABEL_SMALL_FONTSIZE + " ]";
        }
        else
        {
            connect += " [ style=\"setlinewidth(3)\" ]";
        }
        if (!writtenConnects.contains(connect) && !pendingConnects.contains(connect))
            pendingConnects.add(connect);
    }

    public void writePendingConnects()
    {
        String connect;
        for (String pendingConnect : pendingConnects)
        {
            connect = pendingConnect;
            pwOut.println(connect);
            writtenConnects.add(connect);
        }
        pendingConnects.clear();
    }

    public void flushPending()
    {
        writePending(pendingProcNodes, writtenProcNodes);
        writePending(pendingMNodes, writtenMNodes);
        writePending(pendingDNodes, writtenDNodes);
        writePending(pendingProcNodes, writtenProcNodes);
        writePendingConnects();
        groupMNodes.clear();
        groupDNodes.clear();
        groupPANodes.clear();
    }

    public void writePending(Map<Integer, DotNode> pendingMap, Map<Integer, DotNode> writtenMap)
    {
        Set<Integer> nodesToMove = new HashSet<>();
        for (Integer key : pendingMap.keySet())
        {
            DotNode node = pendingMap.get(key);
            if (!nodesToMove.contains(key))
                node.save(pwOut, urlBase);
            if (node instanceof GroupedNode)
            {
                for (Integer memberkey : ((GroupedNode) node).gMap.keySet())
                {
                    assert (pendingMap.containsKey(memberkey));
                    if (null != writtenMap)
                        writtenMap.put(memberkey, node);
                    nodesToMove.add(memberkey);
                }
            }
            else
            {
                writtenMap.put(key, node);
                nodesToMove.add(key);
            }
        }
        for (Integer removeId : nodesToMove)
        {
            pendingMap.remove(removeId);
        }
    }

    private class DotNode
    {
        Integer id;
        String type;
        String label;
        String key;
        String linkUrlBase;
        String linkParams;
        String color;
        String shape;
        Float height = null;
        Float width = null;
        boolean focus = false;
        boolean bold = false;

        public DotNode(String nodeType, Integer nodeId, String nodeLabel)
        {
            id = nodeId;
            type = nodeType;
            label = ((null == nodeLabel) ? "(no name)" : nodeLabel);
            key = nodeType + id;
        }

        public void setBold(boolean bold)
        {
            this.bold = bold;
        }

        public void setLink(String urlBase, String urlParams)
        {
            linkUrlBase = urlBase;
            linkParams = urlParams;
        }

        public void setShape(String dotShape, String dotColor)
        {
            shape = dotShape;
            color = dotColor;
        }

        public void setSize(Float nodeHeight, Float nodeWidth)
        {
            height = nodeHeight;
            width = nodeWidth;
        }

        public void setFocus(boolean f)
        {
            focus = f;
        }

        private String wrap(String l)
        {
            String [] labelParts = StringUtils.split(label);
            StringBuilder sb = new StringBuilder(labelParts[0]);
            int linewidth = sb.length();
            for (int i = 1; i < labelParts.length; i++)
            {
                linewidth += labelParts[i].length() + 1;
                if (linewidth > LABEL_CHAR_WIDTH)
                {
                    sb.append("\\n").append(labelParts[i]);
                    linewidth = labelParts[i].length();
                }
                else
                    sb.append(" ").append(labelParts[i]);
            }
            return sb.toString();
        }

        public void save(PrintWriter out, ActionURL url)
        {
            String tooltip;
            if (TYPECODE_DATA.equals(type))
            {
                tooltip = "Data: " + label;
            }
            else if (TYPECODE_MATERIAL.equals(type))
            {
                tooltip = "Material: " + label;
            }
            else
            {
                tooltip = label;
            }
            if (bold)
            {
                tooltip += " (Run Output)";
            }

            if (focus)
                color = EXPRUN_COLOR;
            if (label.length() > LABEL_CHAR_WIDTH)
                label = wrap(label);
            String link = null;
            if (null != linkUrlBase)
                link = url.relativeUrl(linkUrlBase, linkParams);
            if (null != shape)
            {
                out.println(key + "["
                        + "label=\"" + escape(label) + "\", tooltip=\"" + escape(tooltip) + "\" "
                        + ",style=\"filled" + (bold ? ", setlinewidth(6)" : ", setlinewidth(2)") + "\" "
                        + ", fillcolor=\"" + color + "\" shape=" + shape
                        + ((null != height) ? ", height=\"" + height + "\"" : "")
                        + ((null != width) ? ", width=\"" + width + "\"" : "")
                        + ((null != width) || (null != height) ? ", fixedsize=true" : "")
                        + ((null != link) ? ",  URL=\"" + escape(link) + "\"" : "")
                        + "]");
            }
        }
    }

    private String escape(String s)
    {
        if (s == null)
        {
            return null;
        }
        return s.replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
    }

    private class MNode extends DotNode
    {
        Integer srcPAId;
        String LSID;

        public MNode(ExpMaterial m)
        {
            super(TYPECODE_MATERIAL, m.getRowId(), m.getName());
            setShape("box", MATERIAL_COLOR);
            setLink("resolveLSID", "type=material&lsid=" + m.getLSID());
            ExpProtocolApplication sourceApplication = m.getSourceApplication();
            srcPAId = sourceApplication == null ? null : sourceApplication.getRowId();
            LSID = m.getLSID();
        }
    }

    private class DNode extends DotNode
    {
        Integer srcPAId;
        String LSID;

        public DNode(ExpData d)
        {
            super(TYPECODE_DATA, d.getRowId(), d.getName());
            setShape("ellipse", DATA_COLOR);
            setLink("resolveLSID", "type=data&lsid=" + d.getLSID());
            ExpProtocolApplication sourceApplication = d.getSourceApplication();
            srcPAId = sourceApplication == null ? null : sourceApplication.getRowId();
            LSID = d.getLSID();
        }
    }

    private class PANode extends DotNode
    {
        public PANode(Integer id, String name)
        {
            super(TYPECODE_PROT_APP, id, name);
            setShape("diamond", PROTOCOLAPP_COLOR);
            setLink("showApplication", "rowId=" + id.toString());
        }
    }

    private class ExpNode extends PANode
    {
        public ExpNode(Integer runid, String name)
        {
            super(runid, name);
            setShape("hexagon", EXPRUN_COLOR);
            setLink("showRunGraphDetail.view", "rowId=" + runid.toString());
        }
    }

    private class LinkedExpNode extends PANode
    {
        public LinkedExpNode(Integer runid, String name)
        {
            super(runid, name);
            setShape("hexagon", LINKEDRUN_COLOR);
            setLink("showRunGraph.view", "rowId=" + runid.toString());
        }
    }

    private class OutputNode extends PANode
    {
        public OutputNode(Integer id, String name)
        {
            super(id, name);
            setShape(null, null);
        }
    }

    private class GroupedNode extends DotNode
    {
        Integer gid;
        Integer sequence;
        SortedMap<Integer, DotNode> gMap;

        public GroupedNode(Integer groupId, Integer actionseq, DotNode node)
        {
            super(GROUP_ID_PREFIX + actionseq + node.type, node.id, "More... ");
            gid = groupId;
            sequence = actionseq;
            gMap = new TreeMap<>();
            gMap.put(node.id, node);
            //setShape(node.shape, node.color + GROUP_OPACITY);
            setShape(node.shape, GROUP_COLOR);
            setSize(node.height, node.width);
            setLink("showGraphMoreList.view", "objtype=" + node.type);
        }

        public void addNode(DotNode newnode)
        {
            assert (Objects.equals(gMap.get(gMap.firstKey()).type, newnode.type));
            gMap.put(newnode.id, newnode);
        }

        public void save(PrintWriter out, ActionURL url)
        {
            String sep = "";
            StringBuilder sbIn = new StringBuilder();
            for (Integer rowid : gMap.keySet())
            {
                sbIn.append(sep).append(rowid);
                sep = ",";
            }
            linkParams += "&rowId~in=" + PageFlowUtil.encode(sbIn.toString()); 
            String link = url.relativeUrl("showGraphMoreList", linkParams, "Experiment");

            label += " (" + gMap.keySet().size() + " entries)";
            if (null != shape)
            {
                out.println(key + "[label=\"" + escape(label)
                        + "\",style=\"filled\", fillcolor=\"" + color + "\" shape=" + shape
                        + ((null != height) ? ", height=\"" + height + "\"" : "")
                        + ((null != width) ? ", width=\"" + width + "\"" : "")
                        + ((null != width) || (null != height) ? ", fixedsize=true" : "")
                        + ((null != link) ? ",  URL=\"" + escape(link) + "\"" : "")
                        + "]");
            }
        }
    }
}
