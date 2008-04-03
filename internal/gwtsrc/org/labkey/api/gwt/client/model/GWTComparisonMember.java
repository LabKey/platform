package org.labkey.api.gwt.client.model;

import java.io.Serializable;

/**
 * User: jeckels
 * Date: Jan 31, 2008
 */
public class GWTComparisonMember implements Serializable
{
    private String _name;
    private String _url;
    private boolean[] _hits;
    private int _count = 0;
    
    public GWTComparisonMember()
    {

    }

    public GWTComparisonMember(String name, boolean[] hits)
    {
        _name = name;
        _hits = hits;
        getCount();
    }

    public int getTotalCount()
    {
        return _hits.length;
    }

    public int getCount()
    {
        if (_count == -1)
        {
            _count = 0;
            for (int i = 0; i < _hits.length; i++)
            {
                if (_hits[i])
                {
                    _count++;
                }
            }
        }
        return _count;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public String getUrl()
    {
        return _url;
    }

    public void setUrl(String url)
    {
        _url = url;
    }

    public boolean contains(int index)
    {
        return _hits[index];
    }
}
