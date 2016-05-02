<%@ page import="org.labkey.api.util.UniqueID" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    String notificationCountId = "labkey-notifications-count" + UniqueID.getServerSessionScopedUID();
    String notificationPanelId = "labkey-notifications-panel" + UniqueID.getServerSessionScopedUID();
%>

<script type="text/javascript">
(function($)
{
    LABKEY.Utils.onReady(function ()
    {
        LABKEY.Notification.setElementIds(<%=q(notificationCountId)%>, <%=q(notificationPanelId)%>);
        LABKEY.Notification.updateUnreadCount();

        var html = "";
        html += "<div class='lk-notificationheader'>"
            + "   <div class='lk-notificationtitle'>Notifications</div>"
            + "</div>";

        html += "<div class='lk-notificationclearall " + (LABKEY.notifications.count > 0 ? "" : "labkey-hidden") + "' onclick='LABKEY.Notification.clearAllUnread(); return true;'>clear all</div>";
        html += "<div class='lk-notificationnone " + (LABKEY.notifications.count == 0 ? "" : "labkey-hidden") + "'>No new notifications</div>";
        html += "<div class='lk-notificationarea'>";
        if (LABKEY.notifications.count > 0)
        {
            var groupings = LABKEY.notifications.grouping ? Object.keys(LABKEY.notifications.grouping) : [];

            // sort groups alphabetically, with "Other" at the bottom
            groupings.sort(function(a, b)
            {
                if (a === "Other")
                    return 1;
                else if (b === "Other")
                    return -1;
                else
                    return a.localeCompare(b);
            });

            for (var i = 0; i < groupings.length; i++)
            {
                html += "<div class='lk-notificationtype' id='notificationtype-" + groupings[i] + "'>" + groupings[i] + "</div>";

                var groupRowIds = LABKEY.notifications.grouping[groupings[i]];
                for (var j = 0; j < groupRowIds.length; j++)
                {
                    var rowId = groupRowIds[j], info = LABKEY.notifications[rowId];
                    html += "<div class='lk-notification' id='notification-" + rowId + "' onclick='LABKEY.Notification.goToActionLink(event, " + rowId + "); return true;'>"
                        + "   <div class='fa fa-bell lk-notificationicon'></div>"
                        + "   <div class='lk-notificationbody'>" + info.HtmlContent + "</div>"
                        + "   <div class='fa fa-times lk-notificationclose lk-notificationtimes' onclick='LABKEY.Notification.markAsRead(" + rowId + "); return true;'></div>"
                        + "</div>";
                }
            }
        }
        html += "</div>";

        $('#' + <%=q(notificationPanelId)%>).html(html);
    });
})(jQuery);
</script>

<a href=# class='labkey-menu-text-link' onclick="LABKEY.Notification.showPanel(); return true;">
    <span class="fa fa-inbox lk-notificationinbox"></span>
    <span id=<%=q(notificationCountId)%>>&nbsp;</span>
</a>
<div class="labkey-hidden lk-notificationpanel" id=<%=q(notificationPanelId)%>></div>




