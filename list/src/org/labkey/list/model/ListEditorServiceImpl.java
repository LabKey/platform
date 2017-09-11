/*
 * Copyright (c) 2010-2017 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.DomainDescriptor;
import org.labkey.api.exp.DomainNotFoundException;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListDefinition.BodySetting;
import org.labkey.api.exp.list.ListDefinition.DiscussionSetting;
import org.labkey.api.exp.list.ListDefinition.IndexSetting;
import org.labkey.api.exp.list.ListDefinition.KeyType;
import org.labkey.api.exp.list.ListDefinition.TitleSetting;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainEditorServiceBase;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.DomainUtil;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.lists.permissions.DesignListPermission;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewContext;
import org.labkey.list.client.GWTList;
import org.labkey.list.client.ListEditorService;
import org.labkey.list.view.ListItemAttachmentParent;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: matthewb
 * Date: Mar 23, 2010
 * Time: 1:15:41 PM
 */
public class ListEditorServiceImpl extends DomainEditorServiceBase implements ListEditorService
{
    public ListEditorServiceImpl(ViewContext context)
    {
        super(context);
    }

    public void deleteList(GWTList list)
    {
        if (!getContainer().hasPermission(getUser(), AdminPermission.class))
            throw new UnauthorizedException();
        if (list.getListId() == 0)
            throw new IllegalArgumentException();

        try
        {
            ListDefinition definition = ListService.get().getList(getContainer(), list.getName());
            definition.delete(getUser());
            //NOTE: should we look for possible optimistic concurrency (ie. double-deleting)?
        }
        catch (Exception x)
        {
            throw new RuntimeException(x);
        }
    }

    public GWTList createList(GWTList list) throws ListImportException
    {
        if (!getContainer().hasPermission(getUser(), DesignListPermission.class))
            throw new UnauthorizedException();
        if (list.getListId() != 0)
            throw new IllegalArgumentException();
        if (list.getName().length() > ListEditorService.MAX_NAME_LENGTH)
            throw new ListImportException("List name cannot be longer than " + ListEditorService.MAX_NAME_LENGTH + " characters");

        ListDefinition definition;

        try
        {
            definition = ListService.get().createList(getContainer(), list.getName(), KeyType.valueOf(list.getKeyPropertyType()));
            update(definition, list);
            definition.save(getUser(), false);
        }
        //NOTE: handling of constraint exceptions / duplicate names should be handled in ListDefinitionImpl, which will throw a ListImportException instead of SQLException
        //issue 12162
        catch (SQLException x)
        {
            throw new RuntimeException(x);
        }
        catch (ListImportException x)
        {
            //issue 12162.  known exceptions should throw ListImportException, which will be handled more appropriately downstream
            throw x;
        }
        catch (RuntimeException x)
        {
            throw x;
        }
        catch (Exception x)
        {
            throw new RuntimeException(x);
        }

        return verifyList(definition);
    }

    //
    // Ensure the list was created correctly by trying to get the table.
    // If we can't get the table then something went wrong and we need to delete the
    // list.  Otherwise we'll have a list that can't be edited, updated, or deleted.
    //
    private GWTList verifyList(ListDefinition def) throws ListImportException
    {
        try
        {
            // verify underlying table was created correctly (could throw illegal state exception)
            if (null == def.getDomain())
                throw new IllegalStateException("Domain not found.");
            ListTable table = new ListTable(new ListQuerySchema(getUser(), getContainer()), def, def.getDomain());
        }
        catch (IllegalStateException e)
        {
            // cleanup the list
            try
            {
                def.delete(getUser());
            }
            catch (DomainNotFoundException ignore) {}

            // rethrow so the client knows that something went wrong
            throw new ListImportException(e.getMessage());
        }

        return getList(def.getListId());
    }


    public List<String> getListNames()
    {
        List<String> ret = new ArrayList<>();
        for (ListDef def : ListManager.get().getLists(getContainer()))
        {
            ret.add(def.getName());
        }

        Map<String,QueryDefinition> queries = new ListQuerySchema(getUser(), getContainer()).getQueryDefs();
        ret.addAll(queries.keySet());
        return ret;
    }


