/*
 * Copyright (c) 2015 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
package org.labkey.authentication.cas;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.security.AuthenticationManager.LinkFactory;
import org.labkey.api.security.AuthenticationProvider.SSOAuthenticationProvider;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;

import javax.servlet.http.HttpServletRequest;

/**
 * Created by adam on 3/29/2015.
 */
public class CasAuthenticationProvider implements SSOAuthenticationProvider
{
    private static final CasAuthenticationProvider INSTANCE = new CasAuthenticationProvider();
    static final String NAME = "CAS";

    private final LinkFactory _linkFactory = new LinkFactory(this);

    private CasAuthenticationProvider()
    {
    }

    public static CasAuthenticationProvider getInstance()
    {
        return INSTANCE;
    }

    @Override
    public URLHelper getURL(String secret)
    {
        return CasManager.getInstance().getLoginURL();
    }

    @Override
    public LinkFactory getLinkFactory()
    {
        return _linkFactory;
    }

    @Nullable
    @Override
    public ActionURL getConfigurationLink()
    {
        return CasController.getConfigureURL();
    }

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public String getDescription()
    {
        return "Apereo Central Authentication Service (CAS)";
    }

    @Override
    public void logout(HttpServletRequest request)
    {
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
