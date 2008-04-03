<%@ page import="org.labkey.api.exp.api.ExpProtocol" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewURLHelper" %>
<%@ page import="org.labkey.xarassay.XarChooseAssayForm" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<XarChooseAssayForm> me = (JspView<XarChooseAssayForm>) HttpView.currentView();
    List<ExpProtocol> protocols = me.getModelBean().getAvailableProtocols();
    Map<String, String> lnks = me.getModelBean().getLinks();


%>
    <div>
        <br/>
<table class="normal" column="2"  >
        <tr>
            <td colspan="2" align='center'>Select the Assay definition to apply to these files:</td>
        </tr>
        <%
        for (Map.Entry<String, String> entry : lnks.entrySet())
        {
            boolean active = false;
            %>
             <tr>
                 <td align="right"><b><%= entry.getKey() %></b>&nbsp; &nbsp; </td>
                 <td align="left"> <%= buttonLink("select", entry.getValue()) %> </td>
              </tr>
        <% } %>
    <tr>
        <td height="10" colspan="2">&nbsp;</td>
        </tr><tr>

         <td colspan="2" align="left">
         <%=buttonLink("New Assay",new ViewURLHelper("assay", "chooseAssayType", me.getModelBean().getContainer()) )%>
            &nbsp;
             <%=buttonLink("Cancel",new ViewURLHelper("Pipeline", "returnToReferer", me.getModelBean().getContainer()) )%>
        </td>


     </tr>
 
</table>
</ div >

