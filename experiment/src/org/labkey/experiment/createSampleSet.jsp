<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.experiment.controllers.exp.ExperimentController" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    JspView<ExperimentController.CreateSampleSetForm> view = (JspView<ExperimentController.CreateSampleSetForm>) HttpView.currentView();
    ExperimentController.CreateSampleSetForm bean = view.getModelBean();
    String helpText = "Used for generating unique sample IDs (" + helpLink("sampleIDs#expression", "more info") + ")";
%>

<labkey:errors />
<labkey:form action="" method="POST" layout="horizontal">
    <labkey:input
        id="name" name="name" label="Name" value="<%=bean.getName()%>"
        contextContent="Name of sample set (required)." size="60" isDisabled="<%=bean.getNameReadOnly()%>"
    />
    <labkey:input
        id="nameExpression" name="nameExpression" label="Name Expression" value="<%=h(bean.getNameExpression())%>"
        placeholder="S-\${now:date}-\${batchRandomId}-\${randomId}"
        contextContent="<%=helpText%>" size="60"
    />
    <br/>
    <%=button("Create").submit(true)%>
    <%=button("Cancel").href(bean.getReturnURLHelper())%>
</labkey:form>



