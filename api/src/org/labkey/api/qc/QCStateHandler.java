package org.labkey.api.qc;

import java.util.List;

public interface QCStateHandler
{
    List<QCState> getQCStates();
    boolean isQCStateInUse(QCState state);
    boolean isBlankQCStatePublic();
    Integer getDefaultPipelineQCState();
    Integer getDefaultAssayQCState();
    Integer getDefaultDirectEntryQCState();
    boolean isShowPrivateDataByDefault();
}
