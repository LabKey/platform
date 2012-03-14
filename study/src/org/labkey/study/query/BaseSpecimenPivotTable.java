package org.labkey.study.query;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.study.Site;
import org.labkey.api.study.StudyService;
import org.labkey.study.SampleManager;
import org.labkey.study.model.SiteImpl;
import org.labkey.study.model.SpecimenTypeSummary;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Mar 14, 2012
 */
public abstract class BaseSpecimenPivotTable extends FilteredTable
{
    protected static final String AGGREGATE_DELIM = "::";
    protected static final String TYPE_DELIM = "-";

    public BaseSpecimenPivotTable(final TableInfo tinfo, final StudyQuerySchema schema)
    {
        super(tinfo, schema.getContainer());

        addWrapColumn(_rootTable.getColumn(StudyService.get().getSubjectColumnName(getContainer())));
        addWrapColumn(_rootTable.getColumn(StudyService.get().getSubjectVisitColumnName(getContainer())));
        addWrapColumn(_rootTable.getColumn("Visit"));
    }

    protected ColumnInfo wrapPivotColumn(ColumnInfo col, String ...parts)
    {
        StringBuilder name = new StringBuilder();
        StringBuilder label = new StringBuilder();
        String delim = "";
        String labelDelim = "";

        for (String part : parts)
        {
            name.append(delim).append(part);
            label.append(labelDelim).append(part);

            delim = "_";
            labelDelim = ":";
        }
        ColumnInfo colInfo = new AliasedColumn(this, ColumnInfo.legalNameFromName(name.toString()), col);
        colInfo.setLabel(label.toString());

        return addColumn(colInfo);
    }
    
    /**
     * Returns a map of primary type id's to labels
     */
    protected Map<Integer, String> getPrimaryTypeMap(Container container)
    {
        Map<Integer, String> typeMap = new HashMap<Integer, String>();
        SpecimenTypeSummary summary = SampleManager.getInstance().getSpecimenTypeSummary(getContainer());

        for (SpecimenTypeSummary.TypeCount type : summary.getPrimaryTypes())
        {
            if (type.getId() != null)
                typeMap.put(type.getId(), type.getLabel());
        }
        return typeMap;
    }

    /**
     * Returns a map of derivative type id's to labels
     */
    protected Map<Integer, String> getDerivativeTypeMap(Container container)
    {
        Map<Integer, String> typeMap = new HashMap<Integer, String>();
        SpecimenTypeSummary summary = SampleManager.getInstance().getSpecimenTypeSummary(getContainer());

        for (SpecimenTypeSummary.TypeCount type : summary.getDerivatives())
        {
            if (type.getId() != null)
                typeMap.put(type.getId(), type.getLabel());
        }
        return typeMap;
    }

    /**
     * Returns a map of site id's to labels
     */
    protected Map<Integer, String> getSiteMap(Container container) throws SQLException
    {
        Map<Integer, String> siteMap = new HashMap<Integer, String>();

        for (SiteImpl site : SampleManager.getInstance().getSites(container))
            siteMap.put(site.getRowId(), site.getLabel());

        return siteMap;
    }
}
