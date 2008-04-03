package org.labkey.api.gwt.client.model;

import com.google.gwt.user.client.rpc.IsSerializable;

/**
 * User: jeckels
 * Date: Jun 8, 2007
 */
public class GWTComparisonResult implements IsSerializable
{
    private GWTComparisonMember[] _members;
    private GWTComparisonGroup[] _groups;
    private int _totalCount;
    private String _memberDescription;

    public GWTComparisonResult()
    {
    }

    public GWTComparisonResult(GWTComparisonMember[] members, GWTComparisonGroup[] groups, int totalCount, String memberDescription)
    {
        _members = members;
        _groups = groups;
        _totalCount = totalCount;
        _memberDescription = memberDescription;
    }

    public GWTComparisonMember[] getMembers()
    {
        return _members;
    }

    public GWTComparisonGroup[] getGroups()
    {
        return _groups;
    }

    public int getTotalCount()
    {
        return _totalCount;
    }

    public String getMemberDescription()
    {
        return _memberDescription;
    }
}
