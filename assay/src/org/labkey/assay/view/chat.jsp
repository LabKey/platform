<%
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
%>
<%@ page import="org.labkey.api.util.InputBuilder.Input" %>
<%@ page import="org.labkey.api.util.TextAreaBuilder" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.assay.AssayController.UpdateQCStateForm" %>
<%@ page import="org.labkey.api.assay.actions.AssayRunsAction" %>
<%@ page import="org.labkey.api.util.HtmlString" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
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
    JspView<AssayRunsAction.TransformForm> form = HttpView.currentView();
    AssayRunsAction.TransformForm bean = form.getModelBean();

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
</style>

<script type="text/javascript" nonce="<%=getScriptNonce()%>">

    (function($){

        saveState = function(){

            let form = document.querySelector('#qc_form');
            if (form){
                const formData = new FormData(form);

                const url = buildURL('test', 'chatendpoint.api', { prompt });

                LABKEY.Ajax.request({
                    method  : 'POST',
                    url     : LABKEY.ActionURL.buildURL("test", "chatendpoint.api"),
                    form    : new FormData(form),
                    success : LABKEY.Utils.getCallbackWrapper(function(response)
                    {
                        console.log('agent response', response);
                    }),
                    failure : LABKEY.Utils.displayAjaxErrorResponse
                });
            }
        };

        $(document).ready(function () {
        });

    })(jQuery);

    Ext4.onReady(function() {
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

        {
            elPrompt.addEventListener('keydown', function (ev) {
                var isEnter = (ev.key === 'Enter') || (ev.keyCode === 13);
                if (ev.shiftKey && isEnter) {
                    const prompt = elPrompt.value;
                    if (!prompt)
                        return;
                    appendUserPrompt(prompt);
                    elPrompt.value = '';
                    // TODO waiting/thinking UI
                    // Build URL with same base as current document, endpoint /query-queryagent.api and prompt parameter
                    var url = new URL('./query-transformagent.api', window.location.href);
                    url.searchParams.set('prompt', prompt);
                    url.searchParams.set('protocolId', <%=bean.getProtocolId()%>);
                    var req = new XMLHttpRequest();
                    req.open('GET', url.toString(), true);
                    req.onreadystatechange = function () {
                        if (req.readyState === 4) {
                            if (req.status >= 200 && req.status < 300) {
                                var responseJson = JSON.parse(req.responseText);
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
                                appendTextResponse('Request failed: ' + req.status + ' ' + (req.statusText || ''));
                            }
                        }
                    };
                    req.send();
                    ev.preventDefault();
                    ev.stopPropagation();
                    return false;
                }
                return true;
            });
        }
    }, this);

</script>

<table style="width:100%; min-width:600px" id="querySourceLayout"><tr>
<%--
    <td style="width:80%; min-width:400px; vertical-align: top; ">
        <div id="status" class="labkey-status-info" style="visibility: hidden;" width="100%">(status)</div>
        <div id="query-editor-panel" class="extContainer"></div>
    </td>
--%>
    <td style="width:20%; min-width:200px; vertical-align: top;">
        <div id="chatHistory" style="height:500px; width:100%; overflow:scroll;" ></div>
        <textarea id="geminiPrompt" style="height:100px; width:100%;" placeholder="Shift-Enter to submit"></textarea>
    </td>
</tr></table>
