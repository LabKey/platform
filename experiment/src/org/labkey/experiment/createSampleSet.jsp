<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.experiment.controllers.exp.ExperimentController" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    JspView<ExperimentController.CreateSampleSetForm> view = (JspView<ExperimentController.CreateSampleSetForm>) HttpView.currentView();
    ExperimentController.CreateSampleSetForm bean = view.getModelBean();
    String returnUrl = getViewContext().getActionURL().getParameter("returnUrl");
%>

<labkey:errors />
<labkey:form action="" method="POST" layout="horizontal">
    <labkey:input
        id="name" name="name" label="Name" value="<%=h(bean.getName())%>"
        contextContent="Name of the sample set (required)." forceSmallContext="true" size="60"
    />
    <labkey:input
        id="nameExpression" name="nameExpression" label="Name Expression" value="<%=h(bean.getNameExpression())%>"
        placeholder="\${DataInputs:first:defaultValue('S')}-\${now:date}-\${batchRandomId}"
        contextContent="Name expression to use for generating unique sample ids." forceSmallContext="true" size="60"
    />
    <br/>
    <%=button("Create").submit(true)%>
    <%=button("Cancel").href(returnUrl)%>
</labkey:form>



