<%
/*
 * Copyright (c) 2007-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.data.RenderContext" %>
<%@ page import="org.labkey.api.study.assay.ThawListBean" %>
<%@ page import="org.labkey.api.study.assay.ThawListResolverType" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!

    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromFilePath("ext3"));
        return resources;
    }
%>
<%
    JspView<ThawListBean> thisView = (JspView<ThawListBean>)HttpView.currentView();
    ThawListBean bean = thisView.getModelBean();
    RenderContext ctx = bean.getRenderContext();
    boolean listType = ThawListResolverType.LIST_NAMESPACE_SUFFIX.equalsIgnoreCase((String)ctx.getForm().get(ThawListResolverType.THAW_LIST_TYPE_INPUT_NAME));
    boolean textType = !listType;
%>
<table>
    <tr>
        <td><input type="radio" name="<%= ThawListResolverType.THAW_LIST_TYPE_INPUT_NAME %>"<%=checked(textType)%> value="<%= ThawListResolverType.TEXT_NAMESPACE_SUFFIX %>" onClick="document.getElementById('ThawListDiv-List').style.display='none'; document.getElementById('ThawListDiv-TextArea').style.display='block';"></td>
        <td>Paste a sample list as a TSV (tab-separated values)<%= helpPopup("Sample Lookup", "A lookup lets you assign a mapping from your own specimen numbers to participants and visits. The format is a tab-separated values (TSV), requiring the column 'Index' and using the values of the columns 'SpecimenID', 'ParticipantID', and 'VisitID'.<p>To use the template, fill in the values, select the entire spreadsheet (Ctrl-A), copy it to the clipboard, and paste it into the text area below.", true) %> <%=textLink("download template", request.getContextPath()+"/study/assay/SampleLookupTemplate.xls")%></td>
    </tr>
    <tr>
        <td></td>
        <td><div id="ThawListDiv-TextArea" style="display:<%= textType ? "block" : "none" %>;"><textarea id=<%= ThawListResolverType.THAW_LIST_TEXT_AREA_INPUT_NAME %> name="<%= ThawListResolverType.THAW_LIST_TEXT_AREA_INPUT_NAME %>" rows="4" cols="50"><%= h(ctx.get(ThawListResolverType.THAW_LIST_TEXT_AREA_INPUT_NAME)) %></textarea></div></td>
    </tr>
    <tr>
        <td><input type="radio" name="<%= ThawListResolverType.THAW_LIST_TYPE_INPUT_NAME %>"<%=checked(listType)%> value="<%= ThawListResolverType.LIST_NAMESPACE_SUFFIX %>" onClick="document.getElementById('ThawListDiv-List').style.display='block'; document.getElementById('ThawListDiv-TextArea').style.display='none';"></td>
        <td>Use an existing list<%= helpPopup("Sample Lookup", "A lookup lets you assign a mapping from your own specimen numbers to participants and visits. The target list must have your own specimen identifier as its primary key, and uses the values of the 'SpecimenID', 'ParticipantID', and 'VisitID' columns.") %></td>
    </tr>
    <tr>
        <td></td>
        <td>
            <input type="hidden" id="<%= ThawListResolverType.THAW_LIST_LIST_CONTAINER_INPUT_NAME %>" name="<%= ThawListResolverType.THAW_LIST_LIST_CONTAINER_INPUT_NAME %>" value="<%= h(ctx.getForm().get(ThawListResolverType.THAW_LIST_LIST_CONTAINER_INPUT_NAME)) %>"/>
            <input type="hidden" id="<%= ThawListResolverType.THAW_LIST_LIST_SCHEMA_NAME_INPUT_NAME %>" name="<%= ThawListResolverType.THAW_LIST_LIST_SCHEMA_NAME_INPUT_NAME %>" value="<%= h(ctx.getForm().get(ThawListResolverType.THAW_LIST_LIST_SCHEMA_NAME_INPUT_NAME)) %>"/>
            <input type="hidden" id="<%= ThawListResolverType.THAW_LIST_LIST_QUERY_NAME_INPUT_NAME %>" name="<%= ThawListResolverType.THAW_LIST_LIST_QUERY_NAME_INPUT_NAME%>" value="<%= h(ctx.getForm().get(ThawListResolverType.THAW_LIST_LIST_QUERY_NAME_INPUT_NAME)) %>"/>
            <div id="ThawListDiv-List" style="display:<%= listType ? "block" : "none" %>;">
                <% include(bean.getListChooser(), out); %>
            </div>
        </td>
    </tr>
</table>

<script type="text/javascript">
    // Allow tabs in the TSV text area
    Ext.EventManager.on('<%= ThawListResolverType.THAW_LIST_TEXT_AREA_INPUT_NAME %>', 'keydown', LABKEY.ext.Utils.handleTabsInTextArea);
</script>
