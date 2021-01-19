package org.labkey.api.study;

import org.jetbrains.annotations.NotNull;
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

// Utility methods that are shared between study and specimen
public class StudyUtils
{
    // Shared because, for some reason, SpecimenQueryView checks for this type
    public static final String STUDY_CROSSTAB_REPORT_TYPE = "ReportService.crosstabReport";

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
    private static <T> Set<T> intersect(@NotNull Set<T> left, @NotNull Set<T> right)
    {
        Set<T> intersection = new HashSet<>();
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
            Set<Integer> currentLocations = new HashSet<>();
            for (Vial vial : vials)
            {
                if (vial.getCurrentLocation() != null)
                    currentLocations.add(vial.getCurrentLocation());
            }
            if (locationIntersection == null)
                locationIntersection = currentLocations;
            else
            {
                locationIntersection = intersect(locationIntersection, currentLocations);
                if (locationIntersection.isEmpty())
                    return locationIntersection;
            }
        }

        if (null != locationIntersection)
            return locationIntersection;

        return Collections.emptySet();
    }

    public static boolean isFieldTrue(RenderContext ctx, String fieldName)
    {
        Object value = ctx.getRow().get(fieldName);
        if (value instanceof Integer)
            return ((Integer) value).intValue() != 0;
        else if (value instanceof Boolean)
            return ((Boolean) value).booleanValue();
        return false;
    }
}
