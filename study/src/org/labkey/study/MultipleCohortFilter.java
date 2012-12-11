/*
 * Copyright (c) 2012 LabKey Corporation
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
package org.labkey.study;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.study.model.CohortImpl;

import java.util.Collection;

/**
 * User: adam
 * Date: 8/28/12
 * Time: 4:41 PM
 */
public class MultipleCohortFilter extends BaseCohortFilter
{
    private final Collection<Integer> _cohortIds;
    private final boolean _includeUnassigned;
    private final String _description;

    public MultipleCohortFilter(CohortFilter.Type type, Collection<Integer> cohortIds, boolean includeUnassigned, String description)
    {
        super(type);

        for (Integer cohortId : cohortIds)
            if (cohortId < 0)
                throw new IllegalArgumentException("Invalid cohort ID: " + cohortId);

        _cohortIds = cohortIds;
        _includeUnassigned = includeUnassigned;
        _description = description;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MultipleCohortFilter that = (MultipleCohortFilter) o;

        if (_includeUnassigned != that._includeUnassigned) return false;
        if (_cohortIds != null ? !_cohortIds.equals(that._cohortIds) : that._cohortIds != null) return false;
        if (_type != null ? !_type.equals(that._type) : that._type != null) return false;

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = _cohortIds != null ? _cohortIds.hashCode() : 0;
        result = 31 * result + (_includeUnassigned ? 1 : 0);
        result = 31 * result + (_type != null ? _type.hashCode() : 0);
        return result;
    }

    public String getDescription(Container container, User user)
    {
        return getType().getTitle() + " is " + _description;
    }

    public void addFilterCondition(TableInfo table, Container container, SimpleFilter filter)
    {
        SimpleFilter.InClause inClause = new SimpleFilter.InClause(getCohortColumn(table, container).getFieldKey(), _cohortIds);
        inClause.setIncludeNull(_includeUnassigned);
        filter.addClause(inClause);
    }

    public ActionURL addURLParameters(ActionURL url, @Nullable String dataregion)
    {
//        if (StringUtils.isEmpty(dataregion))
        {
            url.replaceParameter(CohortFilterFactory.Params.cohortFilterType, getType().name());
            url.replaceParameter(CohortFilterFactory.Params.cohortEnrolled, "1");
        }
//        else
//        {
//            url.replaceParameter(dataregion + "." + CohortFilterFactory.Params.cohortFilterType, getType().name());
//            url.replaceParameter(dataregion + "." + CohortFilterFactory.Params.cohortEnrolled, "1");
//        }
        return url;
    }

    @Override
    public String getCacheKey()
    {
        return getType().name() + _cohortIds.toString();
    }

    public int getCohortId()
    {
        throw new IllegalStateException("Should not be calling getCohortId()");
    }

    public CohortImpl getCohort(Container container, User user)
    {
        throw new IllegalStateException("Should not be calling getCohort()");
    }
}
