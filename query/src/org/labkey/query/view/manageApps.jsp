<%
/*
 * Copyright (c) 2014 LabKey Corporation
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
<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="org.labkey.query.controllers.OlapController" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.query.olap.ServerManager" %>
<%@ page import="org.labkey.query.olap.OlapSchemaDescriptor" %>
<%@ page import="java.util.Collection" %>
<%@ page import="org.olap4j.metadata.Schema" %>
<%@ page import="org.olap4j.metadata.Cube" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromFilePath("Ext4"));
        return resources;
    }
%>
<%
    OlapController.ManageAppForm bean = (OlapController.ManageAppForm)HttpView.currentModel();
    Collection<OlapSchemaDescriptor> cubeDefs = ServerManager.getDescriptors(getContainer());
%>
<script>
    function deleteApp(contextName)
    {
        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL("olap", "deleteApp"),
            method: 'POST',
            jsonData: {
                contextName: contextName
            },
            success: function () {
                window.location.reload(true);
            }
        });
    }

    function confirmDeleteApp(contextName)
    {
        Ext4.Msg.confirm("Delete App Context",
                "Are you sure you want to delete the app context '" + Ext4.htmlEncode(contextName) + "'?",
                function (btnId) {
                    if (btnId == "yes") {
                        deleteApp(contextName);
                    }
                }
        );
    }

    function initAppConfigForm()
    {
        var cubeDefs = [
        <% for (OlapSchemaDescriptor cubeDef : cubeDefs) {
            for (Schema schema : cubeDef.getSchemas(cubeDef.getConnection(getContainer(), getUser()), getContainer(), getUser())) {
                for (Cube cube : schema.getCubes()) {
                    %>["<%=h(cube.getName())%>","<%=h(schema.getName())%>","<%=h(cubeDef.getId())%>"],<%
                }
            }
        } %>];

        Ext4.define('AppModel', {
            extend: 'Ext.data.Model',
            fields: [{name: 'name'}]
        });

        var form = Ext4.create('Ext.form.Panel', {
            renderTo: 'applicationConfiguration',
            border: false,
            width: 420,
            defaults: {
                width: 400,
                labelWidth: 110,
                padding: 5
            },
            items: [{
                xtype: 'combo',
                name: 'cubeDef',
                editable: false,
                fieldLabel: 'Cube Definition',
                displayField: 'name',
                value: <%=q(bean.getName())%>,
                store: Ext4.create('Ext.data.ArrayStore', {
                    fields: ['name', 'schemaName', 'configId'],
                    data: cubeDefs
                })
            },{
                xtype: 'combo',
                name: 'contextName',
                editable: false,
                fieldLabel: 'Context Name',
                displayField: 'name',
                value: <%=q(bean.getContextName())%>,
                store: Ext4.create('Ext.data.Store', {
                    model: 'AppModel',
                    proxy: {
                        type: 'ajax',
                        url: LABKEY.ActionURL.buildURL('olap', 'listApps'),
                        reader: {
                            type: 'json',
                            root: 'apps'
                        }
                    },
                    autoLoad: true
                })
            }],
            buttonAlign: 'left',
            buttons: [{
                text: 'Save',
                handler: function() {
                    var cubeCb = form.getForm().findField('cubeDef');
                    var cubeRec = cubeCb.getStore().findRecord('name', cubeCb.getValue());

                    var values = {};
                    values.configId = cubeRec ? cubeRec.get("configId") : null;
                    values.name = cubeRec ? cubeRec.get("name") : null;
                    values.schemaName = cubeRec ? cubeRec.get("schemaName") : null;
                    values.contextName = form.getForm().findField('contextName').getValue();
                    saveActiveAppConfig(values);
                }
            },{
                text: 'Clear',
                handler: function() {
                    form.getForm().findField('cubeDef').setValue(null);
                    form.getForm().findField('contextName').setValue(null);
                    saveActiveAppConfig({configId: null, name: null, schemaName: null, contextName: null});
                }
            }]
        });
    }

    function saveActiveAppConfig(values)
    {
        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL("olap", "setActiveAppConfig"),
            method: 'POST',
            jsonData: values,
            success: function () {
                var msgEl = Ext4.get('message');
                msgEl.setVisibilityMode(Ext4.dom.Element.DISPLAY);
                msgEl.setVisible(true);
                msgEl.setOpacity(1);
                msgEl.dom.innerHTML = "<span class='labkey-message'>Save successful</span>";
                msgEl.fadeOut({
                    delay: 500,
                    duration: 2000,
                    remove: false,
                    useDisplay: true,
                    callback: function () {
                        msgEl.dom.innerHTML = "";
                    }
                });
            }
        });
    }

    Ext4.onReady(initAppConfigForm);
</script>

<labkey:errors/>

<h3>Active application configuration for this folder:</h3>
<div id="message"></div>
<div id="applicationConfiguration"></div>
</br>
<h3>Application contexts defined in this folder:</h3>
<p>
<%=textLink("Create New", new ActionURL(OlapController.EditAppAction.class, getContainer()))%>
</p>
<table>
<% for (String contextName : bean.getAllContextNames()) { %>
    <tr>
        <td><%=h(contextName)%></td>
        <td><%=textLink("edit", new ActionURL(OlapController.EditAppAction.class, getContainer()).addParameter("contextName", contextName))%></td>
        <td><%=textLink("delete", "#", "confirmDeleteApp(" + PageFlowUtil.jsString(contextName) + ");return false;", null)%></td>
    </tr>
<% } %>
</table>

