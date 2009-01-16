<%
/*
 * Copyright (c) 2008-2009 LabKey Corporation
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
<%@ page import="org.labkey.api.reports.report.view.ScriptReportBean"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
    JspView<ScriptReportBean> me = (JspView<ScriptReportBean>) HttpView.currentView();
    ScriptReportBean bean = me.getModelBean();
%>

<script type="text/javascript">
    // javascript to help manage report dirty state across tabs and across views.
    //
    function switchTab(destinationURL, saveHandler)
    {
        LABKEY.setSubmit(true);

        if (saveHandler)
        {
            saveHandler(destinationURL);
            return false;
        }
        else
        {
            if (destinationURL)
                window.location = destinationURL;
        }
    }

    LABKEY.setDirty(<%=bean.getIsDirty()%>);

    function viewDirty()
    {
        if (typeof pageDirty != "undefined")
            return pageDirty();
        return false;
    }
    window.onbeforeunload = LABKEY.beforeunload(viewDirty);

</script>
