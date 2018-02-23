<%
/*
 * Copyright (c) 2016-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
%>
<%@ page import="org.labkey.api.util.UniqueID" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("internal/jQuery");
        dependencies.add("notification/Notification.css");
        dependencies.add("notification/Notification.js");
    }
%>
<%
    String notificationCountId = "labkey-notifications-count" + UniqueID.getServerSessionScopedUID();
    String notificationPanelId = "labkey-notifications-panel" + UniqueID.getServerSessionScopedUID();
%>
<script type="text/javascript">
(function($) {
    $(function() {
        // need to create the div as a direct child of body so that the z-index will keep it in front
        var notificationPanelDiv = document.createElement("div");
        notificationPanelDiv.id = <%=q(notificationPanelId)%>;
        notificationPanelDiv.className = 'labkey-notification-panel labkey-hidden';
        document.body.appendChild(notificationPanelDiv);

        LABKEY.Notification.setElementIds(<%=q(notificationCountId)%>, <%=q(notificationPanelId)%>);

        function renderNotifications()
        {
            LABKEY.Notification.updateUnreadCount();
            var html = "<div class='labkey-notification-header'><div class='labkey-notification-title'>Notifications</div></div>";

            html += "<div class='labkey-notification-clear-all " + (LABKEY.notifications.unreadCount > 0 ? "" : "labkey-hidden")
                    + "' onclick='LABKEY.Notification.clearAllUnread(); return true;'>Clear all</div>";

            html += "<div class='labkey-notification-none " + (LABKEY.notifications.unreadCount == 0 ? "" : "labkey-hidden")
                    + "'>No new notifications</div>";

            html += "<div class='labkey-notification-area'>";
            if (LABKEY.notifications.unreadCount > 0) {
                var groupings = LABKEY.notifications.grouping ? Object.keys(LABKEY.notifications.grouping) : [];

                // sort groups alphabetically, with "Other" at the bottom
                groupings.sort(function(a, b) {
                    return a === "Other" ? 1 : (b === "Other" ? -1 : a.localeCompare(b));
                });

                for (var i = 0; i < groupings.length; i++) {
                    html += "<div id='notificationtype-" + groupings[i] + "' class='labkey-notification-type'>";
                    html += "<div class='labkey-notification-type-label'>" + LABKEY.Utils.encodeHtml(groupings[i]) + "</div>";

                    var groupRowIds = LABKEY.notifications.grouping[groupings[i]];
                    for (var j = 0; j < groupRowIds.length; j++) {
                        var rowId = groupRowIds[j], info = LABKEY.notifications[rowId];

                        // get the date/time display string based on the current date
                        var monthNames = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"],
                                d = new Date(info.Created), today = new Date(),
                                dStr = d.toDateString() == today.toDateString() ? 'Today' : monthNames[d.getMonth()] + ' ' + d.getDate();

                        html += "<div class='labkey-notification' id='notification-" + rowId + "' onclick='LABKEY.Notification.goToActionLink(event, " + rowId + "); return true;'>"
                                + "   <div class='fa " + info.IconCls + " labkey-notification-icon'></div>"
                                + "   <div class='labkey-notification-close'>"
                                + "      <div class='fa fa-times labkey-notification-times' onclick='LABKEY.Notification.markAsRead(" + rowId + "); return true;'></div>"
                                + "      <div class='fa fa-angle-down labkey-notification-toggle' onclick='LABKEY.Notification.toggleBody(this); return true;'></div>"
                                + "   </div>"
                                + "   <div class='labkey-notification-createdby'>" + dStr + " - " + LABKEY.Utils.encodeHtml(info.CreatedBy) + "</div>"
                                + "   <div class='labkey-notification-body'>" + info.HtmlContent + "</div>"
                                + "</div>";
                    }
                    html += "</div>";
                }
            }
            html += "</div>";

            if (LABKEY.notifications.unreadCount > 0 || LABKEY.notifications.hasRead) {
                html += "<div class='labkey-notification-footer' onclick='LABKEY.Notification.goToViewAll(); return true;'><span>View all notifications</span></div>";
            }

            $('#' + <%=q(notificationPanelId)%>).html(html);
        }

        LABKEY.Notification.onChange(renderNotifications);
        renderNotifications();
    });
})(jQuery);
</script>
<li>
    <a href="#" onclick="LABKEY.Notification.showPanel(); return false;">
        <i class="fa fa-inbox labkey-notification-inbox"></i>
        <span id=<%=q(notificationCountId)%>>&nbsp;</span>
    </a>
</li>





