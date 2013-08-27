package org.labkey.api.cloud;

import org.labkey.api.data.Container;
import org.labkey.api.util.Pair;

import java.util.Collection;
import java.util.Set;

/**
 * User: kevink
 * Date: 8/17/13
 */
public interface CloudStoreService
{
    String CLOUD_NAME = "@cloud";

    /**
     * Returns a list of blob store provider (id, name) pairs.
     */
    Iterable<Pair<String, String>> providers();

    /**
     * Returns true if the Cloud module is enabled in the container.
     */
    boolean isEnabled(Container container);

    /**
     * Returns true if store is enabled at the site level.
     */
    boolean isEnabled(String storeName);

    /**
     * Returns true if store is enabled within the container.
     */
    boolean isEnabled(String storeName, Container container);

    /**
     * Returns a list of all store names.
     */
    public Collection<String> getCloudStores();

    /**
     * Returns a list of enabled store names in the container.
     */
    Collection<String> getEnabledCloudStores(Container container);

    /**
     * Set the enabled stores within the container -- other stores not included will be disabled.
     */
    void setEnabledCloudStores(Container c, Set<String> enabledCloudStores);

}
