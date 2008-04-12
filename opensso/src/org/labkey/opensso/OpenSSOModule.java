package org.labkey.opensso;

import org.apache.log4j.Logger;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.view.ViewContext;

public class OpenSSOModule extends DefaultModule
{
    public static final String NAME = "OpenSSO";
    private static Logger _log = Logger.getLogger(OpenSSOModule.class);

    public OpenSSOModule()
    {
        super(NAME, 8.10, null, false);
        addController("opensso", OpenSSOController.class);
        AuthenticationManager.registerProvider(new OpenSSOProvider());
    }

    public TabDisplayMode getTabDisplayMode()
    {
        return TabDisplayMode.DISPLAY_NEVER;
    }

    public void afterSchemaUpdate(ModuleContext moduleContext, ViewContext viewContext)
    {
        double installedVersion = moduleContext.getInstalledVersion();

        if (installedVersion > 0 && installedVersion < 2.31)
        {
            AttachmentParent parent = ContainerManager.RootContainer.get();

            try
            {
                Attachment oldLogo = AttachmentService.get().getAttachment(parent, "auth_OpenSSO");

                if (null != oldLogo)
                {
                    AttachmentService.get().copyAttachment(viewContext.getUser(), parent, oldLogo, AuthenticationManager.HEADER_LOGO_PREFIX + "OpenSSO");
                    AttachmentService.get().renameAttachment(parent, oldLogo.getName(), AuthenticationManager.LOGIN_PAGE_LOGO_PREFIX + "OpenSSO");
                }
            }
            catch(Exception e)
            {
                // TODO: log to mothership
                _log.error(e);
            }
        }

        super.afterSchemaUpdate(moduleContext, viewContext);
    }
}