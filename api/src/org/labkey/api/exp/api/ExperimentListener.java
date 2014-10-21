package org.labkey.api.exp.api;

import org.labkey.api.security.User;

/**
 * User: vsharma
 * Date: 8/23/2014
 * Time: 4:06 PM
 */
public interface ExperimentListener
{
    /** Called before deleting a row from exp.experiment */
    void beforeExperimentDeleted(ExpExperiment experiment, User user);
}
