package org.labkey.study.query;

import org.apache.commons.lang3.math.NumberUtils;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.study.StudyService;
import org.labkey.study.SampleManager;
import org.labkey.study.model.SpecimenTypeSummary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Mar 9, 2012
 */
public class SpecimenPivotByDerivativeType extends BaseSpecimenPivotTable
{
    public static final String PIVOT_BY_DERIVATIVE_TYPE = "Primary/Derivative Type Vial Counts";

    public SpecimenPivotByDerivativeType(final StudyQuerySchema schema)
    {
        super(SpecimenReportQuery.getPivotByDerivativeType(schema.getContainer(), schema.getUser()), schema);
        setDescription("Contains up to one row of Specimen Primary/Derivative Type totals for each " + StudyService.get().getSubjectNounSingular(getContainer()) +
            "/visit combination.");

        Map<Integer, String> primaryTypeMap = getPrimaryTypeMap(getContainer());
        Map<Integer, String> derivativeTypeMap = getDerivativeTypeMap(getContainer());

        for (ColumnInfo col : getRealTable().getColumns())
        {
            // look for the primary/derivative pivot encoding
            String parts[] = col.getName().split(AGGREGATE_DELIM);

            if (parts != null && parts.length == 2)
            {
                String types[] = parts[0].split(TYPE_DELIM);

                if (types != null && types.length == 2)
                {
                    int primaryId = NumberUtils.toInt(types[0]);
                    int derivativeId = NumberUtils.toInt(types[1]);

                    if (primaryTypeMap.containsKey(primaryId) && derivativeTypeMap.containsKey(derivativeId))
                    {
                        wrapPivotColumn(col,
                                primaryTypeMap.get(primaryId),
                                derivativeTypeMap.get(derivativeId),
                                parts[1]);
                    }
                }
            }
        }
        setDefaultVisibleColumns(getDefaultVisibleColumns());
        addWrapColumn(_rootTable.getColumn("Container"));
    }
}
