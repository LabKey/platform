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

import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.study.model.CohortImpl;
import org.labkey.study.model.StudyManager;

/**
 * User: adam
 * Date: 8/28/12
 * Time: 3:42 PM
 */
public class SingleCohortFilter extends BaseCohortFilter
{
    private final int _cohortId;

    @Deprecated  // Should use CohortFilterFactory instead
    public SingleCohortFilter(Type type, int cohortId)
    {
        super(type);

        if (cohortId < 0)
            throw new IllegalArgumentException("Invalid cohort ID: " + cohortId);

        _cohortId = cohortId;
    }

    // Special constructor for the 'unassigned' singleton cohort filter
    protected SingleCohortFilter(Type type)
    {
        super(type);
        _cohortId = -1;
    }

    public int getCohortId()
    {
        return _cohortId;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SingleCohortFilter that = (SingleCohortFilter) o;

        if (_cohortId != that._cohortId) return false;
        if (_type != that._type) return false;

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = _type.hashCode();
        result = 31 * result + _cohortId;
        return result;
    }

    public CohortImpl getCohort(Container container, User user)
    {
        if (!StudyManager.getInstance().showCohorts(container, user))
            return null;
        return StudyManager.getInstance().getCohortForRowId(container, user, getCohortId());
    }

    public String getDescription(Container container, User user)
    {
        CohortImpl cohort = getCohort(container, user);
        if (cohort == null)
            return null;
        return getType().getTitle() + " is " + cohort.getLabel();
    }

    public void addFilterCondition(TableInfo table, Container container, SimpleFilter filter)
    {
        filter.addCondition(getCohortColumn(table, container).getFieldKey(), getCohortId());
    }

    public ActionURL addURLParameters(ActionURL url, String dataregion)
    {
//        if (StringUtils.isEmpty(dataregion))
        {
            url.replaceParameter(CohortFilterFactory.Params.cohortFilterType, getType().name());
            url.replaceParameter(CohortFilterFactory.Params.cohortId, "" + getCohortId());
        }
//        else
//        {
//            url.replaceParameter(dataregion + "." + CohortFilterFactory.Params.cohortFilterType, getType().name());
//            url.replaceParameter(dataregion + "." + CohortFilterFactory.Params.cohortId, "" + getCohortId());
//        }
        return url;
    }

    @Override
    public String getCacheKey()
    {
        return getType().name() + getCohortId();
    }


    static class UnassignedCohort extends SingleCohortFilter
    {
        UnassignedCohort()
        {
            super(Type.DATA_COLLECTION);
        }

        @Override
        public String getDescription(Container container, User user)
        {
            return getType().getTitle() + " is unassigned";
        }

        @Override
        public void addFilterCondition(TableInfo table, Container container, SimpleFilter filter)
        {
            filter.addCondition(getCohortColumn(table, container).getFieldKey(), null, CompareType.ISBLANK);
        }

        @Override
        public CohortImpl getCohort(Container container, User user)
        {
            return null;
        }
    }
}
