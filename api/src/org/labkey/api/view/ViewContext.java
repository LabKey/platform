/*
 * Copyright (c) 2005-2017 Fred Hutchinson Cancer Research Center
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.view;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.HasPermission;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.ContainerContext;
import org.labkey.api.util.MemTracker;
import org.labkey.api.writer.ContainerUser;
import org.springframework.beans.PropertyValues;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.support.ResourceBundleMessageSource;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.Closeable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Holds many of the key pieces of context about an HTTP request, including the {@link HttpServletRequest} and
 * {@link HttpServletResponse} objects, the {@link User} making the request, etc.
 * User: matthewb
 * Date: Mar 20, 2005
 */
public class ViewContext implements MessageSource, ContainerContext, ContainerUser, ApplicationContextAware, HasPermission
{
    private ApplicationContext _applicationContext;
    private HttpServletRequest _request;
    private HttpServletResponse _response;
    private User _user;

    private ActionURL _url;                     // path and parameters on the URL (does not include posted values)
    private String _scopePrefix = "";
    private Container _c = null;
    private Set<Role> _contextualRoles = new HashSet<>();

    transient protected HashMap<String, Object> _map = new HashMap<>();
    PropertyValues _pvsBind = null;              // may be set by SpringActionController, representing values used to bind command object

    public ViewContext()
    {
        MemTracker.getInstance().put(this);
    }


    /**
     * Copy constructor.
     */
    public ViewContext(ViewContext copyFrom)
    {
        this();
        _request = copyFrom._request;
        _response = copyFrom._response;
        _url = copyFrom._url;
        _user = copyFrom._user;
        _scopePrefix = copyFrom._scopePrefix;
        _c = copyFrom._c;
        _applicationContext = copyFrom._applicationContext;
        _pvsBind = copyFrom.getBindPropertyValues();
        _map.putAll(copyFrom.getExtendedProperties());
    }


    public ViewContext(ViewBackgroundInfo copyFrom)
    {
        _url = copyFrom.getURL();
        _user = copyFrom.getUser();
        _c = copyFrom.getContainer();
    }


    public ViewContext(HttpServletRequest request, HttpServletResponse response, ActionURL url)
    {
        this();
        setActionURL(url);
        setRequest(request);
        setResponse(response);

        for (Object o : _request.getParameterMap().entrySet())
        {
            Map.Entry<String, String[]> entry = (Map.Entry<String, String[]>) o;
            String key = entry.getKey();
            String[] value = entry.getValue();

            if (value.length == 1)
                _map.put(key, value[0]);
            else
            {
                List list = new ArrayList<>(Arrays.asList(value));
                _map.put(key, list);
            }
        }
    }


    public static class StackResetter implements Closeable
    {
        private final ViewContext _context;
        private final int _stackSize;

        private StackResetter(ViewContext context, int stackSize)
        {
            _context = context;
            _stackSize = stackSize;
        }

        @Override
        public void close()
        {
            HttpView.resetStackSize(_stackSize);
        }

        public ViewContext getContext()
        {
            return _context;
        }
    }


    public static StackResetter pushMockViewContext(User user, Container c, ActionURL url)
    {
        int stackSize = HttpView.getStackSize();
        ViewContext context = getMockViewContext(user, c, url, true);

        return new StackResetter(context, stackSize);
    }


    // Needed by background threads that call entrypoints that require ViewContexts
    // TODO: Well-behaved interfaces should not take ViewContexts -- clean up query, et al to remove ViewContext params
    @Deprecated
    public static ViewContext getMockViewContext(User user, Container c, ActionURL url, boolean pushViewContext)
    {
        ViewContext context = new ViewContext();
        context.setUser(user);
        context.setContainer(c);
        context.setActionURL(url);

        if (null != url)
            context.setBindPropertyValues(url.getPropertyValues());

        HttpServletRequest request = ViewServlet.mockRequest("GET", url, user, null, null);
        context.setRequest(request);

        // Major hack -- QueryView needs the context pushed onto the ViewContext stack in thread local 
        if (pushViewContext)
            HttpView.initForRequest(context, request, null);

        return context;
    }


    // ===========================================
    // Last vestiges of ViewContext implementing BoundMap/Map below.  TODO: Remove these methods

    public Map getExtendedProperties()
    {
        return _map;
    }

    public Object get(Object key)
    {
        return _map.get(key);
    }

    public Object put(String key, Object value)
    {
        return _map.put(key, value);
    }

    public Set<Map.Entry<String, Object>> entrySet()
    {
        return _map.entrySet();
    }

    // ===========================================

    public HttpServletRequest getRequest()
    {
        return _request;
    }


    public void setRequest(HttpServletRequest request)
    {
        _request = request;
    }


    public HttpSession getSession()
    {
        return getRequest().getSession(true);
    }


    public String getContextPath()
    {
        return null == _request ? AppProps.getInstance().getContextPath() : _request.getContextPath();
    }


    // Allows actions to override standard container permission checks; use with caution.
    public void addContextualRole(Class<? extends Role> role)
    {
        _contextualRoles.add(RoleManager.getRole(role));
    }


