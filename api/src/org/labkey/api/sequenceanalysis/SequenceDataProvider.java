package org.labkey.api.sequenceanalysis;

import org.labkey.api.data.Container;
import org.labkey.api.laboratory.DataProvider;
import org.labkey.api.ldk.NavItem;
import org.labkey.api.security.User;

import java.util.List;

/**
 * Created by bimber on 1/4/2015.
 */
public interface SequenceDataProvider extends DataProvider
{
    public List<NavItem> getSequenceNavItems(Container c, User u, SequenceNavItemCategory category);

    public enum SequenceNavItemCategory
    {
        data(),
        references(),
        misc(),
        summary()
    }
}
