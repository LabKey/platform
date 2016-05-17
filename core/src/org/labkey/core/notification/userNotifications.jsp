<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
        dependencies.add("notification/Notification.js");
        dependencies.add("notification/NotificationViewAll.js");
        dependencies.add("notification/NotificationViewAll.css");
    }
%>
<div id="view-all-notifications"></div>

<script type="text/javascript">
    Ext4.onReady(function()
    {
        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('notification', 'getUserNotifications.api'),
            success: LABKEY.Utils.getCallbackWrapper(function(response)
            {
                Ext4.create('LABKEY.core.notification.NotificationViewAll', {
                    renderTo: 'view-all-notifications',
                    notifications: response.notifications
                });
            }, this, false)
        });
    });
</script>