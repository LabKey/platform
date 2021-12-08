<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
    AdminController.AdjustTimestampsForm form = ((JspView<AdminController.AdjustTimestampsForm>) HttpView.currentView()).getModelBean();
%>

<labkey:errors/>

<labkey:form action="<%=urlFor(AdminController.AdjustSystemTimestampsAction.class)%>" method="post">
   <p>
       This form can be used to adjust the values for system-created timestamp fields (Created and Modified)
       for <b>all tables</b> in <b>all schemas</b> in <b>all modules</b>.  It is intended to be used to adjust
       for data created using an incorrect timezone.
   </p>
    <p>No audit log entries will be created for these changes.</p>
    <p>
        Enter the positive or negative integer corresponding to the number of <b>hours</b>
        by which to adjust all of the Created and Modified timestamp fields.
    </p>
    Hour Delta: <input id="hourDelta" name="hourDelta" type="number" value=<%=h(form.getHourDelta())%>>
    <%= button("Update Timestamps").submit(true).name("update")%>
    <br/><br/>
    <p>
    Upon success, you will be redirected to the Admin Console.
    </p>
</labkey:form>
