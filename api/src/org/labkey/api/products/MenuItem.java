package org.labkey.api.products;

import org.labkey.api.view.ActionURL;

public class MenuItem
{
    private String _label;
    private Integer _id;
    private String _url;
    private Integer _orderNum = 0;

    public MenuItem(String label, String url, Integer id, Integer orderNum)
    {
        _label = label;
        _id = id;
        _url = url;
        _orderNum = orderNum == null ? -1 : orderNum;
    }


    public MenuItem(String label, ActionURL url, Integer id, Integer orderNum)
    {
        this(label, url == null ? null : url.toString(), id, orderNum);
    }

    public MenuItem(String label, String url, Integer orderNum)
    {
        this(label, url, null, orderNum);
    }

    public MenuItem(String label, ActionURL url, Integer id)
    {
        this(label, url, id, null);
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

    public String getUrl()
    {
        return _url;
    }

    public void setUrl(ActionURL url)
    {
        _url = url.toString();
    }

    public void setUrl(String url)
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
