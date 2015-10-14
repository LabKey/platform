package org.labkey.query;

import com.drew.lang.annotations.Nullable;
import org.labkey.api.security.User;

/**
 * Created by bimber on 10/11/2015.
 */
public interface EditableCustomView
{
    @Nullable
    public CustomViewImpl getEditableViewInfo(User owner, boolean session);
}
