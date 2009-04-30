/*
 * Copyright (c) 2008-2009 LabKey Corporation
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
package org.labkey.api.security;

import org.labkey.api.action.UrlProvider;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ActionURL;
import org.labkey.api.data.Container;
import org.labkey.api.util.Pair;

import javax.servlet.http.HttpServletRequest;

/**
 * User: adam
 * Date: May 15, 2008
 * Time: 9:40:56 AM
 */
public interface LoginUrls extends UrlProvider
{
    public ActionURL getConfigureURL();
    public ActionURL getVerificationURL(Container c, String email, String verification, Pair<String, String>[] extraParameters);
    public NavTree appendAuthenticationNavTrail(NavTree root);
    public ActionURL getInitialUserURL();
    public ActionURL getLoginURL();
    public ActionURL getLoginURL(ActionURL returnURL);
    public ActionURL getLoginURL(Container c, String returnURLString);
    public ActionURL getLogoutURL(Container c);
    public ActionURL getStopImpersonatingURL(Container c, HttpServletRequest request);
    public ActionURL getLogoutURL(Container c, String returnURLString);
    public ActionURL getAgreeToTermsURL(Container c, ActionURL returnURL);
    public ActionURL getAgreeToTermsURL(Container c, String returnURLString);
}
