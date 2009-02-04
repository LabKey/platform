<%
/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.view.*" %>
<%@ page import="org.labkey.api.study.assay.AssayService" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.TreeMap" %>
<%@ page import="org.labkey.api.study.assay.AssayProvider" %>
<%@ page import="org.labkey.study.view.AssayDetailsWebPartFactory" %>
<%@ page import="org.labkey.api.exp.api.ExpProtocol" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<Portal.WebPart> me = (JspView<Portal.WebPart>) HttpView.currentView();
    Portal.WebPart bean = me.getModelBean();
    ViewContext ctx = me.getViewContext();
    ActionURL postUrl = new ActionURL("Project", "customizeWebPart.post", ctx.getContainer());
    String viewProtocolIdStr = bean.getPropertyMap().get(AssayDetailsWebPartFactory.PREFERENCE_KEY);
    int viewProtocolId = -1;
    try
    {
        if (viewProtocolIdStr != null)
            viewProtocolId = Integer.parseInt(viewProtocolIdStr);
    }
    catch (NumberFormatException e)
    {
        // fall through
    }

    // show buttons should be checked by default for a new assay details webpart.  Otherwise, we preserve the persisted setting:
    boolean showButtons = true;
    if (viewProtocolId >= 0)
    {
        showButtons = Boolean.parseBoolean(bean.getPropertyMap().get(AssayDetailsWebPartFactory.SHOW_BUTTONS_KEY));
    }

    Map<String, Integer> nameToId = new TreeMap<String, Integer>();
    for (ExpProtocol protocol : AssayService.get().getAssayProtocols(ctx.getContainer()))
    {
        AssayProvider provider = AssayService.get().getProvider(protocol);
        nameToId.put(provider.getName() + ": " + protocol.getName(), protocol.getRowId());
    }
%>
<p>This web part displays a list of runs for a specific assay.</p>

<form action="<%=postUrl%>" method="post">
    <input type="hidden" name="pageId" value="<%=bean.getPageId()%>">
    <input type="hidden" name="index" value="<%=bean.getIndex()%>">
    <table>
        <tr>
            <td class="labkey-form-label">Assay</td>
            <td>
                <select name="<%= AssayDetailsWebPartFactory.PREFERENCE_KEY %>">
                    <%
                        for (Map.Entry<String, Integer> entry : nameToId.entrySet())
                        {
                    %>
                         <option value="<%= entry.getValue() %>" <%= viewProtocolId == entry.getValue() ? "SELECTED" : ""%>>
                            <%= h(entry.getKey()) %></option>
                    <%
                        }
                    %>
                </select>
            </td>
        </tr>
        <tr>
            <td class="labkey-form-label">Show buttons in web part</td>
            <td><input type="checkbox" name="<%= AssayDetailsWebPartFactory.SHOW_BUTTONS_KEY%>" value="true" <%= showButtons ? "CHECKED" : "" %>></td>
        </tr>
        <tr>
            <td/>
            <td><%=generateSubmitButton("Submit")%> <%=generateButton("Cancel", "begin.view")%></td>
        </tr>
    </table>
</form>