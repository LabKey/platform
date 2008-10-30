/*
 * Copyright (c) 2008 LabKey Corporation
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

package org.labkey.authentication;

import org.apache.log4j.Logger;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.security.AuthenticationManager.*;
import org.labkey.api.view.ViewContext;
import org.labkey.authentication.opensso.OpenSSOController;
import org.labkey.authentication.opensso.OpenSSOProvider;
import org.labkey.authentication.ldap.LdapAuthenticationProvider;
import org.labkey.authentication.ldap.LdapController;

public class AuthenticationModule extends DefaultModule
{
    public static final String NAME = "Authentication";
    private static Logger _log = Logger.getLogger(AuthenticationModule.class);

    public AuthenticationModule()
    {
        super(NAME, 8.20, "org/labkey/authentication", false);
    }

    protected void init()
    {
        addController("opensso", OpenSSOController.class);
        addController("ldap", LdapController.class);
        AuthenticationManager.registerProvider(new OpenSSOProvider(), Priority.High);
        AuthenticationManager.registerProvider(new LdapAuthenticationProvider(), Priority.High);
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
            catch (Exception e)
            {
                // TODO: log to mothership
                _log.error(e);
            }
        }

        super.afterSchemaUpdate(moduleContext, viewContext);
    }
}