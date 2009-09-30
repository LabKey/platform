<%@ page import="org.labkey.api.security.Group" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.core.security.SecurityController" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page extends="org.labkey.api.jsp.JspBase"%> <%
    ViewContext context = HttpView.currentContext();
    Group group = (Group)HttpView.currentModel();
    ActionURL manageURL = new ActionURL(SecurityController.GroupAction.class, context.getContainer());
    manageURL.addParameter("id",group.getUserId());
%>

<script type="text/javascript">

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
        url:'renameGroup.post',
        items:[
            {name:'newName', xtype:'textfield', fieldLabel:"New Name", allowBlank:false, validator:validGroupName},
            {name:'id', xtype:'hidden', value:<%=group.getUserId()%>}],
        buttons:[{text:'Submit', handler:SubmitButton_onClick}]
    });
    renameForm.render('renameDiv');
    renameForm.items.itemAt(0).focus();
});
</script>

<div id="errorDiv" class="labkey-error">&nbsp;</div>
<div id="renameDiv"></div>

<%--<form action="renameGroup.post" method="POST">
Rename group:
    <input type=hidden name=id value="<%=group.getUserId()%>">
    <input name="newName">
    <input type="submit">
</form>--%>