package org.labkey.api.data;

import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * User: adam
 * Date: 6/12/13
 * Time: 10:26 PM
 */
public class JavaScriptDisplayColumnFactory implements DisplayColumnFactory
{
    private final @Nullable String _javaScriptFile;
    private final @Nullable String _javaScriptEvents;

    public JavaScriptDisplayColumnFactory(Map<String, String> properties)
    {
        _javaScriptFile = properties.get("javaScriptFile");
        _javaScriptEvents = properties.get("javaScriptEvents");
    }

    @Override
    public DisplayColumn createRenderer(ColumnInfo colInfo)
    {
        return new JavaScriptDisplayColumn(colInfo, _javaScriptFile, _javaScriptEvents);
    }
}
