<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.portal.UtilController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    UtilController.DotForm form = (UtilController.DotForm)HttpView.currentModel();
    Container c = getViewContext().getContainer();
%>
<labkey:errors/>
<form>
    <textarea id=dot rows=20 cols=120><%=h(form.getDot())%></textarea><br>
    <input type=button value="execute" onclick="executeSvg()">
</form>
<div id=svg></div>
<script>
    function executeSvg()
    {
        if (0==1)
        {
            var up = Ext.get('dot').getUpdater();
            up.update({method:'POST', url:'dotSvg.post', params : { dot : Ext.get('dot').getValue() }});
        }
        else
        {
            Ext.Ajax.request(
            {
                method : 'POST',
                url: 'dotSvg.post',
                params: { dot : Ext.get('dot').getValue() },
                success: function(response, conn)
                    {
                        Ext.get('svg').update(response.responseText);
                    },
                failure: function(response, conn)
                    {
                        alert('fail');
                    }
            });
        }
    }
</script>
