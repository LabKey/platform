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
        return "Detect wiki rendering and CSP violation issues";
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
                            FormattedHtml html = mgr.formatWiki(c, wiki, wiki.getLatestVersion());
                            Collection<String> errors = new LinkedList<>();
                            Document doc = JSoupUtil.convertHtmlToDocument(html.getHtml().toString(), false, errors);
                            errors.forEach(error -> addResult(list, wiki, title, "error while converting HTML to Document, \"" + error + "\""));
                            if (null != doc)
                            {
                                CspUtils.enumerateCspViolations(doc, message -> addResult(list, wiki, title, message));
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