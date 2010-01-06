package org.labkey.api.exp.query;

import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpProtocol;

/**
 * Base table type for usages of data or materials in runs 
 * User: jeckels
 * Date: Jan 5, 2010
 */
public interface ExpInputTable<C extends Enum> extends ExpTable<C>
{
    void setRun(ExpRun run, ExpProtocol.ApplicationType type);
}
