package org.labkey.api.view;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;

public interface MenuProvider
{
    void addMenuItems(Container c, User user, NavTrees trees);
}
