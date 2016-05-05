
(function ($)
{
    /**
     * @private
     * @namespace API used by the Notification panel to show, mark as read, etc. the new notifications for a given user.
     */
    LABKEY.Notification = new function ()
    {
        var NOTIF_COUNT_ID = null, NOTIF_COUNT_EL = null,
            NOTIF_PANEL_ID = null, NOTIF_PANEL_EL = null;

        /**
         * Set the dom element Ids for the notification count and panel
         * @param countId - the dom element id for the "inbox" header icon/count
         * @param panelId  - the dom element id for the notification panel
         * @private
         */
        var setElementIds = function (countId, panelId)
        {
            if (countId)
            {
                NOTIF_COUNT_ID = countId;
                NOTIF_COUNT_EL = $('#' + NOTIF_COUNT_ID);
            }
            if (panelId)
            {
                NOTIF_PANEL_ID = panelId;
                NOTIF_PANEL_EL = $('#' + NOTIF_PANEL_ID);
            }
        };

        /**
         * Update the display of the notification "inbox" count
         * @private
         */
        var updateUnreadCount = function ()
        {
            // slide open the notification panel and bind the click listener after the slide animation has completed
            if (NOTIF_COUNT_EL && LABKEY.notifications)
            {
                var count = 0;
                for (var id in LABKEY.notifications)
                {
                    if (LABKEY.notifications.hasOwnProperty(id) && LABKEY.notifications[id].RowId
                        && LABKEY.notifications[id].ReadOn == null)
                    {
                        count++;
                    }
                }
                NOTIF_COUNT_EL.html(count);
            }
        };

        /**
         * Show the notification panel
         * @private
         */
        var showPanel = function ()
        {
            // slide open the notification panel and bind the click listener after the slide animation has completed
            if (NOTIF_PANEL_EL)
            {
                NOTIF_PANEL_EL.slideDown(250, _addCheckHandlers);
            }
        };

        /**
         * Hide the notification panel
         * @private
         */
        var hidePanel = function ()
        {
            // slide out the notification panel and unbind the click listener
            if (NOTIF_PANEL_EL)
            {
                NOTIF_PANEL_EL.slideUp(250, _removeCheckHandlers);
            }
        };

        /**
         * Expand the content body vertically for the selected notification
         * @param domEl - the expand/collapse dom element that was clicked
         * @private
         */
        var toggleBody = function (domEl)
        {
            if (domEl)
            {
                var el = $(domEl);
                if (el.hasClass('fa-angle-down'))
                {
                    el.closest('.lk-notification').find('.lk-notificationbody').addClass('lk-notificationbodyexpand');
                    el.removeClass('fa-angle-down');
                    el.addClass('fa-angle-up');
                }
                else
                {
                    el.closest('.lk-notification').find('.lk-notificationbody').removeClass('lk-notificationbodyexpand');
                    el.removeClass('fa-angle-up');
                    el.addClass('fa-angle-down');
                }
            }
        };

        /**
         * Mark a given notification as read based on the RowId
         * @param id - notification RowId
         * @param callback - function to call on success
         * @private
         */
        var markAsRead = function (id, callback)
        {
            if (id)
            {
                LABKEY.Ajax.request({
                    url: LABKEY.ActionURL.buildURL('core', 'markNotificationAsRead.api'),
                    params: {rowIds: [id]},
                    success: LABKEY.Utils.getCallbackWrapper(function (response)
                    {
                        if (response.success && response.numUpdated == 1)
                        {
                            if (NOTIF_PANEL_EL && id && LABKEY.notifications && LABKEY.notifications[id])
                            {
                                LABKEY.notifications[id].ReadOn = new Date();
                                NOTIF_PANEL_EL.find('#notification-' + id).slideUp(250, _updateGroupDisplay);
                                updateUnreadCount();
                            }

                            if (callback)
                                callback.call(this, id);
                        }
                    })
                });
            }
        };

        /**
         * Mark all notifications as read and clear the notification panel
         * @private
         */
        var clearAllUnread = function ()
        {
            if (NOTIF_PANEL_EL && LABKEY.notifications)
            {
                var rowIds = [];
                for (var id in LABKEY.notifications)
                {
                    if (LABKEY.notifications.hasOwnProperty(id) && LABKEY.notifications[id].RowId
                            && LABKEY.notifications[id].ReadOn == null)
                    {
                        rowIds.push(LABKEY.notifications[id].RowId);
                    }
                }

                if (rowIds.length > 0)
                {
                    LABKEY.Ajax.request({
                        url: LABKEY.ActionURL.buildURL('core', 'markNotificationAsRead.api'),
                        params: {rowIds: rowIds},
                        success: LABKEY.Utils.getCallbackWrapper(function (response)
                        {
                            if (response.success && response.numUpdated == rowIds.length)
                            {
                                for(var i = 0; i < rowIds.length; i++)
                                    LABKEY.notifications[rowIds[i]].ReadOn = new Date();
                                updateUnreadCount();

                                NOTIF_PANEL_EL.find('.lk-notificationarea').slideUp(100, _showNotificationsNone);
                            }
                        })
                    });
                }
            }
        };

        /**
         * Navigate to the given notification's ActionLinkUrl
         * @param event - browser click event to check target
         * @param id - notification RowId
         * @private
         */
        var goToActionLink = function (event, id)
        {
            if (id && LABKEY.notifications && LABKEY.notifications[id])
            {
                if (!event.target.classList.contains("lk-notificationtimes")
                    && !event.target.classList.contains("lk-notificationtoggle")
                    && !event.target.classList.contains("lk-notificationclose"))
                {
                    window.location = LABKEY.notifications[id].ActionLinkUrl;
                }
            }
        };

        /**
         * Navigate to the action to view all notifications
         * @private
         */
        var goToViewAll = function ()
        {
            window.location = LABKEY.ActionURL.buildURL('core', 'userNotifications');
        };

        var _addCheckHandlers = function()
        {
            $('body').on('click', _checkBodyClick);
            $(document).on('keyup', _checkKeyUp);
        };

        var _removeCheckHandlers = function()
        {
            $('body').off('click', _checkBodyClick);
            $(document).off('keyup', _checkKeyUp);
        };

        var _checkBodyClick = function(event)
        {
            // close if the click happened outside of the notification panel
            if (NOTIF_PANEL_EL && event.target.id != NOTIF_PANEL_EL.attr('id') && !NOTIF_PANEL_EL.has(event.target).length)
            {
                hidePanel();
            }
        };

        var _checkKeyUp = function(event)
        {
            // close if the ESC key is pressed
            if (NOTIF_PANEL_EL && event.keyCode == 27)
            {
                hidePanel();
            }
        };

        var _updateGroupDisplay = function()
        {
            if (NOTIF_PANEL_EL && LABKEY.notifications.grouping)
            {
                var hasAnyGroup = false;

                for (var group in LABKEY.notifications.grouping)
                {
                    if (LABKEY.notifications.grouping.hasOwnProperty(group))
                    {
                        var groupRowIds = LABKEY.notifications.grouping[group],
                            hasUnread = false;

                        for (var i = 0; i < groupRowIds.length; i++)
                        {
                            if (LABKEY.notifications[groupRowIds[i]].ReadOn == null)
                            {
                                hasUnread = true;
                                break;
                            }
                        }

                        if (!hasUnread)
                        {
                            var notificationGroupDiv = NOTIF_PANEL_EL.find('#notificationtype-' + group);
                            if (notificationGroupDiv)
                                notificationGroupDiv.addClass('labkey-hidden');
                        }
                        else
                        {
                            hasAnyGroup = true;
                        }
                    }
                }

                if (!hasAnyGroup)
                    _showNotificationsNone();
            }
        };

        var _showNotificationsNone = function()
        {
            if (NOTIF_PANEL_EL)
            {
                var el = NOTIF_PANEL_EL.find('.lk-notificationnone');
                if (el)
                    el.removeClass('labkey-hidden');

                el = NOTIF_PANEL_EL.find('.lk-notificationclearall');
                if (el)
                    el.addClass('labkey-hidden');
            }
        };

        return {
            setElementIds: setElementIds,
            updateUnreadCount: updateUnreadCount,
            showPanel: showPanel,
            hidePanel: hidePanel,
            toggleBody: toggleBody,
            markAsRead: markAsRead,
            clearAllUnread: clearAllUnread,
            goToActionLink: goToActionLink,
            goToViewAll: goToViewAll
        };
    };

})(jQuery);