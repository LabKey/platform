<%
/*
 * Copyright (c) 2009-2011 LabKey Corporation
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
<%@ page import="org.labkey.api.util.GUID" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<Map<String, ActionURL>> me = (JspView<Map<String, ActionURL>>) HttpView.currentView();
    Map<String, ActionURL> map = me.getModelBean();
    String guid = GUID.makeGUID();
    boolean first = true;
%>
<table class="labkey-export-tab-contents">
    <tr>
        <td class="labkey-export-tab-options">
            <table class="labkey-export-tab-layout">
                <% for (Map.Entry<String, ActionURL> entry : map.entrySet())
                { %>
                    <tr>
                        <td valign="center"><%= first ? "Scripting language:" : "" %></td>
                        <td valign="center"><input type="radio" <%= first ? "id=\"" + guid + "\"" : "" %> name="scriptExportType" <%= first ? "checked=\"true\"" : "" %> value="<%=h(entry.getValue()) %>"/></td>
                        <td valign="center"><%= h(entry.getKey())%></td>
                    </tr>
                <%  first = false;
                }%>
            </table>
        </td>
        <td class="labkey-export-tab-buttons">
            <%=generateButton("Create Script", "", "var _scriptUrl = getRadioButtonValue(document.getElementById(\"" + guid + "\")); window.open(_scriptUrl, \"_newtab\"); return false;") %>
        </td>
    </tr>
</table>

