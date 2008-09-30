<%
/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.exp.list.ListDefinition"%>
<%@ page import="org.labkey.api.exp.list.ListService" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.Portal" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.TreeMap" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    HttpView<Portal.WebPart> me = (HttpView<Portal.WebPart>) HttpView.currentView();
    Portal.WebPart part = me.getModelBean();
    ViewContext ctx = me.getViewContext();
    Map<String, String> props = part.getPropertyMap();

    Map<String, String> listOptions = new TreeMap<String, String>();
    Map<String, ListDefinition> lists = ListService.get().getLists(ctx.getContainer());
    for (String name : lists.keySet())
    {
        listOptions.put(String.valueOf(lists.get(name).getListId()), name);
    }
%>
This webpart displays data from a single list.<br><br>

If you want to let users change the list that's displayed or customize the view themselves then use the query webpart.<br><br>

<form name="frmCustomize" method="post" action="<%=h(part.getCustomizePostURL(ctx.getContainer()))%>">
    <table>
        <tr>
            <td>Title:</td>
            <td><input type="text" name="title" width="60" value="<%=h(props.get("title"))%>"></td>
        </tr>
        <tr>
            <td>List:</td>
            <td>
                <select name="listId">
                    <labkey:options value="<%=props.get("listId")%>" map="<%=listOptions%>" />
                </select>
            </td>
        </tr>
        <tr>
            <td>View Name:</td>
            <td><input type="text" name="viewName" width="60" value="<%=h(props.get("viewName"))%>"><%=null != props.get("viewName") ? " Clear this value to display the default view" : ""%></td>
        </tr>
        <tr>
            <td colspan="2"><labkey:button text="Submit"/></td>
        </tr>
    </table>
</form>