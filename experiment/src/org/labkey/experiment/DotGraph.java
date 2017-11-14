/*
 * Copyright (c) 2005-2015 Fred Hutchinson Cancer Research Center
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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.view.ActionURL;
import org.labkey.experiment.controllers.exp.ExperimentController;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Represents the GraphViz format for building an experiment run "flowchart", connecting datas, materials through runs
 * and protocol applications.
 * User: migra
 * Date: Jun 13, 2005
 */
public class DotGraph
{
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

    private final PrintWriter _pwOut;
    private final Container _c;

    private final SortedMap<Integer, DotNode> _pendingMNodes = new TreeMap<>();
    private final SortedMap<Integer, DotNode> _pendingDNodes = new TreeMap<>();
    private final SortedMap<Integer, DotNode> _pendingProcNodes = new TreeMap<>();
    private final Map<Integer, GroupedNode> _groupMNodes = new HashMap<>();
    private final Map<Integer, GroupedNode> _groupDNodes = new HashMap<>();
    private final Map<Integer, GroupedNode> _groupPANodes = new HashMap<>();
    private final Map<Integer, DotNode> _writtenMNodes = new HashMap<>();
    private final Map<Integer, DotNode> _writtenDNodes = new HashMap<>();
    private final Map<Integer, DotNode> _writtenProcNodes = new HashMap<>();
    private final Set<String> _pendingConnects = new HashSet<>();
    private final Set<String> _writtenConnects = new HashSet<>();

    private Integer _focusId = null;
    private String _objectType = null;

    // following are used in URLs to generate lineage graphs
    public static String TYPECODE_MATERIAL = "M";
    public static String TYPECODE_DATA = "D";
    public static String TYPECODE_PROT_APP = "A";

    public DotGraph(PrintWriter out, Container c, boolean bSmallFonts)
    {
        _pwOut = out;
        _c = c;

        if (bSmallFonts)
            _pwOut.println("digraph G { node[fontname=\"" + LABEL_FONT + "\" fontsize=" + LABEL_SMALL_FONTSIZE + "]");
        else
            _pwOut.println("digraph G { node[fontname=\"" + LABEL_FONT + "\" fontsize=" + LABEL_DEFAULT_FONTSIZE + "]");
    }

    public void setFocus(Integer focusid, String objtype)
    {
        _focusId = focusid;
        _objectType = objtype;
    }

    public void dispose()
    {
        flushPending();
        _pwOut.println("}");
    }

    public Integer getPAGroupId(int rowIdPA)
    {
        return getGroupId(rowIdPA, _pendingProcNodes, _writtenProcNodes);
    }

    public Integer getMGroupId(Integer rowIdM)
    {
        return getGroupId(rowIdM, _pendingMNodes, _writtenMNodes);
    }

    public Integer getDGroupId(int rowIdD)
    {
        return getGroupId(rowIdD, _pendingDNodes, _writtenDNodes);
    }

    public @Nullable Integer getGroupId(Integer rowId, Map<Integer, DotNode> pendingNodes, Map<Integer, DotNode> writtenNodes)
    {
        DotNode node = null;
        if (pendingNodes.containsKey(rowId))
            node = pendingNodes.get(rowId);
        else if (writtenNodes.containsKey(rowId))
            node = writtenNodes.get(rowId);
        if (null == node)
            return null;
        if (node instanceof GroupedNode)
            return ((GroupedNode) node)._gid;

        return null;
    }

    public void addStartingMaterial(ExpMaterial m, Integer groupId, Integer actionseq, int runId)
    {
        DotNode node = new MNode(m);
        node.setLinkURL(ExperimentController.getResolveLsidURL(_c, "material", m.getLSID()));
        if (null != _focusId && TYPECODE_MATERIAL.equalsIgnoreCase(_objectType) && _focusId == m.getRowId())
            node.setFocus(true);
        if (null != groupId)
        {
            node = addNodeToGroup(node, groupId, actionseq, _groupMNodes);
            node.setLinkURL(ExperimentController.getShowGraphMoreListURL(_c, runId, TYPECODE_MATERIAL));
        }
        _pendingMNodes.put(m.getRowId(), node);
    }

