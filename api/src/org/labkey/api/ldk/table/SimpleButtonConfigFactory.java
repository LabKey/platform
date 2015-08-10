/*
 * Copyright (c) 2013-2015 LabKey Corporation
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
package org.labkey.api.ldk.table;

import org.labkey.api.data.Container;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UserDefinedButtonConfig;
import org.labkey.api.module.Module;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.view.DisplayElement;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.template.ClientDependency;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
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
    private Integer _insertPosition = null;
    private Class<? extends Permission> _permission = null;
    private LinkedHashSet<ClientDependency> _clientDependencies = new LinkedHashSet<>();

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

    public UserDefinedButtonConfig createBtn(final TableInfo ti)
    {
        Container c = ti.getUserSchema().getContainer();
        UserDefinedButtonConfig btn = new UserDefinedButtonConfig()
        {
            @Override
            public DisplayElement createButton(RenderContext ctx, List<DisplayElement> originalButtons)
            {
                DisplayElement result = super.createButton(ctx, originalButtons);
                result.setVisible(isVisible(ti));
                return result;
            }
        };
        btn.setText(_text);
        if (_url != null)
            btn.setUrl(_url.copy(c).getActionURL().toString());
        String onClick = getJsHandler(ti);
        if (onClick != null)
            btn.setOnClick(onClick);

        if (_insertPosition != null)
            btn.setInsertPosition(_insertPosition);

        if (_permission != null)
            btn.setPermission(_permission);

        return btn;
    }

    public void setInsertPosition(Integer insertPosition)
    {
        _insertPosition = insertPosition;
    }

    public NavTree create(TableInfo ti)
    {
        Container c = ti.getUserSchema().getContainer();
        NavTree tree = new NavTree();
        tree.setText(_text);
        if (_url != null)
            tree.setHref(_url.copy(c).getActionURL().toString());
        tree.setScript(getJsHandler(ti));

        return tree;
    }

    protected String getJsHandler(TableInfo ti)
    {
        return _jsHandler;
    }

    public boolean isAvailable(TableInfo ti)
    {
        return _owner == null || ti.getUserSchema().getContainer().getActiveModules().contains(_owner);
    }

    @Override
    public boolean isVisible(TableInfo ti)
    {
        return true;
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

    public void setClientDependencies(Collection<ClientDependency> clientDependencies)
    {
        _clientDependencies.addAll(clientDependencies);
    }

    public void setPermission(Class<? extends Permission> permission)
    {
        _permission = permission;
    }
}
