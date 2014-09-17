package org.labkey.api.sequenceanalysis;

import org.labkey.api.data.Container;
import org.labkey.api.ldk.table.ButtonConfigFactory;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;

import java.io.File;
import java.util.List;

/**
 * This interface describes an action that acts upon sequence data.  If registed, this will appear as an option in the
 * outputfiles dataregion.  This interface is fairly minimal,
 * Created by bimber on 8/25/2014.
 */
public interface SequenceFileHandler
{
    public boolean canProcess(File f);

    public ButtonConfigFactory getButtonConfig();

    public ActionURL getSuccessURL(Container c, User u, List<Integer> outputFileIds);
}
