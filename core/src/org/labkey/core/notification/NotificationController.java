/*
 * Copyright (c) 2016-2019 LabKey Corporation
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
package org.labkey.core.notification;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.FormHandlerAction;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.admin.notification.Notification;
import org.labkey.api.admin.notification.NotificationService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.security.RequiresLogin;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Created by matthew on 5/11/2016.
 */
public class NotificationController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(NotificationController.class);

    public NotificationController()
    {
        setActionResolver(_actionResolver);
    }

    public static class NotificationForm extends ReturnUrlForm
    {
        int rowid = -1;

        public int getRowid()
        {
            return rowid;
        }

        public void setRowid(int rowid)
        {
            this.rowid = rowid;
        }
    }

    // redirect to target URL and mark notification as read
    @RequiresLogin
    public static class GotoAction extends FormHandlerAction<NotificationForm>
    {
        @Override
        public void validateCommand(NotificationForm target, Errors errors)
        {

        }

        @Override
        public boolean handlePost(NotificationForm form, BindException errors)
        {
            NotificationService.get().markAsRead(getUser(), form.getRowid());
            return true;
        }

        @Override
        public URLHelper getSuccessURL(NotificationForm form)
        {
            return form.getReturnActionURL();
        }
    }

    @RequiresPermission(ReadPermission.class) @RequiresLogin
    public class MarkNotificationAsReadAction extends MutatingApiAction<RowIdsForm>
    {
        private List<Notification> _notifications = new ArrayList<>();

        @Override
        public void validateForm(RowIdsForm form, Errors errors)
        {
            List<Notification> validNotifications = validateNotificationRowIdsForUser(getUser(), form, errors);
            _notifications.addAll(validNotifications);
        }

        @Override
        public ApiResponse execute(RowIdsForm form, BindException errors)
        {
            int totalUpdated = 0;

            try(DbScope.Transaction transaction = CoreSchema.getInstance().getSchema().getScope().ensureTransaction())
            {
                for (Notification notification : _notifications)
                {
                    Container c = ContainerManager.getForId(notification.getContainer());
                    int numUpdated = NotificationService.get().markAsRead(c, getUser(), notification.getObjectId(),
                            Collections.singletonList(notification.getType()), notification.getUserId()
                    );

                    totalUpdated += numUpdated;
                }

                transaction.commit();
            }

            ApiSimpleResponse response = new ApiSimpleResponse();
            response.put("numUpdated", totalUpdated);
            response.put("success", true);
            return response;
        }
    }

    @RequiresPermission(ReadPermission.class) @RequiresLogin
    public class MarkAllNotificationAsReadAction extends MutatingApiAction<NotificationsForm>
    {
        @Override
        public ApiResponse execute(NotificationsForm form, BindException errors)
        {
            int totalUpdated = 0;

            try(DbScope.Transaction transaction = CoreSchema.getInstance().getSchema().getScope().ensureTransaction())
            {
                NotificationService service = NotificationService.get();
                Container notificationContainer = form.isByContainer() ? getContainer() : null;
                ContainerFilter containerFilter = null;
                if (form.isByContainer() && !StringUtils.isEmpty(form.getContainerFilter()))
                    containerFilter = ContainerFilter.getContainerFilterByName(form.getContainerFilter(), getContainer(), getUser());

                List<Notification> notifications;
                if (form.getTypeLabels() == null || form.getTypeLabels().isEmpty())
                    notifications = service.getNotificationsByUser(notificationContainer, getUser().getUserId(), true, containerFilter);
                else
                    notifications = service.getNotificationsByTypeLabels(notificationContainer, form.getTypeLabels(), getUser().getUserId(), true, containerFilter);

                for (Notification notification : notifications)
                {
                    Container c = ContainerManager.getForId(notification.getContainer());
                    int numUpdated = NotificationService.get().markAsRead(c, getUser(), notification.getObjectId(),
                            Collections.singletonList(notification.getType()), notification.getUserId()
                    );

                    totalUpdated += numUpdated;
                }

                transaction.commit();
            }

            ApiSimpleResponse response = new ApiSimpleResponse();
            response.put("numUpdated", totalUpdated);
            response.put("success", true);
            return response;
        }
    }

    @RequiresPermission(ReadPermission.class) @RequiresLogin
    public class DeleteNotificationAction extends MutatingApiAction<RowIdsForm>
    {
        private List<Notification> _notifications = new ArrayList<>();

        @Override
        public void validateForm(RowIdsForm form, Errors errors)
        {
            List<Notification> validNotifications = validateNotificationRowIdsForUser(getUser(), form, errors);
            _notifications.addAll(validNotifications);
        }

        @Override
        public ApiResponse execute(RowIdsForm form, BindException errors)
        {
            int totalDeleted = 0;

            try(DbScope.Transaction transaction = CoreSchema.getInstance().getSchema().getScope().ensureTransaction())
            {
                for (Notification notification : _notifications)
                {
                    Container c = ContainerManager.getForId(notification.getContainer());
                    int numDeleted = NotificationService.get().removeNotifications(c, notification.getObjectId(),
                            Collections.singletonList(notification.getType()), notification.getUserId()
                    );

                    totalDeleted += numDeleted;
                }

                transaction.commit();
            }

            ApiSimpleResponse response = new ApiSimpleResponse();
            response.put("numDeleted", totalDeleted);
            response.put("success", true);
            return response;
        }
    }

    private List<Notification> validateNotificationRowIdsForUser(User user, RowIdsForm form, Errors errors)
    {
        List<Notification> notifications = new ArrayList<>();

        if (form.getRowIds() == null || form.getRowIds().isEmpty())
        {
            errors.reject(ERROR_MSG, "No notification rowIds provided.");
        }
        else
        {
            for (Integer rowId : form.getRowIds())
            {
                Notification notification = NotificationService.get().getNotification(rowId);
                if (notification == null || notification.getUserId() != user.getUserId())
                    errors.reject(ERROR_MSG, "You do not have permissions to update this notification: " + rowId);
                else
                    notifications.add(notification);
            }
        }

        return notifications;
    }

    public static class RowIdsForm
    {
        private List<Integer> _rowIds;

        public List<Integer> getRowIds()
        {
            return _rowIds;
        }

        public void setRowIds(List<Integer> rowIds)
        {
            _rowIds = rowIds;
        }
    }

    @RequiresPermission(ReadPermission.class) @RequiresLogin
    public class UserNotificationsAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            return new JspView<>("/org/labkey/core/notification/userNotifications.jsp", o);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("User Notifications");
        }
    }

    @RequiresPermission(ReadPermission.class) @RequiresLogin
    public class GetUserNotificationsAction extends ReadOnlyApiAction<NotificationsForm>
    {
        @Override
        public ApiResponse execute(NotificationsForm form, BindException errors)
        {
            NotificationService service = NotificationService.get();

            List<Map<String, Object>> notificationList = new ArrayList<>();
            Container notificationContainer = form.isByContainer() ? getContainer() : null;
            ContainerFilter containerFilter = null;
            if (form.isByContainer() && !StringUtils.isEmpty(form.getContainerFilter()))
                containerFilter = ContainerFilter.getContainerFilterByName(form.getContainerFilter(), getContainer(), getUser());

            List<Notification> notifications;
            if (form.getTypeLabels() == null || form.getTypeLabels().isEmpty())
                notifications = service.getNotificationsByUser(notificationContainer, getUser().getUserId(), false, containerFilter);
            else
                notifications = service.getNotificationsByTypeLabels(notificationContainer, form.getTypeLabels(), getUser().getUserId(), false, containerFilter);

            int i = 0;
            int unreadCount = 0;
            for (Notification notification : notifications)
            {
                if (form.getMaxRows() == null || i < form.getMaxRows())
                {
                    Map<String, Object> notifPropMap = notification.asPropMap();
                    notifPropMap.put("CreatedBy", UserManager.getDisplayName((Integer) notifPropMap.get("CreatedBy"), getUser()));
                    notifPropMap.put("TypeLabel", service.getNotificationTypeLabel(notification.getType()));
                    notifPropMap.put("IconCls", service.getNotificationTypeIconCls(notification.getType()));
                    notificationList.add(notifPropMap);
                }
                i++;
                if (notification.getReadOn() == null)
                    unreadCount++;
            }

            ApiSimpleResponse response = new ApiSimpleResponse();
            response.put("notifications", notificationList);
            response.put("totalRows", notifications.size());
            response.put("unreadCount", unreadCount);
            response.put("success", true);
            return response;
        }
    }

    public static class NotificationsForm
    {
        private boolean _byContainer;
        private String _containerFilter;
        private List<String> _typeLabels;
        private Integer _maxRows;

        public boolean isByContainer()
        {
            return _byContainer;
        }

        public void setByContainer(boolean byContainer)
        {
            _byContainer = byContainer;
        }

        public String getContainerFilter()
        {
            return _containerFilter;
        }

        public void setContainerFilter(String containerFilter)
        {
            _containerFilter = containerFilter;
        }

        public List<String> getTypeLabels()
        {
            return _typeLabels;
        }

        public void setTypeLabels(List<String> typeLabels)
        {
            _typeLabels = typeLabels;
        }

        public Integer getMaxRows()
        {
            return _maxRows;
        }

        public void setMaxRows(Integer maxRows)
        {
            _maxRows = maxRows;
        }
    }

    @RequiresPermission(ReadPermission.class) @RequiresLogin
    public class GetUserNotificationsForPanelAction extends ReadOnlyApiAction<Object>
    {
        @Override
        public ApiResponse execute(Object form, BindException errors)
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            response.put("notifications", getNotificationJson(getUser()));
            response.put("success", true);
            return response;
        }

        private JSONObject getNotificationJson(User user)
        {
            Map<Integer, Map<String, Object>> notificationsPropMap = new HashMap<>();
            Map<String, List<Integer>> notificationGroupingsMap = new TreeMap<>();
            int unreadCount = 0;
            boolean hasRead = false;

            NotificationService service = NotificationService.get();
            if (service != null && user != null && !user.isGuest())
            {
                List<Notification> userNotifications = service.getNotificationsByUser(null, user.getUserId(), false, null);
                for (Notification notification : userNotifications)
                {
                    if (notification.getReadOn() != null)
                    {
                        hasRead = true;
                        continue;
                    }

                    Map<String, Object> notifPropMap = notification.asPropMap();
                    notifPropMap.put("CreatedBy", UserManager.getDisplayName((Integer)notifPropMap.get("CreatedBy"), user));
                    notifPropMap.put("IconCls", service.getNotificationTypeIconCls(notification.getType()));
                    notificationsPropMap.put(notification.getRowId(), notifPropMap);

                    String groupLabel = service.getNotificationTypeLabel(notification.getType());
                    if (!notificationGroupingsMap.containsKey(groupLabel))
                    {
                        notificationGroupingsMap.put(groupLabel, new ArrayList<>());
                    }
                    notificationGroupingsMap.get(groupLabel).add(notification.getRowId());

                    unreadCount++;
                }
            }

            JSONObject notifications = new JSONObject(notificationsPropMap);
            notifications.put("grouping", notificationGroupingsMap);
            notifications.put("unreadCount", unreadCount);
            notifications.put("hasRead", hasRead);
            return notifications;
        }
    }
}
