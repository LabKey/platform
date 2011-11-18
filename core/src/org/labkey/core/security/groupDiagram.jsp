<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.core.security.SecurityController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    ActionURL groupDiagramURL = new ActionURL(SecurityController.GroupDiagramAction.class, getViewContext().getContainer());
%>
<div id="groupDiagram"></div>
<script type="text/javascript">
    Ext.onReady(function() {
        refreshDiagram();
    });

    function refreshDiagram()
    {
        Ext.Ajax.request({
            url: <%=q(groupDiagramURL.toString())%>,
            success: renderGroupDiagram,
            failure: onError
        });
    }

    function renderGroupDiagram(response)
    {
        var bean = Ext.util.JSON.decode(response.responseText);

        document.getElementById("groupDiagram").innerHTML = bean.html;
    }

    function onError(response)
    {
        alert("Error: " + response);
    }
</script>