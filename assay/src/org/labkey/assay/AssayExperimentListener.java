package org.labkey.assay;

import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentListener;
import org.labkey.api.security.User;

import java.util.List;

public class AssayExperimentListener implements ExperimentListener
{
    @Override
    public void afterExperimentDeleted(Container c, User user, ExpExperiment experiment)
    {
        AssayManager.get().deindexAssayBatches(List.of(experiment));
    }

    @Override
    public void afterExperimentSaved(Container c, User user, ExpExperiment experiment)
    {
        AssayManager.get().indexAssayBatch(experiment.getRowId());
    }

    @Override
    public void afterRunDelete(ExpProtocol protocol, ExpRun run, User user)
    {
        AssayManager.get().deindexAssayRuns(List.of(run));
    }

    @Override
    public void afterRunSaved(Container container, User user, ExpProtocol protocol, ExpRun run)
    {
        AssayManager.get().indexAssayRun(run.getRowId());
    }
}
