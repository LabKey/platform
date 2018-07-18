package org.labkey.api.study.assay;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.security.User;
import org.labkey.api.study.query.ResultsQueryView;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;

import java.util.Set;

public interface AssayWellExclusionService
{
    AssayWellExclusionService[] _providers = new AssayWellExclusionService[1];

    static void registerProvider(AssayWellExclusionService provider)
    {
        if (_providers[0] == null)
            _providers[0] = provider;
        else
            throw new RuntimeException("An Assay Well Exclusion Provider has already been registered");
    }

    @Nullable
    static AssayWellExclusionService getProvider()
    {
        return _providers[0];
    }

    @Nullable
    ColumnInfo createExcludedColumn(TableInfo tinfo, ExpProtocol protocol);
    @Nullable
    ColumnInfo createExcludedByColumn(TableInfo tinfo, ExpProtocol protocol);
    @Nullable
    ColumnInfo createExcludedAtColumn(TableInfo tinfo, ExpProtocol protocol);
    @Nullable
    ColumnInfo createExclusionCommentColumn(TableInfo tinfo, ExpProtocol protocol);

    void createExclusionEvent(ExpRun run, Set<String> rowIds, String comment, User user, Container container);

    void deleteExclusionsForRun(ExpProtocol protocol, int runId);

    int getExclusionCount(ExpRun run);

    @Nullable
    ResultsQueryView.ResultsDataRegion createResultsDataRegion(ExpProtocol protocol);

    ActionURL getExclusionReportURL(Container container, ExpRun run);
    ActionURL getExclusionURL(Container container, AssayProvider provider, int rowId, String runId, String returnUrl);

    @Nullable
    // returns a view containing warnings if the specified run has current well exclusions associated with it
    HttpView getAssayReImportWarningView(Container container, ExpRun run);
}
