package org.labkey.api.view.template;

import java.util.Set;

/**
 * Created by adam on 3/24/2016.
 */
public class ClientDependencies
{
    private final Set<ClientDependency> _dependencies;

    public ClientDependencies(Set<ClientDependency> dependencies)
    {
        _dependencies = dependencies;
    }

    public void add(ClientDependency dependency)
    {
        _dependencies.add(dependency);
    }

    public void add(String path)
    {
        add(ClientDependency.fromPath(path));
    }
}
