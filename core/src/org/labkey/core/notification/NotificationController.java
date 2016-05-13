package org.labkey.core.notification;

import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.RedirectAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.admin.notification.Notification;
import org.labkey.api.admin.notification.NotificationService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.RequiresLogin;
import org.labkey.api.security.RequiresPermission;
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
import java.util.List;
import java.util.Map;

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
    public static class GotoAction extends RedirectAction<NotificationForm>
    {
        @Override
        public URLHelper getSuccessURL(NotificationForm form)
        {
            return form.getReturnActionURL();
        }

        @Override
        public boolean doAction(NotificationForm form, BindException errors) throws Exception
        {
            NotificationService.get().markAsRead(getUser(), form.getRowid());
            return true;
        }

        @Override
        public void validateCommand(NotificationForm target, Errors errors)
        {
            // pass
        }
    }

    @RequiresPermission(ReadPermission.class) @RequiresLogin
    public class MarkNotificationAsReadAction extends ApiAction<RowIdsForm>
    {
        private List<Notification> _notifications = new ArrayList<>();

        @Override
        public void validateForm(RowIdsForm form, Errors errors)
        {
            if (form.getRowIds() == null || form.getRowIds().isEmpty())
            {
                errors.reject(ERROR_MSG, "No notification rowIds provided.");
            }
            else
            {
                for (Integer rowId : form.getRowIds())
                {
                    Notification notification = NotificationService.get().getNotification(rowId);
                    if (notification == null || notification.getUserId() != getUser().getUserId())
                        errors.reject(ERROR_MSG, "You do not have permissions to update this notification: " + rowId);
                    else
                        _notifications.add(notification);
                }
            }
        }

        @Override
        public ApiResponse execute(RowIdsForm form, BindException errors) throws Exception
        {
            int totalUpdated = 0;

            for (Notification notification : _notifications)
            {
                Container c = ContainerManager.getForId(notification.getContainer());
                int numUpdated = NotificationService.get().markAsRead(c, getUser(), notification.getObjectId(),
                        Collections.singletonList(notification.getType()), notification.getUserId()
                );

                totalUpdated += numUpdated;
            }

            ApiSimpleResponse response = new ApiSimpleResponse();
            response.put("numUpdated", totalUpdated);
            response.put("success", true);
            return response;
        }
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
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new JspView<>("/org/labkey/core/notification/userNotifications.jsp");
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("User Notifications");
        }
    }

    @RequiresPermission(ReadPermission.class) @RequiresLogin
    public class GetUserNotificationsAction extends ApiAction<Object>
    {
        @Override
        public ApiResponse execute(Object form, BindException errors) throws Exception
        {
            NotificationService.Service service = NotificationService.get();

            List<Map<String, Object>> notificationList = new ArrayList<>();
            for (Notification notification : service.getNotificationsByUser(null, getUser().getUserId(), false))
            {
                Map<String, Object> notifPropMap = notification.asPropMap();
                notifPropMap.put("CreatedBy", UserManager.getDisplayName((Integer)notifPropMap.get("CreatedBy"), getUser()));
                notifPropMap.put("TypeLabel", service.getNotificationTypeLabel(notification.getType()));
                notifPropMap.put("IconCls", service.getNotificationTypeIconCls(notification.getType()));
                notificationList.add(notifPropMap);
            }

            ApiSimpleResponse response = new ApiSimpleResponse();
            response.put("notifications", notificationList);
            response.put("success", true);
            return response;
        }
    }
}
