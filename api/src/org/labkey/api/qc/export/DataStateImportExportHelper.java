package org.labkey.api.qc.export;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.FolderExportContext;
import org.labkey.api.data.Container;
import org.labkey.api.qc.DataState;
import org.labkey.api.security.User;
import org.labkey.study.xml.qcStates.StudyqcDocument;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public interface DataStateImportExportHelper
{
    List<DataStateImportExportHelper> _providers = new ArrayList<>();

    static void registerProvider(DataStateImportExportHelper provider)
    {
        _providers.add(provider);
    }

    @Nullable
    static DataStateImportExportHelper getProvider(Container container)
    {
        List<DataStateImportExportHelper> helpers = new ArrayList<>();
        for (DataStateImportExportHelper helper : _providers)
        {
            if (helper.matches(container))
            {
                helpers.add(helper);
            }
        }

        helpers.sort(Comparator.comparingInt(DataStateImportExportHelper::getPriority));
        if (!helpers.isEmpty())
            return helpers.get(0);

        return null;
    }

    boolean matches(Container container);

    /**
     * Relative priority to other helpers, a lower number represents a higher priority
     */
    int getPriority();

    void write(Container container, FolderExportContext ctx, StudyqcDocument.Studyqc qcXml);

    boolean isDataStateInUse(Container container, DataState state);

    DataState insertDataState(User user, DataState state);
    DataState updateDataState(User user, DataState state);

    /**
     * The default QC state for data linked (published) to the study
     */
    void setDefaultPublishedDataQCState(Container container, User user, Integer stateId);
    void setDefaultPipelineQCState(Container container, User user, Integer stateId);
    void setDefaultDirectEntryQCState(Container container, User user, Integer stateId);
    void setShowPrivateDataByDefault(Container container, User user, boolean showPrivate);
    void setBlankQCStatePublic(Container container, User user, boolean isPublic);
    void setRequireCommentOnQCStateChange(Container container, User user, boolean requireCommentOnQCStateChange);
}
