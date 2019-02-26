package org.labkey.api.products;

import org.labkey.api.view.ActionURL;

public class MenuItem
{
    private String _label;
    private Integer _id;
    private ActionURL _url;
    private Integer _orderNum;

    public MenuItem(String label, Integer id, ActionURL url, Integer orderNum)
    {
        _label = label;
        _id = id;
        _url = url;
        _orderNum = orderNum;
    }

    public MenuItem(String label, Integer id, ActionURL url)
    {
        this(label, id, url, null);
    }

    public MenuItem(String label, ActionURL url)
    {
        this(label, null, url, null);
    }

    public String getLabel()
    {
        return _label;
    }

    public void setLabel(String label)
    {
        _label = label;
    }

    public Integer getId()
    {
        return _id;
    }

    public void setId(Integer id)
    {
        _id = id;
    }

    public ActionURL getUrl()
    {
        return _url;
    }

    public void setUrl(ActionURL url)
    {
        _url = url;
    }

    public Integer getOrderNum()
    {
        return _orderNum;
    }

    public void setOrderNum(Integer orderNum)
    {
        _orderNum = orderNum;
    }
}
