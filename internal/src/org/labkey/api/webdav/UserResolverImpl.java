package org.labkey.api.webdav;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.Result;
import org.labkey.api.view.HttpView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Created by iansigmon on 6/20/16.
 */
public class UserResolverImpl implements WebdavResolver
{
    static UserResolverImpl _instance = new UserResolverImpl(Path.parse(UsersCollectionResource.USERS_LINK));
    final Path _rootPath;

    private UserResolverImpl(Path path)
    {
        _rootPath = path;
    }

    public static WebdavResolver get()
    {
        return _instance;
    }

    private AbstractWebdavResourceCollection _root;

    synchronized WebdavResource getRoot()
    {
        if (null == _root)
        {
            _root = new UsersCollectionResource(getRootPath(), this)
            {
                @Override
                public boolean canList(User user, boolean forRead)
                {
                    return true;
                }
            };
        }
        return _root;
    }

    @Override
    public boolean requiresLogin()
    {
        return true; //Guest accounts aren't supported for UserCollections
    }

    @Override
    public Path getRootPath()
    {
        return _rootPath;
    }

    @Nullable
    @Override
    public LookupResult lookupEx(Path fullPath)
    {
        if (fullPath == null || !fullPath.startsWith(getRootPath()))
            return null;
        Path path = getRootPath().relativize(fullPath).normalize();

        WebdavResource root = getRoot();
        if (path.size() == 0)
            return new LookupResult(this,root);

        // start at the root and work down, to avoid lots of cache misses
        WebdavResource resource = root;
        for (String name : path)
        {
            WebdavResource r = resource.find(name);
            // short circuit the descent at last web folder
            if (null == r  || r instanceof WebdavResolverImpl.UnboundResource)
                return new LookupResult(this,new WebdavResolverImpl.UnboundResource(fullPath));
            resource = r;
        }

        if (null == resource)
            resource = new WebdavResolverImpl.UnboundResource(fullPath);
        return new LookupResult(this,resource);
    }

    @Nullable
    @Override
    public WebdavResource welcome()
    {
        return lookup(Path.rootPath);
    }

    @Override
    public String toString()
    {
        return "users";
    }

    /**
     * Created by iansigmon on 6/20/16.
     */
    public class UsersCollectionResource extends AbstractWebdavResourceCollection
    {
        public static final String USERS_LINK = "/_users/";

        UsersCollectionResource(Path path, WebdavResolver resolver)
        {
            super(path, resolver);
        }

        @Override
        public boolean exists()
        {
            return true;
        }

        @Override
        public boolean isCollection()
        {
            return exists();
        }

        @Override
        public boolean canList(User user, boolean forRead)
        {
            return true;
        }

        @Override
        public boolean canCreateCollection(User user, boolean forCreate)
        {
            return false;
        }

        @Override
        public boolean canCreate(User user, boolean forCreate)
        {
            return false;
        }

        @Override
        public boolean canRename(User user, boolean forRename)
        {
            return false;
        }

        @Override
        public boolean canDelete(User user, boolean forDelete, List<String> message)
        {
            return false;
        }

        @Override
        public boolean canWrite(User user, boolean forWrite)
        {
            return false;
        }


        @NotNull
        public Collection<String> listNames()
        {
            Set<String> set = new TreeSet<>();
            set.addAll(getWebFoldersNames());
            ArrayList<String> list = new ArrayList<>(set);
            Collections.sort(list);
            return list;
        }

        public synchronized List<String> getWebFoldersNames()
        {
            File fileRoot = UserManager.getHomeDirectory(HttpView.getRootContext().getUser()).get();
            Path relPath = getRootPath().relativize(getPath());

            File file = new File(fileRoot, relPath.toString());
            List<String> children = new ArrayList<>();

            for (String item : file.list())
                children.add(item);
            return children;
        }


        public WebdavResource find(String child)
        {
            Path relPath = getRootPath().relativize(getPath()).append(child);

            Result<File> r = UserManager.getHomeDirectory(HttpView.getRootContext().getUser());
            if (!r.success())
                return new WebdavResolverImpl.UnboundResource(relPath);
            File fileRoot = r.get();

            for(String myChild:getWebFoldersNames())
            {
                if(myChild.equalsIgnoreCase(child))
                {

                    File file = new File(fileRoot, relPath.toString());
                    if(file.isDirectory())
                        return new UsersCollectionResource(getRootPath().append(relPath), getResolver());
                    else if (file.isFile() && file.exists())
                        return new UsersFileResource(this, child, file);
                    else break;
                }
            }

            return new WebdavResolverImpl.UnboundResource(relPath);
        }


        public class UsersFileResource extends FileSystemResource
        {
            protected UsersFileResource(Path item)
            {
                super(item);
            }

            protected UsersFileResource(Path folder, String name)
            {
                super(folder, name);
            }

            public UsersFileResource(WebdavResource folder, String name, File file)
            {
                super(folder.getPath(), name);
                _folder = folder;
                _name = name;
                _files = Collections.singletonList(new FileInfo(FileUtil.getAbsoluteCaseSensitiveFile(file)));
            }

            @Override
            public Set<Class<? extends Permission>> getPermissions(User user)
            {
                return Collections.emptySet();
            }

        }

        @Override
        public Set<Class<? extends Permission>> getPermissions(User user)
        {
            return Collections.emptySet();
        }
    }
}
