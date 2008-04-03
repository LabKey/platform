<%@ page import="org.apache.commons.lang.StringUtils" %>
<%@ page import="org.labkey.api.view.*" %>
<%@ page import="org.labkey.common.util.Pair" %>
<%@ page import="org.labkey.study.controllers.OldStudyController" %>
<%@ page import="java.util.Collections" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.AppProps" %>
<%@ page extends="org.labkey.api.jsp.JspBase"%>

<%
    JspView<OldStudyController.ViewPrefsBean> me = (JspView<OldStudyController.ViewPrefsBean>) HttpView.currentView();
    OldStudyController.ViewPrefsBean bean = me.getModelBean();
    ActionURL url = HttpView.currentContext().cloneActionURL();

    ViewContext context = HttpView.currentContext();
    String defaultView = OldStudyController.getDefaultView(context, bean.getDataSetDefinition().getDataSetId());
%>

<table class="normal">
    <tr class="wpHeader">
        <th colspan="3" align="left">Default View<%=PageFlowUtil.helpPopup("Default View", "Select the default View that will display from the Study Datasets Web Part")%></th>
    </tr>
    <%
        if (bean.getViews().size() > 1) {
            for (Pair<String, String> view : bean.getViews()) {
    %>
            <tr><td><%=getLabel(view, defaultView)%></td>
                <td>&nbsp;</td>
                <td>[<a href="<%=url.relativeUrl("viewPreferences", Collections.singletonMap("defaultView", view.getValue()), "Study", false)%>">select</a>]</td>
            </tr>
    <%
        }
        } else {
    %>
        <tr><td>There is only a single view for this dataset.</td></tr>
    <%
        }
        ActionURL doneUrl = HttpView.currentContext().cloneActionURL();
        doneUrl.setAction("datasetReport");
        doneUrl.deleteParameter("defaultView");
        doneUrl.replaceParameter("Dataset.viewName", defaultView);
    %>
        <tr><td>&nbsp;</td></tr>
        <tr><td><%=PageFlowUtil.buttonLink("Done", doneUrl)%></td></tr>
</table>

<%!
    String getLabel(Pair<String, String> view, String defaultView) {
        if (StringUtils.equals(view.getValue(), defaultView))
            return "<b>" + view.getKey() + "</b>";

        return view.getKey();
    }
%>


