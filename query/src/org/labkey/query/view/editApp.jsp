<%
/*
 * Copyright (c) 2014-2016 LabKey Corporation
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
<%@ page import="org.json.JSONObject" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.query.controllers.OlapController" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
        dependencies.add("codemirror");
    }
%>
<%
    Map<String, Object> context = (Map<String, Object>)HttpView.currentModel();
    String contextName = (String)context.get("name");
    JSONObject defaults = (JSONObject)context.get("defaults");
    JSONObject values = (JSONObject)context.get("values");
%>
<script>
    function updateApp()
    {
        var contextName = document.getElementById("contextName").value;
        var defaults = defaultsEditor.getValue();
        var values = valuesEditor.getValue();

        var jsonData = {
            contextName: contextName
        };

        if (defaults)
            jsonData.defaults = defaults;

        if (values)
            jsonData.values = values;

        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL("olap", "updateApp"),
            method: 'POST',
            jsonData: jsonData,
            success: LABKEY.Utils.getCallbackWrapper(function () {
                var msgEl = Ext4.get('message');
                msgEl.setVisibilityMode(Ext4.dom.Element.DISPLAY);
                msgEl.setVisible(true);
                msgEl.setOpacity(1);
                msgEl.dom.innerHTML = "<span class='labkey-message'>Save successful</span>";
                msgEl.fadeOut({
                    delay: 500,
                    duration: 2000,
                    remove: false,
                    useDisplay: true,
                    callback: function () {
                        msgEl.dom.innerHTML = "";
                    }
                });
            }),
            failure: LABKEY.Utils.getCallbackWrapper(function (data) {
                var msgEl = Ext4.get('message');
                msgEl.setVisibilityMode(Ext4.dom.Element.DISPLAY);
                msgEl.setVisible(true);
                msgEl.setOpacity(1);
                msgEl.dom.innerHTML = "<span class='labkey-error'>Error during save:<br>" + Ext4.htmlEncode(data.exception) + "</span>";
            }, true)
        })
    }

    var defaultsEditor;
    var valuesEditor;

    LABKEY.Utils.onReady(function () {
        // Create json editor
        defaultsEditor = CodeMirror.fromTextArea(document.getElementById("defaults"), {
            mode         : { name: 'javascript', json: true },
            lineNumbers  : true,
            lineWrapping : true,
            indentUnit   : <%=OlapController.APP_CONTEXT_JSON_INDENT%>
        });

        defaultsEditor.setSize(800, 300);
        LABKEY.codemirror.RegisterEditorInstance('defaults', defaultsEditor);


        // Create json editor
        valuesEditor = CodeMirror.fromTextArea(document.getElementById("values"), {
            mode         : { name: 'javascript', json: true },
            lineNumbers  : true,
            lineWrapping : true,
            indentUnit   : <%=OlapController.APP_CONTEXT_JSON_INDENT%>
        });

        valuesEditor.setSize(800, 300);
        LABKEY.codemirror.RegisterEditorInstance('values', valuesEditor);
    });
</script>

<labkey:errors/>

<div id="message"></div>
<table>
    <tr>
        <td class="labkey-form-label">Context name:</td>
        <td><input type="text" name="contextName" id="contextName" value="<%=h(contextName)%>"<%=disabled(contextName != null)%>></td>
    </tr>

    <tr>
        <td valign="top" class="labkey-form-label">Defaults:</td>
        <td>
            <textarea name="defaults" id="defaults"><%=text(defaults == null ? "" : defaults.toString(OlapController.APP_CONTEXT_JSON_INDENT))%></textarea>
        </td>
    </tr>

    <tr>
        <td valign="top" class="labkey-form-label">Values:</td>
        <td>
            <textarea name="values" id="values"><%=text(values == null ? "" : values.toString(OlapController.APP_CONTEXT_JSON_INDENT))%></textarea>
        </td>
    </tr>
</p>

</table>

<p>
<labkey:button text="Save" onclick="updateApp();return false;"/>

