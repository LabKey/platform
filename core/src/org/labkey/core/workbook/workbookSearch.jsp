<%
/*
 * Copyright (c) 2010-2016 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.core.workbook.WorkbookSearchBean" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
    }
%>
<%
    JspView<WorkbookSearchBean> me = (JspView) HttpView.currentView();
    String searchString = StringUtils.trimToNull(me.getModelBean().getSearchString());
    int rowId = me.getModelBean().getQueryView().getWebPartRowId();


%>

<script type="text/javascript">

    Ext4.onReady(function() {
        Ext4.create('Ext.panel.Panel', {
            border: false,
            layout: 'hbox',
            itemId: 'ownerPanel',
            defaults: {
                border: false,
                style: 'margin-right: 10px'
            },
            items: [{
                xtype: 'numberfield',
                itemId: 'workbookId',
                labelWidth: 130,
                width: 250,
                hideTrigger: true,
                keyNavEnabled: false,
                spinUpEnabled: false,
                spinDownEnabled: false,
                minValue: 0,
                allowDecimals: false,
                emptyText: 'Enter ID',
                fieldLabel: 'Jump To Workbook',
                enableKeyEvents: true,
                listeners: {
                    keyup: function (field, e) {
                        if (e.getKey() === Ext4.EventObject.ENTER) {
                            var btn = field.up('#ownerPanel').down('#workbookBtn');
                            btn.handler(btn);
                        }
                    }
                }
            }, {
                xtype: 'button',
                itemId: 'workbookBtn',
                border: true,
                text: 'Go',
                style: 'margin-right: 40px;margin-top: 0px',
                handler: function (btn) {
                    var field = btn.up('panel').down('#workbookId');
                    if (!field.getValue()) {
                        Ext4.Msg.alert('Error', 'Must enter a workbook Id');
                        return;
                    }

                    var val = parseInt(field.getValue());
                    if (!val){
                        Ext4.Msg.alert('Error', 'Must enter a value');
                        return;
                    }

                    Ext4.Msg.wait('Loading...');

                    LABKEY.Query.selectRows({
                        schemaName: 'core',
                        queryName: 'containers',
                        columns: 'Path',
                        filterArray: [
                            LABKEY.Filter.create('name', val),
                            LABKEY.Filter.create('type', 'workbook')
                        ],
                        scope: this,
                        error: function(){
                            Ext4.Msg.alert('Error', 'There was an error searching for workbook: ' + val);
                        },
                        success: function (results) {
                            Ext4.Msg.hide();

                            if (!results.rows || !results.rows.length) {
                                Ext4.Msg.alert('Error', 'Workbook not found');
                                return;
                            }

                            var row = results.rows[0];
                            window.location = LABKEY.ActionURL.buildURL('project', 'start', row['Path']);
                        }
                    });
                }
            }, {
                xtype: 'textfield',
                itemId: 'searchText',
                labelWidth: 130,
                width: 300,
                fieldLabel: 'Search Workbooks',
                emptyText: 'Enter Text',
                enableKeyEvents: true,
                value: <%=q(h(searchString == null ? "" : searchString))%>,
                listeners: {
                    keyup: function (field, e) {
                        if (e.getKey() === Ext4.EventObject.ENTER) {
                            var btn = field.up('#ownerPanel').down('#searchBtn');
                            btn.handler(btn);
                        }
                    }
                }
            }, {
                xtype: 'button',
                border: true,
                itemId: 'searchBtn',
                text: 'Search',
                //style: 'margin-top: 3px',
                handler: function (btn) {
                    var field = btn.up('panel').down('#searchText');
                    if (!field.getValue()) {
                        Ext4.Msg.alert('Error', 'Must enter a search term');
                        return;
                    }

                    window.location = LABKEY.ActionURL.buildURL('search', 'search', null, {
                        container: LABKEY.Security.currentContainer.id,
                        includeWorkbooks: 1,
                        q: field.getValue(),
                        scope: 'FolderAndSubfolders'
                    })
                }
            }]

        }).render('workbookSearch_<%=h(rowId)%>');
    });
</script>

<div id="workbookSearch_<%=h(rowId)%>"></div>
<br>