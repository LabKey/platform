<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%!
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
    }
%>
<div id="view-all-notifications"></div>

<style type="text/css">
    /*.x4-panel-header {*/
        /*background-color: #222222;*/
        /*background-image: none;*/
    /*}*/

    .x4-panel-header-text {
        font-size: 120%;
    }

    .notification-group-panel {
        border: solid #c0c0c0 1px;
        border-top-width: 0;
    }

    .notification-group-view {
        padding: 10px 10px 0 10px;
    }

    .notification-body {
        background-color: #eeeeee;
        border: solid #c0c0c0 1px;
        border-radius: 5px;
        margin-bottom: 10px;
    }

    .notification-header {
        background-color: #c0c0c0;
        padding: 5px;
    }

    .notification-header-unread {
        font-weight: bold;
    }

    .notification-content {
        padding: 5px;
    }

    .notification-readon, .notification-link {
        float: right;
    }

    .notification-header span.fa {
        color: #555555;
    }

    .notification-header-unread span.fa {
        color: #000000;
    }

    .notification-empty-group {
        font-style: italic;
    }

    .notification-all-actions {
        padding-bottom: 10px;
        text-align: right;
    }
</style>

<script type="text/javascript">
    Ext4.onReady(function()
    {
        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('notification', 'getUserNotifications.api'),
            success: LABKEY.Utils.getCallbackWrapper(function(response)
            {
                Ext4.create('LABKEY.core.NotificationViewAll', {
                    renderTo: 'view-all-notifications',
                    notifications: response.notifications
                });
            }, this, false)
        });
    });

    Ext4.define('LABKEY.core.NotificationViewAll', {
        extend: 'Ext.panel.Panel',
        border: false,

        initComponent: function()
        {
            this.groupPanelMap = {};
            this.rowIdToGroupMap = {};

            this.callParent();
            this.addGroupPanels();

            if (!Ext4.isArray(this.notifications) || this.notifications.length == 0)
            {
                this.createNoneDisplay();
            }
            else
            {
                this.insert(0, this.getActionsForAllCmp());
            }
        },

        getStore : function()
        {
            if (!this.store)
            {
                this.store = Ext4.create('Ext.data.Store', {
                    fields: ['RowId', 'Type', 'TypeLabel', 'IconCls',
                        'HtmlContent', 'ActionLinkText', 'ActionLinkUrl',
                        'ReadOn', 'Created', 'CreatedBy', 'ObjectId', 'UserId'],
                    data: this.notifications || [],
                    sorters: [{property: 'Created', direction: 'DESC'}],
                    groupField: 'TypeLabel'
                });
            }

            return this.store;
        },

        addGroupPanels : function()
        {
            var notificationGroups = this.getStore().getGroups();
            Ext4.each(notificationGroups, function(group)
            {
                var notificationDataArr = Ext4.Array.pluck(group.children, 'data');

                // convert date to 'Today' string if applicable
                var today = new Date();
                Ext4.each(notificationDataArr, function(notification)
                {
                    var d = new Date(notification.Created);
                    notification.CreatedDisplay = d.toDateString() == today.toDateString() ? 'Today' : Ext4.Date.format(d, LABKEY.extDefaultDateFormat);

                    d = new Date(notification.ReadOn);
                    notification.ReadOnDisplay = d.toDateString() == today.toDateString() ? 'Today' : Ext4.Date.format(d, LABKEY.extDefaultDateFormat);

                    this.rowIdToGroupMap[notification.RowId] = group.name;
                }, this);

                var panel = Ext4.create('Ext.panel.Panel', {
                    title: group.name + ' (' + group.children.length + ')',
                    cls: 'notification-group-panel',
                    border: false,
                    collapsible: true,
                    titleCollapse: true,
                    collapsed: notificationGroups.length > 4,
                    items: [this.createGroupNotificationsView(notificationDataArr)]
                });

                this.groupPanelMap[group.name] = {
                    panel: panel,
                    rowIds: Ext4.Array.pluck(notificationDataArr, 'RowId')
                };

                this.add(panel);
            }, this);
        },

        createGroupNotificationsView : function(data)
        {
            return Ext4.create('Ext.view.View', {
                border: false,
                cls: 'notification-group-view',
                data: data,
                tpl: new Ext4.XTemplate(
                    '<tpl for=".">',
                        '<div class="notification-body" id="notification-body-{RowId}">',
                            '<div class="notification-header <tpl if="ReadOn == null">notification-header-unread</tpl>">',
                                '<span class="fa {IconCls}"></span> {CreatedDisplay} - {CreatedBy} ',
                                '<span class="notification-readon">',
                                    '<tpl if="ReadOn == null">',
                                        '<a class="labkey-text-link notification-mark-as-read" notificationRowId="{RowId}">Mark As Read</a>',
                                    '<tpl else>Read On: {ReadOnDisplay}',
                                    '</tpl>',
                                '</span>',
                            '</div>',
                            '<div class="notification-content">',
                                '<span class="notification-link"><a class="labkey-text-link notification-dismiss" notificationRowId="{RowId}">Dismiss</a></span>',
                                '<span class="notification-link"><a href="{ActionLinkUrl}" class="labkey-text-link">{ActionLinkText}</a></span>',
                                '<div>{HtmlContent}</div>',
                            '</div>',
                        '</div>',
                    '</tpl>'
                ),
                listeners: {
                    scope: this,
                    viewready: function(view)
                    {
                        // attach click listeners for the "mark as read" text links
                        Ext4.each(view.getEl().query('.notification-mark-as-read'), function(markAsReadEl)
                        {
                            Ext4.get(markAsReadEl).on('click', this.markNotificationAsRead, this);
                        }, this);

                        // attach click listeners for the "dismiss" text links
                        Ext4.each(view.getEl().query('.notification-dismiss'), function(markAsReadEl)
                        {
                            Ext4.get(markAsReadEl).on('click', this.dismissNotification, this);
                        }, this);
                    }
                }
            });
        },

        markNotificationAsRead : function(event, target)
        {
            var rowId = target.getAttribute('notificationRowId');
            LABKEY.Notification.markAsRead(rowId, function()
            {
                // replace the clicked text link with the "Read On: Today" text and remove the "unread" class from the header
                Ext4.get(target.id).up('.notification-readon').update('Read On: Today');
                Ext4.get('notification-body-' + rowId).down('.notification-header').removeCls('notification-header-unread');
            });
        },

        markAllNotificationsAsRead : function(event, target)
        {
            var rowIds = [];
            Ext4.Object.each(this.rowIdToGroupMap, function(rowId, groupName)
            {
                if (Ext4.isDefined(LABKEY.notifications[rowId]) && LABKEY.notifications[rowId].ReadOn == null)
                    rowIds.push(rowId);
            });

            if (rowIds.length > 0)
            {
                LABKEY.Ajax.request({
                    url: LABKEY.ActionURL.buildURL('notification', 'markNotificationAsRead.api'),
                    params: {rowIds: rowIds},
                    success: LABKEY.Utils.getCallbackWrapper(function (response)
                    {
                        if (response.success)
                        {
                            Ext4.Object.each(this.groupPanelMap, function(key, value)
                            {
                                // set the ReadOn value for the notification objects
                                Ext4.each(rowIds, function(rowId)
                                {
                                    LABKEY.notifications[rowId].ReadOn = new Date();
                                });

                                // update the read-on display for the panel elements
                                Ext4.each(value.panel.getEl().query('.notification-readon'), function(readOnEl)
                                {
                                    Ext4.get(readOnEl).update('Read On: Today');
                                });

                                // clear the unread class for the panel elements
                                Ext4.each(value.panel.getEl().query('.notification-header'), function(headerEl)
                                {
                                    Ext4.get(headerEl).removeCls('notification-header-unread');
                                });

                                Ext4.get(target.id).hide();
                                LABKEY.Notification.updateUnreadCount();
                            });
                        }
                    }, this),
                    failure: function(response)
                    {
                        var responseText = LABKEY.Utils.decode(response.responseText);
                        LABKEY.Utils.alert('Error', responseText.exception);
                    }
                });
            }
            else
            {
                Ext4.get(target.id).hide();
            }
        },

        dismissNotification : function(event, target)
        {
            var rowId = target.getAttribute('notificationRowId'),
                me = this;

            LABKEY.Notification.dismiss(rowId, function()
            {
                Ext4.get('notification-body-' + rowId).fadeOut({
                    duration: 750,
                    remove: true,
                    scope: me,
                    callback: function()
                    {
                        this.removeNotificationByRowId(rowId);
                    }
                });
            });
        },

        dismissAllNotifications : function()
        {
            var rowIds = Object.keys(this.rowIdToGroupMap);
            if (rowIds.length > 0)
            {
                LABKEY.Ajax.request({
                    url: LABKEY.ActionURL.buildURL('notification', 'dismissNotification.api'),
                    params: {rowIds: rowIds},
                    success: LABKEY.Utils.getCallbackWrapper(function (response)
                    {
                        if (response.success)
                        {
                            window.location.reload();
                        }
                    }, this),
                    failure: function(response)
                    {
                        var responseText = LABKEY.Utils.decode(response.responseText);
                        LABKEY.Utils.alert('Error', responseText.exception);
                    }
                });
            }
            else
            {
                this.getActionsForAllCmp().hide();
            }
        },

        removeNotificationByRowId : function(rowId)
        {
            var groupName = this.rowIdToGroupMap[rowId],
                groupRowIds = this.groupPanelMap[groupName].rowIds;

            this.groupPanelMap[groupName].rowIds.splice(groupRowIds.indexOf(rowId), 1);
            delete this.rowIdToGroupMap[rowId];

            if (this.groupPanelMap[groupName].rowIds.length == 0)
            {
                this.groupPanelMap[groupName].panel.hide();
                delete this.groupPanelMap[groupName];

                if (Object.keys(this.groupPanelMap).length == 0)
                    this.createNoneDisplay();
            }
            else
            {
                this.groupPanelMap[groupName].panel.setTitle(groupName + ' (' + this.groupPanelMap[groupName].rowIds.length + ')');
            }
        },

        createNoneDisplay : function()
        {
            this.add(Ext4.create('Ext.panel.Panel', {
                title: 'Other (0)',
                cls: 'notification-group-panel',
                border: false,
                collapsible: true,
                titleCollapse: true,
                items: [{
                    xtype: 'box',
                    padding: 10,
                    cls: 'notification-empty-group',
                    html: 'No data to show.'
                }]
            }));
        },

        getActionsForAllCmp : function()
        {
            if (!this.actionsForAllCmp)
            {
                this.actionsForAllCmp = Ext4.create('Ext.Component', {
                    cls: 'notification-all-actions',
                    html: '<a class="labkey-text-link notification-all-read">Mark All As Read</a> '
                        + '<a class="labkey-text-link notification-all-dismiss">Dismiss All</a>',
                    listeners: {
                        scope: this,
                        render: function(cmp)
                        {
                            cmp.getEl().down('.notification-all-read').on('click', this.markAllNotificationsAsRead, this);
                            cmp.getEl().down('.notification-all-dismiss').on('click', this.dismissAllNotifications, this);
                        }
                    }
                });
            }

            return this.actionsForAllCmp;
        }
    });
</script>