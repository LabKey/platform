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
package org.labkey.api.util;

import java.util.Comparator;

/**
 * Comparator used to sort the list of search results.
 * Currently supports only a default sort order, which is
 * container path, type, title. However, this can be
 * updated to allow for a user-specified sort order.
 *
 * Created by IntelliJ IDEA.
 * User: Dave
 * Date: May 2, 2008
 * Time: 11:10:31 AM
 * To change this template use File | Settings | File Templates.
 */
public class SearchHitComparator implements Comparator<SearchHit>
{
    public int compare(SearchHit hit1, SearchHit hit2)
    {
        if(null == hit1 && null == hit2)
            return 0;
        if(null == hit1 || null == hit2)
            return null == hit1 ? -1 : 1;

        //default sort order is by container path,
        //then hit type,
        //then hit title
        int ret = comparePaths(hit1.getContainerPath(), hit2.getContainerPath());
        if(0 == ret)
            ret = hit1.getTypeDescription().compareTo(hit2.getTypeDescription());
        if(0 == ret)
            ret = hit1.getTitle().compareTo(hit2.getTitle());
        return ret;
    }

    protected int comparePaths(String path1, String path2)
    {
        if(null == path1 && null == path2)
            return 0;
        if(null == path1 || null == path2)
            return null == path1 ? -1 : 1;
        if(path1.length() == 0 && path2.length() == 0)
            return 0;

        String[] path1Parts = path1.split("/");
        String[] path2Parts = path2.split("/");
        int minParts = Math.min(path1Parts.length, path2Parts.length);

        for(int idx = 0; idx < minParts; ++idx)
        {
            int comp = path1Parts[idx].compareTo(path2Parts[idx]);
            if(0 != comp)
                return comp;
        }
        
        //if we get here, check size (shorter sorts higher)
        return path1Parts.length < path2Parts.length ? -1 : 1;
    }

}
