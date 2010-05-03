/*
 * Copyright (c) 2010 LabKey Corporation
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
    /** Get the resolver that found this Resource. */
    Resolver getResolver();

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

    /**
     * The String returned can be used as a cache key in TTLCaches and,
     * unlike getPath(), will be unique across all resolvers.
     */
    String toString();
}