    public GWTList getList(int listId)
    {
        if (listId == 0)
            return null;

        ListDefinition def =  ListService.get().getList(getContainer(), listId);
        if (def == null)
            return null;

        GWTList gwt = new GWTList();
        gwt._listId(listId);
        gwt.setName(def.getName());
        gwt.setAllowDelete(def.getAllowDelete());
        gwt.setAllowExport(def.getAllowExport());
        gwt.setAllowUpload(def.getAllowUpload());

        gwt.setEntireListIndex(def.getEntireListIndex());
        gwt.setEntireListIndexSetting(def.getEntireListIndexSetting().getValue());
        gwt.setEntireListTitleSetting(def.getEntireListTitleSetting().getValue());
        gwt.setEntireListTitleTemplate(def.getEntireListTitleTemplate());
        gwt.setEntireListBodySetting(def.getEntireListBodySetting().getValue());
        gwt.setEntireListBodyTemplate(def.getEntireListBodyTemplate());

        gwt.setEachItemIndex(def.getEachItemIndex());
        gwt.setEachItemTitleSetting(def.getEachItemTitleSetting().getValue());
        gwt.setEachItemTitleTemplate(def.getEachItemTitleTemplate());
        gwt.setEachItemBodySetting(def.getEachItemBodySetting().getValue());
        gwt.setEachItemBodyTemplate(def.getEachItemBodyTemplate());

        gwt.setFileAttachmentIndex(def.getFileAttachmentIndex());

        gwt.setDescription(def.getDescription());
        gwt.setDiscussionSetting(def.getDiscussionSetting().getValue());
        gwt.setKeyPropertyName(def.getKeyName());
        gwt.setKeyPropertyType(def.getKeyType().name());
        gwt.setTitleField(def.getTitleColumn());
        gwt._typeURI(def.getDomain().getTypeURI());

        if (StringUtils.isEmpty(gwt.getTitleField()))
        {
            try
            {
                TableInfo list = def.getTable(getUser());
                if (null != list)
                {
                    gwt._defaultTitleField(list.getTitleColumn());
                }
            }
            catch (Exception x)
            {
                /* */
            }
        }
        return gwt;
    }


    private ListDef update(ListDef def, GWTList gwt)
    {
        ListDef.ListDefBuilder builder = new ListDef.ListDefBuilder(def);
        builder.setName(gwt.getName());
        builder.setAllowDelete(gwt.getAllowDelete());
        builder.setAllowExport(gwt.getAllowExport());
        builder.setAllowUpload(gwt.getAllowUpload());

        builder.setEntireListIndex(gwt.getEntireListIndex());
        builder.setEntireListIndexSetting(gwt.getEntireListIndexSetting());
        builder.setEntireListTitleSetting(gwt.getEntireListTitleSetting());
        builder.setEntireListTitleTemplate(gwt.getEntireListTitleTemplate());
        builder.setEntireListBodySetting(gwt.getEntireListBodySetting());
        builder.setEntireListBodyTemplate(gwt.getEntireListBodyTemplate());

        builder.setEachItemIndex(gwt.getEachItemIndex());
        builder.setEachItemTitleSetting(gwt.getEachItemTitleSetting());
        builder.setEachItemTitleTemplate(gwt.getEachItemTitleTemplate());
        builder.setEachItemBodySetting(gwt.getEachItemBodySetting());
        builder.setEachItemBodyTemplate(gwt.getEachItemBodyTemplate());

        builder.setFileAttachmentIndex(gwt.getFileAttachmentIndex());

        builder.setDescription(gwt.getDescription());
        builder.setDiscussionSetting(gwt.getDiscussionSetting());
        builder.setKeyName(gwt.getKeyPropertyName());
        builder.setKeyType(gwt.getKeyPropertyType());
        builder.setTitleColumn(gwt.getTitleField());
        return builder.build();
    }


