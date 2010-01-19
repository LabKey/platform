<%
/*
 * Copyright (c) 2010 LabKey Corporation
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
<%@ page import="org.labkey.core.workbook.CreateWorkbookBean" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    JspView<CreateWorkbookBean> me = (JspView<CreateWorkbookBean>) HttpView.currentView();
    CreateWorkbookBean searchBean = me.getModelBean();
    Container container = me.getViewContext().getContainer();
%>
<style type="text/css">
    .cwb-layout-table
    {
        width: 100%;
        padding: 2px;
    }
    .cwb-input
    {
        width: 100%
    }
    .cwb-button-bar
    {
        text-align:right;
    }
    td.labkey-form-label
    {
        width: 1%;
    }
</style>
<labkey:errors/>
<form action="createWorkbook.post" method="POST">
    <table class="cwb-layout-table">
        <tr>
            <td class="labkey-form-label">Title:</td>
            <td><input type="text" name="title" class="cwb-input" value="<%=searchBean.getTitle()%>"/></td>
        </tr>
        <tr>
            <td class="labkey-form-label">Description:</td>
            <td>
                <textarea name="description" rows="4" cols="40" class="cwb-input"><%=null == searchBean.getDescription() ? "" : searchBean.getDescription()%></textarea>
            </td>
        </tr>
        <tr>
            <td colspan="2" class="cwb-button-bar">
                <%=PageFlowUtil.generateButton("Cancel", container.getStartURL(me.getViewContext()))%>
                <%=PageFlowUtil.generateSubmitButton("Create Workbook")%>
            </td>
        </tr>
    </table>
</form>
