package org.labkey.api.qc.export;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.data.Container;
import org.labkey.api.qc.QCState;
import org.labkey.api.security.User;
import org.labkey.folder.xml.FolderDocument;
import org.labkey.study.xml.qcStates.StudyqcDocument;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public interface QCStateImportExportHelper
{
    List<QCStateImportExportHelper> _providers = new ArrayList<>();

    static void registerProvider(QCStateImportExportHelper provider)
    {
        _providers.add(provider);
    }

    @Nullable
    static QCStateImportExportHelper getProvider(Container container)
    {
        List<QCStateImportExportHelper> helpers = new ArrayList<>();
        for (QCStateImportExportHelper helper : _providers)
        {
            if (helper.matches(container))
            {
                helpers.add(helper);
            }
        }

        helpers.sort(Comparator.comparingInt(QCStateImportExportHelper::getPriority));
        if (!helpers.isEmpty())
            return helpers.get(0);

        return null;
    }

    boolean matches(Container container);

    /**
     * Relative priority to other helpers, a lower number represents a higher priority
     * @return
     */
    int getPriority();

    void write(Container container, ImportContext<FolderDocument.Folder> ctx, StudyqcDocument.Studyqc qcXml);

    boolean isQCStateInUse(Container container, QCState state);

    QCState insertQCState(User user, QCState state);

    void setDefaultAssayQCState(Container container, User user, Integer stateId);
    void setDefaultPipelineQCState(Container container, User user, Integer stateId);
    void setDefaultDirectEntryQCState(Container container, User user, Integer stateId);
    void setShowPrivateDataByDefault(Container container, User user, boolean showPrivate);
    void setBlankQCStatePublic(Container container, User user, boolean isPublic);
}
