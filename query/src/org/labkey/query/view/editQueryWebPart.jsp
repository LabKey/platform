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

<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.Portal" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.util.UniqueID" %>
<%@ page extends="org.labkey.query.view.EditQueryPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
        dependencies.add("sqv");
    }
%>
<%
    HttpView<Portal.WebPart> me = (HttpView<Portal.WebPart>) HttpView.currentView();
    Portal.WebPart part = me.getModelBean();
    ViewContext ctx = getViewContext();
    Map<String, String> props = part.getPropertyMap();
    String renderId = "edit-query-" + UniqueID.getRequestScopedUID(HttpView.currentRequest());
%>
<div id="<%=h(renderId)%>"></div>
<script type="text/javascript">
    Ext4.onReady(function() {

        var sqvModel = Ext4.create('LABKEY.sqv.Model', {});

        var schemaName = Ext4.create('Ext.form.field.ComboBox', sqvModel.makeSchemaComboConfig({
            id : 'schemaName',
            editable : true,
            typeAhead : true,
            typeAheadDelay : 250,
            forceSelection : true,
            initialValue : <%=q(props.get("schemaName"))%>,
            fieldLabel : 'Schema',
            name : 'schemaName',
            labelWidth : 225,
            validateOnBlur: false
        }));

        var queryName = Ext4.create('Ext.form.field.ComboBox', sqvModel.makeQueryComboConfig({
            id : 'queryName',
            editable : true,
            typeAhead : true,
            typeAheadDelay : 250,
            forceSelection : true,
            initialValue : <%=q(props.get("queryName"))%>,
            labelWidth : 225,
            name : 'queryName'
        }));

        var allowEnable = <%=PageFlowUtil.jsString(props.get("queryName"))%>;
        queryName.on('enable', function(){if(!allowEnable) queryName.disable();}, this);

        var viewName = Ext4.create('Ext.form.field.ComboBox', sqvModel.makeViewComboConfig({
            id : 'viewName',
            editable : true,
            typeAhead : true,
            typeAheadDelay : 250,
            forceSelection : true,
            initialValue : <%=q(props.get("viewName"))%> ? <%=q(props.get("viewName"))%> : '',
            labelWidth: 225,
            name : 'viewName'
        }));

        var queryForm = Ext4.create('Ext.form.Panel', {
            renderTo: <%=q(renderId)%>,
            defaults : {
                labelWidth: 225,
                width : 525,
                fieldStyle : 'margin-left: 5px;'
            },
            border : false,
            width: 700,
            standardSubmit: true,
            bodyStyle : 'background-color: transparent;',
            items : [{
                xtype: 'textfield',
                fieldLabel: 'Web Part Title',
                name : 'title',
                value : <%=PageFlowUtil.jsString(props.get("title"))%>
            }, schemaName, {
                xtype : 'radiogroup',
                fieldLabel: 'Query and View',
                columns : 1,
                items : [
                    { xtype: 'hidden', name: 'X-LABKEY-CSRF', value: LABKEY.CSRF },
                    {
                    id : 'selectQueryList',
                    name : 'selectQuery',
                    boxLabel : 'Show the list of queries in this schema.',
                    value : false,
                    checked : !<%=PageFlowUtil.jsString(props.get("queryName"))%>,
                    listeners : {
                        change : function(cb, nv, ov){
                            if (nv) {
                                allowEnable = false;
                                queryName.disable();
                                viewName.disable();
                            }
                        }
                    },
                    scope : this
                },{
                    id : 'selectQueryContents',
                    name : 'selectQuery',
                    boxLabel : 'Show the contents of a specific query and view.',
                    width : 500,
                    value : true,
                    checked : <%=PageFlowUtil.jsString(props.get("queryName"))%>,
                    listeners : {
                        change : function(cb, nv){
                            if (nv) {
                                allowEnable = true;
                            }
                            if (schemaName.getRawValue() != '' && nv) {
                                queryName.setDisabled(false);
                                if (queryName.getRawValue() != '') {
                                    viewName.setDisabled(false);
                                }
                            }
                        }
                    }
                }]
            }, queryName, viewName, {
                xtype : 'combo',
                fieldLabel : 'Allow user to choose query?',
                name : 'allowChooseQuery',
                store : {
                    xtype : 'store',
                    fields:['value', 'answer'],
                    data : [
                        {"value": false, "answer" : "No"},
                        {"value": true, "answer" : "Yes"}
                    ]
                },
                queryMode : 'local',
                displayField : 'answer',
                valueField : 'value',
                listeners : {
                    afterrender : function(cb){
                        var value = <%=q(props.get("allowChooseQuery"))%>;
                        cb.select(cb.findRecord('answer', value === 'true' ? 'Yes' : 'No'));
                    }
                }
            }, {
                xtype : 'combo',
                fieldLabel : 'Allow user to choose view?',
                name : 'allowChooseView',
                store : {
                    xtype : 'store',
                    fields:['value', 'answer'],
                    data : [
                        {"value": false, "answer" : "No"},
                        {"value": true, "answer" : "Yes"}
                    ]
                },
                queryMode : 'local',
                displayField : 'answer',
                valueField : 'value',
                listeners : {
                    afterrender : function(cb){
                        var value = <%=q(props.get("allowChooseView"))%>;
                        cb.select(cb.findRecord('answer', value === 'false' ? 'No' : 'Yes'));
                    }
                }
            }, {
                xtype : 'combo',
                fieldLabel : 'Button bar position',
                name : 'buttonBarPosition',
                store : {
                    xtype : 'store',
                    fields : ['value', 'answer'],
                    data : [
                        {"value" : "TOP", "answer" : "Top"},
                        {"value" : "NONE", "answer" : "None"}
                    ]
                },
                queryMode : 'local',
                displayField : 'answer',
                valueField : 'value',
                listeners : {
                    afterrender : function(cb) {
                        if (<%=q(props.get("buttonBarPosition"))%>)
                        {
                            var value = <%=q(props.get("buttonBarPosition"))%>;
                            value = value.substring(0, 1) + value.substring(1, value.length).toLowerCase();
                            cb.select(cb.findRecord('answer', value));
                        }
                        else
                        {
                            cb.select(cb.findRecord('answer', 'Top'));
                        }
                    }
                }
            },{
                xtype : 'button',
                text : 'Submit',
                width : 70,
                margin : '4 0 0 235',
                handler : function() {
                    if (queryForm) {
                        if (validate()) {
                            queryForm.getForm().submit({
                                url : <%=PageFlowUtil.jsString(h(part.getCustomizePostURL(ctx)))%>,
                                success : function(){},
                                failure : function(){}
                            });
                        }
                        else
                        {
                            Ext4.MessageBox.alert("Error Saving", "There are errors in the form.");
                        }
                    }
                }
            }],
            listeners : {
                afterrender : function(){
                    var labels = Ext4.select('.x4-field-label-cell');

                    for(var i = 0; i < labels.getCount(); i++)
                    {
                        labels.item(i).addCls('labkey-form-label');
                        labels.item(i).setStyle('padding', '2px 4px');
                    }

                    var triggers = Ext4.select('.x4-form-trigger');

                    for(i = 0; i < triggers.getCount(); i++)
                    {
                        triggers.item(i).setStyle('margin-left', '5px');
                    }
                }
            }
        });

        var validate = function() {
            if(schemaName.getValue() == null){return false;}
            if(allowEnable){
                if(queryName.getValue() == null){return false;}
                if(viewName.getValue() == null){return false;}
            }
            return true;
        }
    });
</script>
