package org.labkey.api.laboratory;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;

import java.util.Collections;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 10/8/12
 * Time: 2:58 PM
 */
public abstract class AbstractDataProvider implements DataProvider
{
    @Override
    public String getKey()
    {
        return this.getClass().getName() + "||" +
                (getOwningModule() == null ? "" : getOwningModule().getName()) + "||" +
                getName()
                ;
    }

    public List<NavItem> getReportItems(Container c, User u)
    {
        return Collections.emptyList();
    }
}
