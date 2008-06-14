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
<%@ page import="org.labkey.query.controllers.InternalSourceViewForm" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.query.persist.CstmView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    InternalSourceViewForm form = (InternalSourceViewForm) HttpView.currentModel();
    ActionURL urlPost = new ActionURL("query", "internalSourceView", getViewContext().getContainer());
    urlPost.addParameter("customViewId", Integer.toString(form.getCustomViewId()));
    ActionURL urlCancel = new ActionURL("query", "manageViews", getViewContext().getContainer());
    CstmView view = form.getViewAndCheckPermission();

%>
<labkey:errors />
<form method = "POST" action="<%=h(urlPost)%>">
    <p>Schema: <%=h(view.getSchema())%><br>
        Query: <%=h(view.getQueryName())%><br>
        Name: <%=h(view.getName())%><br>
        Owner: <%=h(String.valueOf(view.getCustomViewOwner()))%><br>
    </p>
    <table><tr><th>Columns</th><th>Filter/Sort</th></tr>
        <tr><td><textarea name="ff_columnList" rows="20" cols="40"><%=h(form.ff_columnList)%></textarea></td>
        <td><textarea name="ff_filter" rows="20" cols="40"><%=h(form.ff_filter)%></textarea></td>
        </tr>
    </table>

    <labkey:button text="Save" /> <labkey:button text="Cancel" href="<%=urlCancel%>" />
</form>
