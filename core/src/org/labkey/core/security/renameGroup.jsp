<%
/*
 * Copyright (c) 2009-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.security.Group" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.core.security.SecurityController" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase"%>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
    }
%>
<%
    Group group = (Group)HttpView.currentModel();
    ActionURL manageURL = new ActionURL(SecurityController.GroupAction.class, getContainer());
    manageURL.addParameter("id", group.getUserId());
%>
<div id="renameDiv"></div>
<script type="text/javascript">
    Ext4.onReady(function() {

        var validGroupName = function(s) {
            if (!s)
                return "Required";
            if (!s.match(/^[^@\.\/\\\-&~_]+$/))
                return "Group name should not contain punctuation.";
            return true;
        };

        var renameForm = Ext4.create('Ext.form.Panel', {
            renderTo: 'renameDiv',
            border: false,
            url: LABKEY.ActionURL.buildURL('security', 'renameGroup.post'),
            items: [
                { xtype: 'textfield', name:'newName', fieldLabel: "New Name", allowBlank: false, validator: validGroupName, width: 300 },
                { xtype: 'hidden', name: 'id', value: <%=group.getUserId()%> }
            ],
            bodyStyle: 'background-color: transparent;',
            dockedItems: [{
                xtype: 'toolbar',
                dock: 'bottom',
                ui: 'footer',
                style: 'background-color: transparent; padding: 10px 0 0 0;',
                items: [{
                    text: 'Submit',
                    formBind: true,
                    handler: function() {
                        renameForm.getForm().submit({
                            success: function() {
                                window.location = <%=PageFlowUtil.jsString(manageURL.getLocalURIString())%>;
                            }
                        });
                    }
                },{
                    text: 'Cancel',
                    handler: function() {
                        window.location = <%=PageFlowUtil.jsString(manageURL.getLocalURIString())%>;
                    }
                }]
            }]
        });
    });
</script>