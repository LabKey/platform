/*
 * Copyright (c) 2021-2026 LabKey Corporation
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
package org.labkey.api.study;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.IntHashSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.specimen.Vial;
import org.labkey.api.util.DateUtil;

import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Simple utility methods that are shared between study and specimen
public class StudyUtils
{
    // Shared because, for some reason, SpecimenQueryView checks for this type
    public static final String STUDY_CROSSTAB_REPORT_TYPE = "ReportService.crosstabReport";

    // These don't really belong here...
    public static final String SUBMISSION_WARNING = "Once a request is submitted, its specimen list may no longer be modified. Continue?";
    public static final String CANCELLATION_WARNING = "Canceling will permanently delete this pending request. Continue?";

    //Create a fixed point number encoding the date.
    public static double sequenceNumFromDate(Date d)
    {
        Calendar cal = DateUtil.newCalendar(d.getTime());
        return cal.get(Calendar.YEAR) * 10000 + (cal.get(Calendar.MONTH) + 1) * 100 + cal.get(Calendar.DAY_OF_MONTH);
    }

    public static SQLFragment sequenceNumFromDateSQL(String dateColumnName)
    {
        // Returns a SQL statement that produces a single number from a date, in the form of YYYYMMDD.
        SqlDialect dialect = StudyService.get().getStudySchema().getSqlDialect();
        SQLFragment sql = new SQLFragment();
        sql.append("(10000 * ").append(dialect.getDatePart(Calendar.YEAR, dateColumnName)).append(") + ");
        sql.append("(100 * ").append(dialect.getDatePart(Calendar.MONTH, dateColumnName)).append(") + ");
        sql.append("(").append(dialect.getDatePart(Calendar.DAY_OF_MONTH, dateColumnName)).append(")");
        return sql;
    }

    @NotNull
    private static <T> Set<T> intersect(@NotNull Set<T> left, @NotNull Set<T> right, Set<T> result)
    {
        Set<T> intersection = null == result ? new HashSet<>() : result;
        for (T item : left)
        {
            if (right.contains(item))
                intersection.add(item);
        }
        return intersection;
    }

    @NotNull
    public static Collection<Integer> getPreferredProvidingLocations(Collection<List<Vial>> specimensBySample)
    {
        Set<Integer> locationIntersection = null;

        for (List<Vial> vials : specimensBySample)
        {
            Set<Integer> currentLocations = new IntHashSet();
            for (Vial vial : vials)
            {
                if (vial.getCurrentLocation() != null)
                    currentLocations.add(vial.getCurrentLocation());
            }
            if (locationIntersection == null)
                locationIntersection = currentLocations;
            else
            {
                locationIntersection = intersect(locationIntersection, currentLocations, new IntHashSet());
                if (locationIntersection.isEmpty())
                    return locationIntersection;
            }
        }

        if (null != locationIntersection)
            return locationIntersection;

        return Collections.emptySet();
    }

    public static boolean isFieldTrue(RenderContext ctx, ColumnInfo col)
    {
        Object value = col.getValue(ctx.getRow());
        return null!=value && (Boolean)JdbcType.BOOLEAN.convert(value);
    }

    public static boolean isFieldTrue(RenderContext ctx, String fieldName)
    {
        Object value = ctx.getRow().get(fieldName);
        return null!=value && (Boolean)JdbcType.BOOLEAN.convert(value);
    }

    public static String getParticipantSequenceNumExpr(DbSchema schema, String ptidColumnName, String sequenceNumColumnName)
    {
        SqlDialect dialect = schema.getSqlDialect();
        String strType = dialect.getSqlTypeName(JdbcType.VARCHAR);

        //CAST(CAST(? AS NUMERIC(15, 4)) AS " + strType +

        return "(" + dialect.concatenate(ptidColumnName, "'|'", "CAST(CAST(" + sequenceNumColumnName + " AS NUMERIC(15,4)) AS " + strType + ")") + ")";
    }
}
