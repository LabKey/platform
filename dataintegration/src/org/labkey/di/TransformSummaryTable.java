package org.labkey.di;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Sort;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.UserSchema;

import java.util.HashMap;

/**
 * Created with IntelliJ IDEA.
 * User: Dax
 * Date: 9/13/13
 * Time: 12:27 PM
 */
public class TransformSummaryTable extends TransformBaseTable
{
    @Override
    public String getTransformTableName()
    {
        return DataIntegrationQuerySchema.TRANSFORMSUMMARY_TABLE_NAME;
    }

    @Override
    protected HashMap<String, String> buildNameMap()
    {
        HashMap<String, String> colMap = super.buildNameMap();
        colMap.put("StartTime", "LastRun");
        colMap.put("Status", "LastStatus");
        return colMap;
    }

    public TransformSummaryTable(UserSchema schema)
    {
        super(schema);

        _sql = new SQLFragment();
        _sql.append(getBaseSql());
        _sql.append("INNER JOIN (SELECT TransformId, max(StartTime) AS StartTime\n");
        _sql.append("FROM ");
        _sql.append(DataIntegrationQuerySchema.getTransformRunTableName());
        _sql.append(" GROUP BY TransformId) m\n");
        _sql.append("ON t.TransformId=m.TransformId AND t.StartTime=m.StartTime\n");

        // add columns common to history and summary views
        addBaseColumns();

        // summary table should link to filtered history table
        String name = getNameMap().get("TransformId");
        ColumnInfo transformId = getColumn(name);
        transformId.setURL(DetailsURL.fromString("dataintegration/viewTransformHistory.view?transformId=${" + name + "}&transformRunId=${TransformRunId}"));
        transformId.setSortDirection(Sort.SortDirection.ASC);
    }
}
