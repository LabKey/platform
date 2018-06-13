<%
    /*
     * Copyright (c) 2013-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.data.ColumnInfo" %>
<%@ page import="org.labkey.api.data.TableInfo" %>
<%@ page import="org.labkey.api.exp.PropertyType" %>
<%@ page import="org.labkey.api.study.MasterPatientIndexService" %>
<%@ page import="org.labkey.api.util.element.Input" %>
<%@ page import="org.labkey.api.util.element.Select" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.model.DatasetDefinition" %>
<%@ page import="org.labkey.study.model.StudyImpl" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    MasterPatientIndexService service = (MasterPatientIndexService) HttpView.currentView().getModelBean();
    MasterPatientIndexService.FolderSettings settings = service.getFolderSettings(getContainer());

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
        };

        $(document).ready(function () {

            // load the dataset dropdown and fields (if necessary)
            populateDatasets();
            if (selectedDataset){
                loadFields(selectedDataset, selectedField);
            }
        });

    })(jQuery);
</script>

<labkey:errors/>
<%  if (service == null) {
%>
<p>
    A Master Patient Index Provider has not been configured yet or is not available in this LabKey installation.
</p>
<%  }
else
{
%>
<labkey:form method="POST" layout="horizontal">
    <%= new Select.SelectBuilder().name("schema").id("schemaNameInput").label("Schema")
            .layout(Input.Layout.HORIZONTAL)
            .formGroup(true)
            .disabled(true)
    %>
    <%= new Select.SelectBuilder().name("query").id("queryNameInput").label("Query")
            .layout(Input.Layout.HORIZONTAL)
            .formGroup(true)
            .disabled(true)
    %>
    <%= new Select.SelectBuilder().name("dataset").id("datasetInput").label("UID Dataset")
            .layout(Input.Layout.HORIZONTAL)
            .formGroup(true)
    %>
    <%= new Select.SelectBuilder().name("fieldName").id("fieldInput").label("UID Field")
            .layout(Input.Layout.HORIZONTAL)
            .formGroup(true)
    %>
    <labkey:input type="checkbox" label="Enabled" name="enabled" checked="<%=settings.isEnabled()%>"/>

    <labkey:button text="save" submit="true"/>
    <labkey:button text="cancel" href="<%=new ActionURL(StudyController.ManageStudyAction.class, getContainer())%>"/>
    <labkey:button text="update now" href=""/>
</labkey:form>

<%  }
%>

<script type="application/javascript">
    LABKEY.Query.schemaSelectInput({renderTo: 'schemaNameInput', initValue: <%=q(!StringUtils.isBlank(settings.getSchema()) ? settings.getSchema() : "study")%>});
    LABKEY.Query.querySelectInput({renderTo: 'queryNameInput', schemaInputId: 'schemaNameInput', initValue: <%=q(!StringUtils.isBlank(settings.getQuery()) ? settings.getQuery() : "empty")%>});
</script>
