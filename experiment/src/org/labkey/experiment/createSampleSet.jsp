<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.experiment.controllers.exp.ExperimentController" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("internal/jQuery");
        dependencies.add("experiment/aliasGroup.js");
    }
%>
<%
    JspView<ExperimentController.CreateSampleSetForm> view = (JspView<ExperimentController.CreateSampleSetForm>) HttpView.currentView();
    ExperimentController.CreateSampleSetForm bean = view.getModelBean();
    String helpText = "Used for generating unique sample IDs (" + helpLink("sampleIDs#expression", "more info") + ")";

%>

<labkey:errors />
<labkey:form action="" method="POST" layout="horizontal" id="createSampleSetForm">
    <labkey:input
            id="name" name="name" label="Name" value="<%=bean.getName()%>"
            contextContent="Name of sample set (required)." size="60" isDisabled="<%=bean.getNameReadOnly()%>"
    />
    <labkey:input
            id="nameExpression" name="nameExpression" label="Name Expression" value="<%=h(bean.getNameExpression())%>"
            placeholder="S-\${now:date}-\${batchRandomId}-\${randomId}"
            contextContent="<%=helpText%>" size="60"
    />
    <div class="form-group">
        <label class=" control-label col-sm-3 col-lg-2">
            Description
        </label>
        <div class="col-sm-9 col-lg-10">
            <textarea name="description" id="description" cols="60" rows="5"><%=text(bean.getDescription())%></textarea>
        </div>
    </div>

    <div class=" form-group">
        <div>
            <div id="extraAlias">
            </div>
            <a class="lk-exp-addAliasGroup" style="cursor: pointer; color: #555;">
                <i class="fa fa-plus-circle"></i> add parent column import alias
            </a>
        </div>
    </div>

    <br/>
    <%=button("Create").id("btnSubmit").submit(true)%>
    <%=button("Cancel").href(bean.getReturnURLHelper())%>
</labkey:form>
<script type="application/javascript">
    +function ($) {
        <%
            if (bean.getRowId() != null) {
        %>
        //TODO this is kinda gross ... can't we just iterate over the bean?
        $(document).ready(function(){
            LABKEY.Query.selectRows({
                schemaName: "experiment",
                queryName: "materialsource",
                success: onLoadSuccess,
                failure: onLoadFailure,
                filterArray: [LABKEY.Filter.create("RowId", rowId)]
            });
        });
        <%--LABKEY.Query.selectRows({--%>
        <%--schemaName: "pipeline",--%>
        <%--queryName: "TriggerConfigurations",--%>
        <%--success: onSuccess,--%>
        <%--failure: onFailure,--%>
        <%--filterArray: [LABKEY.Filter.create("RowId", <%=bean.getImportAliases()%>)]--%>
        <%--});--%>
        <%
            }
        %>
    }(jQuery);
</script>


