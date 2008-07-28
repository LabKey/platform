/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.experiment.list;

import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.*;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListItem;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.view.ActionURL;
import org.labkey.experiment.controllers.list.ListController;

import java.sql.SQLException;
import java.util.Collection;

public class ListDefinitionImpl implements ListDefinition
{
    static public ListDefinitionImpl of(ListDef def)
    {
        if (def == null)
            return null;
        return new ListDefinitionImpl(def);
    }

    boolean _new;
    ListDef _defOld;
    ListDef _def;
    Domain _domain;
    public ListDefinitionImpl(ListDef def)
    {
        _def = def;
    }

    public ListDefinitionImpl(Container container, String name)
    {
        _new = true;
        _def = new ListDef();
        _def.setContainer(container.getId());
        _def.setName(name);
        String typeURI = "urn:lsid:" + AppProps.getInstance().getDefaultLsidAuthority() + ":List" + ".Folder-" + container.getRowId() + ":" + name;
        _domain = PropertyService.get().createDomain(container, new Lsid(typeURI).toString(), name);
    }

    public int getListId()
    {
        return _def.getRowId();
    }

    public Container getContainer()
    {
        return ContainerManager.getForId(_def.getContainerId());
    }

    public Domain getDomain()
    {
        if (_domain == null)
        {
            _domain = PropertyService.get().getDomain(_def.getDomainId());
        }
        return _domain;
    }

    public String getName()
    {
        return _def.getName();
    }

    public String getKeyName()
    {
        return _def.getKeyName();
    }

    public void setKeyName(String name)
    {
        if (_def.getTitleColumn() != null && _def.getTitleColumn().equals(getKeyName()))
        {
            edit().setTitleColumn(name);
        }
        edit().setKeyName(name);
    }

    public void setDescription(String description)
    {
        edit().setDescription(description);
    }

    public KeyType getKeyType()
    {
        return KeyType.valueOf(_def.getKeyType());
    }

    public void setKeyType(KeyType type)
    {
        _def.setKeyType(type.toString());
    }

    public DiscussionSetting getDiscussionSetting()
    {
        return _def.getDiscussionSettingEnum();
    }

    public void setDiscussionSetting(DiscussionSetting discussionSetting)
    {
        _def.setDiscussionSettingEnum(discussionSetting);
    }

    public boolean getAllowDelete()
    {
        return _def.getAllowDelete();
    }

    public void setAllowDelete(boolean allowDelete)
    {
        _def.setAllowDelete(allowDelete);
    }

    public boolean getAllowUpload()
    {
        return _def.getAllowUpload();
    }

    public void setAllowUpload(boolean allowUpload)
    {
        _def.setAllowUpload(allowUpload);
    }

    public boolean getAllowExport()
    {
        return _def.getAllowExport();
    }

    public void setAllowExport(boolean allowExport)
    {
        _def.setAllowExport(allowExport);
    }

    public void save(User user) throws Exception
    {
        boolean fTransaction = false;
        try
        {
            if (!ExperimentService.get().isTransactionActive())
            {
                ExperimentService.get().beginTransaction();
                fTransaction = true;
            }
            if (_new)
            {
                _domain.save(user);
                _def.setDomainId(_domain.getTypeId());
                _def = ListManager.get().insert(user, _def);
                _new = false;
            }
            else
            {
                _def = ListManager.get().update(user, _def);
                _defOld = null;
                addAuditEvent(user, String.format("The definition of the list %s was modified", _def.getName()));
            }
            if (fTransaction)
            {
                ExperimentService.get().commitTransaction();
                fTransaction = false;
            }
        }
        finally
        {
            if (fTransaction)
            {
                ExperimentService.get().rollbackTransaction();
            }
        }
    }

    public ListItem createListItem()
    {
        return new ListItemImpl(this);
    }

    public ListItem getListItem(Object key)
    {
        // Convert key value to the proper type, since PostgreSQL 8.3 requires that key parameter types match their column types.
        Object typedKey = getKeyType().convertKey(key);

        return getListItem(new SimpleFilter("Key", typedKey));
    }

    public ListItem getListItemForEntityId(String entityId)
    {
        return getListItem(new SimpleFilter("EntityId", entityId));
    }

    private ListItem getListItem(SimpleFilter filter)
    {
        try
        {
            filter.addCondition("ListId", getListId());
            ListItm itm = Table.selectObject(getIndexTable(), Table.ALL_COLUMNS, filter, null, ListItm.class);
            if (itm == null)
            {
                return null;
            }
            return new ListItemImpl(this, itm);
        }
        catch (SQLException e)
        {
            return null;
        }
    }

