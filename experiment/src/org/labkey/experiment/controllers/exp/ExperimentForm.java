package org.labkey.experiment.controllers.exp;

import org.labkey.experiment.api.Experiment;
import org.labkey.experiment.api.ExperimentServiceImpl;
import org.labkey.api.data.BeanViewForm;

/**
 * User: jeckels
* Date: Dec 17, 2007
*/
public class ExperimentForm extends BeanViewForm<Experiment>
{
    public ExperimentForm()
    {
        super(Experiment.class, ExperimentServiceImpl.get().getTinfoExperiment());
    }

    public ExperimentForm(Experiment exp)
    {
        super(Experiment.class, ExperimentServiceImpl.get().getTinfoExperiment());
        setBean(exp);
    }
}
