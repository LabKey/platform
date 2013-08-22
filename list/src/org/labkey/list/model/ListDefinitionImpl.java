/*
 * Copyright (c) 2007-2013 LabKey Corporation
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

package org.labkey.list.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.announcements.DiscussionService;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.DomainNotFoundException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListImportProgress;
import org.labkey.api.exp.list.ListItem;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.reader.MapLoader;
import org.labkey.api.security.User;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.VirtualFile;
import org.labkey.list.client.ListEditorService;
import org.labkey.list.controllers.ListController;
import org.labkey.list.view.ListItemAttachmentParent;
import org.springframework.web.servlet.mvc.Controller;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.labkey.api.util.GUID.makeGUID;

public class ListDefinitionImpl implements ListDefinition
{
    protected static final String NAMESPACE_PREFIX = "List";

    static public ListDefinitionImpl of(ListDef def)
    {
        if (def == null)
            return null;
        return new ListDefinitionImpl(def);
    }

    private boolean _new;
    // If set to a collection of IDs, we'll attempt to use them (in succession) as the list ID on insert
    private Collection<Integer> _preferredListIds = Collections.emptyList();
    private ListDef _defOld;
    private Domain _domain;

    ListDef _def;

    public ListDefinitionImpl(ListDef def)
    {
        _def = def;
    }

    public ListDefinitionImpl(Container container, String name, KeyType keyType)
    {
        _new = true;
        _def = new ListDef();
        _def.setContainer(container.getId());
        _def.setName(name);
        _def.setEntityId(makeGUID());
        _def.setKeyType(keyType.toString());
        Lsid lsid = ListDomainKind.generateDomainURI(name, container, keyType);
        _domain = PropertyService.get().createDomain(container, lsid.toString(), name);
    }

    // For new lists only, we'll attempt to use these IDs at insert time
    public void setPreferredListIds(Collection<Integer> preferredListIds)
    {
        _preferredListIds = preferredListIds;
    }

    @Override
    public int getListId()
    {
        return _def.getListId();
    }

    public String getEntityId()
    {
        return _def.getEntityId();
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

    public void clearDomain()
    {
        _domain = null;
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
        edit().setKeyType(type.toString());
    }

    public DiscussionSetting getDiscussionSetting()
    {
        return _def.getDiscussionSettingEnum();
    }

    public void setDiscussionSetting(DiscussionSetting discussionSetting)
    {
        edit().setDiscussionSettingEnum(discussionSetting);
    }

    public boolean getAllowDelete()
    {
        return _def.getAllowDelete();
    }

    public void setAllowDelete(boolean allowDelete)
    {
        edit().setAllowDelete(allowDelete);
    }

    public boolean getAllowUpload()
    {
        return _def.getAllowUpload();
    }

    public void setAllowUpload(boolean allowUpload)
    {
        edit().setAllowUpload(allowUpload);
    }

    public boolean getAllowExport()
    {
        return _def.getAllowExport();
    }

    public void setAllowExport(boolean allowExport)
    {
        edit().setAllowExport(allowExport);
    }

    @Override
    public boolean getEntireListIndex()
    {
        return _def.getEntireListIndex();
    }

    @Override
    public void setEntireListIndex(boolean eachItemIndex)
    {
        edit().setEntireListIndex(eachItemIndex);
    }

    @Override
    public IndexSetting getEntireListIndexSetting()
    {
        return _def.getEntireListIndexSettingEnum();
    }

    @Override
    public void setEntireListIndexSetting(IndexSetting setting)
    {
        edit().setEntireListIndexSettingEnum(setting);
    }

    @Override
    public TitleSetting getEntireListTitleSetting()
    {
        return _def.getEntireListTitleSettingEnum();
    }

    @Override
    public void setEntireListTitleSetting(TitleSetting setting)
    {
        edit().setEntireListTitleSettingEnum(setting);
    }

    @Override
    public String getEntireListTitleTemplate()
    {
        return _def.getEntireListTitleTemplate();
    }

    @Override
    public void setEntireListTitleTemplate(String template)
    {
        edit().setEntireListTitleTemplate(template);
    }

    @Override
    public BodySetting getEntireListBodySetting()
    {
        return _def.getEntireListBodySettingEnum();
    }

    @Override
    public void setEntireListBodySetting(BodySetting setting)
    {
        edit().setEntireListBodySettingEnum(setting);
    }

    @Override
    public String getEntireListBodyTemplate()
    {
        return _def.getEntireListBodyTemplate();
    }

    @Override
    public void setEntireListBodyTemplate(String template)
    {
        edit().setEntireListBodyTemplate(template);
    }

    @Override
    public boolean getEachItemIndex()
    {
        return _def.getEachItemIndex();
    }

    @Override
    public void setEachItemIndex(boolean eachItemIndex)
    {
        edit().setEachItemIndex(eachItemIndex);
    }

    @Override
    public TitleSetting getEachItemTitleSetting()
    {
        return _def.getEachItemTitleSettingEnum();
    }

    @Override
    public void setEachItemTitleSetting(TitleSetting setting)
    {
        edit().setEachItemTitleSettingEnum(setting);
    }

    @Override
    public String getEachItemTitleTemplate()
    {
        return _def.getEachItemTitleTemplate();
    }

    @Override
    public void setEachItemTitleTemplate(String template)
    {
        edit().setEachItemTitleTemplate(template);
    }

    @Override
    public BodySetting getEachItemBodySetting()
    {
        return _def.getEachItemBodySettingEnum();
    }

    @Override
    public void setEachItemBodySetting(BodySetting setting)
    {
        edit().setEachItemBodySettingEnum(setting);
    }

    @Override
    public String getEachItemBodyTemplate()
    {
        return _def.getEachItemBodyTemplate();
    }

    @Override
    public void setEachItemBodyTemplate(String template)
    {
        edit().setEachItemBodyTemplate(template);
    }

    public void save(User user) throws Exception
    {
        save(user, true);
    }

    public void save(User user, boolean ensureKey) throws Exception
    {
        if (ensureKey)
        {
            assert getKeyName() != null : "Key not provided for List: " + getName();
            assert getKeyType() != null : "Invalid Key Type for List: " + getName();
        }

        try (DbScope.Transaction transaction = ExperimentService.get().ensureTransaction())
        {
            if (ensureKey)
                ensureKey();

            if (_new)
            {
                // The domain kind cannot lookup the list definition if the domain has not been saved
                ((ListDomainKind) _domain.getDomainKind()).setListDefinition(this);

                _domain.save(user);

                _def.setDomainId(_domain.getTypeId());
                _def = ListManager.get().insert(user, _def, _preferredListIds);
                _new = false;
            }
            else
            {
                _def = ListManager.get().update(user, _def);
                _defOld = null;
                ListManager.get().addAuditEvent(this, user, String.format("The definition of the list %s was modified", _def.getName()));
            }

            transaction.commit();
        }
        catch (SQLException e) //issue 12162
        {
            processSqlException(e);
            throw e;
        }
        catch (RuntimeSQLException e)
        {
            processSqlException(e.getSQLException());
            throw e;
        }
//        ListManager.get().indexList(_def);
    }

    private void ensureKey()
    {
        for (DomainProperty dp : _domain.getProperties())
        {
            if (dp.getName().equalsIgnoreCase(getKeyName()))
                return;
        }

        DomainProperty prop = _domain.addProperty();
        prop.setPropertyURI(_domain.getTypeURI() + "#" + getKeyName());
        prop.setName(getKeyName());
        prop.setType(PropertyService.get().getType(_domain.getContainer(), getKeyType().getPropertyType().getXmlName()));

        _domain.setPropertyIndex(prop, 0);
    }

    private void processSqlException(SQLException e) throws Exception
    {
        if (SqlDialect.isConstraintException(e))
        {
            //verify this is actually due to a duplicate name
            for (ListDef l : ListManager.get().getLists(getContainer()))
            {
                if (l.getName().equals(_def.getName()))
                {
                    throw new ListEditorService.ListImportException("The name '" + _def.getName() + "' is already in use.");
                }
            }

            throw Table.OptimisticConflictException.create(Table.ERROR_ROWVERSION);
        }
    }

    public ListItem createListItem()
    {
        return new ListItemImpl(this);
    }

    public ListItem getListItem(Object key, User user)
    {
        // Convert key value to the proper type, since PostgreSQL 8.3 requires that key parameter types match their column types.
        Object typedKey = getKeyType().convertKey(key);

        return getListItem(new SimpleFilter(FieldKey.fromParts(getKeyName()), typedKey), user);
    }

    public ListItem getListItemForEntityId(String entityId, User user)
    {
        return getListItem(new SimpleFilter(FieldKey.fromParts("EntityId"), entityId), user);
    }

    private ListItem getListItem(SimpleFilter filter, User user)
    {
        TableInfo tbl = getTable(user);

        if (null == tbl)
            return null;

        Map<String, Object> row = null;

        try
        {
            row = new TableSelector(tbl, filter, null).getObject(Map.class);
        }
        catch (IllegalStateException e)
        {
            /* more than one row matches */
        }

        if (row == null)
            return null;

        ListItm itm = new ListItm();

        itm.setListId(getListId());
        itm.setEntityId(row.get("EntityId").toString());
        itm.setKey(row.get(getKeyName()));

        ListItemImpl impl = new ListItemImpl(this, itm);
        for (DomainProperty prop : getDomain().getProperties())
        {
            impl.setProperty(prop, row.get(prop.getName()));
        }

        return impl;
    }

    public void delete(User user) throws SQLException, DomainNotFoundException
    {
        TableInfo table = getTable(user);
        QueryUpdateService qus = table.getUpdateService();

        if (qus != null && (qus instanceof ListQueryUpdateService))
        {
            try (DbScope.Transaction transaction = table.getSchema().getScope().ensureTransaction())
            {
                // remove related attachments, discussions, and indices
                ((ListQueryUpdateService)qus).deleteRelatedListData(user, getContainer());

                // then delete the list itself
                Table.delete(ListManager.get().getListMetadataTable(), new Object[] {getContainer(), getListId()});
                Domain domain = getDomain();
                domain.delete(user);

                transaction.commit();
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
    }


    @Override
    public int insertListItems(User user, List<ListItem> listItems) throws IOException
    {
        BatchValidationException ve = new BatchValidationException();

        List<Map<String, Object>> rows = new ArrayList<>();

        for (ListItem item : listItems)
        {
            Map<String, Object> row = new CaseInsensitiveHashMap<>();
            Map<String, ObjectProperty> propertyMap = item.getProperties();

            if (null != propertyMap)
            {
                for (String key : propertyMap.keySet())
                {
                    ObjectProperty prop = propertyMap.get(key);
                    if (null != prop)
                    {
                        row.put(prop.getName(), prop.getObjectValue());
                    }
                }
                rows.add(row);
            }
        }

        MapLoader loader = new MapLoader(rows);

        // TODO: Find out the attachment directory?
        return insertListItems(user, loader, ve, null, null, false);
    }


    @Override
    public int insertListItems(User user, DataLoader loader, @NotNull BatchValidationException errors, @Nullable VirtualFile attachmentDir, @Nullable ListImportProgress progress, boolean supportAutoIncrementKey) throws IOException
    {
        ListQueryUpdateService lqus = (ListQueryUpdateService)getTable(user).getUpdateService();
        return lqus.insertETL(loader, user, errors, attachmentDir, progress, supportAutoIncrementKey);
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

    @Override
    public Date getModified()
    {
        return _def.getModified();
    }

    @Override
    public void setModified(Date modified)
    {
        edit().setModified(modified);
    }

    @Override
    public Date getLastIndexed()
    {
        return _def.getLastIndexed();
    }

    @Override
    public void setLastIndexed(Date modified)
    {
        edit().setLastIndexed(modified);
    }

    /** NOTE consider using ListQuerySchema.getTable(), unless you have a good reason */
    public TableInfo getTable(User user)
    {
        ListTable ret = new ListTable(new ListQuerySchema(user, getContainer()), this);
        ret.afterConstruct();
        return ret;
    }

    public ActionURL urlShowDefinition()
    {
        return urlFor(ListController.EditListDefinitionAction.class);
    }

    public ActionURL urlShowData()
    {
        return urlFor(ListController.GridAction.class);
    }

    public ActionURL urlUpdate(User user, @Nullable Object pk, @Nullable URLHelper cancelUrl)
    {
        ActionURL url = QueryService.get().urlFor(user, getContainer(), QueryAction.updateQueryRow, ListQuerySchema.NAME, getName());

        // Can be null if caller will be filling in pk (e.g., grid edit column)
        if (null != pk)
            url.addParameter("pk", pk.toString());

        if (cancelUrl != null)
            url.addParameter(QueryParam.srcURL, cancelUrl.getLocalURIString());

        return url;
    }

    public ActionURL urlDetails(@Nullable Object pk)
    {
        ActionURL url = urlFor(ListController.DetailsAction.class);
        // Can be null if caller will be filling in pk (e.g., grid edit column)

        if (null != pk)
            url.addParameter("pk", pk.toString());

        return url;
    }

    public ActionURL urlShowHistory()
    {
        return urlFor(ListController.HistoryAction.class);
    }

    public ActionURL urlFor(Class<? extends Controller> actionClass)
    {
        ActionURL ret = new ActionURL(actionClass, getContainer());
        ret.addParameter("listId", getListId());
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

    @Override
    public String toString()
    {
        return getName() + ", id: " + getListId();
    }

    public int compareTo(ListDefinition l)
    {
        return getName().compareToIgnoreCase(l.getName());
    }
}
