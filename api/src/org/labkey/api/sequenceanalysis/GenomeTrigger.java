package org.labkey.api.sequenceanalysis;

import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;

/**
 * Created by bimber on 8/21/2014.
 */
public interface GenomeTrigger
{
    public String getName();

    public void onCreate(Container c, User u, Logger log, int genomeId);

    public void onRecreate(Container c, User u, Logger log, int genomeId);

    public void onDelete(Container c, User u, Logger log, int genomeId);
}
