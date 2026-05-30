/*
 * Copyright (c) 2022-2026 LabKey Corporation
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
package org.labkey.study.audit;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.audit.DetailedAuditTypeEvent;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.audit.query.DefaultAuditTypeTable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.ObjectFactory;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.study.model.ParticipantGroup;
import org.labkey.study.StudySchema;
import org.labkey.study.model.ParticipantCategoryImpl;
import org.labkey.study.model.ParticipantGroupManager;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.labkey.api.util.IntegerUtils.asInteger;

public class ParticipantGroupAuditProvider extends AbstractAuditTypeProvider implements AuditTypeProvider
{
    private static final String EVENT_NAME = "ParticipantGroupEvent";
    private static final String PARTICIPANT_CATEGORY_ID_COLUMN_NAME = "ParticipantCategory";
    private static final String PARTICIPANT_GROUP_ID_COLUMN_NAME = "ParticipantGroup";
    private static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();

    static
    {
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CONTAINER));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_IMPERSONATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(PARTICIPANT_CATEGORY_ID_COLUMN_NAME));
        defaultVisibleColumns.add(FieldKey.fromParts(PARTICIPANT_GROUP_ID_COLUMN_NAME));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_COMMENT));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_DATA_CHANGES));
    }

    public ParticipantGroupAuditProvider()
    {
        super(new ParticipantGroupAuditDomainKind());
    }

    @Override
    public String getEventName()
    {
        return EVENT_NAME;
    }

    @Override
    public String getLabel()
    {
        return "Participant Group Events";
    }

    @Override
    public String getDescription()
    {
        return "Events related to the creation and modification of Participant Groups and Categories.";
    }

    @Override
    public <K extends AuditTypeEvent> Class<K> getEventClass()
    {
        return (Class<K>) ParticipantGroupAuditEvent.class;
    }

    @Override
    public TableInfo createTableInfo(UserSchema userSchema, ContainerFilter cf)
    {
        DefaultAuditTypeTable table = new DefaultAuditTypeTable(this, createStorageTableInfo(), userSchema, cf, defaultVisibleColumns)
        {
            @Override
            protected void initColumn(MutableColumnInfo col)
            {
                if (PARTICIPANT_CATEGORY_ID_COLUMN_NAME.equalsIgnoreCase(col.getName()))
                {
                    col.setLabel("Category");
                    String detailsString = "query/detailsQueryRow.view?schemaName=" + StudySchema.getInstance().getSchemaName() +
                            "&queryName=" + ParticipantGroupManager.getTableInfoParticipantCategory().getName() + "&RowId=${" + PARTICIPANT_CATEGORY_ID_COLUMN_NAME + "}";
                    col.setURL(DetailsURL.fromString(detailsString, getContainerContext()));
                    col.setFk(QueryForeignKey
                            .from(userSchema, cf)
                            .table(ParticipantGroupManager.getTableInfoParticipantCategory()).key("RowId").display("Label"));
                }
                else if (PARTICIPANT_GROUP_ID_COLUMN_NAME.equalsIgnoreCase(col.getName()))
                {
                    col.setLabel("Group");
                    String detailsString = "query/detailsQueryRow.view?schemaName=" + StudySchema.getInstance().getSchemaName() +
                            "&queryName=" + ParticipantGroupManager.getTableInfoParticipantGroup().getName() + "&RowId=${" + PARTICIPANT_GROUP_ID_COLUMN_NAME + "}";
                    col.setURL(DetailsURL.fromString(detailsString, getContainerContext()));
                    col.setFk(QueryForeignKey
                            .from(userSchema, cf)
                            .table(ParticipantGroupManager.getTableInfoParticipantGroup()).key("RowId").display("Label"));
                }
            }
        };
        appendValueMapColumns(table, null, true);

        return table;
    }

    public static class ParticipantGroupAuditEvent extends DetailedAuditTypeEvent
    {
        private Integer _participantCategory;
        private Integer _participantGroup;

        public ParticipantGroupAuditEvent()
        {
        }

        public ParticipantGroupAuditEvent(Container container, String comment, Integer participantCategoryId)
        {
            super(EVENT_NAME, container, comment);
            _participantCategory = participantCategoryId;
        }

        public Integer getParticipantCategory()
        {
            return _participantCategory;
        }

        public void setParticipantCategory(Integer participantCategory)
        {
            _participantCategory = participantCategory;
        }

        public Integer getParticipantGroup()
        {
            return _participantGroup;
        }

        public void setParticipantGroup(Integer participantGroup)
        {
            _participantGroup = participantGroup;
        }

        @Override
        public Map<String, Object> getAuditLogMessageElements()
        {
            Map<String, Object> elements = super.getAuditLogMessageElements();
            elements.put("categoryId", getParticipantCategory());
            elements.put("groupId", getParticipantGroup());

            return elements;
        }
    }

    public static class ParticipantGroupAuditDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "ParticipantGroupAuditDomain";
        public static String NAMESPACE_PREFIX = "ParticipantGroup-" + NAME;

        private final Set<PropertyDescriptor> fields;

        public ParticipantGroupAuditDomainKind()
        {
            super(EVENT_NAME);

            fields = new LinkedHashSet<>();
            fields.add(createPropertyDescriptor(PARTICIPANT_CATEGORY_ID_COLUMN_NAME, PropertyType.INTEGER, PARTICIPANT_CATEGORY_ID_COLUMN_NAME, null, true));
            fields.add(createPropertyDescriptor(PARTICIPANT_GROUP_ID_COLUMN_NAME, PropertyType.STRING, PARTICIPANT_GROUP_ID_COLUMN_NAME, null, false));
            fields.add(createOldDataMapPropertyDescriptor());
            fields.add(createNewDataMapPropertyDescriptor());
        }

        @Override
        protected String getNamespacePrefix()
        {
            return NAMESPACE_PREFIX;
        }

        @Override
        public Set<PropertyDescriptor> getProperties()
        {
            return fields;
        }

        @Override
        public String getKindName()
        {
            return NAME;
        }
    }

    public static class EventFactory
    {
        // list of fields for the data map to include
        static final List<String> _allowedFields = List.of(
                "categoryLabel",
                "categoryId",
                "participantIds",
                "label",
                "shared");

        public static ParticipantGroupAuditEvent categoryChange(
                Container c,
                User user,
                @Nullable ParticipantCategoryImpl prevCategory,
                ParticipantCategoryImpl newCategory)
        {
            ObjectFactory<ParticipantCategoryImpl> factory = ObjectFactory.Registry.getFactory(ParticipantCategoryImpl.class);
            String comment = "The participant category was " + (prevCategory == null ? "created" : "modified");
            var event = new ParticipantGroupAuditProvider.ParticipantGroupAuditEvent(c, comment, newCategory.getRowId());

            if (prevCategory != null)
                event.setOldRecordMap(createEncodedRecordMap(factory.toMap(prevCategory, null)));
            event.setNewRecordMap(createEncodedRecordMap(factory.toMap(newCategory, null)));

            return event;
        }

        public static ParticipantGroupAuditEvent categoryDeleted(
                Container c,
                User user,
                ParticipantCategoryImpl category)
        {
            return new ParticipantGroupAuditProvider.ParticipantGroupAuditEvent(c,
                    "The participant category : " + category.getLabel() + " was deleted",
                    category.getRowId());
        }

        @Nullable
        public static ParticipantGroupAuditEvent groupChange(
                Container c,
                User user,
                @Nullable ParticipantGroup prevGroup,
                ParticipantGroup newGroup)
        {
            ObjectFactory<ParticipantGroup> factory = ObjectFactory.Registry.getFactory(ParticipantGroup.class);
            String comment = "The participant group was " + (prevGroup == null ? "created" : "modified");
            var event = new ParticipantGroupAuditProvider.ParticipantGroupAuditEvent(c, comment, newGroup.getCategoryId());
            event.setParticipantGroup(newGroup.getRowId());

            event.setNewRecordMap(createEncodedRecordMap(factory.toMap(newGroup, null)));
            if (prevGroup != null)
            {
                event.setOldRecordMap(createEncodedRecordMap(factory.toMap(prevGroup, null)));
                if (event.getNewRecordMap().equals(event.getOldRecordMap()))
                    return null;
            }

            return event;
        }

        public static ParticipantGroupAuditEvent groupDeleted(
                Container c,
                User user,
                ParticipantGroup group)
        {
            var event = new ParticipantGroupAuditProvider.ParticipantGroupAuditEvent(c,
                    "The participant group : " + group.getLabel() + " was deleted",
                    group.getCategoryId());
            event.setParticipantGroup(group.getRowId());

            return event;
        }

        public static ParticipantGroupAuditEvent participantDeleted(
                String participantId,
                Container c,
                String groupName,
                Integer groupId)
        {
            return new ParticipantGroupAuditProvider.ParticipantGroupAuditEvent(c,
                    "The participant '" + participantId + "' was deleted", groupId);
        }

        private static String createEncodedRecordMap(Map<String, Object> bean)
        {
            if (bean.containsKey("ownerId"))
            {
                bean.put("shared", asInteger(bean.get("ownerId")) == -1);
            }

            if (bean.containsKey("participantIds"))
            {
                String[] ids = (String[])bean.get("participantIds");
                if (ids != null)
                    bean.replace("participantIds", String.join(", ", ids));
            }
            Map<String, Object> filteredMap = bean.entrySet().stream()
                    .filter(e -> _allowedFields.contains(e.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue() != null ? e.getValue() : ""));

            return AbstractAuditTypeProvider.encodeForDataMap(filteredMap);
        }
    }
}
