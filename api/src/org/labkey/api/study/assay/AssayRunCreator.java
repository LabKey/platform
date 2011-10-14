package org.labkey.api.study.assay;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.qc.DataTransformer;
import org.labkey.api.qc.DataValidator;
import org.labkey.api.query.ValidationException;

/**
 * User: jeckels
 * Date: Oct 12, 2011
 */
public interface AssayRunCreator<ProviderType extends AssayProvider>
{
    /**
     * @return the batch to which the run has been assigned
     */
    public ExpExperiment saveExperimentRun(AssayRunUploadContext context, @Nullable ExpExperiment batch, ExpRun run)
        throws ExperimentException, ValidationException;

    DataValidator getDataValidator();
}
