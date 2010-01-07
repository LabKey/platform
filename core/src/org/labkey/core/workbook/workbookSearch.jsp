<%
/*
 * Copyright (c) 2006-2009 LabKey Corporation
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
<style type="text/css">
    .wbsearch-empty-search
    {
        color: #CCCCCC;
    }
</style>
<form method="GET" action="<%=new ActionURL(CoreController.LookupWorkbookAction.class, container)%>">
    <input type="text" id="wbsearch-id" name="id" size="20" value="Enter ID or Name" class="wbsearch-empty-search"/>
    <%=PageFlowUtil.generateSubmitButton("Go")%>
</form>

<script type="text/javascript">
    Ext.onReady(function(){
        var input = Ext.get("wbsearch-id");
        if(!input)
            return;
        input.on("focus", function(){
            input.removeClass("wbsearch-empty-search");
            input.set({value:""});
        });
        input.on("blur", function(){
            var value = input.getValue();
            if (!value || value.toString().trim().length <= 0)
            {
                input.addClass("wbsearch-empty-search");
                input.set({value:"Enter ID or Name"});
            }
        });
    });
</script>