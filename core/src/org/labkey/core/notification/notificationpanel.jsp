<%@ page import="org.labkey.api.admin.notification.Notification" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<style>
    div.lk-notificationpanel
    {
        position:fixed; right:0; top:0;
        background-color: #323336;
        color:#ffffff;
        height:100%;
        width:400px;
    }
    div.lk-notification
    {
        color:#ffffff;
        padding:10px;
    }
    .lk-notificationpanel a.lk-action
    {
        color:#aaaaaa;
    }
    .lk-notificationlabel
    {
        font-weight:bold;
        background-color:#c7c7c7;
        color:#404040;
        -moz-border-radius:10px 10px 10px 10px;
        border-radius:10px 10px 10px 10px;
        border:solid 1px #000;
        padding:10px;
    }


</style>
<%
    List<Notification> notifications = (List<Notification>) HttpView.currentModel();
    String contextPath = HttpView.currentContext().getContextPath();
%>
<script type="text/ecmascript">
var $ = $ || jQuery;
LABKEY.notifications = {};
LABKEY.notifications.showNotificationsPanel = function()
{
    $('#labkey-notifications-panel').removeClass('labkey-hidden');
    return true;
};
LABKEY.notifications.hideNotificationsPanel = function()
{
    $('#labkey-notifications-panel').addClass('labkey-hidden');
    return true;
};
LABKEY.notifications.markAsSeen = function(el)
{
    // DOESN'T ACTUALLY DO ANYTHING
    $(el).addClass('labkey-hidden');
}
</script>
<a href=# class='labkey-menu-text-link' onclick="return LABKEY.notifications.showNotificationsPanel()"><%=notifications.size()%></a>
<div class="labkey-hidden lk-notificationpanel" style="" id="labkey-notifications-panel">
<div>
    <img style="float:left;" src="<%=contextPath%>/_images/close.png" onclick="return LABKEY.notifications.hideNotificationsPanel()" alt="close">
    <div style="height:20px"><span style="margin:auto;" class="lk-notificationlabel">notifications</span></div>
</div>
<%
    for (Notification notification : notifications)
    {
        %><div class="lk-notification" id="notification-<%=notification.getRowId()%>">
            <div style="float:right;"><img src="<%=contextPath%>/_images/close.png" onclick="return LABKEY.notifications.markAsSeen('#notification-<%=notification.getRowId()%>')" alt="seen"></div>
            <%=text(notification.getHtmlContent())%>
            <br>
            <a class="lk-action" href="<%=notification.getActionLinkURL()%>"><%=h(notification.getActionLinkText())%></a>
        </div><%
    }
%>
</div>




