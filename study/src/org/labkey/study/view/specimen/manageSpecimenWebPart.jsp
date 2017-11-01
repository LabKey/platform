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
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.study.controllers.specimen.SpecimenController.SpecimenWebPartForm" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
    }
%>
<%
    JspView<SpecimenWebPartForm> me = (JspView<SpecimenWebPartForm>) HttpView.currentView();
    SpecimenWebPartForm bean = me.getModelBean();
    String[] grouping1 = bean.getGrouping1();
    String[] grouping2 = bean.getGrouping2();
    String[] columns = bean.getColumns();
%>
<div style="max-width: 1000px">
    <p>You can specify one or two specimen grouping sections on the specimen web part that allow specimens to be easily viewed by those groupings.
    </p>
</div>
<div id="configurePanel"></div>

<script type="text/javascript">

    var dataFieldName = 'name';
    function createCombo(fieldLabel, name, id, allowBlank, width)
    {
        var combo = Ext4.create('Ext.form.ComboBox', {
            typeAhead: false,
            store: Ext4.create('Ext.data.ArrayStore', {
                fields: [{
                    name: dataFieldName,
                    sortType: function(value) { return value.toLowerCase(); }
                },{
                    name: 'value'
                }],
                sortInfo: { field: dataFieldName }
            }),
            valueField: dataFieldName,
            displayField: dataFieldName,
            fieldLabel: fieldLabel,
            name: name,
            id: id,
            allowBlank: allowBlank,
            readOnly:false,
            editable:false,
            queryMode:'local',
            triggerAction: 'all',
            forceSelection: true,
            listeners: {
                select: function(combo1, records, opts) {
                    if (records && records[0] && records[0].get(this.displayField) == '&nbsp;')
                        this.setValue('',true);
                }
            },
            margin: 2
        });

        if(width){
            combo.setWidth(width);
        }
        return combo;
    }

    function populateColumns(columnCombo, details, initialValue)
    {
        var records = [['&nbsp;','']];
        var columns = [
                <%
                    for (int i = 0; i < columns.length; i += 1)
                    {
                        if (i != 0)
                        {
                            %> , <%
                        }
                %>      <%=q(columns[i])%>
                <%  }%>
        ];
        for (var i = 0; i < columns.length; i++)
        {
            var name = columns[i];
            records[records.length] = [name, name];
        }

        var savedValue = columnCombo.getValue();
        columnCombo.clearValue();

        columnCombo.store.removeAll();
        columnCombo.store.loadData(records);

        if (initialValue)
        {
            savedValue = initialValue;
        }

        if (savedValue)
        {
            var queryColumnIndex = columnCombo.getStore().findExact('name', savedValue);

            if (-1 != queryColumnIndex)
            {
                var record = columnCombo.getStore().getAt(queryColumnIndex);
                columnCombo.setValue(savedValue);
                columnCombo.fireEvent('select', columnCombo, record, queryColumnIndex);
            }
        }
    }

    function getColumns(customizePanel, columnCombos, initialValues)
    {
        for (var i = 0; i < columnCombos.length; i += 1)
            populateColumns(columnCombos[i], null, initialValues[i]);
    }

    (function(){

        var init = function()
        {
            Ext4.QuickTips.init();

            var containerHeader = Ext4.create('Ext.container.Container',{
                layout: {
                    type: 'hbox'
                },
                items: [{
                    xtype: 'label',
                    text: 'Group By',
                    margin: '0 0 0 160',
                    width: 170
                },{
                    xtype: 'label',
                    text: 'Then Group By',
                    margin: '0 0 0 0',
                    width: 184
                },{
                    xtype: 'label',
                    text: 'Then Group By',
                    margin: '0 0 0 0',
                    width: 184
                }]
            });

            var comboBox11 = createCombo('Grouping 1', 'combo11', 'combo11', true, 290);
            var comboBox12 = createCombo('', 'combo12', 'combo12', true, 184);
            var comboBox13 = createCombo('', 'combo13', 'combo13', true, 184);
            var container1 = Ext4.create('Ext.container.Container',{
                layout: {
                    type: 'hbox'
                },
                items: [comboBox11, comboBox12, comboBox13]
            });
            var comboBox21 = createCombo('Grouping 2', 'combo21', 'combo21', true, 290);
            var comboBox22 = createCombo('', 'combo22', 'combo22', true, 184);
            var comboBox23 = createCombo('', 'combo23', 'combo23', true, 184);
            var container2 = Ext4.create('Ext.container.Container',{
                layout: {
                    type: 'hbox'
                },
                items: [comboBox21, comboBox22, comboBox23]
            });

            var form = Ext4.create('Ext.form.FormPanel', {
                renderTo: 'configurePanel',
                bodyStyle: 'background: transparent;',
                frame: false,
                border: false,
                width: 682,
                buttonAlign : 'left',
                items: [containerHeader, container1, container2],
                dockedItems: [{
                    xtype: 'toolbar',
                    dock: 'bottom',
                    ui : 'footer',
                    style : 'background: none',
                    height : 40,
                    items: [{
                        xtype: 'button',
                        text: 'Save',
                        handler: function() {
                            saveSettings(container1, container2);
                        }
                    },{
                        xtype: 'button',
                        text: 'Cancel',
                        handler: function() {window.location = LABKEY.ActionURL.buildURL('study', 'manageStudy.view', null, null);}
                    }]
                }]
            });

            var saveSettings = function(grouping1, grouping2) {
                var data = {grouping1 : [grouping1.items.items[0].getValue(), grouping1.items.items[1].getValue(), grouping1.items.items[2].getValue()],
                            grouping2 : [grouping2.items.items[0].getValue(), grouping2.items.items[1].getValue(), grouping2.items.items[2].getValue()]};
                Ext4.Ajax.request({
                    url : (LABKEY.ActionURL.buildURL('study-samples', 'saveSpecimenWebPartSettings')),
                    method : 'POST',
                    success: function(){
                        window.location = LABKEY.ActionURL.buildURL("study", 'manageStudy.view', null, null);
                    },
                    failure: function(response, options){
                        LABKEY.Utils.displayAjaxErrorResponse(response, options, false, 'An error occurred:<br>');
                    },
                    jsonData : data,
                    headers : {'Content-Type' : 'application/json'},
                    scope: this
                });
            }

            var initialValues = [
                <%= q(grouping1[0])%>, <%= q(grouping1[1])%>, <%= q(grouping1[2])%>,
                <%= q(grouping2[0])%>, <%= q(grouping2[1])%>, <%= q(grouping2[2])%>
            ];
            getColumns(form, [comboBox11, comboBox12, comboBox13, comboBox21, comboBox22, comboBox23], initialValues);
        };

        Ext4.onReady(init);

    })();

</script>
