<%
/*
 * Copyright (c) 2013-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.mcp.McpService"%>
<%@ page import="org.labkey.api.query.QueryAction"%>
<%@ page import="org.labkey.api.query.QueryDefinition" %>
<%@ page import="org.labkey.api.util.HelpTopic" %>
<%@ page import="org.labkey.api.util.JavaScriptFragment" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.query.controllers.QueryController" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
        dependencies.add("codemirror");
        dependencies.add("query/QueryEditorPanel.js");
    }
%>
<%
    QueryController.SourceQueryAction action = (QueryController.SourceQueryAction)HttpView.currentModel();
    QueryDefinition queryDef = action._queryDef;
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
    boolean canEditMetadata = queryDef.canEditMetadata(getUser());
    boolean canDelete = queryDef.canDelete(getUser());
    boolean isChatReady = McpService.get().isReady();
%>
<style type="text/css">

    /* Buttons on Panels */
    .query-button {
        float: left;
        margin-right: 3px;
    }

    .query-editor-panel-parent, .query-editor-panel {
        /*background: transparent;*/
    }

    table.labkey-data-region {
        width: 100%;
    }

    .error-container {
        margin-left: 30px !important;
        margin-top: 10px !important;
    }

    .labkey-status-info {
        font-size: 12px;
        margin : 0 0 10px 0;
    }

    /* Masking style */
    .x4-mask-msg {
        border: none;
    }

<% if (isChatReady) { %>
    DIV.chatItem {
      margin: 5px;
      padding: 5px;
      background-color: #4CAF50;
      border-radius: 15px;
      border : solid 1px darkgray;
      display: flex;
      align-items: center;
      box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
   }

    DIV.userPrompt {
      margin: 5px;
      margin-right: 20px;
      background-color : white;
    }

    DIV.genaiResponse {
      margin: 5px;
      margin-left: 20px;
      background-color: lightgray;
    }

    DIV.sqlResponse {
      margin-left: 10px;
      background-color: pink;
    }
<% } %>
</style>

<% if (isChatReady) { %>
<%-- should use Ext4 Panel for layout, but this is just a prototype anyway --%>
<table style="width:100%; min-width:600px" id="querySourceLayout"><tr>
    <td style="width:80%; min-width:400px; vertical-align: top; ">
        <div id="status" class="labkey-status-info" style="visibility: hidden;" width="100%">(status)</div>
        <div id="query-editor-panel" class="extContainer"></div>
    </td>
    <td style="width:20%; min-width:200px; vertical-align: top;">
        <div id="chatHistory" style="height:500px; width:100%; overflow:scroll;" ></div>
        <textarea id="geminiPrompt" style="height:100px; width:100%;" placeholder="Shift-Enter to submit"></textarea>
    </td>
</tr></table>
<% } else { %>
<div id="status" class="labkey-status-info" style="visibility: hidden;" width="100%">(status)</div>
<div id="query-editor-panel" class="extContainer"></div>
<% } %>

