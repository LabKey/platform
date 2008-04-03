package org.labkey.api.gwt.client.model;

import java.io.Serializable;
import java.util.List;
import java.util.ArrayList;

/**
 * User: jeckels
 * Date: Jan 31, 2008
 */
public class GWTComparisonGroup implements Serializable
{
    /** @gwt.typeArgs <org.labkey.api.gwt.client.model.GWTComparisonMember> */
    private List _members = new ArrayList();
    private String _name;
    private String _url;
    private int _count = 0;

    public List getMembers()
    {
        return _members;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public String getURL()
    {
        return _url;
    }

    public void setURL(String url)
    {
        _url = url;
    }

    public void addMember(GWTComparisonMember member)
    {
        _members.add(member);
        _count = -1;
        getCount();
    }

    public int getCount()
    {
        if (_count == -1)
        {
            _count = 0;
            int totalCount = ((GWTComparisonMember)_members.get(0)).getTotalCount();
            for (int i = 0; i < totalCount; i++)
            {
                if (contains(i))
                {
                    _count++;
                }
            }
        }
        return _count;
    }

    public boolean contains(int index)
    {
        for (int i = 0; i < _members.size(); i++)
        {
            if (((GWTComparisonMember)_members.get(i)).contains(index))
            {
                return true;
            }
        }
        return false;
    }
}
