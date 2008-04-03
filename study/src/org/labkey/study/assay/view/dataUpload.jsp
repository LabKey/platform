<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.study.actions.AssayRunUploadForm" %>
<%@ page import="org.labkey.api.study.assay.AssayDataCollector" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.io.File" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.api.exp.ExperimentException" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<AssayRunUploadForm> me = (JspView<org.labkey.api.study.actions.AssayRunUploadForm>) HttpView.currentView();
    AssayRunUploadForm bean = me.getModelBean();
%>
<table>
    <tr>
        <td colspan="2">
            <table>
                <%
                    boolean first = true;
                    List<AssayDataCollector> visibleCollectors = new ArrayList<org.labkey.api.study.assay.AssayDataCollector>();
                    Map<String, File> uploadedData = null;
                    try
                    {
                        uploadedData = bean.getUploadedData();
                    }
                    // These exceptions are reported and handled in UploadWizardAction
                    catch (ExperimentException e)
                    {
                    }

                    for (AssayDataCollector collector : bean.getProvider().getDataCollectors(uploadedData))
                    {
                        if (collector.isVisible())
                        {
                            visibleCollectors.add(collector);
                        }
                    }
                    for (AssayDataCollector collector : visibleCollectors)
                    { %>
                    <tr>
                        <% if (visibleCollectors.size() > 1)
                        { %>
                            <td><input value="<%= h(collector.getShortName()) %>" type="radio" name="dataCollectorName" <% if (first) { %>checked="true" <% } %> onchange="hideAllCollectors(); showCollector('<%= h(collector.getShortName()) %>')"></td>
                        <% }
                        else
                        { %>
                            <td><input value="<%= h(collector.getShortName()) %>" type="hidden" name="dataCollectorName" /></td>
                        <% } %>
                        <td><%= collector.getDescription() %></td>
                    </tr>
                    <tr style="visibility: <%= first ? "visible" : "collapse" %>;" id="collector-<%= h(collector.getShortName()) %>">
                        <td></td>
                        <td>
                            <%= collector.getHTML(bean) %>
                        </td>
                    </tr>
                <%
                    first = false;
                } %>
            </table>
        </td >
    </tr >
</table>
<script type="text/javascript">
    function hideAllCollectors()
    {
        <%
        for (AssayDataCollector collector : visibleCollectors)
        { %>
            document.getElementById('collector-<%= h(collector.getShortName()) %>').style.visibility = 'collapse';
        <% } %>
    }

    function showCollector(collectorName)
    {
        document.getElementById('collector-' + collectorName).style.visibility = 'visible';
    }
</script>
