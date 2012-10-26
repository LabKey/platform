<%
    /*
    * Copyright (c) 2012 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.study.Study"%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
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
    JspView<StudyController.ChangeAlternateIdsForm> me = (JspView<StudyController.ChangeAlternateIdsForm>) HttpView.currentView();
    StudyController.ChangeAlternateIdsForm bean = me.getModelBean();
    Container c = me.getViewContext().getContainer();
    Study s = StudyManager.getInstance().getStudy(c);
    String subjectNounSingular = s.getSubjectNounSingular();
    String subjectNounPlural = s.getSubjectNounPlural();
    String subjectNounColName = s.getSubjectColumnName();
    boolean isAdmin = c.hasPermission(getViewContext().getUser(), AdminPermission.class);
    Integer numberOfDigits = bean.getNumDigits() > 0 ? bean.getNumDigits() : 6;
%>
<div style="max-width: 1000px">
<p>Alternate <%= PageFlowUtil.filter(subjectNounSingular) %> IDs allow you to publish a study with all <%= PageFlowUtil.filter(subjectNounSingular.toLowerCase()) %> IDs
    replaced by randomly generated alternate IDs. Alternate IDs are unique and are automatically generated for all <%= PageFlowUtil.filter(subjectNounPlural.toLowerCase()) %>.
    Alternate IDs will not change unless you explicitly request to change them. You may specify a prefix and the number of digits you want for the Alternate IDs.
</p>
</div>
<div id="alternateIdsPanel"></div>

<script type="text/javascript">

    (function(){

        var init = function()
        {
            Ext4.QuickTips.init();

            var prefixField = Ext4.create('Ext.form.field.Text', {
                fieldLabel: 'Prefix',
                labelSeparator: '',
                value: <%= q(bean.getPrefix())%>,
                width : 220,
                labelWidth: 130,
                maxLength: 20,
                enforceMaxLength: true
            });

            var digitsField = Ext4.create('Ext.form.field.Number', {
                fieldLabel: 'Number of Digits',
                name: 'numberOfDigits',
                labelSeparator: '',
                minValue: 6,
                maxValue: 10,
                value: <%= numberOfDigits%>,
                width : 220,
                labelWidth: 130,
                height: 28
            });

            var controls = [prefixField, digitsField];

            var form = Ext4.create('Ext.form.FormPanel', {
                renderTo: 'alternateIdsPanel',
                bodyPadding: 10,
                bodyStyle: 'background: none',
                frame: false,
                border: false,
                width: 600,
                buttonAlign : 'left',
                items: controls,
                dockedItems: [{
                    xtype: 'toolbar',
                    dock: 'bottom',
                    ui : 'footer',
                    style : 'background: none',
                    height : 30,
                    items: [{
                        xtype: 'button',
                        text: 'Change Alternate IDs',
                        handler: function() {changeAlternateIds(prefixField, digitsField);}
                    },{
                        xtype: 'button',
                        text: 'Export',
                        handler: function() {window.location = LABKEY.ActionURL.buildURL("study", "exportParticipantTransforms");}
                    },{
                        xtype: 'button',
                        text: 'Done',
                        handler: function() {window.location = LABKEY.ActionURL.buildURL('study', 'manageStudy.view', null, null);}
                    }]
                }]
            });

            var displayDoneChangingMessage = function() {
                Ext4.MessageBox.show({
                    title: "Change All Alternate IDs",
                    msg: "Changing Alternate IDs is complete.",
                    buttons: Ext4.MessageBox.OK,
                    icon: Ext4.MessageBox.INFO
                });
            };

            var changeAlternateIds = function(prefixField, digitsField) {
                Ext4.MessageBox.show({
                    title: "Change All Alternate IDs",
                    msg: "This action will change the Alternate IDs for all participants in this study. The Alternate IDs in future published studies will not match the Alternate IDs in previously published studies. Are you sure you want to change all Alternate IDs?",
                    buttons: Ext4.MessageBox.OKCANCEL,
                    icon: Ext4.MessageBox.WARNING,
                    fn : function(buttonID) {
                        if (buttonID == 'ok')
                        {
                            var preVal = prefixField.getValue();
                            var digVal = digitsField.getValue();
                            Ext4.Ajax.request({
                                url : (LABKEY.ActionURL.buildURL("study", "changeAlternateIds")),
                                method : 'POST',
                                success: function(){
                                    displayDoneChangingMessage();
                                },
                                failure: function(response, options){
                                    LABKEY.Utils.displayAjaxErrorResponse(response, options, false, 'An error occurred:<br>');
                                },
                                jsonData : {prefix : preVal, numDigits : digVal},
                                headers : {'Content-Type' : 'application/json'},
                                scope: this
                            });
                        }
                    }
                });
            };
        };

        Ext4.onReady(init);

    })();

</script>
