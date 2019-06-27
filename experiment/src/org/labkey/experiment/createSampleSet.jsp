<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.experiment.controllers.exp.ExperimentController" %>
<%@ page import="org.labkey.api.exp.api.ExpSampleSet" %>
<%@ page import="org.labkey.api.exp.api.ExpDataClass" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("internal/jQuery");
    }
%>
<%
    JspView<ExperimentController.BaseSampleSetForm> view = (JspView<ExperimentController.BaseSampleSetForm>) HttpView.currentView();
    ExperimentController.BaseSampleSetForm bean = view.getModelBean();
    String helpText = "Used for generating unique sample IDs (" + helpLink("sampleIDs#expression", "more info") + ")";

%>

<labkey:errors />
<labkey:form action="" method="POST" layout="horizontal" id="sampleSetForm">
    <labkey:input
            id="name" name="name" label="Name" isReadOnly="<%=bean.isUpdate()%>" value="<%=h(bean.getName())%>"
            contextContent="Name of sample set (required)." size="60"
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
            <div id="extraAlias" >
            </div>
            <a class="lk-exp-addAliasGroup" style="cursor: pointer; color: #555;">
                <i class="fa fa-plus-circle"></i> add parent column import alias
            </a>
        </div>
    </div>

    <br/>
    <labkey:input type="hidden" name="isUpdate" value="<%=h(bean.isUpdate())%>"/>
    <labkey:input type="hidden" name="LSID" value="<%=h(bean.getLSID())%>"/>
    <labkey:input type="hidden" name="rowId" value="<%=h(bean.getRowId())%>"/>

    <%=button(bean.isUpdate() ? "Update" : "Create").id("btnSubmit").submit(true)%>
    <%=button("Cancel").href(bean.getReturnURLHelper())%>
</labkey:form>
<script type="application/javascript">
    +function ($) {
        $(document).ready(function(){
            function processAliasJson(aliases) {
                if (aliases) {
                    for (var j in aliases) {
                        if (aliases.hasOwnProperty(j) && aliases[j]) {
                            addAliasGroup(j, aliases[j])
                        }
                    }
                }
            }

            var parentAliasTemplate = new DocumentFragment();
            var selectList = document.createElement("datalist");
            selectList.setAttribute("id", "materialInputs");
            selectList.setAttribute("class", "form-control lk-exp-alias-value");
            selectList.hidden = true;
            parentAliasTemplate.appendChild(selectList);

            var sampleSetList = [];
            <%
                if (bean.getSampleSetList() != null && bean.getSampleSetList().size() > 0) {
                for (ExpSampleSet ss : bean.getSampleSetList()) {
            %>
                sampleSetList.push(<%=q(ss.getName())%>);
            <%
                }
            }
            %>
            var dataClassList = [];
            <%
            if (bean.getDataClassList() != null && bean.getDataClassList().size() > 0) {
            for (ExpDataClass dc : bean.getDataClassList()) {
            %>
            dataClassList.push(<%=q(dc.getName())%>);
            <%
                }
            }
            %>

            function createOptions(list, selectEl, valPrefix) {
                for (var i = 0; i < list.length; i++) {
                    var option = document.createElement("option");
                    option.value = valPrefix + '/' + list[i];
                    option.text = list[i];
                    selectEl.appendChild(option);
                }
            }

            createOptions(sampleSetList, selectList, 'materialInputs');
            createOptions(dataClassList, selectList, 'dataInputs');
            $('#extraAlias').append(parentAliasTemplate);

            function addAliasGroup(key, value) {
                let elem = $("<div class='form-group lk-exp-alias-group' name='importAliases'>" +
                        "<label class=' control-label col-sm-3 col-lg-2'>Parent Alias</label>" +
                        "<div class='col-sm-3 col-lg-2'>" +
                        "<input type='text' class='form-control lk-exp-alias-key' placeholder='Import Header' name='importAliasKeys' style='float: right;'>" +
                        "</div>" +
                        "<div class='col-sm-3 col-lg-2'>" +
                        //TODO should this be a dropdown selector of existing SampleSets? -- yes
                        "<input type='text' class='form-control lk-exp-alias-value' placeholder='Parent' name='importAliasValues' list='materialInputs' style='display: inline-block;'>" +
                        "<a class='removeAliasTrigger' style='cursor: pointer;' title='remove'><i class='fa fa-trash' style='padding: 0 8px; color: #555;'></i></a>" +
                        "</div>" +
                        "</div>");

                elem.append(parentAliasTemplate.cloneNode(true));

                if (key && value) {
                    elem.find(".lk-exp-alias-key").val(key);
                    elem.find(".lk-exp-alias-value").val(value);
                }

                elem.appendTo($("#extraAlias"));
            }

            $('#extraAlias').on('click', '.removeAliasTrigger' , function() {
                $(this).parents('.lk-exp-alias-group').remove();
            });

            $(".lk-exp-addAliasGroup").on('click', function () {
                addAliasGroup();
            });

            $("#btnSubmit").on('click', (function() {
                let data = {};
                $("#createSampleSetForm").serializeArray().map(function(x){
                    if (!data[x.name]) {
                        data[x.name] = x.value;
                    } else {
                        if (!$.isArray(data[x.name])){
                            let prev = data[x.name];
                            data[x.name] = [prev];
                        }
                        data[x.name].push(x.value);
                    }
                });
            }));
        <%
            if (bean.getRowId() != null && StringUtils.isNotBlank(bean.getImportAliasJson())) {
        %>
            let aliases = JSON.parse(<%=q(bean.getImportAliasJson())%>);
            processAliasJson(aliases);
        <%
            }
        %>
        });
    }(jQuery);
</script>


