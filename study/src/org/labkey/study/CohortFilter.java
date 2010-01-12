/*
 * Copyright (c) 2009 LabKey Corporation
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

import org.labkey.api.view.ActionURL;
import org.labkey.api.data.*;
import org.labkey.api.security.User;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.study.StudyService;
import org.labkey.study.model.CohortImpl;
import org.labkey.study.model.StudyManager;

import java.util.Map;
import java.util.Collections;

/**
 * Created by IntelliJ IDEA.
* User: brittp
* Date: Sep 10, 2009
* Time: 10:32:54 AM
*/
public class CohortFilter
{
    public enum Type
    {
        PTID_INITIAL("Initial cohort")
        {
            public FieldKey getFilterColumn(Container container)
            {
                return FieldKey.fromParts(StudyService.get().getSubjectColumnName(container), "InitialCohort", "rowid");
            }
        },

        PTID_CURRENT("Current cohort")
        {
            public FieldKey getFilterColumn(Container container)
            {
                return FieldKey.fromParts(StudyService.get().getSubjectColumnName(container), "Cohort", "rowid");
            }
        },
        DATA_COLLECTION("Cohort as of data collection")
        {
            public FieldKey getFilterColumn(Container container)
            {
                return FieldKey.fromParts(StudyService.get().getSubjectVisitColumnName(container), "Cohort", "rowid");
            }
        };

        private String _title;
        Type(String title)
        {
            _title = title;
        }

        public String getTitle()
        {
            return _title;
        }

        public abstract FieldKey getFilterColumn(Container container);
    }

    public static final CohortFilter UNASSIGNED = new UnassignedCohort();

    private static class UnassignedCohort extends CohortFilter
    {
        public UnassignedCohort()
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
            filter.addCondition(getCohortColumn(table, container).getName(), null, CompareType.ISBLANK);
        }

        @Override
        public CohortImpl getCohort(Container container, User user)
        {
            return null;
        }
    }

    private Type _type;
    private int _cohortId;

    public CohortFilter(Type type, int cohortId)
    {
        if (type == null)
            throw new IllegalArgumentException("Cohort filter type must not be null");
        if (cohortId < 0)
            throw new IllegalArgumentException("Invalid cohort ID: " + cohortId);
        _type = type;
        _cohortId = cohortId;
    }

    // Special constructor for the 'unassigned' singleton cohort filter
    protected CohortFilter(Type type)
    {
        _type = type;
        _cohortId = -1;
    }

    public Type getType()
    {
        return _type;
    }

    public int getCohortId()
    {
        return _cohortId;
    }

    public enum Params
    {
        cohortFilterType,
        cohortId
    }

    public boolean equalsURLFilter(ActionURL url)
    {
        Type type = getTypeFromURL(url);
        Integer cohortId = getCohortIdFromURL(url);
        if (type == null || cohortId == null)
            return false;
        return _type.equals(type) && _cohortId == cohortId.intValue();
    }

    public static ActionURL clearURLParameters(ActionURL url)
    {
        url.deleteParameter(Params.cohortFilterType);
        url.deleteParameter(Params.cohortId);
        return url;
    }

    private static Type getTypeFromURL(ActionURL url)
    {
        String cohortFilterType = url.getParameter(Params.cohortFilterType);
        if (cohortFilterType != null)
        {
            try
            {
                return Type.valueOf(cohortFilterType);
            }
            catch (IllegalArgumentException e)
            {
                // fall through to return a null filter if the type parameter isn't recognized
            }
        }
        return null;
    }

    private static Integer getCohortIdFromURL(ActionURL url)
    {
        String cohortIdStr = url.getParameter(Params.cohortId);
        try
        {
            return Integer.parseInt(cohortIdStr);
        }
        catch (NumberFormatException e)
        {
            // fall through to return a null filter if the id parameter isn't a valid int
        }
        return null;
    }

    public static boolean isCohortFilterParameterName(String name)
    {
        for (Params param : Params.values())
        {
            if (name.equals(param.name()))
                return true;
        }
        return false;
    }

    public static CohortFilter getFromURL(ActionURL url)
    {
        Type type = getTypeFromURL(url);
        Integer cohortId = getCohortIdFromURL(url);
        if (type != null && cohortId != null)
        {
            if (type == UNASSIGNED.getType() && cohortId.intValue() == UNASSIGNED.getCohortId())
                return UNASSIGNED;
            return new CohortFilter(type, cohortId.intValue());
        }
        return null;
    }

    public ActionURL addURLParameters(ActionURL url)
    {
        url.replaceParameter(Params.cohortFilterType, getType().name());
        url.replaceParameter(Params.cohortId, "" + getCohortId());
        return url;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CohortFilter that = (CohortFilter) o;

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

    protected ColumnInfo getCohortColumn(TableInfo table, Container container)
    {
        FieldKey cohortColKey = _type.getFilterColumn(container);
        Map<FieldKey, ColumnInfo> cohortColumnMap = QueryService.get().getColumns(table, Collections.singleton(cohortColKey));
        ColumnInfo cohortColumn = cohortColumnMap.get(cohortColKey);
        if (cohortColumn == null)
            throw new IllegalStateException("A column with key '" + cohortColKey.toString() + "'  was not found on table " + table.getName());
        return cohortColumn;
    }

    public void addFilterCondition(TableInfo table, Container container, SimpleFilter filter)
    {
        filter.addCondition(getCohortColumn(table, container).getName(), getCohortId());
    }
}
