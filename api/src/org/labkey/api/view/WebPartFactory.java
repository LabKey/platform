package org.labkey.api.view;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.module.FolderType;
import org.labkey.api.module.Module;
import org.labkey.api.util.ExceptionUtil;

import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

/**
 * User: brittp
 * Date: Nov 1, 2005
 * Time: 5:09:48 PM
 */
public abstract class WebPartFactory
{
    private static Logger _log = Logger.getLogger(Portal.class);

    public static final String LOCATION_RIGHT = "right";

    String name;
    String defaultLocation;
    Module module = null;
    private boolean editable;
    private boolean showCustomizeOnInsert;
    private List<String> _legacyNames = Collections.emptyList();

    public WebPartFactory(String name, String defaultLocation, boolean isEditable, boolean showCustomizeOnInsert)
    {
        if (!isEditable && showCustomizeOnInsert)
            throw new IllegalArgumentException("CustomizeOnInsert is only valid when web part is editable.");
        this.name = name;
        this.showCustomizeOnInsert = showCustomizeOnInsert;
        this.editable = isEditable;
        this.defaultLocation = null == defaultLocation ? HttpView.BODY : defaultLocation;
    }

    public WebPartFactory(String name, String defaultLocation)
    {
        this(name, defaultLocation, false, false);
    }

    public WebPartFactory(String name)
    {
        this(name, null);
    }

    public String getName()
    {
        return name;
    }

    public String getDefaultLocation()
    {
        return defaultLocation;
    }

    public final WebPartView getWebPartViewSafe(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
    {
        WebPartView view;
        try
        {
            view = getWebPartView(portalCtx, webPart);
        }
        catch(Throwable t)
        {
            int status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            String message = "An unexpected error occurred";
            view = ExceptionUtil.getErrorWebPartView(status, message, t, portalCtx.getRequest());
            view.setTitle(webPart.getName());
        }
        return view;
    }

    public abstract WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception;

    public HttpView getEditView(Portal.WebPart webPart)
    {
        return null;
    }

    public Portal.WebPart createWebPart()
    {
        Portal.WebPart part = new Portal.WebPart();
        part.setLocation(defaultLocation);
        part.setName(getName());
        return part;
    }

    protected void populateProperties(WebPartView view, Map<String, String> properties) throws IllegalAccessException, InvocationTargetException
    {
        for (Map.Entry<String, String> entry : properties.entrySet())
        {
            if (PropertyUtils.isWriteable(view, entry.getKey()))
            {
                try
                {
                    BeanUtils.setProperty(view, entry.getKey(), entry.getValue());
                }
                catch (Exception e)
                {
                    // Unfortunately, we have to catch Exception here, since BeanUtils throws RuntimeExceptions
                    // for various failures.
                    _log.error("Couldn't set property " + entry.getKey() + " to value " + entry.getValue(), e);
                }
            }
            else
                view.addObject(entry.getKey(), entry.getValue());
        }
    }

    public void addLegacyNames(String... names)
    {
        List<String> newNames = new ArrayList<String>(_legacyNames);
        newNames.addAll(Arrays.asList(names));
        _legacyNames = Collections.unmodifiableList(newNames);
    }

    public boolean isEditable()
    {
        return editable;
    }

    public boolean showCustomizeOnInsert()
    {
        return showCustomizeOnInsert;
    }

    public Module getModule()
    {
        if (module == null)
            throw new IllegalStateException("Module has not been set.");
        return module;
    }

    public void setModule(Module module)
    {
        if (this.module != null)
            throw new IllegalStateException("Module has already been set.");
        this.module = module;
    }

    public List<String> getLegacyNames()
    {
        return _legacyNames;
    }

    public boolean isAvailable(Container c, String location)
    {
        if (!location.equals(getDefaultLocation()))
        {
            return false;
        }
        if (c.getFolderType() != null)
        {
            for (Portal.WebPart webPart : c.getFolderType().getPreferredWebParts())
            {
                if (getName().equals(webPart.getName()) || getLegacyNames().contains(webPart.getName()))
                {
                    return true;
                }
            }
            for (Portal.WebPart webPart : c.getFolderType().getRequiredWebParts())
            {
                if (getName().equals(webPart.getName()) || getLegacyNames().contains(webPart.getName()))
                {
                    return true;
                }
            }
        }
        return FolderType.NONE.equals(c.getFolderType()) || c.getActiveModules().contains(getModule());
    }
}