    public boolean hasPermission(String logMsg, @NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        return null != user && getContainer().hasPermission(logMsg, user, perm, _contextualRoles);
    }


    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        return null != user && getContainer().hasPermission(user, perm, _contextualRoles);
    }


    public boolean hasPermission(String logMsg, Class<? extends Permission> perm) throws NotFoundException
    {
        User user = getUser();
        return null != user && getContainer().hasPermission(logMsg, user, perm, _contextualRoles);
    }


    public boolean hasPermission(Class<? extends Permission> perm) throws NotFoundException
    {
        User user = getUser();
        return null != user && getContainer().hasPermission(user, perm, _contextualRoles);
    }


    public HttpServletResponse getResponse()
    {
        return _response;
    }


    public void setResponse(HttpServletResponse response)
    {
        _response = response;
    }

    
    public ActionURL getActionURL()
    {
        return _url;
    }


    public ActionURL cloneActionURL()
    {
        return _url.clone();
    }


    public void setActionURL(ActionURL url)
    {
        _url = url;
    }

    public String getScopePrefix()
    {
        return _scopePrefix;
    }


    public User getUser()
    {
        if (_user == null)
            _user = null == _request ? null : (User) _request.getUserPrincipal();
        return _user;
    }


    public void setUser(User user)
    {
        _user = user;
    }

    public Container getContainerNoTab()
    {
        // Return parent container if container is a Container Tab
        Container container = getContainer();
        if (null != container && container.isContainerTab())
            container = container.getParent();
        return container;
    }

    public Container getContainer()
    {
        if (null == _c)
        {
            Container c = null;
            ActionURL url = getActionURL();
            if (null != url)
            {
                String path = url.getExtraPath();
                c = ContainerManager.getForPath(path);
                if (c == null)
                    c = ContainerManager.getForId(StringUtils.strip(path, "/"));
            }
            _c = c;
        }
        return _c;
    }


    /** ContainerContext */
    public Container getContainer(Map context)
    {
        return getContainer();
    }


    public void setContainer(Container c)
    {
        _c = c;
    }


    // Always returns a list, even for singleton.  Use for multiple values.
    // TODO: Return empty list instead of null
    public List<String> getList(Object key)
    {
        Object values = _map.get(key);

        if (values == null || List.class.isAssignableFrom(values.getClass()))
            return (List<String>) values;

        if (values.getClass().isArray())
            return Arrays.asList((String[]) values);

        return Arrays.asList((String) values);
    }


    //
    //  Error formatting (SPRING) should 
    //


    static final ResourceBundleMessageSource _defaultMessageSource = new ResourceBundleMessageSource();
    static
    {
        _defaultMessageSource.setBasenames("messages.Validation", "messages.Global");
        _defaultMessageSource.setBundleClassLoader(ViewContext.class.getClassLoader());
    }
    List<String> _messageBundles = new ArrayList<>();
    ResourceBundleMessageSource _messageSource = null;

    public void pushMessageBundle(String path)
    {
        _messageBundles.add(0,path);
        _messageSource = null;
    }


    public MessageSource getMessageSource()
    {
        if (_messageSource == null)
        {
            if (_messageBundles.size() == 0)
                _messageSource = _defaultMessageSource;
            else
            {
                _messageSource = new ResourceBundleMessageSource();
                _messageSource.setParentMessageSource(_defaultMessageSource);
                _messageSource.setBasenames(_messageBundles.toArray(new String[_messageBundles.size()]));
            }
        }
        return _messageSource;
    }

    public String getMessage(String code, Object[] args, String defaultMessage, Locale locale)
    {
        return getMessageSource().getMessage(code, args, defaultMessage, locale);
    }

    public String getMessage(String code, Object[] args, Locale locale) throws NoSuchMessageException
    {
        return getMessageSource().getMessage(code, args, locale);
    }

    public String getMessage(MessageSourceResolvable resolvable, Locale locale) throws NoSuchMessageException
    {
        return getMessageSource().getMessage(resolvable, locale);
    }

    public String getMessage(MessageSourceResolvable resolvable) throws NoSuchMessageException
    {                    
        return getMessageSource().getMessage(resolvable, Locale.getDefault());
    }

    public boolean isAdminMode()
    {
        HttpSession s = getSession();
        if (null == s)
            return false;
        Boolean mode = (Boolean) getSession().getAttribute("adminMode");
        return null == mode ? false : mode.booleanValue();
    }

    public boolean isShowFolders()
    {
        if (isAdminMode())
            return true;

        LookAndFeelProperties laf = LookAndFeelProperties.getInstance(getContainer());
        switch (laf.getFolderDisplayMode())
        {
            case ALWAYS:
                return true;
            case ADMIN:
                return isAdminMode() || getUser().hasRootAdminPermission();
            default:
                return true;
        }
    }

    public ApplicationContext getApplicationContext()
    {
        return _applicationContext;
    }

    public void setApplicationContext(ApplicationContext applicationContext)
    {
        _applicationContext = applicationContext;
    }

    /* return PropertyValues object used to bind the current command object */
    public PropertyValues getBindPropertyValues()
    {
        if (null != _pvsBind)
            return _pvsBind;
        return HttpView.getBindPropertyValues();
    }

    public void setBindPropertyValues(PropertyValues pvs)
    {
        _pvsBind = pvs;
    }
}
