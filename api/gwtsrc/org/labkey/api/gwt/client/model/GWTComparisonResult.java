/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
