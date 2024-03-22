package org.labkey.wiki;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.sitevalidation.SiteValidationProvider;
import org.labkey.api.admin.sitevalidation.SiteValidationResultList;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.util.CspUtils;
import org.labkey.api.util.JSoupUtil;
import org.labkey.api.wiki.FormattedHtml;
import org.labkey.wiki.model.Wiki;
import org.labkey.wiki.model.WikiTree;
import org.w3c.dom.Document;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Set;

public class WikiCspValidationProvider implements SiteValidationProvider
{
    @Override
    public String getName()
    {
        return "Wiki Validator";
    }

    @Override
    public String getDescription()
    {
        return "Detects various strict CSP violations in wiki HTML";
    }

    @Override
    public boolean isSiteScope()
    {
        return false;
    }

    @Nullable
    @Override
    public SiteValidationResultList runValidation(Container c, User u)
    {
        WikiManager mgr = WikiManager.get();
        Set<WikiTree> trees = WikiSelectManager.getWikiTrees(c);
        Collection<String> violations = new LinkedList<>();

        for (WikiTree tree : trees)
        {
            Wiki wiki = WikiSelectManager.getWiki(c, tree.getRowId());
            if (null != wiki)
            {
                FormattedHtml html = mgr.formatWiki(c, wiki, wiki.getLatestVersion());
                Collection<String> errors = new LinkedList<>();
                Document doc = JSoupUtil.convertHtmlToDocument(html.getHtml().toString(), false, errors);
                if (null != doc)
                {
                    CspUtils.collectCspViolations(doc, wiki.getName(), violations);
                }
            }
        }

        SiteValidationResultList list = new SiteValidationResultList();
        violations.forEach(list::addWarn);

        return list;
    }
}