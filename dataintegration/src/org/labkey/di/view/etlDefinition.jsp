<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.di.EtlDef" %>
<%@ page import="org.labkey.di.view.DataIntegrationController" %>
<%@ page import="java.util.Collections" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("codemirror");
    }
%>
<%
    JspView<DataIntegrationController.EtlDefinitionForm> me =
            (JspView<DataIntegrationController.EtlDefinitionForm>) HttpView.currentView();
    DataIntegrationController.EtlDefinitionForm form = me.getModelBean();
    EtlDef def = form.getBean();
    boolean newDef = true;
    boolean enabled = false;
    if (null != def.getName())
    {
        newDef = false;
        if (!form.isReadOnly())
        {
            enabled = !DataIntegrationController.getEnabledTransformIds(getContainer(), Collections.singleton(def)).isEmpty();
        }
    }
    ActionURL cancelURL = form.getReturnURLHelper() != null ? form.getReturnActionURL() : getContainer().getStartURL(getUser());
    boolean isAdmin = getViewContext().hasPermission(AdminPermission.class);

    String sampleXml =
            "<etl xmlns=\"http://labkey.org/etl/xml\">\n" +
            "  <name>Add name</name>\n" +
            "  <description>Add description</description>\n" +
            "  <transforms>\n" +
            "    <!--<transform id=\"step1\" type=\"org.labkey.di.pipeline.TransformTask\">\n" +
            "      <description>Copy to target</description>\n" +
            "      <source schemaName=\"etltest\" queryName=\"source\" />\n" +
            "      <destination schemaName=\"etltest\" queryName=\"target\" />\n" +
            "    </transform>-->\n" +
            "  </transforms>\n" +
            "  <incrementalFilter className=\"ModifiedSinceFilterStrategy\" timestampColumnName=\"modified\"/>\n" +
            "  <schedule>\n" +
            "    <poll interval=\"1h\" />\n" +
            "  </schedule>\n" +
            "</etl>\n";
%>
<script type="text/javascript">
    LABKEY.Utils.onReady(function () {
        this.definitionEditor = CodeMirror.fromTextArea(document.getElementById("definition"), {
            mode : "xml",
            lineNumbers : true,
            lineWrapping : true,
            autofocus : true
        });
        LABKEY.codemirror.RegisterEditorInstance('etlDefinition', this.definitionEditor);
        if (<%=form.isReadOnly()%>)
        {
            this.definitionEditor.options.readOnly = "nocursor";
            this.definitionEditor.options.viewportMargin = "Infinity";
            document.getElementsByClassName("CodeMirror")[0].style.backgroundColor = window.getComputedStyle(document.getElementById('etlDef')).backgroundColor;
        }
        else
        {
            this.definitionEditor.setSize(1200, 500);

        }
        document.getElementsByClassName("CodeMirror")[0].style.border = window.getComputedStyle(document.getElementById("definition")).border;
    });
</script>
<style type="text/css">
    .CodeMirror {
        height: auto;
        width: 1200px;
    }
</style>

<h4 id="name"><%=h(newDef ? "New Definition" : "Definition: \"" + def.getName() + "\"")%></h4>
<% if (enabled) { %>
    <p>Warning: This ETL has been enabled and is scheduled to run. Modifying it will unschedule it from future runs, and it must be reenabled.</p>
<% } %>
<br/>
<labkey:errors/>
<labkey:form id="etlDefinitionForm" method="post">
    <table id="etlDef">
        <tr>
            <td colspan="2">
                <textarea name="definition" id="definition" cols="80" rows="30"><%=text(def.getDefinition() == null ? sampleXml : def.getDefinition())%></textarea>
            </td>
        </tr>
    </table>
    <br/>
    <% String cancelText = "Cancel";
    if (form.isReadOnly()) {
        cancelText = "Show Grid";
        ActionURL editUrl = new ActionURL(DataIntegrationController.EditDefinitionAction.class, getContainer());
        editUrl.addParameter("etlDefId", def.getEtlDefId());
        editUrl.addReturnURL(cancelURL);
        if (isAdmin) { %>
            <labkey:button text="Edit" id="editButton" href="<%=editUrl%>"/>
        <% } %>
    <% } else if (isAdmin){%> <%-- Should always be admin to get here, but just to be safe --%>
    <labkey:button text="Save" id="submitButton"/>
    <% } %>
    <labkey:button text="<%=h(cancelText)%>" href="<%= cancelURL %>"/>
</labkey:form>