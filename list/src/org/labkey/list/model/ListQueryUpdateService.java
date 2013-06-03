/*
 * Copyright (c) 2008-2013 LabKey Corporation
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
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.etl.DataIteratorContext;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.list.view.ListItemAttachmentParent;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
* User: Dave
* Date: Jun 12, 2008
* Time: 1:51:50 PM
*/

/**
 * Implementation of QueryUpdateService for Lists
 */
public class ListQueryUpdateService extends DefaultQueryUpdateService
{
    ListDefinitionImpl _list = null;

    public ListQueryUpdateService(ListTable queryTable, TableInfo dbTable, ListDefinition list)
    {
        super(queryTable, dbTable);
        _list = (ListDefinitionImpl) list;
    }

    @Override
    protected DataIteratorContext getDataIteratorContext(BatchValidationException errors, InsertOption insertOption)
    {
        DataIteratorContext context = super.getDataIteratorContext(errors, insertOption);
        if (insertOption.batch)
        {
            context.setMaxRowErrors(100);
            context.setFailFast(false);
        }
        return context;
    }

    @Override
    protected Map<String, Object> getRow(User user, Container container, Map<String, Object> listRow) throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        Map<String, Object> ret = null;

        if (null != listRow)
        {
            Object key = listRow.get(_list.getKeyName().toLowerCase());
            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts(_list.getKeyName()), key);
            TableSelector selector = new TableSelector(getQueryTable(), filter, null);
            ret = selector.getMap();
        }

        return ret;
    }

    @Override
    protected Map<String, Object> insertRow(User user, Container container, Map<String, Object> row) throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        throw new UnsupportedOperationException("Update Service Not Complete");
    }

    @Override
    public List<Map<String, Object>> insertRows(User user, Container container, List<Map<String, Object>> rows, BatchValidationException errors, Map<String, Object> extraScriptContext) throws DuplicateKeyException, QueryUpdateServiceException, SQLException
    {
        List<Map<String, Object>> result = super._insertRowsUsingETL(user, container, rows, getDataIteratorContext(errors, InsertOption.INSERT), extraScriptContext);

        if (null != result)
        {
            int idx = 0; // index used to compare result and rows
            for (Map row : result)
            {
                if (null != row.get("entityId"))
                {
                    // Audit each row
                    String entityId = (String) row.get("entityId");
                    addAuditEvent(user, "A new list record was inserted", entityId, null, null);

                    // Add attachments
                    AttachmentParent parent = new ListItemAttachmentParent(entityId, _list.getContainer());
                    List<AttachmentFile> newAttachments = new ArrayList<>();

                    for (DomainProperty property : _list.getDomain().getProperties())
                    {
                        if (null != row.get(property.getName()))
                        {
                            Object value = rows.get(idx).get(property.getName());
                            if (property.getPropertyDescriptor().getPropertyType() == PropertyType.ATTACHMENT)
                            {
                                newAttachments.add((AttachmentFile) value);
                            }
                        }
                    }

                    if (newAttachments.size() > 0)
                    {
                        try
                        {
                            AttachmentService.get().addAttachments(parent, newAttachments, user);
                        }
                        catch (IOException e)
                        {
//                                    throw new ValidationException(e.getMessage());
                        }
                    }
                }
                idx++;
            }

            if (result.size() > 0 && !errors.hasErrors())
                ListManager.get().indexList(_list);
        }

        return result;
    }

    @Override
    protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, @NotNull Map<String, Object> oldRow) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        // TODO: Check for equivalency so that attachments can be deleted etc.

        Map<String, Object> result = super.updateRow(user, container, row, oldRow);

        if (null != result)
        {
            if (null != result.get("entityId"))
            {
                String entityId = (String) result.get("entityId");

                // Audit
                addAuditEvent(user, "An existing list record was modified", entityId, null, null);
            }
        }

        return result;
    }

    @Override
    protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRowMap) throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        Map<String, Object> result = super.deleteRow(user, container, oldRowMap);

        if (null != result)
        {
            if (null != result.get("entityId"))
            {
                String entityId = (String) result.get("entityId");

                // Audit
                addAuditEvent(user, "An existing list record was deleted", entityId, null, null);

                // Remove discussions
                DiscussionService.get().deleteDiscussions(container, user, entityId);

                // Remove attachments
                AttachmentService.get().deleteAttachments(new ListItemAttachmentParent(entityId, container));

                // Clean up Search indexer
                if (result.size() > 0)
                    ListManager.get().deleteItemIndex(_list, entityId);
            }
        }

        return result;
    }


    /**
     * Modeled after ListItemImpl.addAuditEvent
     * @param user
     * @param comment
     * @param entityId
     * @param oldRecord
     * @param newRecord
     */
    private void addAuditEvent(User user, String comment, String entityId, @Nullable String oldRecord, @Nullable String newRecord)
    {
        AuditLogEvent event = new AuditLogEvent();

        event.setCreatedBy(user);
        event.setComment(comment);

        Container c = _list.getContainer();
        event.setContainerId(c.getId());
        Container project = c.getProject();
        if (null != project)
            event.setProjectId(project.getId());

        event.setKey1(_list.getDomain().getTypeURI());
        event.setEventType(ListManager.LIST_AUDIT_EVENT);
        event.setIntKey1(_list.getListId());
        event.setKey2(_list.getName());
        event.setKey3(entityId);

        final Map<String, Object> dataMap = new HashMap<String, Object>();
        if (oldRecord != null) dataMap.put(ListAuditViewFactory.OLD_RECORD_PROP_NAME, oldRecord);
        if (newRecord != null) dataMap.put(ListAuditViewFactory.NEW_RECORD_PROP_NAME, newRecord);

        if (!dataMap.isEmpty())
            AuditLogService.get().addEvent(event, dataMap, AuditLogService.get().getDomainURI(ListManager.LIST_AUDIT_EVENT));
        else
            AuditLogService.get().addEvent(event);
    }
}
