<%
/*
 * Copyright (c) 2014-2016 LabKey Corporation
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
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.query.controllers.OlapController" %>
<%@ page import="org.labkey.query.olap.CustomOlapSchemaDescriptor" %>
<%@ page import="org.labkey.query.olap.OlapSchemaDescriptor" %>
<%@ page import="org.labkey.query.olap.ServerManager" %>
<%@ page import="org.olap4j.OlapConnection" %>
<%@ page import="org.olap4j.metadata.Cube" %>
<%@ page import="org.olap4j.metadata.Schema" %>
<%@ page import="java.util.Collection" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
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

    function generateCubeDisplayValue(cubeName, defName)
    {
        return cubeName + " (" + defName + ")";
    }

    function isCubeDefInitSelected(cubeName, schemaName, configId)
    {
        return cubeName == <%=q(bean.getName())%> && schemaName == <%=q(bean.getSchemaName())%> && configId == <%=q(bean.getConfigId())%>;
    }

    function initAppConfigForm()
    {
        var cubeDefs = [
        <% for (OlapSchemaDescriptor cubeDef : cubeDefs) {
            try (OlapConnection conn = cubeDef.getConnection(getContainer(), getUser())) {
                for (Schema schema : cubeDef.getSchemas(conn, getContainer(), getUser())) {
                    for (Cube cube : schema.getCubes()) {
                        %>[
                        generateCubeDisplayValue(<%=q(cube.getName())%>, <%=q(cubeDef.getName())%>),
                        "<%=h(cube.getName())%>","<%=h(schema.getName())%>",
                        "<%=h(cubeDef.getId())%>",
                        isCubeDefInitSelected(<%=q(cube.getName())%>, <%=q(schema.getName())%>, <%=q(cubeDef.getId())%>)
                        ],<%
                    }
                }
            }
            catch (Exception e)
            {
                // ignore -- error message will be displayed below
            }
        } %>];

        Ext4.define('AppModel', {
            extend: 'Ext.data.Model',
            fields: [{name: 'name'}]
        });

        var contextCombo = Ext4.create('Ext.form.field.ComboBox', {
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
        });

        var cubeDefCombo = Ext4.create('Ext.form.field.ComboBox', {
            xtype: 'combo',
            name: 'cubeDef',
            editable: false,
            fieldLabel: 'Cube Definition',
            displayField: 'displayValue',
            store: Ext4.create('Ext.data.ArrayStore', {
                fields: ['displayValue', 'cubeName', 'schemaName', 'configId', 'initSelected'],
                data: cubeDefs
            }),
            listeners: {
                render: function(combo) {
                    var initSelectRecord = combo.getStore().findRecord('initSelected', true);
                    if (initSelectRecord)
                        cubeDefCombo.select(initSelectRecord);
                }
            }
        });

        var form = Ext4.create('Ext.form.Panel', {
            renderTo: 'applicationConfiguration',
            bodyStyle: 'background-color: transparent;',
            border: false,
            width: 420,
            defaults: {
                width: 400,
                labelWidth: 110,
                padding: 5
            },
            items: [contextCombo,cubeDefCombo],
            buttonAlign: 'left',
            dockedItems: [{
                xtype: 'toolbar',
                dock: 'bottom',
                ui: 'footer',
                style: 'background-color: transparent;',
                items: [{ xtype: 'component', flex: 1 },{
                    text: 'Save',
                    handler: function() {
                        var cubeCb = form.getForm().findField('cubeDef');
                        var cubeRec = cubeCb.getStore().findRecord('displayValue', cubeCb.getValue());

                        var values = {};
                        values.configId = cubeRec ? cubeRec.get("configId") : null;
                        values.name = cubeRec ? cubeRec.get("cubeName") : null;
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

<h3>Active application configuration:</h3>
<div id="message"></div>
<div id="applicationConfiguration"></div>
</br>
<h3>Application contexts defined in this folder:</h3>
<p>
<%=textLink("create new", new ActionURL(OlapController.EditAppAction.class, getContainer()), "create-app-context")%>
</p>
<table id="app-contexts">
<% for (String contextName : bean.getAllContextNames()) { %>
    <tr data-name="<%=h(contextName)%>">
        <td><%=h(contextName)%></td>
        <td><%=textLink("edit", new ActionURL(OlapController.EditAppAction.class, getContainer()).addParameter("contextName", contextName))%></td>
        <td><%=textLink("delete", "#", "confirmDeleteApp(" + PageFlowUtil.jsString(contextName) + ");return false;", null)%></td>
    </tr>
<% } %>
</table>
</br>
<h3>OLAP Cube definitions in this folder:</h3>
<p>
<%=textLink("create new", new ActionURL(OlapController.CreateDefinitionAction.class, getContainer()).addReturnURL(getActionURL().clone()), "create-cube-definition")%>
</p>
<%
    Collection<OlapSchemaDescriptor> list = ServerManager.getDescriptors(getContainer());
    %><table id="cube-definitions"><%
    for (OlapSchemaDescriptor sd : list)
    {
        %><tr data-name="<%=h(sd.getName())%>">
            <td style="font-weight: bold;"><%=h(sd.getName())%></td>
            <% if (sd.isEditable()) { %>
                <td><%=textLink("edit", ((CustomOlapSchemaDescriptor) sd).urlEdit().addReturnURL(getActionURL().clone()))%></td>
                <td><%=textLink("delete", ((CustomOlapSchemaDescriptor)sd).urlDelete().addReturnURL(getActionURL().clone()))%></td>
            <% } %>
        </tr><%

        try (OlapConnection conn = sd.getConnection(getContainer(), getUser()))
        {
            for (Schema s : sd.getSchemas(conn,getContainer(), getUser()))
            {
                %><tr><td colspan="3"><ul><%
                for (Cube c : s.getCubes())
                {
                    %><li><%=h(c.getName())%></li><%
                }
                %></ul></td></tr><%
            }
        }
        catch (Exception e)
        {
            %><tr><td colspan="3"><div class="labkey-error"><%=h(e.getMessage())%></div></td><%
        }
    }
    %></table><%
%>