    private void update(ListDefinition defn, GWTList gwt)
    {
        defn.setAllowDelete(gwt.getAllowDelete());
        defn.setAllowExport(gwt.getAllowExport());
        defn.setAllowUpload(gwt.getAllowUpload());

        defn.setEntireListIndex(gwt.getEntireListIndex());
        defn.setEntireListIndexSetting(IndexSetting.getForValue(gwt.getEntireListIndexSetting()));
        defn.setEntireListTitleSetting(TitleSetting.getForValue(gwt.getEntireListTitleSetting()));
        defn.setEntireListTitleTemplate(gwt.getEntireListTitleTemplate());
        defn.setEntireListBodySetting(BodySetting.getForValue(gwt.getEntireListBodySetting()));
        defn.setEntireListBodyTemplate(gwt.getEntireListBodyTemplate());

        defn.setEachItemIndex(gwt.getEachItemIndex());
        defn.setEachItemTitleSetting(TitleSetting.getForValue(gwt.getEachItemTitleSetting()));
        defn.setEachItemTitleTemplate(gwt.getEachItemTitleTemplate());
        defn.setEachItemBodySetting(BodySetting.getForValue(gwt.getEachItemBodySetting()));
        defn.setEachItemBodyTemplate(gwt.getEachItemBodyTemplate());

        defn.setFileAttachmentIndex(gwt.getFileAttachmentIndex());

        defn.setDescription(gwt.getDescription());
        defn.setDiscussionSetting(DiscussionSetting.getForValue(gwt.getDiscussionSetting()));
        defn.setKeyName(gwt.getKeyPropertyName());
        defn.setKeyType(KeyType.valueOf(gwt.getKeyPropertyType()));
        defn.setTitleColumn(gwt.getTitleField());
    }

    /** @return Errors encountered during the save attempt */
    @NotNull
    public List<String> updateListDefinition(GWTList list, GWTDomain orig, GWTDomain dd) throws ListEditorService.ListImportException
    {
        if (!getContainer().hasPermission(getUser(), DesignListPermission.class))
            throw new UnauthorizedException();

        DbScope scope = ListManager.get().getListMetadataSchema().getScope();

        ListDef def = ListManager.get().getList(getContainer(), list.getListId());
        if (def.getDomainId() != orig.getDomainId() || def.getDomainId() != dd.getDomainId() || !orig.getDomainURI().equals(dd.getDomainURI()))
            throw new IllegalArgumentException();

        if (list.getName().length() > ListEditorService.MAX_NAME_LENGTH)
        {
            throw new ListImportException("List name cannot be longer than " + ListEditorService.MAX_NAME_LENGTH + " characters");
        }

        // handle key column name change
        GWTPropertyDescriptor key = findField(def.getKeyName(), orig.getFields());
        if (null != key)
        {
            int id = key.getPropertyId();
            GWTPropertyDescriptor newKey = findField(id, dd.getFields());
            if (null != newKey && !key.getName().equalsIgnoreCase(newKey.getName()))
            {
                return Collections.singletonList("Cannot change key field name");
            }
        }

        // Note attachment columns that are getting removed
        Domain domain = PropertyService.get().getDomain(getContainer(), orig.getDomainURI());
        ListDefinition listDefinition = ListService.get().getList(getContainer(), list.getListId());
        TableInfo table = listDefinition.getTable(getUser());
        if (null == domain || null == table)
            throw new IllegalArgumentException("Expected domain and table for list: " + list.getName());

        Map<String, ColumnInfo> modifiedAttachmentColumns = new CaseInsensitiveHashMap<>();
        for (DomainProperty oldProp : domain.getProperties())
        {
            if (PropertyType.ATTACHMENT.equals(oldProp.getPropertyDescriptor().getPropertyType()))
            {
                GWTPropertyDescriptor newGWTProp = findField(oldProp.getPropertyId(), dd.getFields());
                if (null == newGWTProp || !PropertyType.ATTACHMENT.equals(PropertyType.getFromURI(newGWTProp.getConceptURI(), newGWTProp.getRangeURI(), null)))
                {
                    ColumnInfo column = table.getColumn(oldProp.getPropertyDescriptor().getName());
                    if (null != column)
                        modifiedAttachmentColumns.put(oldProp.getPropertyDescriptor().getName(), column);
                }
            }
        }

        Collection<Map<String, Object>> attachmentMapCollection = null;
        if (!modifiedAttachmentColumns.isEmpty())
        {
            List<ColumnInfo> columns = new ArrayList<>(modifiedAttachmentColumns.values());
            columns.add(table.getColumn("entityId"));
            attachmentMapCollection = new TableSelector(table, columns, null, null).getMapCollection();
        }

        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            List<String> errors;
            // Check for legalName problems -- GWT designer does not catch them (and doesn't have support on the client to easily check)
            errors = checkLegalNameConflicts(dd);
            if (!errors.isEmpty())
                return errors;

            boolean changedName = !def.getName().equals(list.getName());
            def = update(def, list);
            try
            {
                //Update list metadata first to ensure Indexing flags are correctly set before domain is updated
                ListManager.get().update(getUser(), def);
            }
            catch (RuntimeSQLException x)
            {
                if (changedName && x.isConstraintException())
                    throw new ListImportException("The name '" + def.getName() + "' is already in use.");
                throw x;
            }

            try
            {
                // Remove attachments from any attachment columns that are removed or no longer attachment columns
                if (null != attachmentMapCollection)
                    for (Map<String, Object> map : attachmentMapCollection)
                    {
                        String entityId = (String)map.get("entityId");
                        ListItemAttachmentParent parent = new ListItemAttachmentParent(entityId, getContainer());
                        for (Map.Entry<String, Object> entry : map.entrySet())
                            if (null != entry.getValue() && modifiedAttachmentColumns.containsKey(entry.getKey()))
                                AttachmentService.get().deleteAttachment(parent, entry.getValue().toString(), getUser());
                    }

                errors = super.updateDomainDescriptor(orig, dd);  //Triggers search indexing during Domain refresh
            }
            catch (RuntimeSQLException x)
            {
                // issue 19202 - check for null value exceptions in case provided file data not contain the column
                // and return a better error message
                String message = x.getMessage();
                if (x.isNullValueException())
                {
                    message = "The provided data does not contain the specified '" +
                            def.getKeyName() + "' field or contains null key values.";
                }

                throw new ListImportException(message);
            }

            if (!errors.isEmpty())
                return errors;

            transaction.commit();
        }

