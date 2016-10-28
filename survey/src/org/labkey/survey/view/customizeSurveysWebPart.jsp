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
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.Portal" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4ClientApi");
    }
%>
<%
    HttpView<Portal.WebPart> me = (HttpView<Portal.WebPart>) HttpView.currentView();
    Portal.WebPart part = me.getModelBean();
    ViewContext ctx = getViewContext();
    Map<String, String> props = part.getPropertyMap();

    Integer surveyDesignId = props.get("surveyDesignId") != null ? Integer.parseInt(props.get("surveyDesignId")) : null;
    final String divId = "form" + getRequestScopedUID();
%>

This webpart displays a list of survey instances created by the end user. Select which survey design this webpart should use:<br><br>
<div id=<%=q(divId)%>></div>

<script type="text/javascript">
    Ext4.onReady(function(){

        var surveyDesignCombo = Ext4.create('Ext.form.field.ComboBox', {
            xtype: 'combo',
            width: 400,
            fieldLabel: 'Survey Design',
            emptyText: 'Select a survey design for this webpart',
            disabled: true,
            name: 'surveyDesignId',
            queryMode: 'local',
            editable : false,
            valueField: 'RowId',
            displayField: 'Label',
            value : <%=surveyDesignId%>,
            store: Ext4.create('LABKEY.ext4.Store', {
                schemaName: "survey",
                queryName: "SurveyDesigns",
                columns: "RowId,Label",
                autoLoad: true,
                sort: "Label",
                listeners: {
                    load: function() {
                        surveyDesignCombo.enable();
                        surveyDesignCombo.addCls('survey-designs-loaded-marker');// for selenium testing
                    }
                }
            })
        });

        var surveyDesignPanel = Ext4.create('Ext.form.Panel', {
            border : false,
            renderTo : <%=q(divId)%>,
            bodyStyle : 'background-color: transparent;',
            standardSubmit: true,
            items : [{ xtype: 'hidden', name: 'X-LABKEY-CSRF', value: LABKEY.CSRF }, surveyDesignCombo],
            dockedItems: [{
                xtype: 'toolbar',
                dock: 'bottom',
                ui: 'footer',
                style: 'background-color: transparent;',
                items: [{
                    xtype: 'button',
                    text: 'Submit',
                    handler : function() {
                        if (surveyDesignPanel && surveyDesignPanel.getForm().isValid())
                        {
                            surveyDesignPanel.getForm().submit({
                                submitEmptyText: false,
                                url : <%=PageFlowUtil.jsString(h(part.getCustomizePostURL(ctx)))%>,
                                success : function(){},
                                failure : function(){}
                            });
                        }
                        else
                            Ext4.MessageBox.alert("Error Saving", "There are errors in the form.");
                    }
                }]
            }]
        });
    });
</script>