    public void addStartingData(ExpData d, Integer groupId, Integer actionseq, int runId)
    {
        DotNode node = new DNode(d);
        node.setLinkURL(ExperimentController.getResolveLsidURL(_c, "data", d.getLSID()));

        if (null != _focusId && TYPECODE_DATA.equalsIgnoreCase(_objectType) && _focusId == d.getRowId())
            node.setFocus(true);
        if (null != groupId)
        {
            node = addNodeToGroup(node, groupId, actionseq, _groupDNodes);
            node.setLinkURL(ExperimentController.getShowGraphMoreListURL(_c, runId, TYPECODE_DATA));
        }
        _pendingDNodes.put(d.getRowId(), node);
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
        if (_writtenMNodes.containsKey(m.getRowId()) || _pendingMNodes.containsKey(m.getRowId()))
            return;
        DotNode node = new MNode(m);
        if (output)
        {
            node.setBold(true);
        }
        if (null != _focusId && TYPECODE_MATERIAL.equalsIgnoreCase(_objectType) && _focusId == m.getRowId())
            node.setFocus(true);
        if (null != groupId)
            node = addNodeToGroup(node, groupId, actionseq, _groupMNodes);
        _pendingMNodes.put(m.getRowId(), node);
    }

    public void addData(ExpData d, Integer groupId, Integer actionseq, boolean output)
    {
        if (_writtenDNodes.containsKey(d.getRowId()) || _pendingDNodes.containsKey(d.getRowId()))
            return;
        DotNode node = new DNode(d);
        if (output)
        {
            node.setBold(true);
        }
        if (null != _focusId && TYPECODE_DATA.equalsIgnoreCase(_objectType) && _focusId == d.getRowId())
            node.setFocus(true);
        if (null != groupId)
            node = addNodeToGroup(node, groupId, actionseq, _groupDNodes);
        _pendingDNodes.put(d.getRowId(), node);
    }

    public void addProtApp(Integer groupId, int rowId, String name, Integer actionseq)
    {
        if (_writtenProcNodes.containsKey(rowId) || _pendingProcNodes.containsKey(rowId))
            return;
        DotNode node = new PANode(rowId, name);
        if (null != _focusId && TYPECODE_PROT_APP.equalsIgnoreCase(_objectType) && _focusId == rowId)
            node.setFocus(true);
        if (null != groupId)
            node = addNodeToGroup(node, groupId, actionseq, _groupPANodes);
        _pendingProcNodes.put(rowId, node);
    }

    public void addOutputNode(Integer groupId, int rowId, String name, Integer actionseq)
    {
        if (_writtenProcNodes.containsKey(rowId) || _pendingProcNodes.containsKey(rowId))
            return;
        DotNode node = new OutputNode(rowId, name);
        if (null != groupId)
            node = addNodeToGroup(node, groupId, actionseq, _groupPANodes);
        _pendingProcNodes.put(rowId, node);
    }

    public void addExpRun(int runId, String name)
    {
        DotNode node = new ExpNode(runId, name);
        _pendingProcNodes.put(runId, node);
    }

    public void addLinkedRun(int runId, String name)
    {
        DotNode node = new LinkedExpNode(runId, name);
        _pendingProcNodes.put(runId, node);
    }

    public void connectMaterialToProtocolApp(Integer rowIdM, Integer rowIdPA, String label)
    {
        addConnectorObject(rowIdM, rowIdPA, _pendingMNodes, _writtenMNodes, _pendingProcNodes, _writtenProcNodes, label);
    }

    public void connectDataToProtocolApp(Integer rowIdD, Integer rowIdPA, String label)
    {
        addConnectorObject(rowIdD, rowIdPA, _pendingDNodes, _writtenDNodes, _pendingProcNodes, _writtenProcNodes, label);
    }

    public void connectProtocolAppToMaterial(Integer rowIdPA, Integer rowIdM)
    {
        addConnectorObject(rowIdPA, rowIdM, _pendingProcNodes, _writtenProcNodes, _pendingMNodes, _writtenMNodes, null);
    }

    public void connectProtocolAppToData(Integer rowIdPA, Integer rowIdD)
    {
        addConnectorObject(rowIdPA, rowIdD, _pendingProcNodes, _writtenProcNodes, _pendingDNodes, _writtenDNodes, null);
    }

    public void connectRunToMaterial(Integer runId, Integer rowIdM)
    {
        addConnectorObject(runId, rowIdM, _pendingProcNodes, null, _pendingMNodes, _writtenMNodes, null);
    }

