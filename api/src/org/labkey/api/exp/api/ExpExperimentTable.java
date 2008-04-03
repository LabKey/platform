package org.labkey.api.exp.api;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.query.DetailsURL;

public interface ExpExperimentTable extends ExpTable<ExpExperimentTable.Column>
{
    void addExperimentMembershipColumn(ExpRun run);

    enum Column
    {
        RowId,
        LSID,
        Name,
        Hypothesis,
        Comments,
        Created,
        CreatedBy,
        Modified,
        ModifiedBy,
        RunCount,
        Container,
    }

    void populate(ExpSchema schema);
    ColumnInfo createRunCountColumn(String alias, ExpProtocol parentProtocol, ExpProtocol childProtocol);
}
