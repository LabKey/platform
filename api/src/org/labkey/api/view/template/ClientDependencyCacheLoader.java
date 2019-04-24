package org.labkey.api.view.template;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.collections.ConcurrentHashSet;
import org.labkey.api.files.FileSystemDirectoryListener;
import org.labkey.api.files.FileSystemWatchers;
import org.labkey.api.files.SupportsFileSystemWatcher;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.resource.Resource;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Path;
import org.labkey.api.webdav.WebdavService;
import org.labkey.clientLibrary.xml.ModeTypeEnum;

import java.util.Set;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

public class ClientDependencyCacheLoader implements CacheLoader<String, ClientDependency>
{
    private static final Logger LOG = Logger.getLogger(ClientDependencyCacheLoader.class);
    private static final Set<String> EXISTING_LISTENERS = new ConcurrentHashSet<>();

    private final Cache<String, ClientDependency> _cache;
    private final Path _path;
    private final ModeTypeEnum.Enum _mode;

    public ClientDependencyCacheLoader(@NotNull Cache<String, ClientDependency> cache, @NotNull Path path, @NotNull ModeTypeEnum.Enum mode)
    {
        _cache = cache;
        _path = path;
        _mode = mode;
    }

    @Override
    public @Nullable ClientDependency load(@NotNull String key, @Nullable Object argument)
    {
        ClientDependency.TYPE primaryType = ClientDependency.TYPE.fromPath(_path);

        if (primaryType == null)
        {
            LOG.warn("Client dependency type not recognized: " + _path);
            return null;
        }

        ClientDependency cr;

        if (ClientDependency.TYPE.context == primaryType)
        {
            String moduleName = FileUtil.getBaseName(_path.getName());
            Module m = ModuleLoader.getInstance().getModule(moduleName);

            if (m == null)
            {
                ClientDependency.logError("Module \"" + moduleName + "\" not found, skipping script file \"" + _path + "\".");
                return null;
            }

            cr = new ContextClientDependency(m, _mode);
        }
        else
        {
            Resource r = WebdavService.get().getRootResolver().lookup(_path);
            //TODO: can we connect this resource back to a module, and load that module's context by default?

            if (r == null || !r.exists())
            {
                // Allows you to run in dev mode without having the concatenated scripts built
                if (!AppProps.getInstance().isDevMode() || !_mode.equals(ModeTypeEnum.PRODUCTION))
                {
                    ClientDependency.logError("ClientDependency \"" + _path + "\" not found, skipping.");
                    return null;
                }
            }

            if (ClientDependency.TYPE.lib == primaryType)
            {
                cr = new LibClientDependency(_path, _mode, r);

                if (null != r)
                    ensureListener(key, r);
            }
            else
            {
                cr = new FilePathClientDependency(_path, _mode, primaryType);
            }
        }

        cr.init();

        return cr;
    }

    // We're registering listeners only on lib.xml files to invalidate the cache when they change. There aren't many lib files (< 100),
    // so we don't have to be particularly clever... just construct and register one listener per lib file + mode combo.
    private void ensureListener(@NotNull String key, @NotNull Resource r)
    {
        if (!EXISTING_LISTENERS.add(key))
            return;

        String name = r.getName();
        Resource parent = r.parent();

        if (parent instanceof SupportsFileSystemWatcher)
        {
            //noinspection unchecked
            ((SupportsFileSystemWatcher) parent).registerListener(FileSystemWatchers.get(), new FileSystemDirectoryListener()
            {
                @Override
                public void entryCreated(java.nio.file.Path directory, java.nio.file.Path entry)
                {
                    process(entry);
                }

                @Override
                public void entryDeleted(java.nio.file.Path directory, java.nio.file.Path entry)
                {
                    process(entry);
                }

                @Override
                public void entryModified(java.nio.file.Path directory, java.nio.file.Path entry)
                {
                    process(entry);
                }

                private void process(java.nio.file.Path entry)
                {
                    if (entry.toString().equals(name))
                        _cache.remove(key);
                }

                @Override
                public void overflow()
                {
                }
            }, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
        }
    }
}
