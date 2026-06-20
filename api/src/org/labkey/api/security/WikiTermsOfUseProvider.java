/*
 * Copyright (c) 2016-2026 LabKey Corporation
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

import jakarta.servlet.http.HttpSession;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.Project;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.PropertyManager.WritablePropertyMap;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.SecurityManager.TermsOfUseProvider;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.SafeToRenderEnum;
import org.labkey.api.util.SessionHelper;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.ViewContext;
import org.labkey.api.wiki.WikiService;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;

public class WikiTermsOfUseProvider implements TermsOfUseProvider
{
    public static final String TERMS_OF_USE_WIKI_NAME = "_termsOfUse";
    public static final String TERMS_APPROVED_KEY = "TERMS_APPROVED_KEY";

    private static final Logger LOG = LogHelper.getLogger(WikiTermsOfUseProvider.class, "Terms of use workflow");
    private static final TermsOfUse NO_TERMS = new TermsOfUse(TermsOfUseType.NONE, null);

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

    // Are terms required in this container and the user hasn't approved them yet
    public static boolean isTermsOfUseRequired(ViewContext ctx)
    {
        Container proj = ctx.getContainer().getProject();

        Project project = null;
        if (null != proj)
        {
            project = new Project(proj);
        }

        return isTermsOfUseRequired(ctx, project);
    }

    // Are terms required in this container and the user hasn't approved them yet
    public static boolean isTermsOfUseRequired(ViewContext ctx, @Nullable Project project)
    {
        // First, quick check for whether terms are ever needed for this project
        TermsOfUseConfiguration config = getTermsOfUseConfiguration(project);
        if (config.type() == TermsOfUseType.NONE)
            return false;

        // Terms are needed, so check if user has already approved
        //noinspection DataFlowIssue - termsContainer() is null only in the NONE case (see above)
        return !isTermsOfUseApproved(ctx, config.termsContainer());
    }

    private static final String LAST_TERMS_ACCEPTANCE = "lastTermsAcceptance";
    private static final String DATE = "date";

    // termsContainer is guaranteed to have a terms-of-use wiki
    public static boolean isTermsOfUseApproved(ViewContext ctx, @NotNull Container termsContainer)
    {
        HttpSession session = ctx.getRequest().getSession(false);
        if (null == session)
            return false;
        boolean approved;
        synchronized (SessionHelper.getSessionLock(session))
        {
            @NotNull Set<Container> termsApproved = getApprovedTerms(session);
            approved = termsApproved.contains(termsContainer);
        }
        if (!approved)
            LOG.debug("Approved terms did not include {} for {}", termsContainer, ctx.getUser());
        if (!approved)
        {
            User user = ctx.getUser();
            if (!user.isGuest())
            {
                int frequencySeconds = AppProps.getInstance().getTermsOfUseFrequencySeconds();
                if (frequencySeconds > 0)
                {
                    String isoDateString = PropertyManager.getProperties(user, termsContainer, LAST_TERMS_ACCEPTANCE).get(DATE);
                    if (isoDateString != null)
                    {
                        Instant lastAccepted = Instant.parse(isoDateString);
                        approved = Instant.now().isBefore(lastAccepted.plusSeconds(frequencySeconds));
                        // On first terms check at the site or each project, if last acceptance hasn't expired, stash
                        // an "approval" into session. This short-circuits future requests (so we don't run through
                        // this code block on every request). It also ensures the terms dialogs don't randomly pop up
                        // later in the session (when acceptance expires).
                        if (approved)
                        {
                            try (var ignored = SpringActionController.ignoreSqlUpdates())
                            {
                                setTermsOfUseApprovedInSession(ctx, termsContainer);
                            }
                        }
                    }
                }
            }
        }
        return approved;
    }

    public static @NotNull Container getTermsContainer(@Nullable Project project)
    {
        return project != null ? project.getContainer() : ContainerManager.getRoot();
    }

    public static @NotNull Set<Container> getApprovedTerms(@NotNull HttpSession session)
    {
        return SessionHelper.getAttribute(session, TERMS_APPROVED_KEY, (Callable<Set<Container>>) HashSet::new);
    }

    public static boolean isTermsOfUseConfigured(@Nullable Project project)
    {
        // This should be fairly efficient. We could consider caching a project -> terms configuration map.
        return getTermsOfUseConfiguration(project).type() != TermsOfUseType.NONE;
    }

    public record TermsOfUseConfiguration(TermsOfUseType type, /* Null only if type is NONE */ @Nullable Container termsContainer){}
    public static final TermsOfUseConfiguration NO_TERMS_CONFIGURATION = new TermsOfUseConfiguration(TermsOfUseType.NONE, null);

    public static TermsOfUseConfiguration getTermsOfUseConfiguration(@Nullable Project project)
    {
        if (ModuleLoader.getInstance().isStartupComplete())
        {
            WikiService service = WikiService.get();

            // Need the wiki service to do terms
            if (null != service)
            {
                // Project terms override root terms
                if (project != null)
                {
                    Container c = project.getContainer();
                    if (service.hasTermsOfUseWiki(c))
                        return new TermsOfUseConfiguration(TermsOfUseType.PROJECT_LEVEL, c);
                }

                // Now check the root
                Container c = ContainerManager.getRoot();
                if (service.hasTermsOfUseWiki(c))
                    return new TermsOfUseConfiguration(TermsOfUseType.SITE_WIDE, c);
            }
        }

        return NO_TERMS_CONFIGURATION;
    }

    @NotNull
    public static TermsOfUse getTermsOfUse(@Nullable Project project)
    {
        TermsOfUseConfiguration config = getTermsOfUseConfiguration(project);

        if (config.type() != TermsOfUseType.NONE)
        {
            // Check above guarantees that wiki service is present and getContainer is non-null
            @SuppressWarnings("DataFlowIssue")
            HtmlString termsString = WikiService.get().getHtml(config.termsContainer(), TERMS_OF_USE_WIKI_NAME);
            if (null != termsString)
            {
                return new TermsOfUse(config.type(), termsString);
            }
        }
        return NO_TERMS;
    }

    public static void setTermsOfUseApproved(ViewContext ctx, @NotNull Container termsContainer)
    {
        setTermsOfUseApprovedInSession(ctx, termsContainer);
        User user = ctx.getUser();
        if (!user.isGuest() && AppProps.getInstance().getTermsOfUseFrequencySeconds() > 0)
        {
            WritablePropertyMap map = PropertyManager.getWritableProperties(ctx.getUser(), termsContainer, LAST_TERMS_ACCEPTANCE, true);
            map.put(DATE, Instant.now().toString());
            map.save();
            LOG.debug("Saving terms acceptance timestamp for {} in {}", ctx.getUser(), termsContainer);
        }
    }

    private static void setTermsOfUseApprovedInSession(ViewContext ctx, @NotNull Container termsContainer)
    {
        HttpSession session = ctx.getRequest().getSession(true);
        synchronized (SessionHelper.getSessionLock(session))
        {
            Set<Container> termsApproved = getApprovedTerms(session);
            termsApproved.add(termsContainer);
        }
        LOG.debug("Stashing terms acceptance in session for {} in {}", ctx.getUser(), termsContainer);
    }

    public enum TermsOfUseType implements SafeToRenderEnum
    { NONE, PROJECT_LEVEL, SITE_WIDE }

    public static class TermsOfUse
    {
        private final TermsOfUseType _type;
        private final HtmlString _html;

        public TermsOfUse(@NotNull TermsOfUseType type, @Nullable HtmlString html)
        {
            _type = type;
            _html = html;
        }

        public HtmlString getHtml() { return _html; }

        public TermsOfUseType getType() { return _type; }
    }
}
