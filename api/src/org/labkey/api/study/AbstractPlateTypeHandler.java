package org.labkey.api.study;

import org.labkey.api.data.Container;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Jun 13, 2012
 */
public abstract class AbstractPlateTypeHandler implements PlateTypeHandler
{
    @Override
    public void validate(Container container, User user, PlateTemplate template) throws ValidationException
    {
    }

    @Override
    public Map<String, List<String>> getDefaultGroupsForTypes()
    {
        return Collections.emptyMap();
    }

    @Override
    public boolean showEditorWarningPanel()
    {
        return true;
    }
}
