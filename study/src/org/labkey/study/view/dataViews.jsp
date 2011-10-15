<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.Portal" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<Portal.WebPart> me = (JspView) HttpView.currentView();
    User u = me.getViewContext().getUser();
    int webPartId = me.getModelBean().getRowId();
%>
<div>
    <div id='dataset-browsing-<%=me.getModelBean().getIndex()%>'></div>
</div>
<script type="text/javascript">

    LABKEY.requiresExt4Sandbox(true);

    LABKEY.requiresCss("studyRedesign/redesign.css");
    LABKEY.requiresCss("study/DataViewsPanel.css");

    LABKEY.requiresScript("study/DataViewsPanel.js");

</script>
<script type="text/javascript">

    function init()
    {
        var dataViewsPanel = Ext4.create('LABKEY.ext4.DataViewsPanel', {
            id       : 'data-views-panel-<%= webPartId %>',
            renderTo : 'dataset-browsing-<%= me.getModelBean().getIndex() %>',
            pageId   : <%= PageFlowUtil.jsString(me.getModelBean().getPageId()) %>,
            index    : <%= me.getModelBean().getIndex() %>,
            webpartId: <%= webPartId %>,
            allowCustomize : <%= me.getViewContext().getContainer().hasPermission(u, AdminPermission.class) %>
        });
    }

    /**
     * Called by Server to handle cusomization actions. NOTE: The panel must be set to allow customization
     * See LABKEY.ext4.DataViewsPanel.isCustomizable()
     */
    function customizeDataViews(webpartId, pageId, index) {

        // eew, should find better way to access global scope
        var panel = Ext4.getCmp('data-views-panel-' + webpartId);

        if (panel) { panel.customize(); }
    }

    Ext4.onReady(init);
</script>