    public void connectRunToData(Integer runId, Integer rowIdD)
    {
        addConnectorObject(runId, rowIdD, _pendingProcNodes, null, _pendingDNodes, _writtenDNodes, null);
    }

    public void connectMaterialToRun(Integer rowIdM, Integer runId, String label)
    {
        addConnectorObject(rowIdM, runId, _pendingMNodes, _writtenMNodes, _pendingProcNodes, _writtenProcNodes, label);
    }

    public void connectDataToRun(Integer rowIdD, Integer runId, String label)
    {
        addConnectorObject(rowIdD, runId, _pendingDNodes, _writtenDNodes, _pendingProcNodes, _writtenProcNodes, label);
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
            connect += src._key;
        connect += " -> ";
        if (null != trgt)
        {
            if (null == trgt._shape && src != null)  // it's an output node, drawn just as an arrow to a label
            {
                String outnodekey = src._key + "out";
                connect += outnodekey + " [arrowhead = diamond] ";
                connect += "\n" + outnodekey + "[shape=plaintext label=\"Output\"]";
            }
            else
                connect += trgt._key + "[arrowsize = 2]";
        }
        if (label != null && !(src instanceof GroupedNode) && !(trgt instanceof GroupedNode))
        {
            connect += " [ style=\"setlinewidth(3)\" label = \"" + escape(label) + "\" fontname=\"" + LABEL_FONT + "\" fontsize=" + LABEL_SMALL_FONTSIZE + " ]";
        }
        else
        {
            connect += " [ style=\"setlinewidth(3)\" ]";
        }
        if (!_writtenConnects.contains(connect) && !_pendingConnects.contains(connect))
            _pendingConnects.add(connect);
    }

    public void writePendingConnects()
    {
        String connect;
        for (String pendingConnect : _pendingConnects)
        {
            connect = pendingConnect;
            _pwOut.println(connect);
            _writtenConnects.add(connect);
        }
        _pendingConnects.clear();
    }

    public void flushPending()
    {
        writePending(_pendingProcNodes, _writtenProcNodes);
        writePending(_pendingMNodes, _writtenMNodes);
        writePending(_pendingDNodes, _writtenDNodes);
        writePending(_pendingProcNodes, _writtenProcNodes);
        writePendingConnects();
        _groupMNodes.clear();
        _groupDNodes.clear();
        _groupPANodes.clear();
    }