<script type="text/javascript" nonce="<%=getScriptNonce()%>">
    Ext4.onReady(function(){

    Ext4.QuickTips.init();

    Ext4.Ajax.timeout = 60 * 60 * 24 * 1000; // 1 day in ms

    // TODO: Replace the following object with an Ajax call
    var query = {
        schema    : <%= q(queryDef.getSchemaPath().toString()) %>,
        query     : <%= q(queryDef.getName()) %>,
        executeUrl: <%= q(exeUrl) %>,
        canEdit   : <%= canEdit %>,
        canDelete : <%= canDelete %>,
        canEditSql   : <%= canEdit && queryDef.isSqlEditable() %>,
        canEditMetadata   : <%=canEditMetadata && queryDef.isMetadataEditable() %>,
        userDefined  : <%= queryDef.isUserDefined() %>,
        sqlEditable  : <%=queryDef.isSqlEditable()%>,
        metadataEditable : <%=queryDef.isMetadataEditable()%>,
        propEdit     : <%=queryDef.isSqlEditable() && canEdit%>,
        queryText    : <%=q(action._form.ff_queryText)%>,
        metadataText : <%=q(action._form.ff_metadataText)%>,
        help         : <%=q(new HelpTopic(sqlHelpTopic).getHelpTopicHref())%>,
        metadataHelp : <%=q(new HelpTopic(metadataHelpTopic).getHelpTopicHref())%>
    };

    var tabMap = {
        source: 0,
        data: 1,
        metadata: 2
    };

    var hash = window.location.hash.replace('#', '').toLowerCase();
    var activeTab = tabMap[hash] !== undefined ? tabMap[hash] : tabMap.source;

    var clearStatus = function() {
        var elem = Ext4.get('status');
        elem.update('&nbsp;');
        elem.setVisible(false);
    };

    var setError = function(msg) {
        var elem = Ext4.get('status');
        elem.update(LABKEY.Utils.encodeHtml(msg));
        elem.dom.className = 'labkey-status-error';
        elem.setVisible(true);
    };

    var setStatus = function(msg, autoClear) {
        var elem = Ext4.get('status');
        elem.update(LABKEY.Utils.encodeHtml(msg));
        elem.dom.className = 'labkey-status-info';
        elem.setDisplayed(true);
        elem.setVisible(true);
        if (autoClear) {
            Ext4.defer(clearStatus, 5000);
        }
    };

    var afterSave = function(qep, saved, json) {
        if (saved) {
            if (json && json.parseErrors) {
                var msg1 = 'Saved with parse errors: ';
                for (var i = 0; i < json.parseErrors.length; i++)
                    msg1 += "; " + json.parseErrors[i].msg;
                setStatus(msg1, true);
            }
            else
                setStatus('Saved', true);
        }
        else {
            var msg = 'Failed to Save';
            if (json && json.exception) {
                msg += ': ' + json.exception;
            }
            setError(msg);
        }
    };

        var panel = Ext4.create('Ext.panel.Panel', {
        renderTo   : 'query-editor-panel',
        bodyCls    : 'query-editor-panel-parent',
        layout     : 'fit',
        frame      : false,
        border     : false,
        minHeight  : 650,
        items      : [Ext4.create('LABKEY.query.QueryEditorPanel', {
            id          : 'qep',
            border      : false,
            layout      : 'fit',
            bodyCssClass: 'query-editor-panel',
            query       : query,
            activeTab   : activeTab,
            listeners: {
                render: function(qep) {
                    Ext4.EventManager.addListener(document, 'keydown', function(evt) {
                        var handled = false;

                        if (evt.ctrlKey && !evt.altKey && !evt.shiftKey) {
                            var key = evt.getKey();
                            if (83 === key) {  // s
                                qep.getSourceEditor().save();
                                handled = true;
                            }
                            else if (69 === key) {  // e
                                qep.openSourceEditor(true);
                                handled = true;
                            }
                            else if (13 === key) {  // enter
                                qep.getSourceEditor().execute(true);
                                handled = true;
                            }
                        }

                        if (handled) {
                            evt.preventDefault();
                            evt.stopPropagation();
                        }
                    });
                },
                beforeSave: function() { setStatus('Saving...'); },
                save: afterSave
            }
        })]
    });

<%  if (isChatReady) { %>
    const isChatReady = <%=JavaScriptFragment.bool(isChatReady)%>;

     const resizeFn = function(evt)
    {
        // console.log(evt);
        // console.log("window " + window.innerHeight + " " + window.innerWidth);
        var el = document.getElementById("querySourceLayout");
        if (el)
        {
            const rect = document.getElementById("querySourceLayout").getBoundingClientRect();
            const width = Math.max(600, window.innerWidth-rect.left-40)
            const height = Math.max(400, window.innerHeight-rect.top-40);
            el.style.width =  width + "px";
            el.style.height = height + "px";
            panel.setWidth(Math.max(400,width*0.75));
            panel.setHeight(Math.max(650,height));
            document.getElementById("geminiPrompt").style.width = Math.max(200,width*0.25) + 'px';
            document.getElementById("chatHistory").style.height = Math.max(200,height-100) + 'px';
            panel.updateLayout();
        }
    };
    window.onresize = resizeFn;
    resizeFn({});

    var elPrompt = document.getElementById('geminiPrompt');

    function scrollToBottom()
    {
        const div = document.getElementById("chatHistory");
        div.scrollTo({ top: div.scrollHeight, behavior: "smooth" });
    }
    function appendUserPrompt(text)
    {
        const chatItem = document.createElement('div');
        chatItem.className = 'chatItem userPrompt';
        chatItem.innerText = text;
        document.getElementById('chatHistory').appendChild(chatItem);
        scrollToBottom();
    }
    function appendTextResponse(text)
    {
        const chatItem = document.createElement('div');
        chatItem.className = 'chatItem genaiResponse';
        chatItem.innerText = text;
        document.getElementById('chatHistory').appendChild(chatItem);
        scrollToBottom();
    }
    function appendHtmlResponse(html)
    {
        const chatItem = document.createElement('div');
        chatItem.className = 'chatItem genaiResponse';
        chatItem.innerHTML = html;
        document.getElementById('chatHistory').appendChild(chatItem);
        scrollToBottom();
    }
    function appendSqlResponse(text)
    {
        const chatItem = document.createElement('div');
        chatItem.className = 'chatItem sqlResponse';
        const copy = document.createElement("i");
        copy.className = "fa fa-copy";
        chatItem.appendChild(copy);
        const pre = document.createElement('pre');
        pre.innerText = text;
        chatItem.appendChild(pre);
        chatItem.onclick = function(evt)
        {
            navigator.clipboard.writeText(evt.target.parentElement.innerText);
            return false;
        };
        document.getElementById('chatHistory').appendChild(chatItem);
        scrollToBottom();
    }

    function initChat()
    {
        if (!isChatReady)
            return;

        // initialize conversation with the current SQL
        // CONSIDER: update before every prompt if the SQL has changed
        var schemaName = <%=q(queryDef.getSchemaPath().getName())%>;
        var queryText = null;
        try
        {
            queryText = Ext4.getCmp("qep").getSourceEditor().getValue();
        }
        catch(ex)
        {
            // pass;
        }

        var initPrompt = '';
        if (schemaName)
            initPrompt += "The current schema is " + schemaName + ".\n";

        if (queryText)
            initPrompt += "This is my current SQL query, this may be relevant to subsequent prompts:\n```" + queryText + "```\n";

        if (initPrompt)
        {
            var url = new URL('./query-queryagent.api', window.location.href);
            LABKEY.Ajax.request({
                url: url,
                method: 'POST',
                params: {
                    prompt: initPrompt,
                    schemaName: schemaName || ''
                }
            });
        }
    }

    let firstChat = true;
    if (isChatReady)
    {
        elPrompt.addEventListener('keydown', function (ev)
        {
            var isEnter = (ev.key === 'Enter') || (ev.keyCode === 13);
            if (ev.shiftKey && isEnter)
            {
                const prompt = elPrompt.value;
                if (!prompt)
                    return;
                if (firstChat)
                {
                    firstChat = false;
                    initChat();
                }
                appendUserPrompt(prompt);
                elPrompt.value = '';
                // TODO waiting/thinking UI
                // Build URL with same base as current document, endpoint /query-queryagent.api and prompt parameter
                var url = new URL('./query-queryagent.api', window.location.href);
                LABKEY.Ajax.request({
                    url: url,
                    method: 'POST',
                    params: {prompt: prompt},
                    callback: function (config, success, xhr) {
                        if (success) {
                            var responseJson = JSON.parse(xhr.responseText);
                            var responseText = responseJson['text'];
                            var responseHtml = responseJson['html'];
                            var responseSql = responseJson['sql'];
                            if (responseSql) {
                                Ext4.getCmp("qep").getSourceEditor().setValue(responseSql);
                                appendSqlResponse(responseSql);
                            }
                            if (responseHtml) {
                                appendHtmlResponse(responseHtml);
                            }
                            if (responseText) {
                                appendTextResponse(responseText);
                            }
                        } else {
                            appendTextResponse('Request failed: ' + xhr.status + ' ' + (xhr.statusText || ''));
                        }
                    }
                });
                ev.preventDefault();
                ev.stopPropagation();
                return false;
            }
            return true;
        });
    }
<%  } %>
});
</script>
