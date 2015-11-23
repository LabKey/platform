package org.labkey.api.data;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.view.ViewContext;

/**
 * Created by susanh on 11/20/15.
 */
public interface ActivityService
{
    @Nullable
    PHI getPHI(ViewContext context);
}
