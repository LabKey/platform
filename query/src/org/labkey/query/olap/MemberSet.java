/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.query.olap;

import org.labkey.api.collections.SparseBitSet;
import org.olap4j.metadata.Member;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
* Created with IntelliJ IDEA.
* User: matthew
* Date: 11/5/13
* Time: 10:36 AM
*/
public class MemberSet
{
    final Map<Member,Integer> _keyMemberToIndex;
    final List<Member> _indexToKeyMember;
    final SparseBitSet _set;

    MemberSet()
    {
        _keyMemberToIndex = null;
        _indexToKeyMember = null;
        _set = null;
    }

    // construct sealed empty set
    MemberSet(List<Member> members)
    {
        Map<Member,Integer> tmap = new HashMap<>(2*members.size());
        List<Member> tlist = new ArrayList(members.size());
        for (Member m : members)
        {
            tmap.put(m, tlist.size());
            tlist.add(m);
        }
        assert null != (tmap = Collections.unmodifiableMap(tmap));
        assert null != (tlist = Collections.unmodifiableList(tlist));
        _keyMemberToIndex = tmap;
        _indexToKeyMember = tlist;
        _set = new SparseBitSet(members.size());
        _set.seal();
    }

    // create mutable set with shared member indexes
    MemberSet(MemberSet from, boolean copy)
    {
        _keyMemberToIndex = from._keyMemberToIndex;
        _indexToKeyMember = from._indexToKeyMember;
        _set = new SparseBitSet(_indexToKeyMember.size());
        if (copy)
            _set.or(from._set);
    }

    void seal()
    {
        _set.seal();
    }

    void addMembers(List<Member> c)
    {
        for (Member m : c)
        {
            Integer i = _keyMemberToIndex.get(m);
            if (null == i)
                throw new IllegalStateException();
            _set.or(i,true);
        }
    }

    List<Member> getMembers()
    {
        SparseBitSet s = _set;
        ArrayList<Member> list = new ArrayList<>(s.size());
        for( int i = s.nextSetBit(0); i >= 0; i = s.nextSetBit(i+1) )
            list.add(_indexToKeyMember.get(i));
        return list;
    }

    MemberSet intersect(MemberSet... sets)
    {
        MemberSet ret = new MemberSet(sets[0], true);
        for (int i=1 ; i<sets.length ; i++)
            ret._set.and(sets[i]._set);
        return ret;
    }
}
