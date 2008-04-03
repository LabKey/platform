<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.reports.Report" %>
<%@ page import="org.labkey.api.reports.report.view.RReportBean" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
    JspView<RReportBean> me = (JspView<RReportBean>) HttpView.currentView();
    RReportBean bean = me.getModelBean();
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
