<%@ page import="org.labkey.api.admin.notification.Notification" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.TreeMap" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.HashMap" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<style>
    A.labkey-menu-text-link I
    {
        font-size:120%;
    }
    .lk-notificationpanel
    {
        position:fixed; right:0; top:0;
        background-color: #323336;
        color:#ffffff;
        height:100%;
        width:400px;
    }
    .lk-notification, .lk-notificationnone
    {
        color: #aaaaaa;
        padding: 10px 15px;
    }
    .lk-notification:hover
    {
        background-color: #555555;
        /*cursor: pointer;*/
    }
    .lk-notificationpanel A.lk-action
    {
        color: #aaaaaa;
    }
    .lk-notificationtype
    {
        padding: 10px 5px;
        color: #ffffff;
        /*font-weight: bold;*/
        border-bottom: solid #aaaaaa 1px;
        margin: 0 10px 10px 10px;
    }
    .lk-notificationclose
    {
        position: absolute;
        right: 15px;
        border-left: solid #aaaaaa 1px;
        line-height: 26px;
        padding-left: 10px;
    }
    .lk-notificationtitle
    {
        padding: 20px 15px;
        font-weight:bold;
        text-transform: uppercase;
        color: #ffffff;
    }
    .lk-notificationbody
    {
        display: inline-block;
        color: #aaaaaa;
        cursor: default;
        padding: 5px 0;
        white-space: normal;
        word-wrap: break-word;
        width: 305px;
        max-height: 100px;
        overflow: hidden;
        text-overflow: ellipsis;
    }
    .lk-notificationicon
    {
        display: inline-block;
        font-size: 16px;
        color: #ffffff;
        margin-right: 5px;
        padding: 5px;
        background-color: #aaaaaa;
        vertical-align: top;
    }
    .lk-notificationtimes
    {
        font-size: 16px;
        color: #aaaaaa;
    }
    .lk-notificationtimes:hover
    {
        color: #ffffff;
    }
</style>
<%
    List<Notification> notifications = (List<Notification>) HttpView.currentModel();
%>
<script type="text/ecmascript">
var $ = $ || jQuery;
LABKEY.notifications = {};
LABKEY.notifications.showNotificationsPanel = function()
{
    // slide open the notification panel and bind the click listener after the slide animation has completed
    $('#labkey-notifications-panel').slideDown(250, function()
    {
        $('body').on('click', LABKEY.notifications.checkClick);
        //$('#labkey-notifications-panel').on('mouseleave', LABKEY.notifications.hideNotificationsPanel);
    });

    return true;
};
LABKEY.notifications.checkClick = function(event)
{
    // close if the click happened outside of the notification panel
    var subject = $('#labkey-notifications-panel');
    if (event.target.id != subject.attr('id') && !subject.has(event.target).length)
    {
        LABKEY.notifications.hideNotificationsPanel();
    }

};
LABKEY.notifications.hideNotificationsPanel = function()
{
    // slide out the notification panel and unbind the click listener
    $('#labkey-notifications-panel').slideUp(250, function()
    {
        $('body').off('click', LABKEY.notifications.checkClick);
    });

    return true;
};
LABKEY.notifications.goToNotificationLink = function(event, href)
{
    if (!event.target.classList.contains("lk-notificationclose"))
    {
        window.location = href;
    }
};
LABKEY.notifications.markAsRead = function(notificationId)
{
    LABKEY.Ajax.request({
        url: LABKEY.ActionURL.buildURL('core', 'markNotificationAsRead.api'),
        params: {rowId: notificationId},
        success: LABKEY.Utils.getCallbackWrapper(function (response)
        {
            if (response.success && response.numUpdated == 1)
            {
                $('#notification-' + notificationId).addClass('labkey-hidden');
                // TODO: update the notification count inbox number in the header bar
            }
        })
    });
}
</script>
<a href=# class='labkey-menu-text-link' onclick="return LABKEY.notifications.showNotificationsPanel()"><i class="fa fa-inbox"></i>&nbsp;<%=notifications.size()%></a>
<div class="labkey-hidden lk-notificationpanel" id="labkey-notifications-panel">
<div class="lk-notificationheader">
    <div class="lk-notificationtitle">Notifications</div>
</div>
<%
    if (notifications == null || notifications.isEmpty())
    {
%>
        <div class="lk-notificationnone">No new notifications</div>
<%
    }
    else
    {
        // TODO: THIS IS TEMPORARY. where to put this type map? add notification table property for module name and use that?
        Map<String, String> typeLabels = new HashMap<>();
        typeLabels.put("AdjudicationCaseCreated", "Adjudication");
        typeLabels.put("AdjudicationCaseAssayDataUpdated", "Adjudication");
        typeLabels.put("AdjudicationCaseCompleted", "Adjudication");
        typeLabels.put("AdjudicationCaseReadyForVerification", "Adjudication");
        typeLabels.put("AdjudicationCaseResolutionRequired", "Adjudication");
        typeLabels.put("Study.SendParticipantGroup", "Study");
        typeLabels.put("org.labkey.issue.model.Issue", "Issues");

        Map<String, List<Notification>> notificationsMap = new TreeMap<>();
        for (Notification notification : notifications)
        {
            String type = notification.getType();
            type = typeLabels.containsKey(type) ? typeLabels.get(type) : "Other";

            if (!notificationsMap.containsKey(type))
                notificationsMap.put(type, new ArrayList<>());

            notificationsMap.get(type).add(notification);
        }

        for (Map.Entry<String, List<Notification>> notificationType : notificationsMap.entrySet())
        {
%>
            <div class="lk-notificationtype"><%=h(notificationType.getKey())%></div>
<%

            for (Notification notification : notificationType.getValue())
            {
%>
                <div class="lk-notification" id="notification-<%=notification.getRowId()%>" onclick="return LABKEY.notifications.goToNotificationLink(event, '<%=h(notification.getActionLinkURL())%>')">
                    <div class="fa fa-bell lk-notificationicon"></div>
                    <div class="lk-notificationbody"><%=text(notification.getHtmlContent())%></div>
                    <div class="fa fa-times lk-notificationclose lk-notificationtimes" onclick="return LABKEY.notifications.markAsRead(<%=notification.getRowId()%>)"></div>
                </div>
<%
            }
        }
    }
%>
</div>




