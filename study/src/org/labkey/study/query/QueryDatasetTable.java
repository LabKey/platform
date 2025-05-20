package org.labkey.study.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.util.Pair;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.DatasetDomainKind;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class QueryDatasetTable extends DatasetTableImpl
{
    public static final String[] REQUIRED_COLUMNS = new String[]{
            DatasetDomainKind._KEY,
            DatasetDomainKind.DATE,
            DatasetDomainKind.QCSTATE,
            DatasetDomainKind.LSID,
            DatasetDomainKind.PARTICIPANTID
    };

    QueryDatasetTable(@NotNull StudyQuerySchema schema, ContainerFilter cf, @NotNull DatasetDefinition dsd)
    {
        super(schema, cf, dsd);

        setUpdateURL(null);
        setInsertURL(null);
        setImportURL(null);
        setDeleteURL(null);

        MutableColumnInfo ci = getMutableColumn("_key");
        if (ci != null)
        {
            ci.setHidden(true);
        }
    }

    @Override
    public @NotNull Map<String, Pair<IndexType, List<ColumnInfo>>> getUniqueIndices()
    {
        return Collections.emptyMap();
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        return null;
    }
}
