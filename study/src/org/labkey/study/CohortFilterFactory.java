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
import org.labkey.api.data.BooleanFormat;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.study.CohortFilter.Type;
import org.labkey.study.model.CohortImpl;
import org.labkey.study.model.StudyManager;

import java.text.ParseException;
import java.util.LinkedList;
import java.util.List;

/**
 * User: adam
 * Date: 8/28/12
 * Time: 3:54 PM
 */
public class CohortFilterFactory
{
    public static enum Params
    {
        cohortFilterType,
        cohortId,
        cohortEnrolled
    }

    public static final SingleCohortFilter UNASSIGNED = new SingleCohortFilter.UnassignedCohort();

    public static ActionURL clearURLParameters(ActionURL url)
    {
        url.deleteParameter(Params.cohortFilterType);
        url.deleteParameter(Params.cohortId);
        url.deleteParameter(Params.cohortEnrolled);
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
            if (null != cohortIdStr)
                return Integer.parseInt(cohortIdStr);
        }
        catch (NumberFormatException e)
        {
            // fall through to return a null filter if the id parameter isn't a valid int
        }
        return null;
    }

    private static Boolean getCohortEnrolledFromURL(ActionURL url)
    {
        String cohortEnrolledStr = url.getParameter(Params.cohortEnrolled);
        try
        {
            if (null != cohortEnrolledStr)
                return BooleanFormat.getInstance().parseObject(cohortEnrolledStr);
        }
        catch (ParseException e)
        {
            // fall through to return null if the id parameter isn't a valid boolean
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

    public static @Nullable CohortFilter getFromURL(Container c, User user, ActionURL url)
    {
        // If you're not allowed to see cohorts then we shouldn't filter by anything
        if (StudyManager.getInstance().showCohorts(c, user))
        {
            Type type = getTypeFromURL(url);
            Integer cohortId = getCohortIdFromURL(url);
            Boolean enrolled = getCohortEnrolledFromURL(url);

            if (null != type)
            {
                if (null != cohortId)
                {
                    if (type == UNASSIGNED.getType() && cohortId == UNASSIGNED.getCohortId())
                        return UNASSIGNED;

                    return new SingleCohortFilter(type, cohortId);
                }
                else if (null != enrolled && enrolled)
                {
                    return getEnrolledCohortFilter(c, user, type);
                }
            }
        }

        return null;
    }

    // If BOTH enrolled and unenrolled cohorts exist, then return a MultipleCohortFilter that selects the enrolled and
    // unassigned participants. Otherwise, return null, which means all participants will be shown.
    public static @Nullable CohortFilter getEnrolledCohortFilter(Container c, User user, Type type)
    {
        List<Integer> cohortIds = new LinkedList<Integer>();
        boolean unenrolled = false;

        for (CohortImpl cohort : StudyManager.getInstance().getCohorts(c, user))
        {
            if (cohort.isEnrolled())
                cohortIds.add(cohort.getRowId());
            else
                unenrolled = true;
        }

        if (unenrolled && !cohortIds.isEmpty())
            return new MultipleCohortFilter(type, cohortIds, true, "enrolled or unassigned");
        else
            return null;
    }
}
