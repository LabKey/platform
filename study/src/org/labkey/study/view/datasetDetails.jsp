<%
/*
 * Copyright (c) 2006-2017 LabKey Corporation
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
<%@ page import="gwt.client.org.labkey.study.dataset.client.model.GWTDataset"%>
<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.pipeline.PipelineService"%>
<%@ page import="org.labkey.api.security.User"%>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.security.permissions.Permission" %>
<%@ page import="org.labkey.api.security.permissions.UpdatePermission" %>
<%@ page import="org.labkey.api.study.Dataset" %>
<%@ page import="org.labkey.api.study.StudyService" %>
<%@ page import="org.labkey.api.study.TimepointType" %>
<%@ page import="org.labkey.api.study.Visit" %>
<%@ page import="org.labkey.api.util.Button" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.model.DatasetDefinition" %>
<%@ page import="org.labkey.study.model.StudyImpl" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.study.model.VisitDataset" %>
<%@ page import="org.labkey.study.model.VisitDatasetType" %>
<%@ page import="org.labkey.study.model.VisitImpl" %>
<%@ page import="org.labkey.study.visitmanager.VisitManager" %>
<%@ page import="org.springframework.validation.BindException" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Set" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
    }
%>
<%
    JspView<DatasetDefinition> me = (JspView<DatasetDefinition>) HttpView.currentView();
    DatasetDefinition dataset = me.getModelBean();

    Container c = getContainer();
    User user = getUser();

    String queryName = dataset.getTableInfo(user).getName();
    String schemaName = dataset.getTableInfo(user).getSchema().getQuerySchemaName();

    StudyImpl study = StudyManager.getInstance().getStudy(c);
    Set<Class<? extends Permission>> permissions = c.getPolicy().getPermissions(user);

    // is definition inherited
    boolean isDatasetInherited = dataset.isInherited();

    VisitManager visitManager = StudyManager.getInstance().getVisitManager(study);
    boolean pipelineSet = null != PipelineService.get().findPipelineRoot(c);

    List<DatasetDefinition> shadowed = StudyManager.getInstance().getShadowedDatasets(study, Collections.singletonList(dataset));
%>
<%
if (isDatasetInherited)
{
    ActionURL manageShared = new ActionURL(StudyController.DatasetDetailsAction.class,dataset.getDefinitionContainer()).addParameter("id",dataset.getDatasetId());
    %>This dataset is defined in another folder: <a href="<%=h(manageShared)%>"><%=h(dataset.getDefinitionContainer().getName())%></a><br><%
}
if (!shadowed.isEmpty())
{
    StringBuilder sb = new StringBuilder();
    sb.append(shadowed.size() == 1 ? "A shared dataset is" : "These shared datasets are").append(" shadowed by this local dataset definition:");
    String comma = " ";
    for (DatasetDefinition h : shadowed)
    {
        sb.append(comma).append(h(h.getName()));
        comma = ", ";
    }
    sb.append(".");
    %><%=text(sb.toString())%><br><%
}

List<Button.ButtonBuilder> buttons = new ArrayList<>();
if (permissions.contains(AdminPermission.class))
{
    if (dataset.getType().equals(Dataset.TYPE_STANDARD))
    {
        ActionURL viewDatasetURL = new ActionURL(StudyController.DatasetAction.class, c);
        viewDatasetURL.addParameter("datasetId", dataset.getDatasetId());
        buttons.add(button("View Data").href(viewDatasetURL));
    }
    if (study.getTimepointType() != TimepointType.CONTINUOUS)
    {
        ActionURL updateDatasetURL = new ActionURL(StudyController.UpdateDatasetVisitMappingAction.class, c);
        updateDatasetURL.addParameter("datasetId", dataset.getDatasetId());
        buttons.add(button("Edit Associated " + visitManager.getPluralLabel()).href(updateDatasetURL));
    }
    ActionURL manageTypesURL = new ActionURL(StudyController.ManageTypesAction.class, c);
    buttons.add(button("Manage Datasets").href(manageTypesURL));
    if (!isDatasetInherited)
    {
        ActionURL deleteDatasetURL = new ActionURL(StudyController.DeleteDatasetAction.class, c);
        deleteDatasetURL.addParameter("id", dataset.getDatasetId());
        buttons.add(button("Delete Dataset").href(deleteDatasetURL).onClick("return confirm('Are you sure you want to delete this dataset?  All related data and visitmap entries will also be deleted.')"));
    }
    if (user.hasRootAdminPermission() || dataset.canWrite(user))
    {
        buttons.add(button("Delete All Rows").onClick("truncateTable();"));
    }
}
if (permissions.contains(UpdatePermission.class) && !isDatasetInherited)
{
    ActionURL showHistoryURL = new ActionURL(StudyController.ShowUploadHistoryAction.class, c);
    showHistoryURL.addParameter("id", dataset.getDatasetId());

    ActionURL editTypeURL = new ActionURL(StudyController.EditTypeAction.class, c);
    editTypeURL.addParameter("datasetId", dataset.getDatasetId());

    buttons.add(button("Show Import History").href(showHistoryURL));
    if (dataset.getType().equals(Dataset.TYPE_STANDARD))
    {
        buttons.add(button("Edit Definition").href(editTypeURL));
    }
    else if(dataset.getType().equals(Dataset.TYPE_PLACEHOLDER))
    {
        buttons.add(button("Link or Define Dataset").href("#").onClick("showLinkDialog();"));
    }
}
%><br/><%
for (Button.ButtonBuilder bb : buttons)
{
    %><%= bb %> <%
}
if (!pipelineSet)
{
    include(new StudyController.RequirePipelineView(study, false, (BindException) request.getAttribute("errors")), out);
}
%>
<br/>
<br/>
    <labkey:panel title="Dataset Properties">
        <table id="details" width="600px" class="lk-fields-table">
            <tr>
                <td class=labkey-form-label>Name</td>
                <th align=left><%= h(dataset.getName()) %></th>

                <td class=labkey-form-label>ID</td>
                <td align=left><%= dataset.getDatasetId() %></td>
            </tr>
            <tr>
                <td class=labkey-form-label>Label</td>
                <td><%= h(dataset.getLabel()) %></td>

                <td class=labkey-form-label>Category</td>
                <td><%= h(dataset.getViewCategory() != null ? dataset.getViewCategory().getLabel() : null) %></td>
            </tr>
            <tr>
                <td class=labkey-form-label>Cohort Association</td>
                <td><%=h(dataset.getCohort() != null ? dataset.getCohort().getLabel() : "All")%></td>

                <td class=labkey-form-label><%=h(visitManager.getLabel())%> Date Column</td>
                <td><%= h(dataset.getVisitDateColumnName()) %></td>
            </tr>
            <tr>
                <td class=labkey-form-label>Additional Key Column</td>
                <td><%=h(dataset.getKeyPropertyName() != null ?
                                 h(dataset.getKeyPropertyName()) :
                                 dataset.getUseTimeKeyField() ? h(GWTDataset.TIME_KEY_FIELD_DISPLAY) :
                                 "None")%></td>

                <td class=labkey-form-label>Tag</td>
                <td><%=h(dataset.getTag())%>
                </td>
            </tr>
            <tr>
                <td class=labkey-form-label>Demographic
                    Data <%=helpPopup("Demographic Data", "Demographic data appears only once for each " +
                            StudyService.get().getSubjectNounSingular(c).toLowerCase() +
                            " in the study.")%></td>
                <td><%=text(dataset.isDemographicData() ? "true" : "false")%></td>

                <td rowspan="2" class=labkey-form-label>Description</td>
                <td rowspan="2"><%= h(dataset.getDescription()) %></td>
            </tr>
            <tr>
                <td class=labkey-form-label>Show In Overview</td>
                <td><%=text(dataset.isShowByDefault() ? "true" : "false")%></td>
            </tr>
            <tr>
                <td class=labkey-form-label>Share Data</td>
                <td><%=text(dataset.getDataSharingEnum()== DatasetDefinition.DataSharing.NONE ? "No" : "Share by Participants")%></td>
            </tr>
        </table>
    </labkey:panel>

    <labkey:panel title="Dataset Fields">
        <%
            JspView typeSummary = new StudyController.StudyJspView<>(study, "typeSummary.jsp", dataset, (BindException)me.getErrors());
            me.include(typeSummary, out);
        %>
    </labkey:panel>

<% if (study.getTimepointType() != TimepointType.CONTINUOUS) { %>
    <labkey:panel title="Visit Associations">
        <table class="lk-fields-table"><%
            List<VisitDataset> visitList = StudyManager.getInstance().getMapping(dataset);
            HashMap<Integer,VisitDataset> visitMap = new HashMap<>();
            for (VisitDataset vds : visitList)
                visitMap.put(vds.getVisitRowId(), vds);
            boolean hasVisitAssociations = false;
            for (VisitImpl visit : study.getVisits(Visit.Order.DISPLAY))
            {
                VisitDataset vm = visitMap.get(visit.getRowId());
                if (vm != null)
                {
                    hasVisitAssociations = true;
                    VisitDatasetType type = vm.isRequired() ? VisitDatasetType.REQUIRED : VisitDatasetType.OPTIONAL;
                    %><tr>
                        <td><%= h(visit.getDisplayString()) %></td>
                        <td><%=text(type == VisitDatasetType.NOT_ASSOCIATED ? "&nbsp;" : h(type.getLabel()))%></td>
                    </tr><%
                }
            }
            if (!hasVisitAssociations)
            {
            %><tr><td>This dataset isn't explicitly associated with any visits.</td></tr><%
            }
        %>
        </table>
    </labkey:panel>
<% } %>

<script type="text/javascript">
    function truncateTable()
    {
                var msg = "Are you sure you wish to delete all rows for the dataset "+ '<%=h(dataset.getName())%>' + "?<br>";
                <% if (dataset.isShared() && !isDatasetInherited)
                {
                        %>msg +="<b>This will delete data in sub-folders that use this dataset.</b><br>";<%
                } %>
                msg += "This action cannot be undone and will result in an empty dataset.";
                Ext4.Msg.confirm("Confirm Deletion", msg,
                function(button){
                    if (button === 'yes') {
                        truncate();
                    }
                }
        );

        function truncate()
        {
            var waitMask = Ext4.Msg.wait('Deleting Rows...', 'Delete Rows');
            Ext4.Ajax.request({
                url     : LABKEY.ActionURL.buildURL('query', 'truncateTable'),
                method  : 'POST',
                success: function(response){
                    waitMask.close();
                    var data = Ext4.JSON.decode(response.responseText);
                    Ext4.Msg.alert("Success", data.deletedRows + " rows deleted");
                },
                failure : function(response, opts)
                {
                    waitMask.close();
                    LABKEY.Utils.displayAjaxErrorResponse(response, opts);
                },
                jsonData : {schemaName : <%=q(schemaName)%>, queryName : <%=q(queryName)%>},
                headers : {'Content-Type' : 'application/json'},
                scope : this
            });
        }

    }
    function showLinkDialog(){
        Ext4.onReady(function(){
            var datasets = [
<%
        for (Dataset def : study.getDatasetsByType(Dataset.TYPE_STANDARD, Dataset.TYPE_PLACEHOLDER))
        {
%>
                {label: "<%=h(def.getLabel())%>", id: <%=def.getDatasetId()%>},
<%
        }
%>
            ];

            var datasetStore = Ext4.create('Ext.data.Store', {
                fields: ['label', 'id'],
                data: datasets
            });

            var datasetCombo = Ext4.create('Ext.form.field.ComboBox', {
                disabled: true,
                width: 220,
                allowBlank: false,
                cls : 'existing-dataset-combo',             // test marker
                editable: false,
                forceSelection: true,
                value: 'asdf',
                store: datasetStore,
                queryMode: 'local',
                displayField: 'label',
                valueField: 'id',
                margin: '10 0 0 85',
                listeners      : {
                    render     : function(combo) {
                        var store = combo.getStore();
                        combo.setValue(store.getAt(0));
                    }
                }
            });

            var importRadio = {
                boxLabel: 'Import data from file',
                name: 'deftype',
                inputValue: 'linkImport',
                checked: 'true'
            };
            var manualRadio = {
                boxLabel: 'Define dataset manually',
                name:'deftype',
                inputValue:'linkManually'
            };

            var existingRadio = {
                boxLabel: 'Link to existing dataset',
                name: 'deftype',
                inputValue: 'linkToTarget'
            };

            var linkDatasetGroup = Ext4.create('Ext.form.RadioGroup', {
                columns: 1,
                vertical: true,
                margin: '10 0 0 45',
                items: [importRadio, manualRadio, existingRadio],
                listeners: {
                    scope: this,
                    change: function(rgroup, newValue){
                        if(newValue.deftype == 'linkToTarget'){
                            linkDoneButton.setText('Done');
                            datasetCombo.setDisabled(false);
                        } else {
                            linkDoneButton.setText('Next');
                            datasetCombo.setDisabled(true);
                        }
                    }
                }
            });

            var linkDoneButton = Ext4.create('Ext.Button', {
                text: 'Next',
                handler: linkDatasetHandler,
                scope: this
            });
            
            var dialogConfig = {
                title: 'Link or Define Dataset',
                height: 225,
                width: 400,
                layout: 'fit',
                bodyStyle : 'border: none;',
                modal: true,
                scope: this,
                buttons : [{
                    xtype: 'button',
                    align: 'right',
                    text: 'Cancel',
                    handler: function(){
                        linkDatasetWindow.close();
                    },
                    scope: this
                }, linkDoneButton],
                items: [{
                    xtype: 'form',
                    border: false,
                    title: '',
                    defaults: {
                        margin: '10 0 0 25'
                    },
                    items: [{
                        xtype: 'displayfield',
                        value: "Define <%=h(dataset.getLabel())%>",
                        width: 340
                    },linkDatasetGroup, datasetCombo]
                }]
            };

            function linkDatasetHandler(){
                var json = {};
                json.type = linkDatasetGroup.getValue().deftype;
                json.expectationDataset = <%= dataset.getDatasetId() %>;

                if(json.type == 'linkToTarget'){
                    json.targetDataset = datasetCombo.getValue();
                }

                Ext4.Ajax.request({
                    url     : LABKEY.ActionURL.buildURL('study', 'defineDataset.view'),
                    method  : 'POST',
                    jsonData : json,
                    success : function(response){
                        var resp = Ext4.decode(response.responseText);
                        if(json.type == 'placeHolder' || json.type == 'linkToTarget'){
                            // If placeHolder or linkToTarget, navigate to new page.
                                linkDatasetWindow.close();
                                window.location = LABKEY.ActionURL.buildURL('study', 'datasetDetails.view', null, {id: json.targetDataset});
                        } else {
                            // If manual/import navigate to manual/import page.
                            window.location = resp.redirectUrl;
                        }
                    },
                    failure : function(response){
                        var resp = Ext4.decode(response.responseText);
                        if(resp && resp.exception){
                            Ext4.Msg.alert('Failure', resp.exception);
                        } else {
                            Ext4.Msg.alert('Failure', 'An unknown failure has occurred');
                        }
                    },
                    scope   : this
                });
            }

            var linkDatasetWindow = Ext4.create('Ext.window.Window', dialogConfig);

            linkDatasetWindow.show();
        });
    }
</script>
