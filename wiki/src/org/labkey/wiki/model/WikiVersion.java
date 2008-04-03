package org.labkey.wiki.model;

import org.labkey.api.attachments.Attachment;
import org.labkey.api.data.Container;
import org.labkey.api.util.MemTracker;
import org.labkey.api.wiki.FormattedHtml;
import org.labkey.api.wiki.WikiRenderer;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.wiki.ServiceImpl;
import org.labkey.wiki.WikiManager;

import java.sql.SQLException;
import java.util.Date;
import java.util.Map;


/**
 * User: Tamra Myers
 * Date: Feb 14, 2006
 * Time: 1:31:25 PM
 */
public class WikiVersion implements WikiRenderer.WikiLinkable
{
    private int _rowId;
    private String _pageEntityId;
    private int _version;
    private String _title;
    private String _body;
    private int _createdBy;
    private Date _created;
    private WikiRendererType _rendererType;

    private String _html = null;
    private String _wikiName;

    public WikiVersion()
    {
        assert MemTracker.put(this);
    }

    public WikiVersion(String wikiname)
    {
        _wikiName = wikiname;
        assert MemTracker.put(this);
    }

    public String getName()
    {
        return _wikiName;
    }

    public void setName(String wikiname)
    {
        _wikiName = wikiname;
    }

    public int getCreatedBy()
    {
        return _createdBy;
    }

    public void setCreatedBy(int createdBy)
    {
        this._createdBy = createdBy;
    }

    public Date getCreated()
    {
        return _created;
    }

    public void setCreated(Date created)
    {
        this._created = created;
    }

    public String getPageEntityId()
    {
        return _pageEntityId;
    }

    public void setPageEntityId(String pageEntityId)
    {
        _pageEntityId = pageEntityId;
    }

    // TODO: WikiVersion should know its wiki & container
    public String getHtml(Container c, Wiki wiki) throws SQLException
    {
        if (null != _html)
            return _html;

        FormattedHtml formattedHtml = WikiManager.formatWiki(c, wiki, this);

        if (!formattedHtml.isVolatile())
            _html = formattedHtml.getHtml();

        return formattedHtml.getHtml();
    }

    public String getTitle()
    {
        if (null == _title)
            _title = (null == _wikiName || "default".equals(_wikiName)) ? "Wiki" : _wikiName;

        return _title;
    }

    public void setTitle(String title)
    {
        _title = title;
    }

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public int getVersion()
    {
        return _version;
    }

    public void setVersion(int version)
    {
        _version = version;
    }

    public String getBody()
    {
        return _body;
    }

    public void setBody(String body)
    {
        _body = body;
    }

    //returns string corresponding to name of enum entry
    public String getRendererType()
    {
        if (_rendererType == null)
            _rendererType = ServiceImpl.get().getDefaultWikiRendererType();

        return _rendererType.name();
    }

    public void setRendererType(String rendererType)
    {
        _rendererType = WikiRendererType.valueOf(rendererType);
    }

    //we're not currently calling this one....
//    public WikiRenderer getRenderer()
//    {
//        return getRenderer(null, null, null, null);
//    }

    public WikiRenderer getRenderer(String hrefPrefix,
                                    String attachPrefix,
                                    Map<String, WikiRenderer.WikiLinkable> pages,
                                    Attachment[] attachments)
    {
        if (_rendererType == null)
            _rendererType = ServiceImpl.get().getDefaultWikiRendererType();

        return ServiceImpl.get().getRenderer(_rendererType, hrefPrefix, attachPrefix, pages, attachments);
    }
}