    public void writePending(Map<Integer, DotNode> pendingMap, Map<Integer, DotNode> writtenMap)
    {
        Set<Integer> nodesToMove = new HashSet<>();
        for (Integer key : pendingMap.keySet())
        {
            DotNode node = pendingMap.get(key);
            if (!nodesToMove.contains(key))
                node.save(_pwOut);
            if (node instanceof GroupedNode)
            {
                for (Integer memberkey : ((GroupedNode) node)._gMap.keySet())
                {
                    assert (pendingMap.containsKey(memberkey));
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
        private final int _id;
        private final String _type;

        protected final String _key;

        private boolean _focus = false;
        private boolean _bold = false;
        private ActionURL _linkURL;

        protected String _label;
        protected String _color = null;
        protected String _shape = null;
        protected Float _height = null;
        protected Float _width = null;

        public DotNode(String nodeType, int nodeId, String nodeLabel)
        {
            _id = nodeId;
            _type = nodeType;
            _label = ((null == nodeLabel) ? "(no name)" : nodeLabel);
            _key = nodeType + _id;
        }

        public void setBold(boolean bold)
        {
            _bold = bold;
        }

        public void setLinkURL(ActionURL url)
        {
            _linkURL = url;
        }

        public void setShape(String dotShape, String dotColor)
        {
            _shape = dotShape;
            _color = dotColor;
        }

        public void setSize(Float nodeHeight, Float nodeWidth)
        {
            _height = nodeHeight;
            _width = nodeWidth;
        }

        public void setFocus(boolean f)
        {
            _focus = f;
        }

        private String wrap(String l)
        {
            String[] labelParts = StringUtils.split(l);
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

        public void save(PrintWriter out)
        {
            String tooltip;
            if (TYPECODE_DATA.equals(_type))
            {
                tooltip = "Data: " + _label;
            }
            else if (TYPECODE_MATERIAL.equals(_type))
            {
                tooltip = "Material: " + _label;
            }
            else
            {
                tooltip = _label;
            }
            if (_bold)
            {
                tooltip += " (Run Output)";
            }

            if (_focus)
                _color = EXPRUN_COLOR;
            if (_label.length() > LABEL_CHAR_WIDTH)
                _label = wrap(_label);
            String link = null;
            if (null != _linkURL)
                link = _linkURL.toString();
            if (null != _shape)
            {
                out.println(_key + "["
                        + "label=\"" + escape(_label) + "\", tooltip=\"" + escape(tooltip) + "\" "
                        + ",style=\"filled" + (_bold ? ", setlinewidth(6)" : ", setlinewidth(2)") + "\" "
                        + ", fillcolor=\"" + _color + "\" shape=" + _shape
                        + ((null != _height) ? ", height=\"" + _height + "\"" : "")
                        + ((null != _width) ? ", width=\"" + _width + "\"" : "")
                        + ((null != _width) || (null != _height) ? ", fixedsize=true" : "")
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
        public MNode(ExpMaterial m)
        {
            super(TYPECODE_MATERIAL, m.getRowId(), m.getName());
            setShape("box", MATERIAL_COLOR);
            setLinkURL(ExperimentController.getResolveLsidURL(_c, "material", m.getLSID()));
        }
    }

    private class DNode extends DotNode
    {
        public DNode(ExpData d)
        {
            super(TYPECODE_DATA, d.getRowId(), d.getName());
            setShape("ellipse", DATA_COLOR);
            setLinkURL(ExperimentController.getResolveLsidURL(_c, "data", d.getLSID()));
        }
    }

    private class PANode extends DotNode
    {
        public PANode(int id, String name)
        {
            super(TYPECODE_PROT_APP, id, name);
            setShape("diamond", PROTOCOLAPP_COLOR);
            setLinkURL(ExperimentController.getShowApplicationURL(_c, id));
        }
    }

    private class ExpNode extends PANode
    {
        public ExpNode(int runid, String name)
        {
            super(runid, name);
            setShape("hexagon", EXPRUN_COLOR);
            setLinkURL(ExperimentController.getShowRunGraphDetailURL(_c, runid));
        }
    }

    private class LinkedExpNode extends PANode
    {
        public LinkedExpNode(int runid, String name)
        {
            super(runid, name);
            setShape("hexagon", LINKEDRUN_COLOR);
            setLinkURL(ExperimentController.getRunGraphURL(_c, runid));
        }
    }

    private class OutputNode extends PANode
    {
        public OutputNode(int id, String name)
        {
            super(id, name);
            setShape(null, null);
        }
    }

    private class GroupedNode extends DotNode
    {
        private final Integer _gid;
        private final SortedMap<Integer, DotNode> _gMap = new TreeMap<>();
        private final String _nodeType;

        public GroupedNode(Integer groupId, Integer actionseq, DotNode node)
        {
            super(GROUP_ID_PREFIX + actionseq + node._type, node._id, "More... ");
            _gid = groupId;
            _gMap.put(node._id, node);
            //setShape(node.shape, node.color + GROUP_OPACITY);
            setShape(node._shape, GROUP_COLOR);
            setSize(node._height, node._width);
            _nodeType = node._type;
        }

        public void addNode(DotNode newnode)
        {
            assert (Objects.equals(_gMap.get(_gMap.firstKey())._type, newnode._type));
            _gMap.put(newnode._id, newnode);
        }

        public void save(PrintWriter out)
        {
            ActionURL url = ExperimentController.getShowGraphMoreListURL(_c, null, _nodeType);

            String sep = "";
            StringBuilder sbIn = new StringBuilder();
            for (Integer rowid : _gMap.keySet())
            {
                sbIn.append(sep).append(rowid);
                sep = ",";
            }

            url.addParameter("rowId~in", sbIn.toString());

            _label += " (" + _gMap.keySet().size() + " entries)";

            if (null != _shape)
            {
                out.println(_key + "[label=\"" + escape(_label)
                        + "\",style=\"filled\", fillcolor=\"" + _color + "\" shape=" + _shape
                        + ((null != _height) ? ", height=\"" + _height + "\"" : "")
                        + ((null != _width) ? ", width=\"" + _width + "\"" : "")
                        + ((null != _width) || (null != _height) ? ", fixedsize=true" : "")
                        + (",  URL=\"" + escape(url.toString()) + "\"")
                        + "]");
            }
        }
    }
}
