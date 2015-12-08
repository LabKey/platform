package org.labkey.api.data;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.view.ViewContext;

import java.util.Collection;

/**
 * Created by susanh on 11/20/15.
 */
public interface ActivityService
{
    @Nullable
    PHI getPHI(ViewContext context);

    Collection<? extends ActivityRole> getActivityRoles();

    @Nullable
    ActivityRole getActivityRoleFromName(@Nullable String name);
}
