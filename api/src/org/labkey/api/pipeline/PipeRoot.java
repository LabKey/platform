package org.labkey.api.pipeline;

import org.labkey.api.data.Container;
import org.labkey.api.security.ACL;
import org.labkey.api.security.User;

import javax.servlet.ServletException;
import java.io.File;
import java.net.URI;

/**
 * User: Nick
 * Date: Jul 7, 2007
 * Time: 8:09:14 PM
 */
public interface PipeRoot
{
    Container getContainer();

    URI getUri();

    File getRootPath();

    File resolvePath(String path);

    String relativePath(File file);

    URI getUri(Container container);

    String getStartingPath(Container container, User user);

    void rememberStartingPath(Container container, User user, String path);

    boolean isUnderRoot(File file);

    boolean isUnderRoot(URI uri);

    boolean hasPermission(Container container, User user, int perm);

    void requiresPermission(Container container, User user, int perm) throws ServletException;

    File ensureSystemDirectory();

    String getEntityId();

    ACL getACL();
}
