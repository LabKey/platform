<%
/*
 * Copyright (c) 2006-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.query.QueryAction"%>
<%@ page import="org.labkey.api.query.QueryDefinition"%>
<%@ page import="org.labkey.api.util.HelpTopic" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="org.labkey.query.controllers.QueryController" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%!
  public LinkedHashSet<ClientDependency> getClientDependencies()
  {
      LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
      resources.add(ClientDependency.fromPath("clientapi/ext3"));
      resources.add(ClientDependency.fromPath("codemirror"));
      resources.add(ClientDependency.fromPath("query/QueryEditorPanel.js"));
      return resources;
  }
%>

<%
    QueryController.SourceQueryAction action = (QueryController.SourceQueryAction)HttpView.currentModel();
    QueryDefinition queryDef = action._queryDef;
    boolean builtIn = queryDef.isTableQueryDefinition();
    String sqlHelpTopic = "labkeySql";
    String metadataHelpTopic = "metadataSql";
    ActionURL exeUrl = null;
    try
    {
        exeUrl = queryDef.urlFor(QueryAction.executeQuery, getContainer());
    }
    catch (Exception x)
    {
        /* */
    }

    boolean canEdit = queryDef.canEdit(getUser());
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

    /* Masking style */
    .ext-el-mask-msg {
        border: none;
        background-color: transparent;
    }

    .indicator-helper {
        margin: auto !important;
        margin-left: 3px !important;
        padding-left: 25px !important;
    }

</style>
<div id="status" class="labkey-status-info" style="visibility: hidden;" width="99%">(status)</div>
<div id="query-editor-panel" class="extContainer"></div>
<script type="text/javascript">
    Ext.onReady(function(){

        Ext.QuickTips.init();

        Ext.Ajax.timeout = 60 * 60 * 24 * 1000; // 1 day in ms

        // TODO: Replace the following object with an Ajax call
        var query = {
            schema    : LABKEY.ActionURL.getParameter('schemaName'),
            query     : LABKEY.ActionURL.getParameter('query.queryName'),
            executeUrl: <%= PageFlowUtil.jsString(null==exeUrl ? null : exeUrl.toString()) %>,
            canEdit   : <%= canEdit %>,
            canEditSql   : <%= canEdit && queryDef.isSqlEditable() %>,
            canEditMetaData   : <%=canEdit && queryDef.isMetadataEditable() %>,
            builtIn   : <%= builtIn %>,
            metadataEdit : <%= queryDef.isMetadataEditable() %>,
            propEdit     : <%=  queryDef.isMetadataEditable() && !builtIn %>,
            queryText    : <%=PageFlowUtil.jsString(action._form.ff_queryText)%>,
            metadataText : <%=PageFlowUtil.jsString(action._form.ff_metadataText)%>,
            help         : <%=PageFlowUtil.qh(new HelpTopic(sqlHelpTopic).toString())%>,
            metadataHelp : <%=PageFlowUtil.qh(new HelpTopic(metadataHelpTopic).toString())%>
        };

        var activeTab = 0;
        var hash = window.location.hash;
        if (hash == "#source")
            activeTab = 0;
        else if (hash == "#data")
            activeTab = 1;
        else if (hash == "#metadata")
            activeTab = 2;

        var clearStatus = function()
        {
            var elem = Ext.get("status");
            elem.update("&nbsp;");
            elem.setVisible(false);
        };

        var setError = function(msg)
        {
            var elem = Ext.get("status");
            elem.update(msg);
            elem.dom.className = "labkey-status-error";
            elem.setVisible(true);
        };

        var setStatus = function(msg, autoClear)
        {
            var elem = Ext.get("status");
            elem.update(msg);
            elem.dom.className = "labkey-status-info";
            elem.setDisplayed(true);
            elem.setVisible(true);
            if(autoClear) clearStatus.defer(5000);
        };

        var beforeSave = function(qep)
        {
            setStatus('Saving...');
        };

        var afterSave = function(qep, saved, json)
        {
            if (saved)
            {
                if (json && json.parseErrors)
                    setStatus("Saved with parse errors", true);
                else
                    setStatus("Saved", true);
            }
            else
            {
                var msg = "Failed to Save";
                if (json && json.exception)
                    msg += ": " + json.exception;
                setError(msg);
            }
        };

        var panel = new Ext.Panel({
            renderTo   : 'query-editor-panel',
            layout     : 'fit',
            frame      : false,
            border     : false,
            boxMinHeight: 450,
            items      : [{
                xtype       : 'labkey-query-editor',
                id          : 'qep',
                border      : false,
                layout      : 'fit',
                bodyCssClass: 'query-editor-panel',
                query       : query,
                activeTab   : activeTab,
                listeners: {
                    render: function(qep) {
                        var onKeyDown = function(evt) {
                            var handled = false;

                            if(evt.ctrlKey && !evt.altKey && !evt.shiftKey) {
                                if (83 == evt.getKey()) {  // s
                                    qep.getSourceEditor().save();
                                    handled = true;
                                }
                                if (69 == evt.getKey()) {  // e
                                    qep.openSourceEditor(true);
                                    handled = true;
                                }
                                if (13 == evt.getKey()) {  // enter
                                    qep.getSourceEditor().execute(true);
                                    handled = true;
                                }
                            }

                            if(handled) {
                                evt.preventDefault();
                                evt.stopPropagation();
                            }
                        };

                        Ext.EventManager.addListener(document, "keydown", onKeyDown);
                    },
                    beforeSave: beforeSave,
                    save: afterSave
                }
            }]
        });

        var _resize = function(w, h) {
            LABKEY.ext.Utils.resizeToViewport(panel, w, h, 40, 50);
        };

        Ext.EventManager.onWindowResize(_resize);
        Ext.EventManager.fireWindowResize();
    });
</script>
