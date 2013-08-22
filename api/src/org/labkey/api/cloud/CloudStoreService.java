package org.labkey.api.cloud;

import org.labkey.api.data.Container;
import org.labkey.api.util.Pair;

import java.util.Collection;

/**
 * User: kevink
 * Date: 8/17/13
 */
public interface CloudStoreService
{
    String CLOUD_NAME = "@cloud";

    /**
     * Returns a list of blob store provider (id, name) pairs.
     * @return
     */
    Iterable<Pair<String, String>> providers();

    /**
     * Returns a list of configured store names in the container.
     * @return
     */
    Collection<String> getCloudStores(Container container);
}
