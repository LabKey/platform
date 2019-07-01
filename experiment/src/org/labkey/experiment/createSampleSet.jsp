<%
/*
 * Copyright (c) 2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
%>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.experiment.controllers.exp.ExperimentController" %>
<%@ page import="org.labkey.api.exp.api.ExpSampleSet" %>
<%@ page import="org.labkey.api.exp.api.ExpDataClass" %>
<%@ page import="org.labkey.api.exp.api.SampleSetService" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.api.exp.api.ExperimentService" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="org.labkey.api.util.Pair" %>
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

    List<Pair<String, String>> sampleSetList = new ArrayList<>();
    for (ExpSampleSet ss : SampleSetService.get().getSampleSets(getContainer(), getUser(), true))
    {
        //Apply prefix and suffix to differentiate duplicates
        Pair ssPair = new Pair<>(ss.getName(), String.format("Sample Set: %1$s (%2$s)", ss.getName(), ss.getContainer().getPath()));
        sampleSetList.add(ssPair);
    }

    List<Pair<String, String>> dataClassList = new ArrayList<>();
    for(ExpDataClass dc : ExperimentService.get().getDataClasses(getContainer(), getUser(), true))
    {
        //Apply prefix and suffix to differentiate duplicates
        Pair dcPair = new Pair<>(dc.getName(), String.format("Data Class: %1$s (%2$s)", dc.getName(), dc.getContainer().getPath()));
        dataClassList.add(dcPair);
    }

%>

<style type="text/css">
    .lk-parent-alias-icon {
        cursor: pointer;
        color: #555;
    }

    .lk-parent-alias-input {
        display: inline-block;
        margin-right: 10px;
    }
</style>

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

    <div id="extraAlias"></div>

    <div class=" form-group">
        <label class=" control-label col-sm-3 col-lg-2">
            &nbsp;
        </label>
        <div class="col-sm-9 col-lg-10">
            <a class="lk-exp-addAliasGroup lk-parent-alias-icon">
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

            let parentAliasTemplate = new DocumentFragment();
            let selectListTemplate = document.createElement("select");
            selectListTemplate.setAttribute("name", "importAliasValues");
            selectListTemplate.setAttribute("class", "form-control lk-parent-alias-input lk-exp-alias-value");
            parentAliasTemplate.appendChild(selectListTemplate);

            let defaultOption = document.createElement("option");
            defaultOption.value = "";
            defaultOption.text = "";
            selectListTemplate.append(defaultOption);

            let sampleSetList = [];
            let dataClassList = [];

            <%
                for (Pair<String, String> ssPair : sampleSetList ) {
            %>
                sampleSetList.push([<%=q(ssPair.getKey())%>, <%=q(ssPair.getValue())%>]);  // Do this so we can escape SampleSet names
            <%
                }
            %>
            <%
                for (Pair<String, String> dcPair : dataClassList ) {
            %>
                dataClassList.push([<%=q(dcPair.getKey())%>, <%=q(dcPair.getValue())%>]);  // Do this so we can escape SampleSet names
            <%
                }
            %>

            function createOptions(list, selectEl, valPrefix) {
                for (let i = 0; i < list.length; i++) {
                    let pair = list[i];

                    let option = document.createElement("option");
                    option.value = valPrefix + '/' + pair[0];   //Set value to import path
                    option.text = pair[1];                      //Set display text containing type, name, and path
                    selectEl.appendChild(option);
                }
            }

            createOptions(dataClassList, selectListTemplate, 'dataInputs');
            createOptions(sampleSetList, selectListTemplate, 'materialInputs');

            //Create string template to use for adding new alias rows
            let aliasRowTemplate = "<div class='form-group lk-exp-alias-group' name='importAliases'>" +
                    "<label class=' control-label col-sm-3 col-lg-2'>Parent Alias</label>" +
                    "<div class='col-sm-9 col-lg-10'>" +
                    "<input type='text' class='form-control lk-parent-alias-input lk-exp-alias-key' placeholder='Import Header' name='importAliasKeys'/>";

            aliasRowTemplate += selectListTemplate.outerHTML;  //Add select element and options

            // Add trashcan icon and link for removing rows.
            aliasRowTemplate += "<a class='removeAliasTrigger lk-parent-alias-icon' title='remove'><i class='fa fa-trash'></i></a>" +
                    "</div>" +
                    "</div>";

            //Set existing values and append existing alias to DOM
            function addAliasGroup(key, value) {
                let elem = $(aliasRowTemplate);

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


