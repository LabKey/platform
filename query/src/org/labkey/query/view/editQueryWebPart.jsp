<%
/*
 * Copyright (c) 2006-2013 LabKey Corporation
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
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page extends="org.labkey.query.view.EditQueryPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!

    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<ClientDependency>();
        resources.add(ClientDependency.fromFilePath("Ext4"));
        resources.add(ClientDependency.fromFilePath("sqv"));
        return resources;
    }
%>

<%
    Portal.WebPart part = getWebPart();
    ViewContext ctx = getViewContext();
    Map<String, String> props = part.getPropertyMap();
%>
<labkey:scriptDependency/>
<script type="text/javascript">
    Ext4.onReady(function() {

        var webPartTitle = Ext4.create('Ext.form.field.Text', {
            fieldLabel: 'Web Part Title',
            name : 'title',
            value : <%=PageFlowUtil.jsString(props.get("title"))%>,
            labelWidth : 200,
            width : 500
        });

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
            labelWidth : 200,
            width : 500
        }));


        var queryName = Ext4.create('Ext.form.field.ComboBox', sqvModel.makeQueryComboConfig({
            id : 'queryName',
            editable : true,
            typeAhead : true,
            typeAheadDelay : 250,
            forceSelection : true,
            initialValue : <%=q(props.get("queryName"))%>,
            name : 'queryName',
            labelWidth : 200,
            width : 500
        }));
        var viewName = Ext4.create('Ext.form.field.ComboBox', sqvModel.makeViewComboConfig({
            id : 'viewName',
            editable : true,
            typeAhead : true,
            typeAheadDelay : 250,
            forceSelection : true,
            initialValue : <%=q(props.get("viewName"))%> ? <%=q(props.get("viewName"))%> : '',
            name : 'viewName',
            labelWidth : 200,
            width : 500
        }));

        var allowEnable = <%=PageFlowUtil.jsString(props.get("queryName"))%>;
        queryName.on('enable', function(){if(!allowEnable) queryName.disable();}, this);

        var showContentsRadioGroup = Ext4.create('Ext.form.RadioGroup', {
            xtype : 'radiogroup',
            fieldLabel: 'Query and View',
            labelWidth : 200,
            columns : 1,
            padding : 5,
            items : [{
                xtype : 'radio',
                id : 'selectQueryList',
                name : 'selectQuery',
                boxLabel : 'Show the list of queries in this schema.',
                value : false,
                checked : !<%=PageFlowUtil.jsString(props.get("queryName"))%>,
                listeners : {
                    change : function(cb, nv, ov){
                            if(nv){
                                allowEnable = false;
                                queryName.disable();
                                viewName.disable();
                            }
                        }
                    },
                    scope : this
            }, {
                xtype : 'radio',
                id : 'selectQueryContents',
                name : 'selectQuery',
                boxLabel : 'Show the contents of a specific query and view.',
                width : 500,
                value : true,
                checked : <%=PageFlowUtil.jsString(props.get("queryName"))%>,
                listeners : {
                        change : function(cb, nv){
                            if(nv){
                                allowEnable = true;
                            }
                            if(schemaName.getRawValue() != '' && nv){
                                queryName.setDisabled(false);
                                if(queryName.getRawValue() != ''){
                                    viewName.setDisabled(false);
                                }
                            }
                        }
                 }
            }]
        });

        var ynStore = Ext4.create('Ext.data.Store', {
            fields:['value', 'answer'],
            data : [
                {"value": false, "answer" : "No"},
                {"value": true, "answer" : "Yes"}
            ]
        });

        var buttonBarStore = Ext4.create('Ext.data.Store', {
            fields : ['value', 'answer'],
            data : [
                {"value" : "BOTH", "answer" : "Both"},
                {"value" : "TOP", "answer" : "Top"},
                {"value" : "BOTTOM", "answer" : "Bottom"},
                {"value" : "NONE", "answer" : "None"}
            ]
        });

        var submitButton = Ext4.create('Ext.button.Button', {
            text : 'Submit',
            width : 70,
            handler : function() {
                if(queryForm){
                    if(validate()){
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
        });

        var queryForm = Ext4.create('Ext.form.Panel', {
            defaults : {labelWidth: 200, width : 500, height : 30},
            border : false,
            padding : '5px',
            width: 700,
            standardSubmit: true,
            bodyStyle : 'background-color: transparent;',
            items : [webPartTitle, schemaName, showContentsRadioGroup, queryName, viewName, {
                xtype : 'combo',
                fieldLabel : 'Allow user to choose query?',
                name : 'allowChooseQuery',
                store : ynStore,
                queryMode : 'local',
                width : 275,
                displayField : 'answer',
                valueField : 'value',
                listeners : {
                    afterrender : function(cb){
                        var value = <%=q(props.get("allowChooseQuery"))%>;
                        if(value === 'true')
                        {
                            cb.select(cb.findRecord('answer', 'Yes'));
                        }
                        else
                        {
                            cb.select(cb.findRecord('answer', 'No'));
                        }
                    }
                }
            }, {
                xtype : 'combo',
                fieldLabel : 'Allow user to choose view?',
                name : 'allowChooseView',
                store : ynStore,
                queryMode : 'local',
                width : 275,
                displayField : 'answer',
                valueField : 'value',
                listeners : {
                    afterrender : function(cb){
                        var value = <%=q(props.get("allowChooseView"))%>;
                        if(value === 'false')
                        {
                            cb.select(cb.findRecord('answer', 'No'));
                        }
                        else
                        {
                            cb.select(cb.findRecord('answer', 'Yes'));
                        }
                    }
                }
            }, {
                xtype : 'combo',
                fieldLabel : 'Button bar position',
                name : 'buttonBarPosition',
                store : buttonBarStore,
                queryMode : 'local',
                width : 300,
                displayField : 'answer',
                valueField : 'value',
                listeners : {
                    afterrender : function(cb){
                        if(<%=q(props.get("buttonBarPosition"))%>)
                        {
                            var value = <%=q(props.get("buttonBarPosition"))%>;
                            value = value.substring(0, 1) + value.substring(1, value.length).toLowerCase();
                            cb.select(cb.findRecord('answer', value));
                        }
                        else
                        {
                            cb.select(cb.findRecord('answer', 'Both'));
                        }
                    }
                }
            } , submitButton],
            renderTo: 'extDiv'
        });

        var validate = function(){
            if(schemaName.getValue() == null){return false;}
            if(allowEnable){
                if(queryName.getValue() == null){return false;}
                if(viewName.getValue() == null){return false;}
            }
            return true;
        }
    });



</script>

<div id='extDiv'></div>
