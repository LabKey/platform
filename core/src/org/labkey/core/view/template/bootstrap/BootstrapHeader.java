package org.labkey.core.view.template.bootstrap;

import org.labkey.api.notification.NotificationMenuView;
import org.labkey.api.settings.AppProps;
import org.labkey.api.view.JspView;
import org.labkey.api.view.template.PageConfig;


/**
 * Created by Nick Arnold on 3/7/2017.
 */
public class BootstrapHeader extends JspView<PageConfig>
{
    public BootstrapHeader(PageConfig page)
    {
        super("/org/labkey/core/view/template/bootstrap/header.jsp", page);
        setFrame(FrameType.NONE);
        displayNotifications();
    }

    private void displayNotifications()
    {
        if (AppProps.getInstance().isExperimentalFeatureEnabled(NotificationMenuView.EXPERIMENTAL_NOTIFICATION_MENU))
        {
            this.setView("notifications", NotificationMenuView.createView(getViewContext()));
        }
    }
}
