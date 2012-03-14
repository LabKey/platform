package org.labkey.study.query;

import org.apache.commons.lang3.math.NumberUtils;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.study.StudyService;
import org.labkey.study.SampleManager;
import org.labkey.study.model.SpecimenTypeSummary;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Mar 14, 2012
 */
public class SpecimenPivotByRequestingLocation extends BaseSpecimenPivotTable
{
    public static final String PIVOT_BY_REQUESTING_LOCATION = "Vial Counts by Requesting Location";

    public SpecimenPivotByRequestingLocation(final StudyQuerySchema schema)
    {
        super(SpecimenReportQuery.getPivotByRequestingLocation(schema.getContainer(), schema.getUser()), schema);
        setDescription("Contains up to one row of Specimen Derivative Type totals by Requesting Location for each " + StudyService.get().getSubjectNounSingular(getContainer()) +
            "/visit combination.");

        try {

            Map<Integer, String> primaryTypeMap = getPrimaryTypeMap(getContainer());
            Map<Integer, String> derivativeTypeMap = getDerivativeTypeMap(getContainer());
            Map<Integer, String> siteMap = getSiteMap(getContainer());

            for (ColumnInfo col : getRealTable().getColumns())
            {
                // look for the primary/derivative pivot encoding
                String parts[] = col.getName().split(AGGREGATE_DELIM);

                if (parts != null && parts.length == 2)
                {
                    String types[] = parts[0].split(TYPE_DELIM);

                    if (types != null && types.length == 3)
                    {
                        int primaryId = NumberUtils.toInt(types[0]);
                        int derivativeId = NumberUtils.toInt(types[1]);
                        int siteId = NumberUtils.toInt(types[2]);

                        if (primaryTypeMap.containsKey(primaryId) && derivativeTypeMap.containsKey(derivativeId) && siteMap.containsKey(siteId))
                        {
                            wrapPivotColumn(col,
                                    primaryTypeMap.get(primaryId),
                                    derivativeTypeMap.get(derivativeId),
                                    siteMap.get(siteId));
                        }
                    }
                }
            }
            setDefaultVisibleColumns(getDefaultVisibleColumns());
            addWrapColumn(_rootTable.getColumn("Container"));
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e);
        }
    }
}
