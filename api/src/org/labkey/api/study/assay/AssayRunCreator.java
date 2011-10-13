package org.labkey.api.study.assay;

import org.labkey.api.data.Container;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.qc.DataTransformer;
import org.labkey.api.qc.DataValidator;
import org.labkey.api.query.ValidationException;
import org.labkey.api.util.Pair;

/**
 * User: jeckels
 * Date: Oct 12, 2011
 */
public interface AssayRunCreator<ProviderType extends AssayProvider>
{
    public Pair<ExpRun, ExpExperiment> saveExperimentRun(AssayRunUploadContext context, ExpExperiment batch)
        throws ExperimentException, ValidationException;

    /**
     * Creates a run, but does not persist it to the database. Creates the run only, no protocol applications, etc.
     */
    public ExpRun createExperimentRun(String name, Container container, ExpProtocol protocol);

    DataTransformer getDataTransformer();
    DataValidator getDataValidator();
}
