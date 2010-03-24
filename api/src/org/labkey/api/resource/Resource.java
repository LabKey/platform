package org.labkey.api.resource;

import org.labkey.api.util.Path;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

/**
 * User: kevink
 * Date: Mar 12, 2010 1:20:56 PM
 */
public interface Resource
{
    Path getPath();

    String getName();

    boolean exists();

    // should really be 'isResource()'
    boolean isCollection();

    Resource find(String name);

    boolean isFile();

    Collection<String> listNames();

    Collection<? extends Resource> list();

    Resource parent();

    /**
     * A version stamp for the resource content.  If the content changes, the version number should
     * change.  The version stamp is most likely not a monotonically increasing value so users of
     * this API should test equality of version stamps.
     * For example, the default implementation may just return the lastModified time stamp.
     *
     * @return A version stamp for the resource content.
     */
    long getVersionStamp();

    long getLastModified();

    InputStream getInputStream() throws IOException;
}
