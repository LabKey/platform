package org.labkey.experiment.api;

import org.labkey.api.exp.api.ExpProtocolTable;
import org.labkey.api.exp.api.ExpSchema;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.query.RowIdForeignKey;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.view.ActionURL;

import java.util.Collections;

public class ExpProtocolTableImpl extends ExpTableImpl<ExpProtocolTable.Column> implements ExpProtocolTable
{
    public ExpProtocolTableImpl(String alias)
    {
        super(alias, ExperimentServiceImpl.get().getTinfoProtocol());
        setTitleColumn("Name");
    }
    public ColumnInfo createColumn(String alias, Column column)
    {
        switch (column)
        {
            case RowId:
                return wrapColumn(alias, _rootTable.getColumn("RowId"));
            case Name:
                return wrapColumn(alias, _rootTable.getColumn("Name"));
            case LSID:
                return wrapColumn(alias, _rootTable.getColumn("LSID"));
        }
        throw new IllegalArgumentException("Unknown column " + column);
    }

    public void populate(ExpSchema schema)
    {
        ColumnInfo colRowId = addColumn(Column.RowId);
        colRowId.setIsHidden(true);
        colRowId.setFk(new RowIdForeignKey(colRowId));
        colRowId.setKeyField(true);
        ColumnInfo colName = addColumn(Column.Name);
        setTitleColumn(colName.getName());
        ColumnInfo colLSID = addColumn(Column.LSID);
        colLSID.setIsHidden(true);
        if (schema.isRestrictContainer())
        {
            setContainer(schema.getContainer());
        }
        ActionURL urlDetails = new ActionURL("Experiment", "protocolDetails", schema.getContainer().getPath());
        setDetailsURL(new DetailsURL(urlDetails, Collections.singletonMap("rowId", "RowId")));
        addDetailsURL(new DetailsURL(urlDetails, Collections.singletonMap("LSID", "LSID")));
    }
}