        return new ArrayList<>(); // GWT error Collections.emptyList();
    }

    @NotNull
    private List<String> checkLegalNameConflicts(GWTDomain dd)
    {
        List<String> errors = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (Object obj : dd.getFields())
        {
            GWTPropertyDescriptor descriptor = (GWTPropertyDescriptor)obj;
            String legalName = ColumnInfo.legalNameFromName(descriptor.getName()).toLowerCase();
            if (names.contains(legalName))
                errors.add("Field name's legal name is not unique: " + descriptor.getName());
            else
                names.add(legalName);
        }
        return errors;
    }

    public GWTDomain getDomainDescriptor(GWTList list) throws SQLException
    {
        ListDef def = ListManager.get().getList(getContainer(), list.getListId());
        if (null == def)
            return null;

        GWTDomain<GWTPropertyDescriptor> domain = _getDomainDescriptor(def);
        if (null==domain)
            return null;

        GWTPropertyDescriptor key = findField(list.getKeyPropertyName(), domain.getFields());
        if (null == key)
        {
            // we need to create this property now, so that it doesn't look like an 'added' property in the designer
            key = new GWTPropertyDescriptor(def.getKeyName(), PropertyType.INTEGER.getTypeUri());
            try {
                key.setRangeURI(KeyType.valueOf(def.getKeyType()).getPropertyType().getTypeUri());
            } catch (Exception x) {/* */}

            GWTDomain<GWTPropertyDescriptor> update = new GWTDomain<>(domain);
            List<GWTPropertyDescriptor> fields = new ArrayList<>(domain.getFields());
            fields.add(0,key);
            update.setFields(fields);
            try
            {
                updateListDefinition(list, domain, update);
            }
            catch (ListImportException x)
            {
                throw new RuntimeException(x);
            }

            domain = _getDomainDescriptor(def);
        }

        domain.setAllowAttachmentProperties(true);
        domain.setDefaultValueOptions(new DefaultValueType[]
                { DefaultValueType.FIXED_EDITABLE, DefaultValueType.LAST_ENTERED }, DefaultValueType.FIXED_EDITABLE);
        return domain;
    }


    public GWTDomain<GWTPropertyDescriptor> _getDomainDescriptor(ListDef def)
    {
        DomainDescriptor dd = OntologyManager.getDomainDescriptor(def.getDomainId());
        if (null == dd)
            return null;
        GWTDomain<GWTPropertyDescriptor> domain = DomainUtil.getDomainDescriptor(getUser(), dd.getDomainURI(), dd.getContainer());
        return domain;
    }


    private GWTPropertyDescriptor findField(String name, List<GWTPropertyDescriptor> fields)
    {
        for (GWTPropertyDescriptor f : fields)
        {
            if (name.equalsIgnoreCase(f.getName()))
                return f;
        }
        return null;
    }

    private GWTPropertyDescriptor findField(int id, List<GWTPropertyDescriptor> fields)
    {
        if (id > 0)
        {
            for (GWTPropertyDescriptor f : fields)
            {
                if (id == f.getPropertyId())
                    return f;
            }
        }
        return null;
    }
}
