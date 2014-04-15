/*
 * Copyright (c) 2011-2014 LabKey Corporation
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
package org.labkey.study.model;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.AbstractParticipantGroup;
import org.labkey.api.query.FieldKey;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;


/**
 * User: klum
 * Date: Jun 10, 2011
 * Time: 3:16:27 PM
 */
public class ParticipantGroup extends AbstractParticipantGroup<String>
{
    public Pair<FieldKey, String> getFilterColAndValue(Container container)
    {
        FieldKey key = FieldKey.fromParts(StudyService.get().getSubjectColumnName(container), getCategoryLabel());
        return new Pair<>(key, getLabel());
    }

    public String getURLFilterParameterName(FieldKey filterColumn, String dataRegionName)
    {
        StringBuilder filterKey = new StringBuilder();
        if (!StringUtils.isEmpty(dataRegionName))
        {
            filterKey.append(dataRegionName);
            filterKey.append(".");
        }
        filterKey.append(filterColumn);
        return filterKey.toString();
    }

    public ActionURL addURLFilter(ActionURL url, Container container, String dataRegionName)
    {
        Pair<FieldKey, String> filterColAndValue = getFilterColAndValue(container);
        url.deleteFilterParameters(getURLFilterParameterName(filterColAndValue.getKey(), dataRegionName));
        url.addFilter(dataRegionName, filterColAndValue.getKey(), CompareType.EQUAL, filterColAndValue.getValue());
        return url;
    }

    public ActionURL removeURLFilter(ActionURL url, Container container, String dataRegionName)
    {
        Pair<FieldKey, String> filterColAndValue = getFilterColAndValue(container);
        url.deleteFilterParameters(getURLFilterParameterName(filterColAndValue.getKey(), dataRegionName));
        return url;
    }

    public boolean hasLiveFilter()
    {
        String filters = getFilters();
        if (StringUtils.isNotEmpty(filters))
        {
            // we may have some CDS created participant groups internally with the old style format
            // so make sure we don't blow up if we encounter them
            try
            {
                JSONObject filter = new JSONObject(filters);
                if (filter.has("isLive"))
                    return filter.getBoolean("isLive");
            }
            catch(JSONException ignore){}
        }

        return false;
    }

    @Override
    public String[] getParticipantIds()
    {
        return _participantIds.toArray(new String[_participantIds.size()]);
    }
}
