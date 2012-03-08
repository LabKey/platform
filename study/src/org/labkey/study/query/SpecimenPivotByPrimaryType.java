package org.labkey.study.query;

import org.labkey.api.data.ColumnInfo;
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
 * Date: Mar 5, 2012
 */
public class SpecimenPivotByPrimaryType extends FilteredTable
{
    public static final String PIVOT_BY_PRIMARY_TYPE = "Primary Type Vial Counts";

    public SpecimenPivotByPrimaryType(final StudyQuerySchema schema)
    {
        super(SpecimenReportQuery.getPivotByPrimaryType(schema.getContainer(), schema.getUser()), schema.getContainer());
        setDescription("Contains up to one row of Specimen Primary Type totals for each " + StudyService.get().getSubjectNounSingular(getContainer()) +
            "/visit combination.");
        wrapAllColumns(true);

        List<FieldKey> defaultColumns = getDefaultColumns(schema);
        setDefaultVisibleColumns(defaultColumns);
    }

    private List<FieldKey> getDefaultColumns(StudyQuerySchema schema)
    {
        List<FieldKey> defaultColumns = new ArrayList<FieldKey>();

        defaultColumns.add(FieldKey.fromParts(StudyService.get().getSubjectColumnName(getContainer())));
        defaultColumns.add(FieldKey.fromParts("Visit"));

        SpecimenTypeSummary summary = SampleManager.getInstance().getSpecimenTypeSummary(getContainer());
        Map<String, String> nonZeroPrimaryTypes = new HashMap<String, String>();

        for (SpecimenTypeSummary.TypeCount typeCount : summary.getPrimaryTypes())
            nonZeroPrimaryTypes.put(typeCount.getLabel(), typeCount.getLabel());

        for (ColumnInfo col : getColumns())
        {
            String[] parts = col.getName().split("::");

            if (parts != null && parts.length > 1)
            {
                if (nonZeroPrimaryTypes.containsKey(parts[0]))
                    defaultColumns.add(col.getFieldKey());
                else
                    col.setHidden(true);
            }
        }
        return defaultColumns;
    }
}
