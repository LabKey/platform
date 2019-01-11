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

<style type="text/css">
    .form-item {
        padding: 5px 0 0 30px;
    }
    .form-item input {
        padding: 3px;
    }
    .form-item select {
        width: 360px;
        padding: 3px;
    }
    .form-longinput {
        width: 350px;
    }
    .form-label {
        width: 135px;
        display: inline-block;
    }
</style>


<labkey:errors />
<labkey:form action="" method="POST">

    <div class="form-item">
        <div class="form-label" title="Name of the sample set (required)"><label for="name">Name *:</label></div>
        <input type="text" id="name" name="name" value="<%=h(bean.getName() == null ? "" : bean.getName())%>">
    </div>

    <div class="form-item">
        <div class="form-label" title="Name expression to use for generating unique sample ids"><label for="nameExpression">Name Expression:</label></div>
        <input type="text" id="nameExpression" name="nameExpression" class="form-longinput" value="<%=h(bean.getNameExpression() == null ? "" : bean.getNameExpression())%>">
    </div>
    <br/>

    <div class="form-buttons">
        <%=button("Create").submit(true)%>
        <%=button("Cancel").href(returnUrl)%>
    </div>
</labkey:form>



