<%
/*
 * Copyright (c) 2012-2016 LabKey Corporation
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
<%@ page import="org.json.JSONArray" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.data.xml.externalSchema.TemplateSchemaType" %>
<%@ page import="org.labkey.query.controllers.QueryController.BaseExternalSchemaBean" %>
<%@ page import="org.labkey.query.persist.AbstractExternalSchemaDef" %>
<%@ page import="org.labkey.query.persist.AbstractExternalSchemaDef.SchemaType" %>
<%@ page import="org.labkey.query.persist.LinkedSchemaDef" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Arrays" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
        dependencies.add("sqv");
    }
%>
<%
    BaseExternalSchemaBean bean = (BaseExternalSchemaBean)HttpView.currentModel();
    AbstractExternalSchemaDef def = bean.getSchemaDef();

    Container targetContainer = getContainer();
    Container sourceContainer = targetContainer;

    boolean isExternal = true;
    if (def instanceof LinkedSchemaDef)
    {
        isExternal = false;
        sourceContainer = ((LinkedSchemaDef)def).lookupSourceContainer();
    }

    String initialTemplateName = def.getSchemaTemplate();
    TemplateSchemaType initialTemplate = def.lookupTemplate(sourceContainer);
%>
<labkey:errors/>
<div id="form"></div>

<script type="text/javascript">
    Ext4.onReady(function () {

        var schemaType = <%=q(isExternal ? SchemaType.external.name() : SchemaType.linked.name())%>;
        var external = <%=isExternal%>;

        Ext4.QuickTips.init();

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

        Ext4.define('LABKEY.ext.OverrideField', {
            extend: 'Ext.Component',

            config: {
                boundField: 'field-or-id',
                resetValueFunction: null,
                override: false
            },

            constructor: function (config) {
                this.callParent(arguments);
                this.initConfig(config);
                return this;
            },

            initComponent: function () {
                this.callParent(arguments);
                // Add a click event to the DOM element
                this.on('afterrender', function () {
                    this.getEl().on('click', this.onOverrideClick, this);
                }, this);
            },

            applyOverride: function (override) {
                if (override) {
                    this.update("<span class='labkey-link' style='font-size:smaller;'>Revert to template value</span>");
                } else {
                    this.update("<span class='labkey-link' style='font-size:smaller;'>Override template value</span>");

                    var value = this.resetValueFunction();
                    if (value !== undefined)
                        this.boundField.setValue(value);
                }
                return override;
            },

            onOverrideClick: function () {
                if (this.getOverride()) {
                    this.setOverride(false);
                    this.boundField.setDisabled(true);
                    var fieldContainer = this.boundField.up('fieldcontainer');
                    if (fieldContainer)
                        fieldContainer.setDisabled(true);
                }
                else {
                    this.setOverride(true);
                    this.boundField.setDisabled(false);
                    var fieldContainer = this.boundField.up('fieldcontainer');
                    if (fieldContainer)
                        fieldContainer.setDisabled(false);
                }
            }
        });

        var sqvModel = Ext4.create('LABKEY.sqv.Model', {});

        var schemaTypeField = Ext4.create('Ext.form.field.Hidden', {
            name: 'schemaType',
            value: schemaType
        });

        var schemaNameField = Ext4.create('Ext.form.field.Text', {
            name: 'userSchemaName',
            fieldLabel: 'Schema Name',
            allowBlank: false,
            validateOnBlur: false,
            maxLength: 50,
            value: <%=q(def.getUserSchemaName())%>,
            helpPopup: <%=qh(bean.getHelpHTML("UserSchemaName"))%>
        });

        var sourceContainerCombo = Ext4.create('Ext.form.field.ComboBox', sqvModel.makeContainerComboConfig({
            name: 'dataSource',
            fieldLabel: 'Source Container',
            value: <%=q(def.getDataSource())%>,
            <%--helpPopup: <%=qh(bean.getHelpHTML("DataSource"))%>--%>
        }));

        sourceContainerCombo.on('select', function (field, records) {
            var record = records[0];
            if (record) {
                sourceSchemaField.setDisabled(false);
                metadataField.setDisabled(false);
                schemaTemplateCombo.loadTemplateField(field.getValue());
            } else {
                sourceSchemaField.setDisabled(true);
                metadataField.setDisabled(true);
                schemaTemplateCombo.setDisabled(true);
            }
        });


        var schemaTemplateCombo = Ext4.create('Ext.form.field.ComboBox', {
            name: 'schemaTemplate',
            fieldLabel: 'Schema Template',
            width: 395,
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
                            // Set the tables before setting the schema otherwise the previous tableCombo value may be used once the schemas's tables have been loaded.
                            tablesField.setDisabled(true);
                            tablesCombo.setDisabled(true);
                            tablesCombo.setValue(record.get('tables'));
                            tablesOverride.setOverride(false);
                            tablesOverride.setVisible(true);

                            if (record.get('sourceSchemaName')) {
                                sourceSchemaField.setDisabled(true);
                                sourceSchemaCombo.setDisabled(true);
                                sourceSchemaCombo.setValue(record.get('sourceSchemaName'));
                            } else {
                                sourceSchemaField.setDisabled(false);
                                sourceSchemaCombo.setDisabled(false);
                            }
                            sourceSchemaOverride.setOverride(false);
                            sourceSchemaOverride.setVisible(true);

                            metadataField.setDisabled(true);
                            metadataTextArea.setValue(record.get('metadata'));
                            metadataOverride.setOverride(false);
                            metadataOverride.setVisible(true);
                        }
                    } else {
                        if (sourceSchemaField.isDisabled() || sourceSchemaCombo.isDisabled()) {
                            sourceSchemaField.setDisabled(false);
                            sourceSchemaCombo.setDisabled(false);
                            sourceSchemaCombo.clearValue();
                        }
                        sourceSchemaOverride.setOverride(false);
                        sourceSchemaOverride.setVisible(false);

                        tablesField.setDisabled(false);
                        tablesCombo.setDisabled(false);
                        tablesCombo.clearValue();
                        tablesOverride.setOverride(false);
                        tablesOverride.setVisible(false);

                        metadataField.setDisabled(false);
                        metadataTextArea.setValue(null);
                        metadataOverride.setOverride(false);
                        metadataOverride.setVisible(false);
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
            disabled: <%=def.getSourceSchemaName() == null && initialTemplate != null%>,
            //value: <%=q(def.getSourceSchemaName() != null ? def.getSourceSchemaName() : (initialTemplate != null ? initialTemplate.getSourceSchemaName() : ""))%>,
            initialValue: <%=q(def.getSourceSchemaName() != null ? def.getSourceSchemaName() : (initialTemplate != null ? initialTemplate.getSourceSchemaName() : ""))%>,
            // Prevent the 'dataloaded' event from being fired when the template changes when creating a new linked schema.
            initiallyLoaded: <%=bean.isInsert()%>,
            listeners: {
                change: function (field, value) {
//                    console.log("source schema changed: " + value);
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

        var sourceSchemaOverride = Ext4.create('LABKEY.ext.OverrideField', {
            id: 'sourceSchemaOverride',
            boundField: sourceSchemaCombo,
            override: <%=def.getSourceSchemaName() != null && initialTemplate != null%>,
            hidden: <%=initialTemplate == null%>,
            resetValueFunction: function () {
                var schemaTemplateName = schemaTemplateCombo.getValue();
                var schemaTemplateRecord = schemaTemplateCombo.store.getById(schemaTemplateName);
                if (schemaTemplateRecord) {
                    var sourceSchemaName = schemaTemplateRecord.get('sourceSchemaName');
                    return sourceSchemaName;
                }
            }
        });

        var sourceSchemaField = Ext4.create('Ext.form.FieldContainer', {
            fieldLabel: 'Source Schema',
            layout: 'vbox',
            disabled: <%=def.getSourceSchemaName() == null && initialTemplate != null%>,
            items: [
                {
                    xtype: 'container',
                    layout: 'hbox',
                    items: [ sourceSchemaCombo, systemSchemaCheckbox ]
                },
                sourceSchemaOverride
            ],
            helpPopup: <%=qh(bean.getHelpHTML("SourceSchemaName"))%>
        });

        var updateTablesMessage = function() {
            var tables = tablesCombo.getValue();
            if (tables && tables.length > 0 && tables[0] != '*') {
                tablesMessage.setValue("The " + tables.length + " selected tables will be published");
            } else if (tablesCombo.store.getCount() > 0) {
                tablesMessage.setValue("All " + tablesCombo.store.getCount() + " tables in this schema will be published.");
            }
            tablesMessage.setVisible(true);
        };

        <%
        ArrayList<String> tables = new ArrayList<>();
        if (def.getTables() != null && def.getTables().length() > 0)
        {
            tables.addAll(Arrays.asList(def.getTables().split(",")));
        }
        else if (initialTemplate != null && initialTemplate.isSetTables())
        {
            tables.addAll(Arrays.asList(initialTemplate.getTables().getTableNameArray()));
        }
        %>
        var tablesCombo = Ext4.create('Ext.ux.CheckCombo', sqvModel.makeQueryComboConfig({
            name: 'tables',
            fieldLabel: false,
            width: 395,
            //value: <%=text(new JSONArray(tables).toString())%>,
            initialValue: <%=text(new JSONArray(tables).toString())%>,
            // Prevent the 'dataloaded' event from being fired when the template changes when creating a new linked schema.
            initiallyLoaded: <%=bean.isInsert()%>,
            disabled: <%=def.getTables() == null && initialTemplate != null%>,
            multiSelect: true,
            allowBlank: true,
            getTablesValueForSubmit : function () {
                var tablesValue = "*";
                var tables = tablesCombo.getValue();
                if (!tables) {
                    tablesValue = "*";
                } else if (tables.length == 0 || tables.length == tablesCombo.store.getCount()) {
                    tablesValue = "*";
                } else {
                    tablesValue = tables.join(",");
                }
                return tablesValue;
            }
        }));
        tablesCombo.on('change', updateTablesMessage);
        tablesCombo.on('dataloaded', updateTablesMessage);

        var tablesMessage = Ext4.create('Ext.form.field.Display', {
            width: 400,
            value: '&nbsp;',
            hidden: true
        });

        var tablesOverride = Ext4.create('LABKEY.ext.OverrideField', {
            id: 'tablesOverride',
            boundField: tablesCombo,
            override: <%=def.getTables() != null && initialTemplate != null%>,
            hidden: <%=initialTemplate == null%>,
            resetValueFunction: function () {
                var schemaTemplateName = schemaTemplateCombo.getValue();
                var schemaTemplateRecord = schemaTemplateCombo.store.getById(schemaTemplateName);
                if (schemaTemplateRecord) {
                    var tables = schemaTemplateRecord.get('tables');
                    return tables;
                }
            }
        });

        var tablesField = Ext4.create('Ext.form.FieldContainer', {
            fieldLabel: 'Published Tables',
            layout: 'vbox',
            disabled: <%=def.getTables() == null && initialTemplate != null%>,
            items: [
                tablesCombo,
                tablesMessage,
                tablesOverride
            ],
            helpPopup: <%=qh(bean.getHelpHTML("Tables"))%>
        });

        var metadataTextArea = Ext4.create('Ext.form.field.TextArea', {
            name: 'metaData',
            grow: true,
            width: 600,
            height: 400,
            value: <%=q(def.getMetaData() != null ? def.getMetaData() : (initialTemplate != null && initialTemplate.getMetadata() != null ? initialTemplate.getMetadata().toString() : ""))%>,
            disabled: <%=def.getMetaData() == null && initialTemplate != null%>
        });

        var metadataOverride = Ext4.create('LABKEY.ext.OverrideField', {
            id: 'metadataOverride',
            boundField: metadataTextArea,
            override: <%=def.getMetaData() != null && initialTemplate != null%>,
            hidden: <%=initialTemplate == null%>,
            resetValueFunction : function () {
                var schemaTemplateName = schemaTemplateCombo.getValue();
                var schemaTemplateRecord = schemaTemplateCombo.store.getById(schemaTemplateName);
                if (schemaTemplateRecord) {
                    var metaData = schemaTemplateRecord.get('metadata');
                    return metaData;
                }
            }
        });

        var metadataField = Ext4.create('Ext.form.FieldContainer', {
            fieldLabel: 'Metadata',
            layout: 'vbox',
            width: 800,
            disabled: <%=def.getMetaData() == null && initialTemplate != null%>,
            items: [
                metadataTextArea,
                metadataOverride
            ],
            helpPopup: <%=qh(bean.getHelpHTML("MetaData"))%>
        });

        var f = new Ext4.create("Ext.form.Panel", {
            renderTo: 'form',
            width: 955,
            border: false,
            frame: false,
            bodyStyle: 'background-color: transparent;',
            standardSubmit: true,
            fieldDefaults: {
                labelWidth: 150,
                width: 600
            },
            items: [
                { xtype: 'hidden', name: 'X-LABKEY-CSRF', value: LABKEY.CSRF },
                schemaTypeField,
                schemaNameField,
                sourceContainerCombo,
                schemaTemplateCombo,
                sourceSchemaField,
                tablesField,
                metadataField
            ],
            dockedItems: [{
                xtype: 'toolbar',
                dock: 'bottom',
                ui: 'footer',
                style : 'background-color: transparent;',
                items: [{
                    text: <%=q(bean.isInsert() ? "Create" : "Update")%>,
                    type: 'submit',
                    handler: function () {
                        var sourceContainerValue = sourceContainerCombo.getValue();
                        if (!sourceContainerValue)
                            sourceContainerValue = LABKEY.container.id;

                        var sourceSchemaValue = sourceSchemaCombo.getValue();
                        var tablesValue = tablesCombo.getTablesValueForSubmit();
                        var metadataValue = metadataTextArea.getValue();

                        // If a schema template is set, null the values for any fields that haven't been manually overridden.
                        var templateName = schemaTemplateCombo.getValue();
                        var templateRecord = templateName ? schemaTemplateCombo.store.getById(templateName) : undefined;
                        if (templateRecord) {
                            if (!sourceSchemaOverride.getOverride()) {
                                sourceSchemaValue = null;
                            }

                            if (!tablesOverride.getOverride()) {
                                tablesValue = null;
                            }

                            if (!metadataOverride.getOverride()) {
                                metadataValue = null;
                            }
                        }

                        // Always disable the fields and submit the values manually
                        sourceContainerCombo.setDisabled(true);
                        sourceSchemaField.setDisabled(true);
                        sourceSchemaCombo.setDisabled(true);
                        tablesField.setDisabled(true);
                        tablesCombo.setDisabled(true);
                        metadataField.setDisabled(true);
                        metadataTextArea.setDisabled(true);

                        f.getForm().submit({
                            params: {
                                dataSource: sourceContainerValue,
                                sourceSchemaName: sourceSchemaValue,
                                tables: tablesValue,
                                metaData: metadataValue,
                                // Ext form doesn't do the automatic X-LABKEY-CSRF thing
                                'X-LABKEY-CSRF':LABKEY.CSRF
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
                }]
            }]
        });

    });
</script>

