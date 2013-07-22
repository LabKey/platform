package org.labkey.api.ehr.demographics;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * User: bimber
 * Date: 7/9/13
 * Time: 9:24 PM
 */
public interface DemographicsProvider
{
    public String getName();

    public boolean isAvailable(Container c);

    public Map<String, Map<String, Object>> getProperties(Container c, User u, Collection<String> ids);
}
