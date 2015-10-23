/*
 * Copyright (c) 2015 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
package org.labkey.duo;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.security.AuthenticationProvider.SecondaryAuthenticationProvider;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;

import javax.servlet.http.HttpServletRequest;

/**
 * User: tgaluhn
 * Date: 3/6/2015
 */
public class DuoProvider implements SecondaryAuthenticationProvider
{
    private static final Logger LOG = Logger.getLogger(DuoProvider.class);
    static final String NAME = "Duo 2 Factor";

    @Override
    public ActionURL getRedirectURL(User candidate, Container c)
    {
        ActionURL validateURL = DuoController.getValidateURL(c);
        validateURL.addParameter("sig_request", DuoManager.generateSignedRequest(candidate));
        return validateURL;
    }

    @Override
    public boolean bypass()
    {
        return !DuoManager.isConfigured() || DuoManager.bypassDuoAuthentication();
    }

    @Nullable
    @Override
    public ActionURL getConfigurationLink()
    {
        return DuoController.getConfigureURL();
    }

    @Override
    public String getName()
    {
        return (NAME);
    }

    @Override
    public String getDescription()
    {
        return "Require two-factor authentication via Duo";
    }

    @Override
    public void logout(HttpServletRequest request)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void activate()
    {
    }

    @Override
    public void deactivate()
    {
    }

    @Override
    public boolean isPermanent()
    {
        return false;
    }
}
