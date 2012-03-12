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
public class SpecimenPivotByDerivativeType extends FilteredTable
{
    public static final String PIVOT_BY_DERIVATIVE_TYPE = "Primary/Derivative Type Vial Counts";

    private static final String AGGREGATE_DELIM = "::";
    private static final String TYPE_DELIM = "-";

    public SpecimenPivotByDerivativeType(final StudyQuerySchema schema)
    {
        super(SpecimenReportQuery.getPivotByDerivativeType(schema.getContainer(), schema.getUser()), schema.getContainer());
        setDescription("Contains up to one row of Specimen Primary/Derivative Type totals for each " + StudyService.get().getSubjectNounSingular(getContainer()) +
            "/visit combination.");

        addWrapColumn(_rootTable.getColumn(StudyService.get().getSubjectColumnName(getContainer())));
        addWrapColumn(_rootTable.getColumn(StudyService.get().getSubjectVisitColumnName(getContainer())));
        addWrapColumn(_rootTable.getColumn("Visit"));

        Map<Integer, String> primaryTypeMap = new HashMap<Integer, String>();
        Map<Integer, String> derivativeTypeMap = new HashMap<Integer, String>();

        SpecimenTypeSummary summary = SampleManager.getInstance().getSpecimenTypeSummary(getContainer());

        // need the map of row id's to labels for primary and derivative types
        for (SpecimenTypeSummary.TypeCount type : summary.getPrimaryTypes())
        {
            if (type.getId() != null)
                primaryTypeMap.put(type.getId(), type.getLabel());
        }

        for (SpecimenTypeSummary.TypeCount type : summary.getDerivatives())
        {
            if (type.getId() != null)
                derivativeTypeMap.put(type.getId(), type.getLabel());
        }

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
                        String colName = primaryTypeMap.get(primaryId) + ":" + derivativeTypeMap.get(derivativeId) + "_" + parts[1];
                        ColumnInfo colInfo = new AliasedColumn(this, ColumnInfo.labelFromName(colName), col);

                        addColumn(colInfo);
                    }
                }
            }
        }

        setDefaultVisibleColumns(getDefaultVisibleColumns());

        addWrapColumn(_rootTable.getColumn("Container"));
    }
}
