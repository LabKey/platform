package org.labkey.issue;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.security.User;
import org.labkey.issue.model.CustomColumn;

import java.util.Collection;
import java.util.Map;

/**
 * Created by klum on 4/19/2016.
 */
public interface CustomColumnConfiguration
{
    CustomColumn getCustomColumn(String name);

    // only used for experimental issues list
    Map<String, DomainProperty> getPropertyMap();
    Collection<DomainProperty> getCustomProperties();

    @Deprecated
    Collection<CustomColumn> getCustomColumns();
    Collection<CustomColumn> getCustomColumns(User user);

    @Deprecated
    boolean shouldDisplay(String name);
    boolean shouldDisplay(User user, String name);
    boolean hasPickList(String name);

    @Nullable
    String getCaption(String name);

    // TODO: If we need this, then pre-compute it
    Map<String, String> getColumnCaptions();
}
