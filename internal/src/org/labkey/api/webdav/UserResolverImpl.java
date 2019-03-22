/*
 * Copyright (c) 2016-2017 LabKey Corporation
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
package org.labkey.api.webdav;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.RoleAssignment;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.roles.EditorRole;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.util.Path;
import org.labkey.api.util.Result;
import org.labkey.api.view.HttpView;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * Created by iansigmon on 6/20/16.
 */
public class UserResolverImpl extends AbstractWebdavResolver
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

    protected synchronized WebdavResource getRoot()
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

        User getCurrentUser()
        {
            return HttpView.currentContext().getUser();
        }

        @NotNull
        public Collection<String> listNames()
        {
            User user = getCurrentUser();
            if (null == user)
                return Collections.emptyList();
            return Collections.singletonList(user.getEmail());
        }

        @Override
        public Collection<? extends WebdavResource> list()
        {
            User user = getCurrentUser();
            WebdavResource res = find(user.getEmail());
            return Collections.singletonList(res);
        }

        public WebdavResource find(String child)
        {
            User user = getCurrentUser();
            if (null == user || !child.equalsIgnoreCase(user.getName()))
                return null;

            Result<File> r = UserManager.getHomeDirectory(user);
            if (!r.success() || !r.get().isDirectory())
                return null;
            File fileRoot = r.get();
            SecurityPolicy p = new SecurityPolicy
            (
                user.getEntityId(),
                User.class.getName(),
                ContainerManager.getRoot().getId(),
                Arrays.asList(new RoleAssignment(user.getEntityId(), user, RoleManager.getRole(EditorRole.class))),
                new Date()
            );

            return new FileSystemResource(this, user.getName(), fileRoot, p);
        }


        @Override
        public Set<Class<? extends Permission>> getPermissions(User user)
        {
            return Collections.emptySet();
        }
    }
}
