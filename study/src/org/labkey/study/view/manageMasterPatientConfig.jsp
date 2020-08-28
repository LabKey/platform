<%
    /*
     * Copyright (c) 2018-2019 LabKey Corporation
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
<%@ page import="com.fasterxml.jackson.databind.ObjectMapper" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.labkey.api.admin.AdminUrls" %>
<%@ page import="org.labkey.api.data.ColumnInfo" %>
<%@ page import="org.labkey.api.data.TableInfo" %>
<%@ page import="org.labkey.api.exp.PropertyType" %>
<%@ page import="org.labkey.api.study.MasterPatientIndexService" %>
<%@ page import="org.labkey.api.util.HelpTopic" %>
<%@ page import="org.labkey.api.util.element.Input" %>
<%@ page import="org.labkey.api.util.element.Select" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.model.DatasetDefinition" %>
<%@ page import="org.labkey.study.model.StudyImpl" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    Collection<MasterPatientIndexService> services = MasterPatientIndexService.getProviders();
    MasterPatientIndexService service = (MasterPatientIndexService) HttpView.currentView().getModelBean();
    MasterPatientIndexService.FolderSettings settings = service != null ? service.getFolderSettings(getContainer()) : new MasterPatientIndexService.FolderSettings();
    String docLink = new HelpTopic("empi").getHelpTopicHref();

    ObjectMapper jsonMapper = new ObjectMapper();
    Map<String, List<String>> datasetMap = new HashMap<>();
    StudyImpl study = StudyManager.getInstance().getStudy(getContainer());
    for (DatasetDefinition def : study.getDatasets())
    {
        if (def.isDemographicData())
        {
            List<String> fields = new ArrayList<>();
            datasetMap.put(def.getName(), fields);

            TableInfo table = def.getTableInfo(getUser());
            for (ColumnInfo col : table.getColumns())
            {
                if (!col.isKeyField() && col.getPropertyType() == PropertyType.STRING)
                {
                    fields.add(col.getName());
                }
            }
        }
    }
%>

<script type="application/javascript">

    (function($){
        var datasetMap = <%=text(jsonMapper.writeValueAsString(datasetMap))%>;
        var selectedDataset = <%=q(!StringUtils.isBlank(settings.getDataset()) ? settings.getDataset() : "")%>;
        var selectedField = <%=q(!StringUtils.isBlank(settings.getFieldName()) ? settings.getFieldName() : "")%>;

        populateDatasets = function(){

            var datasetSelect = $("select[id='datasetInput']");

            datasetSelect.empty().append($('<option>'));
            $.each(datasetMap, function (key, option) {
                datasetSelect.append($('<option>', { value: key,  text: key,  selected: (key == selectedDataset)}));
            });

            datasetSelect.on('change', function (event, ds) {
                loadFields(ds || event.target.value);
                LABKEY.setDirty(true);
            });
        };

        loadFields = function(ds, selected) {

            var fieldSelect = $("select[id='fieldInput']");
            var fields = datasetMap[ds];

            if (fields){
                fieldSelect.empty().append($('<option>'));
                $.each(fields, function (i, field) {
                    fieldSelect.append($('<option>', { value: field,  text: field,  selected: (field == selected)}));
                });
            }

            fieldSelect.on('change', function (event, ds) {
                LABKEY.setDirty(true);
            });
        };

        updateIndexes = function(){

            if (LABKEY.isDirty()){
                LABKEY.Utils.alert("Update failed", "You have unsaved changes on this page, you must first save your changes before you can update identifiers.");
            }
            else {
                LABKEY.Ajax.request({
                    url: LABKEY.ActionURL.buildURL("study", "refreshMasterPatientIndex.api"),
                    method: "POST",
                    success: LABKEY.Utils.getCallbackWrapper(function(response)
                    {
                        if (response.success)
                            window.location = response.returnUrl;
                        else if (response.message){
                            LABKEY.Utils.alert("Update failed", response.message);
                        }
                    })
                });
            }
        };

        $(document).ready(function () {

            // load the dataset dropdown and fields (if necessary)
            populateDatasets();
            if (selectedDataset){
                loadFields(selectedDataset, selectedField);
            }

            window.onbeforeunload = LABKEY.beforeunload(LABKEY.isDirty());
        });

    })(jQuery);
</script>

<labkey:errors/>
<%  if (services.isEmpty()) {
%>
    <div class="alert alert-info">
        <h1 class="fa fa-star-o"> Premium Feature</h1>
        <h3>Enterprise Master Patient Index integration is a premium feature and is not available with your current edition of LabKey Server.</h3>
        <hr>
        <p>Premium edition subscribers have the ability to integrate with an Enterprise Master Patient Index, using EMPI IDs to
            create an authoritative connection between LabKey-housed data and a patient's master index record.</p>
        <p><a class="alert-link" href="<%=h(docLink)%>" target="_blank" rel="noopener noreferrer">Learn more <i class="fa fa-external-link"></i></a></p>
        <p>In addition to this feature, premium editions of LabKey Server provide professional support and advanced functionality to help teams maximize the value of the platform.</p>
        <br>
        <p><a class="alert-link" href="https://www.labkey.com/platform/go-premium/" target="_blank" rel="noopener noreferrer">Learn more about premium editions <i class="fa fa-external-link"></i></a></p>
    </div>
<%  }
    else if (service == null) {
%>
    <div class="alert alert-info">
        <h3>Enterprise Master Patient Index integration is not configured for your LabKey Server.</h3>
        <hr>
        <p>Premium edition subscribers have the ability to integrate with an Enterprise Master Patient Index, using EMPI IDs to
            create an authoritative connection between LabKey-housed data and a patient's master index record. An administrator will
            need to set up the initial connection to a Master Patient Index Provider through the Admin Panel.</p>
        <p><a class="alert-link" href="<%=urlProvider(AdminUrls.class).getAdminConsoleURL()%>" target="_blank">Configure <i class="fa fa-external-link"></i></a></p>
    </div>
<%  }
else
{
%>
<labkey:form method="POST" layout="horizontal" onsubmit="LABKEY.setSubmit(true);">
    <%= new Select.SelectBuilder().name("schema").id("schemaNameInput").label("Schema *")
            .layout(Input.Layout.HORIZONTAL)
            .required(true)
            .contextContent("The schema name of the query used to provide patient data to the Master Patient Index Provider")
            .forceSmallContext(true)
            .formGroup(true)
            .disabled(true)
    %>
    <%= new Select.SelectBuilder().name("query").id("queryNameInput").label("Query *")
            .layout(Input.Layout.HORIZONTAL)
            .required(true)
            .contextContent("The query used to provide patient data to the Master Patient Index Provider")
            .forceSmallContext(true)
            .formGroup(true)
            .disabled(true)
    %>

    <%
        if (datasetMap.isEmpty())
        {
    %>
        <labkey:input type="displayfield" label="UID Dataset" value="The UID datasets are required to be of type: demographics. There are no demographics datasets configured for this study"/>
        <labkey:input type="displayfield" label="UID Field" value=""/>
    <%
        } else {
    %>
    <%= new Select.SelectBuilder().name("dataset").id("datasetInput").label("UID Dataset *")
            .layout(Input.Layout.HORIZONTAL)
            .required(true)
            .contextContent("The dataset to store the universal ID returned from the Master Patient Index")
            .forceSmallContext(true)
            .formGroup(true)
    %>
    <%= new Select.SelectBuilder().name("fieldName").id("fieldInput").label("UID Field *")
            .layout(Input.Layout.HORIZONTAL)
            .required(true)
            .contextContent("The field in the dataset to store the universal ID")
            .forceSmallContext(true)
            .formGroup(true)
    %>
    <%
        }
    %>

    <labkey:input type="checkbox" label="Enabled" name="enabled" checked="<%=settings.isEnabled()%>" onChange="LABKEY.setDirty(true);"/>

    <labkey:button text="save" submit="true"/>
    <labkey:button text="cancel" href="<%=new ActionURL(StudyController.ManageStudyAction.class, getContainer())%>" onclick="LABKEY.setSubmit(true);"/>
    <labkey:button text="update patient identifiers" submit="false" onclick="updateIndexes();"/>
</labkey:form>

<%  }
%>

<script type="application/javascript">
    LABKEY.Query.schemaSelectInput({renderTo: 'schemaNameInput', initValue: <%=q(!StringUtils.isBlank(settings.getSchema()) ? settings.getSchema() : "study")%>});
    LABKEY.Query.querySelectInput({renderTo: 'queryNameInput', schemaInputId: 'schemaNameInput', initValue: <%=q(!StringUtils.isBlank(settings.getQuery()) ? settings.getQuery() : "empty")%>});
</script>
