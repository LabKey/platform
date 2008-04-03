package org.labkey.api.wiki;

import org.labkey.api.view.ViewContext;
import org.labkey.api.view.HttpView;

import java.util.Map;

/**
 * Modules that want to provide wiki macros should implement this interface.
 * The module should call @link WikiService.Service#registerMacroProvider
 * to register the provider. Macros by this provider will be called like this
 *
 * {providerName:viewName|paramName=paramVal|paramName2=paramVal2...}
 *
 * It is up to the MacroProvider implementor to enforce security on the included view!
 *
 */
public interface MacroProvider
{
    /**
     * return an HttpView to render the current data.
     * @param name Name of the view to return. In {module:viewName} macro this would be viewName
     * @param params Map of <i>named<i> parameters to the macro supplied currently as described above
     * @param parentContext Context beind used to render the wiki
     * @return HttpView that will be included in the wiki.
     */
    public HttpView getView(String name, Map<String,String> params, ViewContext parentContext);
}
