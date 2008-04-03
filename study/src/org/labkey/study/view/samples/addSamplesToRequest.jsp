<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.study.controllers.samples.SpringSpecimenController"%>
<%@ page import="org.labkey.study.model.Specimen"%>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<SpringSpecimenController.AddToExistingRequestBean> me = (JspView<org.labkey.study.controllers.samples.SpringSpecimenController.AddToExistingRequestBean>) HttpView.currentView();
    SpringSpecimenController.AddToExistingRequestBean bean = me.getModelBean();
    String headerTDStyle = "text-align:left;background-color:#EEEEEE;border-top:solid 1px";
%>
<%
    if (bean.getSpecimenQueryView() == null)
    {
%>
    <span class="labkey-error">ERROR: No samples were selected.  If you believe you've received this message in error,
    please contact your system administrator.</span><br>
<%
    }
    else
    {
%>
<form action="<%= "showCreateSampleRequest.post" %>" method="POST">
    Please select a request below to which to add the selected specimens.<br>
    Note that only the creator of a request or an administrator can add specimens to an existing request.<br>
    <br>
    Alternately, you may create a new request for the selected specimens.<br><br>
    <%= buttonImg("Create New Specimen Request") %><br><br>
    <%
    for (Specimen specimen : bean.getSamples())
    {
    %><input type="hidden" name="sampleIds" value="<%= specimen.getRowId() %>"><%
    }
%>
    <table class="normal">
        <tr>
            <th style="<%= headerTDStyle %>">Available Specimen Requests</th>
        </tr>
        <tr>
            <td><% me.include(bean.getRequestsGridView(), out); %><br></td>
        </tr>
        <tr>
            <th style="<%= headerTDStyle %>">Selected Vials</th>
        </tr>
        <tr>
            <td><% if (bean.getSpecimenQueryView() != null)
                    me.include(bean.getSpecimenQueryView(), out); %></td>
        </tr>
    </table>
</form>
<%
    }
%>