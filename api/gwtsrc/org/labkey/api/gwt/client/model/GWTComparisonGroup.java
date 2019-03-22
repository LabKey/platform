/*
 * Copyright (c) 2008 LabKey Corporation
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
    private List<GWTComparisonMember> _members = new ArrayList<GWTComparisonMember>();
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
            int totalCount = _members.get(0).getTotalCount();
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
        for (GWTComparisonMember _member : _members)
        {
            if (_member.contains(index))
            {
                return true;
            }
        }
        return false;
    }
}
