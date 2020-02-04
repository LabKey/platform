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
<%@ page import="org.labkey.api.exp.api.ExpDataClass" %>
<%@ page import="org.labkey.api.exp.api.ExpSampleSet" %>
<%@ page import="org.labkey.api.exp.api.ExperimentService" %>
<%@ page import="org.labkey.api.exp.api.SampleSetService" %>
<%@ page import="org.labkey.api.util.HtmlString" %>
<%@ page import="org.labkey.api.util.Pair" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.experiment.controllers.exp.ExperimentController" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.List" %>
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
    Integer rowId = bean.getRowId();
    HtmlString helpText = HtmlString.unsafe(String.format("Used for generating unique sample IDs (%1$s)", helpLink("sampleIDs#expression", "more info")));
    final String SELF_OPTION_TEXT = "(Current Sample Set)";

    List<Pair<String, String>> sampleSetList = new ArrayList<>();
    for (ExpSampleSet ss : SampleSetService.get().getSampleSets(getContainer(), getUser(), true))
    {
        String label = (rowId != null && ss.getRowId() == rowId) ?  //If this SampleSet use self option text.
            SELF_OPTION_TEXT :
            String.format("Sample Set: %1$s (%2$s)", ss.getName(), ss.getContainer().getPath());  //Apply prefix and suffix to differentiate duplicates

        Pair<String, String> ssPair = new Pair<>(ss.getName(), label);
        sampleSetList.add(ssPair);
    }

    List<Pair<String, String>> dataClassList = new ArrayList<>();
    for(ExpDataClass dc : ExperimentService.get().getDataClasses(getContainer(), getUser(), true))
    {
        //Apply prefix and suffix to differentiate duplicates
        Pair<String, String> dcPair = new Pair<>(dc.getName(), String.format("Data Class: %1$s (%2$s)", dc.getName(), dc.getContainer().getPath()));
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
            id="name" name="name" label="Name" isReadOnly="<%=bean.isUpdate() || bean.isNameReadOnly()%>" value="<%=bean.getName()%>"
            contextContent="Name of sample set (required)." size="60" isDisabled="<%=bean.isNameReadOnly()%>"
    />
    <labkey:input
            id="nameExpression" name="nameExpression" label="Name Expression" value="<%=bean.getNameExpression()%>"
            placeholder="S-\${now:date}-\${batchRandomId}-\${randomId}"
            contextContent="<%=helpText%>" size="60"
    />
    <div class="form-group">
        <label class=" control-label col-sm-3 col-lg-2" for="description">
            Description
        </label>
        <div class="col-sm-9 col-lg-10">
            <textarea name="description" id="description" cols="60" rows="5"><%=h(bean.getDescription())%></textarea>
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
    <labkey:input type="hidden" name="isUpdate" value="<%=String.valueOf(bean.isUpdate())%>"/>
    <labkey:input type="hidden" name="LSID" value="<%=bean.getLSID()%>"/>
    <labkey:input type="hidden" name="rowId" value="<%=String.valueOf(bean.getRowId())%>"/>

    <%=button(bean.isUpdate() ? "Update" : "Create").id("btnSubmit").submit(true)%>
    <%=button("Cancel").href(bean.getReturnURLHelper())%>
</labkey:form>
<script type="application/javascript">
    +function ($) {
        $(document).ready(function(){
            function processAliasJson(aliases) {
                if (aliases) {
                    for (let j in aliases) {
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

                for (Pair<String, String> dcPair : dataClassList ) {
            %>
                dataClassList.push([<%=q(dcPair.getKey())%>, <%=q(dcPair.getValue())%>]);  // Do this so we can escape DataClass names
            <%
                }
            %>

            function createOption(value, text, prefix) {
                let option = document.createElement("option");
                option.value = prefix  + value;   //Set value to import path
                option.text = text; //Set display text containing type, name, and path

                return option;
            }

            const sampleSetPrefix = 'materialInputs';
            const dataClassPrefix = 'dataInputs';

            <% if (!bean.isUpdate()) { %>
            const selfOption = createOption('<%=h(ExperimentController.BaseSampleSetForm.NEW_SAMPLE_SET_VALUE)%>', '<%=h(SELF_OPTION_TEXT)%>', '');
            selectListTemplate.append(selfOption);

            <% } %>

            const ssOptionList = sampleSetList.map(ssPair => createOption(ssPair[0], ssPair[1], sampleSetPrefix + '/'));
            ssOptionList.sort((a, b) => LABKEY.internal.SortUtil.naturalSort(a.text, b.text));
            const dcOptionList = dataClassList.map(dcPair => createOption(dcPair[0], dcPair[1], dataClassPrefix + '/'));
            dcOptionList.sort((a, b) => LABKEY.internal.SortUtil.naturalSort(a.text, b.text));

            selectListTemplate.append(...ssOptionList);
            selectListTemplate.append(...dcOptionList);

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

            const extraAlias = $('#extraAlias');

            //Set existing values and append existing alias to DOM
            function addAliasGroup(key, value) {
                let elem = $(aliasRowTemplate);

                if (key && value) {
                    elem.find(".lk-exp-alias-key").val(key);
                    elem.find(".lk-exp-alias-value").val(value);
                }

                elem.appendTo(extraAlias);
            }

            extraAlias.on('click', '.removeAliasTrigger' , function() {
                $(this).parents('.lk-exp-alias-group').remove();
            });

            $(".lk-exp-addAliasGroup").on('click', function () {
                addAliasGroup();
            });

        <%
            if (bean.getRowId() != null && StringUtils.isNotBlank(bean.getImportAliasJSON())) {
        %>
            let aliases = JSON.parse(<%=q(bean.getImportAliasJSON())%>);
            processAliasJson(aliases);
        <%
            }
        %>
        });
    }(jQuery);
</script>


