<%
/*
 * Copyright (c) 2006-2011 LabKey Corporation
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
<%@ page import="org.labkey.api.util.HelpTopic"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.query.controllers.SourceForm" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    SourceForm form = (SourceForm)HttpView.currentModel();
    boolean canEdit = form.canEdit();
    boolean editableSQL = canEdit && !form.getQueryDef().isTableQueryDefinition();
    boolean builtIn = form.getQueryDef().isTableQueryDefinition();
    String topic = "labkeySql";
%>
<style type="text/css">

    /* Back Panel */
    .x-border-layout-ct {
        background: none repeat scroll 0 0 transparent;
    }

    .x-panel-body {
        background-color: transparent;
    }

    /* Strip behind Tabs */
    .x-tab-panel-header, .x-tab-panel-footer {
        background-color: transparent;
    }

    ul.x-tab-strip-top {
        background: transparent;
    }

    /* Buttons on Panels */
    .query-button {
        float: left;
        padding: 3px 5px;
    }

    .query-editor-panel {
        background-color: transparent;
    }

    /* Allow fullscreen on editor */
    .x-panel-body {
        position: static;
    }

    table.labkey-data-region {
        width: 100%;
    }

    .error-container {
        margin-left: 30px !important;
        margin-top: 25px !important;
    }

    .labkey-status-info {
        height : 12px;
        font-size: 12px;
        margin : 0 0 10px 0;
    }

</style>
<script type="text/javascript">
    LABKEY.requiresScript("query/QueryEditorPanel.js", true);
</script>
<div id="status" class="labkey-status-info" style="visibility: hidden;" width="99%">(status)</div>
<div id="query-editor-panel" class="extContainer"></div>
<script type="text/javascript">

    // CONSIDER : These events are used by both this window and the iFrame of the editAreaLoader.
    // If you are adding another event please be aware of both instances. 
    function saveEvent(e) {
        var cmp = Ext.getCmp('qep');
        if (cmp) {  cmp.getSourceEditor().save(); }
    }

    function editEvent(e) {
        var cmp = Ext.getCmp('qep');
        if (cmp) {  cmp.openSourceEditor(true); }
    }

    function executeEvent(e) {
        var cmp = Ext.getCmp('qep');
        if (cmp) {  cmp.getSourceEditor().execute(true); }
    }
   
    Ext.onReady(function(){

        Ext.QuickTips.init();

        Ext.Ajax.timeout = 86400000; // 1 day

        // TODO: Replace the following object with an Ajax call
        var query = {
            schema    : LABKEY.ActionURL.getParameter('schemaName'),
            query     : LABKEY.ActionURL.getParameter('query.queryName'),
            canEdit   : <%= canEdit %>,
            builtIn   : <%= builtIn %>,
            metadataEdit : <%= form.getQueryDef().isMetadataEditable() && canEdit %>,
            queryText : <%=PageFlowUtil.jsString(form.ff_queryText)%>,
            metadataText  : <%=PageFlowUtil.jsString(form.ff_metadataText)%>,
            help      : <%=PageFlowUtil.jsString(new HelpTopic(topic).toString())%>
        };

        var queryEditor = new LABKEY.query.QueryEditorPanel({
            id          : 'qep',
            border      : false,
            layout      : 'fit',
            bodyCssClass: 'query-editor-panel',
            query       : query
        });

        var panel = new Ext.Panel({
            renderTo   : 'query-editor-panel',
            layout     : 'fit',
            frame      : false,
            border     : false,
            boxMinHeight: 450,
            items      : [queryEditor]
        });

        var _resize = function(w, h) {
            if (!panel.rendered)
                return;
            var padding = [30,40];
            var xy = panel.el.getXY();
            var size = {
                width : Math.max(100,w-xy[0]-padding[0]),
                height : Math.max(100,h-xy[1]-padding[1])};
            panel.setSize(size);
            panel.doLayout();
        };

        Ext.EventManager.onWindowResize(_resize);
        Ext.EventManager.fireWindowResize();

        function beforeSave() { setStatus('Saving...'); }
        function afterSave(saved)  {
            if (saved) setStatus("Saved", true);
            else {
                setError("Failed to Save");
            }
        }
        queryEditor.getSourceEditor().on('beforeSave', beforeSave);
        queryEditor.getSourceEditor().on('save', afterSave);
        queryEditor.getMetadataEditor().on('beforeSave', beforeSave);
        queryEditor.getMetadataEditor().on('save', afterSave);

        function clearStatus() {
            var elem = Ext.get("status");
            elem.update("&nbsp;");
            elem.setVisible(false);
        }

        function setStatus(msg, autoClear)
        {
            var elem = Ext.get("status");
            elem.update(msg);
            elem.dom.className = "labkey-status-info";
            elem.setDisplayed(true);
            elem.setVisible(true);
            var clear = clearStatus;
            if(autoClear) clearStatus.defer(5000);
        }

        function setError(msg)
        {
            var elem = Ext.get("status");
            elem.update(msg);
            elem.dom.className = "labkey-status-error";
            elem.setVisible(true);
        }

        function onKeyDown(evt) {
            var handled = false;

            if(evt.ctrlKey && !evt.altKey && !evt.shiftKey) {
                if (83 == evt.getKey()) {  // s
                    saveEvent(evt);
                    handled = true;
                }
                if (69 == evt.getKey()) {  // e
                    editEvent(evt);
                    handled = true;
                }
                if (13 == evt.getKey()) {  // enter
                    executeEvent(evt);
                    handled = true;
                }
            }

            if(handled) {
                evt.preventDefault();
                evt.stopPropagation();
            }
        }

        Ext.EventManager.addListener(document, "keydown", onKeyDown);
    });
</script>