package org.labkey.api.study;

import org.labkey.api.data.Container;
import org.labkey.api.view.ActionURL;

public abstract class StudyManagementOption
{
    private String _title;
    private String _linkText;
    private ActionURL _linkUrl;
    private Container _container;

    public StudyManagementOption(String title, String linkText, ActionURL linkUrl)
    {
        this._title = title;
        this._linkText = linkText;
        this._linkUrl = linkUrl;
    }

    public String getDescription()
    {
        return "Manage " + getTitle() + " for this study.";
    }

    public String getTitle()
    {
        return _title;
    }

    public String getLinkText()
    {
        return _linkText;
    }

    public ActionURL getLinkUrl()
    {
        return _linkUrl;
    }

    public Container getContainer()
    {
        return _container;
    }

    public void setContainer(Container container)
    {
        _container = container;
    }
}
