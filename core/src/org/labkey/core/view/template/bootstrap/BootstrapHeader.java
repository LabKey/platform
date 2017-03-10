package org.labkey.core.view.template.bootstrap;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.view.JspView;
import org.labkey.api.view.template.PageConfig;

import java.util.Map;

/**
 * Created by Nick Arnold on 3/7/2017.
 */
public class BootstrapHeader extends JspView<BootstrapHeader.BootstrapHeaderBean>
{
    public BootstrapHeader(@Nullable String upgradeMessage, @Nullable Map<String, Throwable> moduleErrors, PageConfig page)
    {
        super("/org/labkey/core/view/template/bootstrap/header.jsp", new BootstrapHeader.BootstrapHeaderBean(upgradeMessage, moduleErrors, page));
        setFrame(FrameType.NONE);
        buildWarningMessageList();
    }

    private void buildWarningMessageList()
    {
        // TODO: ...
    }

    public static class BootstrapHeaderBean
    {
        public @Nullable String upgradeMessage;
        public @Nullable Map<String, Throwable> moduleFailures;
        public PageConfig pageConfig;

        private BootstrapHeaderBean(@Nullable String upgradeMessage, @Nullable Map<String, Throwable> moduleFailures, PageConfig page)
        {
            this.upgradeMessage = upgradeMessage;
            this.moduleFailures = moduleFailures;
            this.pageConfig = page;
        }
    }
}
