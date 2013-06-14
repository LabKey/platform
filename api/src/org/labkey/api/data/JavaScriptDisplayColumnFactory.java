package org.labkey.api.data;

import org.apache.commons.collections15.MultiMap;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * User: adam
 * Date: 6/12/13
 * Time: 10:26 PM
 */
public class JavaScriptDisplayColumnFactory implements DisplayColumnFactory
{
    private final @Nullable Collection<String> _dependencies;
    private final @Nullable String _javaScriptEvents;

    public JavaScriptDisplayColumnFactory(MultiMap<String, String> properties)
    {
        _dependencies = properties.get("dependency");
        _javaScriptEvents = StringUtils.join(properties.get("javaScriptEvents"), " ");
    }

    @Override
    public DisplayColumn createRenderer(ColumnInfo colInfo)
    {
        return new JavaScriptDisplayColumn(colInfo, _dependencies, _javaScriptEvents);
    }
}
