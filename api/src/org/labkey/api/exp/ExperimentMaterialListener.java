package org.labkey.api.exp;

import org.labkey.api.exp.api.ExpMaterial;

import java.util.List;

/**
 * User: jeckels
 * Date: 6/11/14
 */
public interface ExperimentMaterialListener
{
    /** Invoked immediately prior to the exp.material row being deleted */
    public void beforeDelete(List<? extends ExpMaterial> materials);
}
