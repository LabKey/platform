package org.labkey.wiki.model;

/**
 * User: adam
 * Date: Aug 11, 2007
 * Time: 3:30:42 PM
 */
public class WikiView extends BaseWikiView
{
    public WikiView(Wiki wiki, WikiVersion wikiversion, boolean hasContent)
    {
        super();
        _wiki = wiki;
        _wikiVersion = wikiversion;
        addObject("hasContent", hasContent);

        init(getViewContext().getContainer(), wiki.getName(), false);
    }
}