    public void deleteListItems(User user, Collection keys) throws Exception
    {
        boolean fTransaction = false;
        try
        {
            if (!ExperimentService.get().isTransactionActive())
            {
                ExperimentService.get().beginTransaction();
                fTransaction = true;
            }
            for (Object key : keys)
            {
                ListItem item = getListItem(key);
                if (item != null)
                {
                    item.delete(user, getContainer());
                }
            }
            if (fTransaction)
            {
                ExperimentService.get().commitTransaction();
                fTransaction = false;
            }
        }
        finally
        {
            if (fTransaction)
            {
                ExperimentService.get().rollbackTransaction();
            }
        }
    }

    public void delete(User user) throws Exception
    {
        boolean fTransaction = false;
        try
        {
            if (!ExperimentService.get().isTransactionActive())
            {
                ExperimentService.get().beginTransaction();
                fTransaction = true;
            }
            SimpleFilter lstItemFilter = new SimpleFilter("ListId", getListId());
            ListItm[] itms = Table.select(getIndexTable(), Table.ALL_COLUMNS, lstItemFilter, null, ListItm.class);
            Table.delete(getIndexTable(), lstItemFilter);
            for (ListItm itm : itms)
            {
                if (itm.getObjectId() == null)
                    continue;
                ListItemImpl.deleteListItemContents(itm, getContainer(), user);
            }
            Table.delete(ListManager.get().getTinfoList(), getListId(), null);
            Domain domain = getDomain();
            domain.delete(user);
            if (fTransaction)
            {
                ExperimentService.get().commitTransaction();
                fTransaction = false;
            }
        }
        finally
        {
            if (fTransaction)
            {
                ExperimentService.get().rollbackTransaction();
            }
        }
    }

    private void addAuditEvent(User user, String comment) throws Exception
    {
        if (user != null)
        {
            AuditLogEvent event = new AuditLogEvent();

            event.setCreatedBy(user);
            event.setComment(comment);

            Container c = getContainer();
            event.setContainerId(c.getId());
            if (c.getProject() != null)
                event.setProjectId(c.getProject().getId());
            event.setKey1(getDomain().getTypeURI());

            event.setEventType(ListManager.LIST_AUDIT_EVENT);
            event.setIntKey1(getListId());
            event.setKey3(getName());

            AuditLogService.get().addEvent(event);
        }
    }


    public int getRowCount()
    {
        return 0;
    }

    public String getDescription()
    {
        return _def.getDescription();
    }

    public String getTitleColumn()
    {
        return _def.getTitleColumn();
    }

    public void setTitleColumn(String titleColumn)
    {
        edit().setTitleColumn(titleColumn);
    }

    public TableInfo getTable(User user, String alias)
    {
        ListTable ret = new ListTable(user, this);
        if (alias != null)
        {
            ret.setName(alias);
        }
        return ret;
    }

    public ActionURL urlShowDefinition()
    {
        return urlFor(ListController.Action.showListDefinition);
    }

    public ActionURL urlEditDefinition()
    {
        return urlFor(ListController.Action.editListDefinition);
    }

    public ActionURL urlShowData()
    {
        return urlFor(ListController.Action.grid);
    }

    public ActionURL urlUpdate(Object pk, ActionURL returnUrl)
    {
        ActionURL url = urlFor(ListController.Action.update);

        // Can be null if caller will be filling in pk (e.g., grid edit column)
        if (null != pk)
            url.addParameter("pk", pk.toString());

        url.addParameter("returnUrl", returnUrl.getLocalURIString());

        return url;
    }

    public ActionURL urlDetails(Object pk)
    {
        ActionURL url = urlFor(ListController.Action.details);
        // Can be null if caller will be filling in pk (e.g., grid edit column)

        if (null != pk)
            url.addParameter("pk", pk.toString());

        return url;
    }

    public ActionURL urlShowHistory()
    {
        return urlFor(ListController.Action.history);
    }

    public ActionURL urlFor(Enum action)
    {
        ActionURL ret = getContainer().urlFor(action);
        ret.addParameter("listId", Integer.toString(getListId()));
        return ret;
    }

    private ListDef edit()
    {
        if (_new)
        {
            return _def;
        }
        if (_defOld == null)
        {
            _defOld = _def;
            _def = _defOld.clone();
        }
        return _def;

    }

    public TableInfo getIndexTable()
    {
        switch (getKeyType())
        {
            case Integer:
            case AutoIncrementInteger:
                return ListManager.get().getTinfoIndexInteger();
            case Varchar:
                return ListManager.get().getTinfoIndexVarchar();
            default:
                throw new IllegalStateException();
        }
    }
}
