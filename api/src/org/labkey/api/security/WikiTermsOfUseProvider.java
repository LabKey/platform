/*
 * Copyright (c) 2016 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.Project;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.SecurityManager.TermsOfUseProvider;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.SessionHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.ViewContext;
import org.labkey.api.wiki.WikiService;

import javax.servlet.http.HttpSession;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by adam on 1/13/2016.
 */
public class WikiTermsOfUseProvider implements TermsOfUseProvider
{
    public static final String TERMS_OF_USE_WIKI_NAME = "_termsOfUse";
    public static final String TERMS_APPROVED_KEY = "TERMS_APPROVED_KEY";

    private static TermsOfUse NO_TERMS = new TermsOfUse(TermsOfUseType.NONE, null);

    @Override
    public void verifyTermsOfUse(ViewContext context, boolean isBasicAuth) throws RedirectException
    {
        // Skip terms of use on basic authentication requests
        if (!isBasicAuth && isTermsOfUseRequired(context))
        {
            ActionURL termsURL = PageFlowUtil.urlProvider(LoginUrls.class).getAgreeToTermsURL(context.getContainer(), context.getActionURL());
            throw new RedirectException(termsURL);
        }
    }

    private static boolean isTermsOfUseRequired(ViewContext ctx)
    {
        Container proj = ctx.getContainer().getProject();

        Project project = null;
        if (null != proj)
        {
            project = new Project(proj);
        }

        // not required if user has already approved at the project level
        if (isTermsOfUseApproved(ctx, project))
            return false;

        TermsOfUse termsOfUse = getTermsOfUse(project);
        boolean required;
        switch (termsOfUse.getType())
        {
            case SITE_WIDE:
                // if we don't require project-level and have approved site-wide level, not required to ask again,
                // but we don't cache for the project in case we set project-level terms later
                required = !isTermsOfUseApproved(ctx, null);
                break;
            case PROJECT_LEVEL: // we already checked if the project-level terms were approved, so we know that they are required here
                required = true;
                break;
            default:
                required = false;
        }

        return required;
    }

    public static boolean isTermsOfUseApproved(ViewContext ctx, @Nullable Project project)
    {
        HttpSession session = ctx.getRequest().getSession(false);
        if (null == session)
            return false;
        @Nullable Set<Project> termsApproved = getApprovedTerms(session);
        return null != termsApproved && termsApproved.contains(project);
    }

    public static @Nullable Set<Project> getApprovedTerms(@NotNull HttpSession session)
    {
        synchronized (SessionHelper.getSessionLock(session))
        {
            return (Set<Project>) session.getAttribute(TERMS_APPROVED_KEY);
        }
    }

    public static boolean isTermsOfUseRequired(@Nullable Project project)
    {
        //TODO: Should do this more efficiently, but no efficient public wiki api for this yet
        TermsOfUse terms = getTermsOfUse(project);
        return terms.getType() != TermsOfUseType.NONE;
    }

    @NotNull
    public static TermsOfUse getTermsOfUse(@Nullable Project project)
    {
        if (!ModuleLoader.getInstance().isStartupComplete())
            return NO_TERMS;

        WikiService service = ServiceRegistry.get().getService(WikiService.class);
        //No wiki service. Must be in weird state. Don't do terms here...
        if (null == service)
            return NO_TERMS;

        String termsString;
        if (null != project) // find project-level terms of use, if any
        {
            termsString = service.getHtml(project.getContainer(), TERMS_OF_USE_WIKI_NAME);
            if (null != termsString)
            {
                return new TermsOfUse(TermsOfUseType.PROJECT_LEVEL, termsString);
            }
        }

        // now check if we have site-wide terms of use
        termsString = service.getHtml(ContainerManager.getRoot(), TERMS_OF_USE_WIKI_NAME);
        if (null != termsString)
        {
            return new TermsOfUse(TermsOfUseType.SITE_WIDE, termsString);
        }
        return NO_TERMS;
    }

    public static void setTermsOfUseApproved(ViewContext ctx, @Nullable Project project, boolean approved)
    {
        HttpSession session = ctx.getRequest().getSession(false);
        if (null == session && !approved)
            return;
        session = ctx.getRequest().getSession(true);

        synchronized (SessionHelper.getSessionLock(session))
        {
            Set<Project> termsApproved = (Set<Project>) session.getAttribute(TERMS_APPROVED_KEY);
            if (null == termsApproved)
            {
                termsApproved = new HashSet<>();
                session.setAttribute(TERMS_APPROVED_KEY, termsApproved);
            }
            if (approved)
            {
                termsApproved.add(project);
            }
            else
            {
                termsApproved.remove(project);
            }

        }
    }

    public enum TermsOfUseType { NONE, PROJECT_LEVEL, SITE_WIDE }

    public static class TermsOfUse
    {
        private final TermsOfUseType _type;
        private final String _html;

        public TermsOfUse(@NotNull TermsOfUseType type, @Nullable String html)
        {
            _type = type;
            _html = html;
        }

        public String getHtml() { return _html; }

        public TermsOfUseType getType() { return _type; }
    }
}
