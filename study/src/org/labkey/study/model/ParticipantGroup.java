/*
 * Copyright (c) 2011 LabKey Corporation
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

import org.apache.commons.lang.StringUtils;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.Entity;
import org.labkey.api.query.FieldKey;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Jun 10, 2011
 * Time: 3:16:27 PM
 */
public class ParticipantGroup extends Entity
{
    private int _rowId;
    private String _label;
    private int _categoryId;  // fk to participant category
    private String _categoryLabel;

    private Set<String> _participantIds = new LinkedHashSet<String>();

    public boolean isNew()
    {
        return _rowId == 0;
    }

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public String getLabel()
    {
        return _label;
    }

    public void setLabel(String label)
    {
        _label = label;
    }

    public int getCategoryId()
    {
        return _categoryId;
    }

    public void setCategoryId(int categoryId)
    {
        _categoryId = categoryId;
    }

    public String[] getParticipantIds()
    {
        return _participantIds.toArray(new String[_participantIds.size()]);
    }

    public void setParticipantIds(String[] participantIds)
    {
        Set<String> participants = new LinkedHashSet<String>();
        Set<String> duplicates = new LinkedHashSet<String>();

        // validate that there are no duplicates
        for (String id : participantIds)
        {
            if (participants.contains(id))
                duplicates.add(id);

            participants.add(id);
        }

        if (!duplicates.isEmpty())
        {
            StringBuilder sb = new StringBuilder();

            sb.append("Duplicate ID(s) were found: ");
            String delim = "";
            int i=0;

            for (String id : duplicates)
            {
                sb.append(delim);
                sb.append(id);
                delim = ", ";

                if (++i >= 5)
                    break;
            }

            if (duplicates.size() > 5)
            {
                sb.append(" (5 shown out of a total of: ");
                sb.append(duplicates.size());
                sb.append(")");
            }
            sb.append(". Duplicates are not allowed in a group.");

            throw new IllegalArgumentException(sb.toString());
        }
        _participantIds = participants;
    }

    public void addParticipantId(String participantId)
    {
        if (_participantIds.contains(participantId))
            throw new IllegalArgumentException("ID :" + participantId + " is specified more than once, duplicates are not allowed in a group.");

        _participantIds.add(participantId);
    }

    public String getCategoryLabel()
    {
        return _categoryLabel;
    }

    public void setCategoryLabel(String categoryLabel)
    {
        _categoryLabel = categoryLabel;
    }

    public Pair<FieldKey, String> getFilterColAndValue(Container container)
    {
        FieldKey key = FieldKey.fromParts(StudyService.get().getSubjectColumnName(container), getCategoryLabel());
        return new Pair<FieldKey, String>(key, getLabel());
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

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ParticipantGroup that = (ParticipantGroup) o;

        if (_rowId != that._rowId) return false;

        return true;
    }

    @Override
    public int hashCode()
    {
        return _rowId;
    }
}
