<%
/*
 * Copyright (c) 2012-2013 LabKey Corporation
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
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.data.xml.externalSchema.TemplateSchemaType" %>
<%@ page import="org.labkey.query.controllers.QueryController.BaseExternalSchemaBean" %>
<%@ page import="org.labkey.query.controllers.QueryController.DataSourceInfo" %>
<%@ page import="org.labkey.query.persist.AbstractExternalSchemaDef" %>
<%@ page import="org.labkey.query.persist.AbstractExternalSchemaDef.SchemaType" %>
<%@ page import="org.labkey.query.persist.ExternalSchemaDef" %>
<%@ page import="org.json.JSONArray" %>
<%@ page import="java.util.Collection" %>
<%@ page import="org.json.JSONObject" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="java.util.Arrays" %>
<%@ page import="java.util.ArrayList" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
  public LinkedHashSet<ClientDependency> getClientDependencies()
  {
      LinkedHashSet<ClientDependency> resources = new LinkedHashSet<ClientDependency>();
      resources.add(ClientDependency.fromFilePath("Ext4"));
      resources.add(ClientDependency.fromFilePath("SQVSelector.js"));
      return resources;
  }
%>
<%
    Container c = getViewContext().getContainer();
    BaseExternalSchemaBean bean = (BaseExternalSchemaBean)HttpView.currentModel();
    AbstractExternalSchemaDef def = bean.getSchemaDef();

    boolean isExternal = def instanceof ExternalSchemaDef;

    String initialTemplateName = bean.getSchemaDef().getSchemaTemplate();
    TemplateSchemaType initialTemplate = bean.getSchemaDef().lookupTemplate(c);
%>

<labkey:errors/>
<div id="form"></div>

<script>
    Ext4.onReady(function () {

        var schemaType = <%=q(isExternal ? SchemaType.external.name() : SchemaType.linked.name())%>;
        var external = <%=isExternal%>;

        Ext4.QuickTips.init();

        Ext4.define('LABKEY.Query.FolderTreeStore', {
            extend: 'Ext.data.Model',
            fields: [
                {name: 'containerPath', type: 'string'},
                {name: 'text', type: 'string'},
                {name: 'expanded', type: 'boolean'},
                {name: 'isProject', type: 'boolean'},
                {name: 'id'}
            ]
        });

        Ext4.define('LABKEY.Query.SchemaTemplate', {
            extend: 'Ext.data.Model',
            fields: [
                {name: 'name', type: 'string'},
                {name: 'sourceSchemaName', type: 'string'},
                {name: 'tables', type: 'array'},
                {name: 'metadata', type: 'string'}
            ],
            idProperty: 'name'
        });

        var sqvModel = Ext4.create('LABKEY.SQVModel', {});

        var schemaTypeField = Ext4.create('Ext.form.field.Hidden', {
            name: 'schemaType',
            value: schemaType
        });

        var schemaNameField = Ext4.create('Ext.form.field.Text', {
            name: 'userSchemaName',
            fieldLabel: 'Schema Name',
            allowBlank: false,
            maxLength: 50,
            value: <%=q(def.getUserSchemaName())%>,
            helpPopup: <%=qh(bean.getHelpHTML("UserSchemaName"))%>
        });

        var sourceContainerCombo = Ext4.create('Ext.form.field.ComboBox', sqvModel.makeContainerComboConfig({
            name: 'dataSource',
            fieldLabel: 'Source Container',
            value: <%=q(def.getDataSource())%>
            <%--helpPopup: <%=qh(bean.getHelpHTML("DataSource"))%>--%>
        }));

        sourceContainerCombo.on('change', function (field, newValue, oldValue) {
            var record = field.store.getById(newValue);
            if (record) {
                sourceSchemaField.setDisabled(false);
                metadataField.setDisabled(false);
                schemaTemplateCombo.loadTemplateField(newValue);
            } else {
                sourceSchemaField.setDisabled(true);
                metadataField.setDisabled(true);
                schemaTemplateCombo.setDisabled(true);
            }
        });

        sqvModel.on('beforeschemaload', function (field, newValue, oldValue) {
            // cancel changing the schema combo if a template is selected
            var templateName = schemaTemplateCombo.getValue();
            if (templateName) {
                var templateRecord = schemaTemplateCombo.store.getById(templateName);
                return false;
            }
        });

        sqvModel.on('beforequeryload', function (field, newValue, oldValue) {
            // cancel changing the query combo if a template is selected
            var templateName = schemaTemplateCombo.getValue();
            if (templateName) {
                var templateRecord = schemaTemplateCombo.store.getById(templateName);
                return false;
            }
        });

        var schemaTemplateCombo = Ext4.create('Ext.form.field.ComboBox', {
            name: 'schemaTemplate',
            fieldLabel: 'Schema Template',
            displayField: 'name',
            valueField: 'name',
            editable: true,
            //autoLoad: <%=def.getDataSource() != null%>,
            disabled: <%=def.getDataSource() == null%>,
            value: <%=q(initialTemplateName)%>,
            listConfig : {
                getInnerTpl: function (displayField) {
                    return '{' + displayField + ':htmlEncode}';
                }
            },
            store: Ext4.create('Ext.data.Store', {
                autoLoad: true,
                model: 'LABKEY.Query.SchemaTemplate',
                proxy: {
                    type: 'ajax',
                    buildUrl: function (request) {
                        return LABKEY.ActionURL.buildURL('query', 'schemaTemplates.api', sourceContainerCombo.getValue());
                    },
                    reader: Ext4.create('Ext.data.reader.Json', {
                        type: 'json',
                        root: 'templates',
                        idProperty: 'name'
                    }),
                    idParam: 'name'
                }
            }),
            helpPopup: <%=qh(bean.getHelpHTML("SchemaTemplate"))%>,
            listeners: {
                change: function (field, templateName) {
                    if (templateName) {
                        var record = field.store.getById(templateName);
                        if (record) {
                            if (record.get('sourceSchemaName')) {
                                sourceSchemaField.setDisabled(true);
                                sourceSchemaCombo.setDisabled(true);
                                sourceSchemaCombo.setValue(record.get('sourceSchemaName'));
                            } else {
                                sourceSchemaField.setDisabled(false);
                                sourceSchemaCombo.setDisabled(false);
                            }

                            tablesField.setDisabled(true);
                            tablesCombo.setDisabled(true);
                            tablesCombo.setValue(record.get('tables'));

                            metadataField.setDisabled(true);
                            metadataField.setValue(record.get('metadata'));
                        }
                    } else {
                        if (sourceSchemaField.isDisabled() || sourceSchemaCombo.isDisabled()) {
                            sourceSchemaField.setDisabled(false);
                            sourceSchemaCombo.setDisabled(false);
                            sourceSchemaCombo.clearValue();
                        }

                        tablesField.setDisabled(false);
                        tablesCombo.setDisabled(false);
                        tablesCombo.clearValue();

                        metadataField.setDisabled(false);
                        metadataField.setValue(null);
                    }
                }
            },
            loadTemplateField: function (containerId) {
                if (containerId) {
                    schemaTemplateCombo.setLoading(true);
                    schemaTemplateCombo.clearValue();
                    schemaTemplateCombo.store.load({
                        scope: this,
                        callback: function (records, operation, success) {
                            schemaTemplateCombo.setLoading(false);
                            schemaTemplateCombo.setDisabled(false);
                        }
                    });
                }
            }
        });

        // hard-coded list of system schemas
        var systemSchemas = {
            'announcement': true,
            'auditLog':true,
            'core':true,
            'exp':true,
            'issues':true,
            'pipeline':true,
            'wiki':true
        };

        var systemSchemaFilter = Ext4.create('Ext.util.Filter', {
            filterFn: function (item) {
                var schemaName = item.get('schema');
                return !(schemaName in systemSchemas);
            }
        });

        var sourceSchemaCombo = Ext4.create('Ext.form.field.ComboBox', sqvModel.makeSchemaComboConfig({
            name: 'sourceSchemaName',
            fieldLabel: false,
            editable: true,
            width: 200,
            disabled: <%=def.getDataSource() == null || initialTemplate != null%>,
            value: <%=q(def.getSourceSchemaName())%>,
            listeners: {
                change: function (field, value) {
                    console.log("source schema changed: " + value);
                }
//                disable: function (field, options) {
//                    sourceSchemaField.setDisabled(true);
//                },
//                enable: function (field, options) {
//                    sourceSchemaField.setDisabled(false);
//                }
            }
        }));
        sourceSchemaCombo.store.filter(systemSchemaFilter);

        var systemSchemaCheckbox = Ext4.create('Ext.form.field.Checkbox', {
            boxLabel: 'Show System Schemas',
            padding: "0 0 0 10",
            listeners: {
                change: function (field, newValue, oldValue) {
                    if (newValue) {
                        sourceSchemaCombo.store.clearFilter();
                    } else {
                        sourceSchemaCombo.store.filter(systemSchemaFilter);
                    }
                }
            }
        });

        var sourceSchemaField = Ext4.create('Ext.form.FieldContainer', {
            fieldLabel: 'Source Schema',
            layout: 'hbox',
            disabled: <%=def.getDataSource() == null%>,
            items: [
                sourceSchemaCombo,
                systemSchemaCheckbox
            ],
            helpPopup: <%=qh(bean.getHelpHTML("SourceSchemaName"))%>
        });

        function updateTablesMessage() {
            var tables = tablesCombo.getValue();
            if (tables && tables.length > 0 && tables[0] != '*') {
                tablesMessage.setValue("The " + tables.length + " selected tables will be published");
            } else if (tablesCombo.store.getCount() > 0) {
                tablesMessage.setValue("All " + tablesCombo.store.getCount() + " tables in this schema will be published.");
            }
        }

        <%
        ArrayList<String> tables = new ArrayList<String>();
        if (initialTemplate == null)
        {
            if (def.getTables() != null || def.getTables().length() > 0)
                tables.addAll(Arrays.asList(def.getTables().split(",")));
        }
        else
        {
            if (initialTemplate.isSetTables())
                tables.addAll(Arrays.asList(initialTemplate.getTables().getTableNameArray()));
        }
        %>
        var tablesCombo = Ext4.create('Ext.form.field.ComboBox', sqvModel.makeQueryComboConfig({
            name: 'tables',
            fieldLabel: false,
            width: 395,
            value: <%=text(new JSONArray(tables).toString())%>,
            multiSelect: true,
            allowBlank: true
        }));
        tablesCombo.on('change', updateTablesMessage);
        tablesCombo.on('dataloaded', updateTablesMessage);

        var tablesMessage = Ext4.create('Ext.form.field.Display', {
            width: 400,
            value: '&nbsp;'
        });

        var tablesField = Ext4.create('Ext.form.FieldContainer', {
            fieldLabel: 'Published Tables',
            layout: 'vbox',
            items: [
                tablesCombo,
                tablesMessage
            ],
            helpPopup: <%=qh(bean.getHelpHTML("Tables"))%>
        });

        var metadataField = Ext4.create('Ext.form.field.TextArea', {
            name: 'metaData',
            fieldLabel: 'Meta Data',
            grow: true,
            width: 800,
            height: 400,
            value: <%=q(initialTemplate == null ? def.getMetaData() : (initialTemplate.getMetadata() == null ? "" : initialTemplate.getMetadata().toString()))%>,
            disabled: <%=initialTemplate != null%>,
            helpPopup: <%=qh(bean.getHelpHTML("MetaData"))%>
        });


        var f = new Ext4.create("Ext.form.Panel", {
            renderTo: 'form',
            width: 955,
            border: false,
            standardSubmit: true,
            fieldDefaults: {
                labelWidth: 150,
                width: 150+400
            },
            items: [
                schemaTypeField,
                schemaNameField,
                sourceContainerCombo,
                schemaTemplateCombo,
                sourceSchemaField,
                tablesField,
                metadataField
            ],
            buttons:[{
                text: <%=q(bean.isInsert() ? "Create" : "Update")%>,
                type: 'submit',
                handler: function () {
                    var tablesValue;
                    var templateName = schemaTemplateCombo.getValue();
                    var templateRecord = templateName ? schemaTemplateCombo.store.getById(templateName) : undefined;
                    if (templateRecord) {
                        if (!templateRecord.get('sourceSchemaName')) {
                            sourceSchemaCombo.setValue(null);
                            sourceSchemaField.setDisabled(false);
                            sourceSchemaCombo.setDisabled(false);
                        }

                        tablesCombo.setValue(null);
                        tablesField.setDisabled(true);
                        tablesValue = null;

                        metadataField.setValue(null);
                        metadataField.setDisabled(true);
                    } else {
                        var tables = tablesCombo.getValue();
                        tablesCombo.setDisabled(true);

                        if (!tables) {
                            tablesValue = "*";
                        } else if (tables.length == 0 || tables.length == tablesCombo.store.getCount()) {
                            tablesValue = "*";
                        } else {
                            tablesValue = tables.join(",");
                        }
                    }

                    f.getForm().submit({
                        params: {
                            tables: tablesValue
                        }
                    });
                }
            },{
                <% if (bean.isInsert()) { %>
                text: 'Delete',
                handler: function() { document.location = <%=q(bean.getDeleteURL().toString())%>; }
            },{
                <% } %>
                text: 'Cancel',
                handler: function() { document.location = <%=q(bean.getReturnURL().toString())%>; }
            }],
            buttonAlign:'left'
        });

    })
</script>

