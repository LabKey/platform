/*
 * Copyright (c) 2024-2026 LabKey Corporation
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
package org.labkey.wiki;

import org.apache.logging.log4j.Logger;
import org.labkey.api.admin.sitevalidation.SiteValidationProvider;
import org.labkey.api.admin.sitevalidation.SiteValidationProviderFactory;
import org.labkey.api.admin.sitevalidation.SiteValidationResultList;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.util.CspUtils;
import org.labkey.api.util.JSoupUtil;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.wiki.FormattedHtml;
import org.labkey.api.wiki.WikiRenderingService.SubstitutionMode;
import org.labkey.wiki.model.Wiki;
import org.labkey.wiki.model.WikiTree;
import org.w3c.dom.Document;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

public class WikiValidationProviderFactory implements SiteValidationProviderFactory
{
    private static final Logger LOG = LogHelper.getLogger(WikiValidationProviderFactory.class, "Wiki rendering exceptions");

    @Override
    public String getName()
    {
        return "Wiki Validator";
    }

    @Override
    public String getDescription()
    {
        return "Report rendering issues and in-line JavaScript in wikis";
    }

    @Override
    public SiteValidationProvider getSiteValidationProvider()
    {
        return new SiteValidationProvider()
        {
            @Override
            public SiteValidationProviderFactory getFactory()
            {
                return WikiValidationProviderFactory.this;
            }

            @Override
            public SiteValidationResultList runValidation(Container c, User u)
            {
                SiteValidationResultList list = new SiteValidationResultList();
                WikiManager mgr = WikiManager.get();
                Set<WikiTree> trees = WikiSelectManager.getWikiTrees(c);
                Map<String, String> nameTitleMap = WikiSelectManager.getNameTitleMap(c);

                for (WikiTree tree : trees)
                {
                    Wiki wiki = WikiSelectManager.getWiki(c, tree.getRowId());
                    if (null != wiki)
                    {
                        String title = nameTitleMap.get(wiki.getName());
                        try
                        {
                            FormattedHtml html = mgr.formatWiki(c, wiki, wiki.getLatestVersion(), SubstitutionMode.Remove);
                            Collection<String> errors = new LinkedList<>();
                            Document doc = JSoupUtil.convertHtmlToDocument(html.getHtml().toString(), false, errors);
                            errors.forEach(error -> addResult(list, wiki, title, "error while converting HTML to Document, \"" + error + "\""));
                            if (null != doc)
                            {
                                CspUtils.enumerateScriptViolations(doc, message -> addResult(list, wiki, title, message));
                            }
                        }
                        catch (Exception e)
                        {
                            addResult(list, wiki, title, "exception while rendering, \"" + e.getMessage() + "\"");
                            LOG.error("Exception while rendering \"{}\" ({})", title, wiki.getName(), e);
                        }
                    }
                }

                return list;
            }

            private void addResult(SiteValidationResultList list, Wiki wiki, String title, String message)
            {
                list.addWarn(title + " (" + wiki.getName() + "): " + message, wiki.getPageURL());
            }
        };
    }
}