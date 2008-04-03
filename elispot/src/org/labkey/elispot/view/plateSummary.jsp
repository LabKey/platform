<%@ page import="org.labkey.api.data.ColumnInfo"%>
<%@ page import="org.labkey.api.data.TableInfo"%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Date" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.elispot.ElispotRunUploadForm" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.study.Plate" %>
<%@ page import="org.labkey.api.study.Well" %>
<%@ page import="org.labkey.api.study.PlateTemplate" %>
<%@ page import="org.labkey.api.exp.Lsid" %>
<%@ page import="org.labkey.elispot.ElispotDataHandler" %>
<%@ page import="org.labkey.api.exp.OntologyManager" %>
<%@ page import="org.labkey.api.exp.ObjectProperty" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.elispot.ElispotController" %>
<%@ page import="java.sql.SQLException" %>
<%@ page import="org.labkey.api.study.Position" %>
<%@ page import="org.labkey.api.exp.PropertyDescriptor" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<ElispotController.PlateSummaryBean> me = (JspView<ElispotController.PlateSummaryBean>)HttpView.currentView();
    ElispotController.PlateSummaryBean bean = me.getModelBean();
    Container c = HttpView.currentContext().getContainer();
    PlateTemplate template = bean.getTemplate();
    String dataLsid = bean.getDataLsid();
    StringBuffer sb = new StringBuffer();
%>

<script type="text/javascript">

    function showDetails(id)
    {
        var table = document.getElementById('detailsTable');
        if (table)
        {
            var rows = table.getElementsByTagName("tr");
            for (var i=0; i < rows.length; i++)
            {
                var r = rows[i];
                if (r.id == id)
                {
                    r.style.display = "";
                }
                else
                {
                    r.style.display = "none";
                }
            }
        }
    }

    function hideAllDetails(table)
    {
        if (table)
        {
            var rows = table.getElementsByTagName("tr");

            for (var i=0; i < rows.length; i++)
            {
                rows[i].style.display = "none";
            }
        }
    }

</script>

<table border=0 cellspacing=2 cellpadding=2>
    <tr class="wpHeader"><th colspan=50 align=center>Plate Summary Information</th></tr>
<%
    // column header
    out.print("<tr>");
    out.print("<td><div style=\"width:45px; height:35px; text-align:center;\"></div></td>");
    for (int col=0; col < template.getColumns(); col++)
    {
        out.print("<td><div style=\"width:45px; height:35px; text-align:center;\">" + (col + 1) + "</div></td>");
    }
    out.print("</tr>");

    char rowLabel = 'A';
    for (int row=0; row < template.getRows(); row++){
%>
    <tr>
    <%
        out.println("<td><div style=\"width:35px; height:25px; text-align:center;\">" + rowLabel++ + "</div></td>");
        for (int col=0; col < template.getColumns(); col++)
        {
            Position pos = template.getPosition(row, col);
            ElispotController.WellInfo info = bean.getWellInfoMap().get(pos);
    %>
        <td><div style="border:1px solid gray; width:45px; height:35px; vertical-align:middle; text-align:center; background-color:beige;"><br/><a href="javascript:showDetails('<%=getId(pos)%>')"><%=info.getTitle()%></a></div></td>
    <%
        }
    %>
    </tr>
<%
    }
%>
</table>

<p/>&nbsp;<p/>

<table id="detailsTable" border=0 cellspacing=2 cellpadding=2>
    <%
        for (int row=0; row < template.getRows(); row++)
        {
            for (int col=0; col < template.getColumns(); col++)
            {
                Position pos = template.getPosition(row, col);
                ElispotController.WellInfo info = bean.getWellInfoMap().get(pos);
                String id = getId(pos);

                for (ObjectProperty op : info.getWellProperties().values())
                {
                    %>
                    <tr id="<%=id%>" style="display:none;">
                        <td><div style="border:1px solid gray; text-align:left; background-color:beige;"><%=op.getName()%></div></td>
                        <td><div style="border:1px solid gray; text-align:left; background-color:beige;"><%=String.valueOf(op.value())%></div></td>
                    </tr>
                    <%
                }

                for (Map.Entry<PropertyDescriptor, String> entry : info.getSpecimenProperties().entrySet())
                {
                    %>
                    <tr id="<%=id%>" style="display:none;">
                        <td><div style="border:1px solid gray; text-align:left; background-color:beige;"><%=entry.getKey().getName()%></div></td>
                        <td><div style="border:1px solid gray; text-align:left; background-color:beige;"><%=entry.getValue()%></div></td>
                    </tr>
                    <%
                }
            }
        }
    %>
</table>


<%!
    String getId(Position pos)
    {
        return ("wellInfo_" + pos.getRow() + "_" + pos.getColumn());
    }
%>
