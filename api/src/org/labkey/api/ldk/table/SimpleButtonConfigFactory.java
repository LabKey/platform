package org.labkey.api.ldk.table;

import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.security.User;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.template.ClientDependency;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 5/5/13
 * Time: 11:49 AM
 */
public class SimpleButtonConfigFactory implements ButtonConfigFactory
{
    private Module _owner;
    private String _text;
    private DetailsURL _url = null;
    private String _jsHandler = null;
    private LinkedHashSet<ClientDependency> _clientDependencies = new LinkedHashSet<ClientDependency>();

    public SimpleButtonConfigFactory(Module owner, String text, DetailsURL url)
    {
        _owner = owner;
        _text = text;
        _url = url;
    }

    public SimpleButtonConfigFactory(Module owner, String text, String handler)
    {
        _owner = owner;
        _text = text;
        _jsHandler = handler;
    }

    public SimpleButtonConfigFactory(Module owner, String text, String handler, LinkedHashSet<ClientDependency> clientDependencies)
    {
        _owner = owner;
        _text = text;
        _jsHandler = handler;
        _clientDependencies = clientDependencies;
    }

    public NavTree create(Container c, User u)
    {
        NavTree tree = new NavTree();
        tree.setText(_text);
        tree.setHref(_url.copy(c).getActionURL().toString());
        tree.setScript(_jsHandler);

        return tree;
    }

    public boolean isAvailable(Container c, User u)
    {
        return c.getActiveModules().contains(_owner);
    }

    public Set<ClientDependency> getClientDependencies(Container c, User u)
    {
        return _clientDependencies;
    }

    public void setClientDependencies(ClientDependency... clientDependencies)
    {
        for (ClientDependency cd : clientDependencies)
            _clientDependencies.add(cd);
    }
}
