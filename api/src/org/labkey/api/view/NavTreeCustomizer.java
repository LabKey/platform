package org.labkey.api.view;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;

import java.util.List;

public interface NavTreeCustomizer
{
    List<NavTree> getNavTrees(Container container, User user);
}
