<%@ page import="org.labkey.timeline.TimelineSettings" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
JspView<TimelineSettings> me = (JspView<TimelineSettings>) HttpView.currentView();
TimelineSettings bean = me.getModelBean();
%>
<div class="ms-form" style="border:1px solid black;width:100%;height:<%=bean.getPixelHeight()%>px" id="<%=bean.getDivId()%>"></div>
<script type="text/javascript">LABKEY.requiresClientAPI();</script>
<script type="text/javascript">LABKEY.requiresScript("timeline.js");</script>
<script type="text/javascript">
    Ext.onReady(function() {
    var tl = LABKEY.Timeline.create({
        renderTo:<%=q(bean.getDivId())%>,
        start:<%=q(bean.getStartField())%>,
        title:<%=q(bean.getTitleField())%>,
        description:<%=nq(bean.getDescriptionField())%>,
        end:<%=nq(bean.getEndField())%>,
        query:{schemaName:<%=nq(bean.getSchemaName())%>, queryName:<%=nq(bean.getQueryName())%>, viewName:<%=nq(bean.getViewName())%>}})
    });
</script>
<%!
    String nq(String str)
    {
        return str == null ? "null" : q(str);
    } %>