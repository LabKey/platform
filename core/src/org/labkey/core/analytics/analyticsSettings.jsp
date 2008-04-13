<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.core.analytics.AnalyticsController" %>
<%@ page import="org.labkey.core.analytics.AnalyticsServiceImpl" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<% AnalyticsController.SettingsForm settingsForm = (AnalyticsController.SettingsForm) __form;
%>
<p>Your LabKey Server can be configured to add JavaScript to your HTML pages, so that information about how your users
    use your server will be sent to Google Analytics.</p>

<form action="<%=new ActionURL(AnalyticsController.BeginAction.class, ContainerManager.getRoot())%>" method="POST">
    <p><labkey:radio name="ff_trackingStatus" value="<%=AnalyticsServiceImpl.TrackingStatus.disabled%>"
                     currentValue="<%=settingsForm.ff_trackingStatus%>"/>
        Do NOT add Google Analytics tracking script to pages on this web site.
    </p>

    <p><labkey:radio name="ff_trackingStatus" value="<%=AnalyticsServiceImpl.TrackingStatus.enabled%>"
                     currentValue="<%=settingsForm.ff_trackingStatus%>"/>
        Add Google Analytics tracking script to pages on this web site.
    </p>

    <p>If you have opted to use Google Analytics, you must provide an Account ID.
        If you use the Account ID <code><%=h(AnalyticsServiceImpl.DEFAULT_ACCOUNT_ID)%></code>,
        your data will be sent to the LabKey Software Foundation. They would love to see how your users are using your
        server.  However, if you would like to see this data yourself, or you would like to keep this information private
        from the LabKey Software Foundation, you should sign up for your own
        <a href="http://www.google.com/analytics">Google Analytics</a> account and use the Account ID that they give you.
    </p>

    <p>Google Analytics Account ID:<br>
        <input type="text" name="ff_accountId" value="<%=h(settingsForm.ff_accountId)%>"/>
    </p>
    <labkey:button text="submit"/>
</form>
