<%
/*
 * Copyright (c) 2009-2014 LabKey Corporation
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
<%@ page import="org.labkey.core.security.SecurityController" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase"%>
<%
    Group group = (Group)HttpView.currentModel();
    ActionURL manageURL = new ActionURL(SecurityController.GroupAction.class, getContainer());
    manageURL.addParameter("id", group.getUserId());
%>

<script type="text/javascript">

Ext.QuickTips.init();    

var renameForm;

function SubmitButton_onClick()
{
    renameForm.getForm().submit({
        success:function(form,action)
        {
            window.location = <%=PageFlowUtil.jsString(manageURL.getLocalURIString())%>;
        }
    });
}


function validGroupName(s)
{
    if (!s)
        return "Required";
    if (!s.match(/^[^@\.\/\\\-&~_]+$/))      // disallow @./\-&~_ see UserManager.validGroupName()
        return "Group name should not contain punctuation.";
    return true;
}


Ext.onReady(function(){
    renameForm = new LABKEY.ext.FormPanel({
        border:false,
        errorEl:'errorDiv',
        url: LABKEY.ActionURL.buildURL('security','renameGroup.post'),
        items:[
            {name:'newName', xtype:'textfield', fieldLabel:"New Name", allowBlank:false, validator:validGroupName, width:300},
            {name:'id', xtype:'hidden', value:<%=group.getUserId()%>}],
        buttons:[{text:'Submit', handler:SubmitButton_onClick}],
        buttonAlign:'left'
    });
    renameForm.render('renameDiv');
    renameForm.items.itemAt(0).focus();
});
</script>

<div id="errorDiv" class="labkey-error">&nbsp;</div>
<div id="renameDiv"></div>

<%--<labkey:form action="renameGroup.post" method="POST">
Rename group:
    <input type=hidden name=id value="<%=group.getUserId()%>">
    <input name="newName">
    <input type="submit">
</labkey:form>--%>