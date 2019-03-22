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
package org.labkey.api.exp;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.settings.AppProps;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * User: migra
 * Date: Aug 17, 2005
 * Time: 2:37:30 PM
 */
public class LsidManager
{
    private static final LsidManager INSTANCE = new LsidManager();

    private final Map<String, Map<String, LsidHandler>> _authorityMap = new ConcurrentHashMap<>();
    private final List<LsidHandlerFinder> _lsidHandlerFinders = new CopyOnWriteArrayList<>();

    private LsidManager()
    {
        // Register a standard LsidHandlerFinder that resolves against LsidHandlers registered by Java modules via registerHandler()
        registerHandlerFinder(new AssayLsidHandlerFinder());
    }

    public static LsidManager get()
    {
        return INSTANCE;
    }

    public interface LsidHandler
    {
        Identifiable getObject(Lsid lsid);

        @Nullable
        ActionURL getDisplayURL(Lsid lsid);

        Container getContainer(Lsid lsid);

        boolean hasPermission(Lsid lsid, @NotNull User user, @NotNull Class<? extends Permission> perm);
    }

    public abstract static class ExpObjectLsidHandler implements LsidHandler
    {
        public abstract ExpObject getObject(Lsid lsid);

        public Container getContainer(Lsid lsid)
        {
            ExpObject run = getObject(lsid);
            return run == null ? null : run.getContainer();
        }

        public boolean hasPermission(Lsid lsid, @NotNull User user, @NotNull Class<? extends Permission> perm)
        {
            Container c = getContainer(lsid);
            return c != null && c.hasPermission(user, perm);
        }
    }

    public static class ExpRunLsidHandler extends ExpObjectLsidHandler
    {
        public ExpRun getObject(Lsid lsid)
        {
            return ExperimentService.get().getExpRun(lsid.toString());
        }

        @Nullable
        public ActionURL getDisplayURL(Lsid lsid)
        {
            ExpRun run = getObject(lsid);
            if (run == null)
                return null;
            ExpProtocol protocol = run.getProtocol();
            if (protocol == null)
                return null;
            return getDisplayURL(run.getContainer(), protocol, run);
        }

        protected ActionURL getDisplayURL(Container c, ExpProtocol protocol, ExpRun run)
        {
            return PageFlowUtil.urlProvider(AssayUrls.class).getAssayResultsURL(c, protocol, run.getRowId());
        }
    }

    public void registerHandlerFinder(LsidHandlerFinder finder)
    {
        _lsidHandlerFinders.add(finder);
    }

    public void registerHandler(String prefix, LsidHandler handler, String authority)
    {
        Map<String, LsidHandler> handlerMap = _authorityMap.computeIfAbsent(authority, k -> new HashMap<>());

        handlerMap.put(prefix, handler);
    }

    public void registerHandler(String prefix, LsidHandler handler)
    {
        registerHandler(prefix, handler, getDefaultAuthority());
    }

    public ActionURL getDisplayURL(String lsid)
    {
        return getDisplayURL(new Lsid(lsid));
    }

    public Identifiable getObject(String identifier)
    {
        return getObject(new Lsid(identifier));
    }

    public Container getContainer(Lsid lsid)
    {
        LsidHandler handler = findHandler(lsid);
        if (null != handler)
            return handler.getContainer(lsid);
        return null;
    }

    public Container getContainer(String lsid)
    {
        return getContainer(new Lsid(lsid));
    }

    public boolean hasPermission(Lsid lsid, User user, Class<? extends ReadPermission> perm)
    {
        LsidHandler handler = findHandler(lsid);
        return null != handler && handler.hasPermission(lsid, user, perm);
    }

    public boolean hasPermission(String lsid, User user, Class<? extends ReadPermission> perm)
    {
        return hasPermission(new Lsid(lsid), user, perm);
    }

    private String getDefaultAuthority()
    {
        return AppProps.getInstance().getDefaultLsidAuthority();
    }

    // This LsidHandlerFinder resolves against LsidHandlers registered by Java modules via registerHandler()
    private class AssayLsidHandlerFinder implements LsidHandlerFinder
    {
        @Nullable
        @Override
        public LsidHandler findHandler(String authority, String namespacePrefix)
        {
            Map<String, LsidHandler> handlerMap = _authorityMap.get(authority);

            //Try the default authority for this server if not found
            if (null == handlerMap)
                handlerMap = _authorityMap.get(AppProps.getInstance().getDefaultLsidAuthority());

            if (null == handlerMap)
                return null;

            return handlerMap.get(namespacePrefix);
        }
    }

    private LsidHandler findHandler(Lsid lsid)
    {
        String authority = lsid.getAuthority();
        String namespacePrefix = lsid.getNamespacePrefix();

        // ConcurrentHashMap doesn't support null keys, so do our own check
        if (authority == null || namespacePrefix == null)
        {
            return null;
        }

        // This mechanism allows LsidHandlers to come and go during a server session, e.g., as file-based assay definitions change
        for (LsidHandlerFinder finder : _lsidHandlerFinders)
        {
            LsidHandler handler = finder.findHandler(authority, namespacePrefix);

            if (null != handler)
                return handler;
        }

        return null;
    }

    public interface LsidHandlerFinder
    {
        @Nullable LsidHandler findHandler(String authority, String namespacePrefix);
    }

    public Identifiable getObject(Lsid lsid)
    {
        LsidHandler handler = findHandler(lsid);
        if (null != handler)
            return handler.getObject(lsid);
        else
            return ExperimentService.get().getObject(lsid);
    }

    public ActionURL getDisplayURL(Lsid lsid)
    {
        LsidHandler handler = findHandler(lsid);
        if (null != handler)
            return handler.getDisplayURL(lsid);

        LsidType type = ExperimentService.get().findType(lsid);
        if (null == type)
            return null;

        return type.getDisplayURL(lsid);
    }
}
