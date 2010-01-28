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
<%@ page import="org.labkey.core.workbook.WorkbookSearchBean" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.core.CoreController" %>
<%@ page import="org.labkey.api.data.Container" %>
<%
    JspView<WorkbookSearchBean> me = (JspView<WorkbookSearchBean>) HttpView.currentView();
    WorkbookSearchBean searchBean = me.getModelBean();
    Container container = me.getViewContext().getContainer();
%>
<form method="GET" action="<%=new ActionURL(CoreController.LookupWorkbookAction.class, container)%>">
    <input type="text" id="wbsearch-id" name="id" size="20" value=""/>
    <%=PageFlowUtil.generateSubmitButton("Go")%>
</form>

<script type="text/javascript">
    Ext.onReady(function(){
        new Ext.form.TextField({
            applyTo: 'wbsearch-id',
            emptyText: 'Jump to ID'
        });
    });
</script>