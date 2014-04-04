<%
/*
 * Copyright (c) 2010-2014 LabKey Corporation
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
%>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.workbook.MoveWorkbooksBean" %>
<%@ page import="java.util.List" %>
<%
    JspView me = (JspView) HttpView.currentView();
    MoveWorkbooksBean bean = (MoveWorkbooksBean)me.getModelBean();
    List<Container> workbooks = bean.getWorkbooks();

    String noun = workbooks.size() > 1 ? "workbooks" : "workbook";
    String buttonCaption = workbooks.size() > 1 ? "Move Workbooks" : "Move Workbook";
%>

<div>Move the <span style="font-weight:bold;"><%=workbooks.size()%></span> <%=noun%>
to the selected folder:</div>

<div id="mwb-container-tree" class="extContainer"></div>
<div style="width:600px;text-align:right;"><%=PageFlowUtil.generateBackButton("Cancel")%>
<%= PageFlowUtil.button(buttonCaption).href("#").onClick("onMoveWorkbooks();") %></div>
<div id="mwb-status"></div>

<script type="text/javascript">

    var _containerTree;
    var _workbookIds = [<%=bean.getIDInitializer()%>];
    var _curIndex = 0;
    var _newParentId;
    var _newParentPath;

    Ext.onReady(function(){
        _containerTree = new Ext.tree.TreePanel({
            dataUrl: LABKEY.ActionURL.buildURL("core", "getExtMWBContainerTree.api"),
            root: new Ext.tree.AsyncTreeNode({
                id: '<%=ContainerManager.getRoot().getRowId()%>',
                expanded: true,
                expandable: false,
                text: 'Projects'
            }),
            enableDrag: false,
            useArrows: true,
            renderTo: 'mwb-container-tree',
            height: 400,
            width: 600,
            autoScroll: true
        });
    });

    function onMoveWorkbooks()
    {
        var node = _containerTree.getSelectionModel().getSelectedNode();
        if (!node)
        {
            Ext.Msg.alert("Error", "Please select a folder in the tree.");
            return;
        }

        if (LABKEY.ActionURL.getContainer() == encodeURI(node.attributes.containerPath))
        {
            Ext.Msg.alert("Error", "The selected <%=noun%> already exist in that folder. Please choose a folder you want to move the <%=noun%> to.");
            return;
        }

        _newParentId = node.id;
        _newParentPath = node.attributes.containerPath;
        _curIndex = 0;
        moveNextWorkbook();
    }

    function moveNextWorkbook()
    {
        if (_curIndex >= _workbookIds.length)
        {
            onFinishedMove();
            return;
        }

        setStatus("Moving workbook " + _workbookIds[_curIndex] + "...");

        Ext.Ajax.request({
            url: LABKEY.ActionURL.buildURL("core", "moveWorkbook.api"),
            method: 'POST',
            headers : {
                'Content-Type' : 'application/json'
            },
            jsonData: {
                workbookId: _workbookIds[_curIndex],
                newParentId: _newParentId
            },
            success: onMoveSuccess,
            failure: LABKEY.Utils.getCallbackWrapper(onMoveFailure, this, true)
        });

    }

    function onMoveSuccess()
    {
        setStatus("Moved.");
        _curIndex++;
        moveNextWorkbook();
    }

    function onMoveFailure(errorInfo)
    {
        setStatus("Failed trying to move workbook " + _workbookIds[_curIndex] + ": " + errorInfo.exception);
    }

    function onFinishedMove()
    {
        setStatus("Successfully moved all workbooks.");
        window.location = LABKEY.ActionURL.buildURL("project", "begin", _newParentPath);
    }

    function setStatus(msg)
    {
        Ext.get("mwb-status").update(msg);
    }
</script>
