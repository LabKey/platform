package org.labkey.api.ldk.table;

import org.labkey.api.data.ButtonConfig;
import org.labkey.api.data.Container;
import org.labkey.api.data.RenderContext;
import org.labkey.api.security.User;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.template.ClientDependency;

import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 5/5/13
 * Time: 9:23 AM
 */
public interface ButtonConfigFactory
{
    public NavTree create(Container c, User u);

    public boolean isAvailable(Container c, User u);

    public Set<ClientDependency> getClientDependencies(Container c, User u);
}
