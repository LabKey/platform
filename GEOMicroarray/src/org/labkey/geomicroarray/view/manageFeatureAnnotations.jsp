<%
/*
 * Copyright (c) 2013 LabKey Corporation
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
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.geomicroarray.GEOMicroarrayManager" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<ClientDependency>();
        resources.add(ClientDependency.fromFilePath("Ext4"));
        return resources;
    }
%>

<%
    JspView<Object> me = (JspView<Object>) HttpView.currentView();
    ViewContext ctx = me.getViewContext();
    Map<String, Object>[] rows = GEOMicroarrayManager.get().getFeatureAnnotationSets(ctx.getUser(), ctx.getContainer());
%>

<div id="featureSetsGrid"></div>

<script type="text/javascript">
    var featureAnnotationSets = []
<%
    for (Map<String, Object> row : rows)
    {
%>
        featureAnnotationSets.push({
            name: <%=qh((String)row.get("name"))%>,
            vendor: <%=qh((String)row.get("vendor"))%>,
            rowid: <%=row.get("rowid")%>
        });
<%
    }
%>

    Ext4.onReady(function(){
        var store = Ext4.create('Ext.data.Store', {
            fields: ['rowid', 'name', 'vendor'],
            data: featureAnnotationSets,
            proxy: {
                type: 'memory',
                reater: {
                    type: 'json'
                }
            }
        });

        var insertHandler = function(){
            window.location = LABKEY.ActionURL.buildURL('geomicroarray', 'uploadFeatureAnnotationSet');
        };

        var deleteHandler = function(){
            var record = gridPanel.getSelectionModel().getSelection()[0];
            console.log(record.data);

            Ext4.Ajax.request({
                url: LABKEY.ActionURL.buildURL('geomicroarray', 'deleteFeatureAnnotationSet.api'),
                method: 'POST',
                jsonData: {rowId: record.data.rowid},
                success: function(response){
                    var json = Ext4.JSON.decode(response.responseText);
                    gridPanel.getStore().remove(record);
                    console.log(json);
                },
                failure: function(response){
                    var json = Ext4.JSON.decode(response.responseText);
                }
            });
        };

        var deleteButton = Ext4.create('Ext.button.Button', {
            text: 'delete selected',
            disabled: true,
            handler: deleteHandler,
            scope: this
        });

        var gridPanel = Ext4.create('Ext.grid.Panel', {
            renderTo: 'featureSetsGrid',
            cls: 'participantCategoriesGrid',
            store: store,
            tbar: ['->', deleteButton, {text: 'insert', handler: insertHandler}],
            columns: [
                {text: 'Name', dataIndex: 'name', flex: 1},
                {text: 'Vendor', dataIndex: 'vendor', flex: 1}
            ],
            width: 400,
            listeners: {
                selectionchange: function(grid, selected){
                    if(selected != null){
                        deleteButton.setDisabled(false);
                    } else {
                        deleteButton.setDisabled(true);
                    }
                }
            }
        });
    });
</script>
