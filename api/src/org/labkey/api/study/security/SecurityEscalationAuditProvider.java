/*
 * Copyright (c) 2019 LabKey Corporation
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
package org.labkey.api.study.security;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.audit.query.DefaultAuditTypeTable;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.UserSchema;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * This is an abstract class that represents an Audit Log for a Security Escalator.  To use it, you need to
 * extend it, and then register it with the AuditLogManager.
 *
 * <code>
 *     // Register the Security Escalation Audit Log
 *     AuditLogService.get().registerAuditType(new SecurityEscalatorAuditProvider());
 * </code>
 */
public abstract class SecurityEscalationAuditProvider extends AbstractAuditTypeProvider implements AuditTypeProvider
{
    private static Logger _log = LogManager.getLogger(SecurityEscalationAuditProvider.class);

    /**
     * A name for the event to log as.  This shouldn't have any spaces, and uniquely
     * identifies this audit log type.  A good choice is the class name of the event class.
     *
     * @return A name for the event to log as.  This shouldn't have any spaces
     */
    abstract public String getEventType();

    /**
     * The title for this table in the audit log.
     *
     * @return The title for this table in the audit log.
     */
    abstract public String getAuditLogTitle();

    /*
     * The custom columns for this audit log type.
     */
    public enum CustomColumn {
        START_TIME        ("startTime",      "Start Time",      PropertyType.DATE_TIME),
        END_TIME          ("endTime",        "End Time",        PropertyType.DATE_TIME),
        SERVICE_NAME      ("serviceName",    "Service Name",    PropertyType.STRING),
        STACK_TRACE       ("stackTrace",     "Stack Trace",     PropertyType.MULTI_LINE),
        ESCALATING_USER   ("escalatingUser", "Escalating User", PropertyType.INTEGER),
        LEVEL             ("level",          "Level",           PropertyType.INTEGER)
        ;

        String displayName;
        String columnName;
        boolean isHidden = false;
        PropertyType type;

        CustomColumn(String columnName, String displayName, PropertyType type) {
            this.displayName = displayName;
            this.columnName = columnName;
            this.type = type;
        }

        CustomColumn(String columnName, String displayName, PropertyType type, boolean isHidden) {
            this(displayName, columnName, type);
            this.isHidden = isHidden;
        }
    }

    /**
     * @see #getEventType()
     * @return The unique identifier of the Event Type
     */
    @Override
    public String getEventName() {
        return getEventType();
    }

    /**
     * @see #getAuditLogTitle()
     * @return Audit Log Title
     */
    @Override
    public String getLabel() {
        return getAuditLogTitle();
    }

    /**
     * Generates the {@link TableInfo} that will be displayed in the audit log.  This gives us a chance to
     * add foreign keys, custom renderers, etc.
     *
     * @param userSchema The schema that's storing the data
     * @return A {@link TableInfo} to be displayed in the Audit Log
     */
    @Override
    public TableInfo createTableInfo(UserSchema userSchema, ContainerFilter cf)
    {
        DefaultAuditTypeTable table = new DefaultAuditTypeTable(this, createStorageTableInfo(), userSchema, cf, getDefaultVisibleColumns()) {
            @Override
            protected void initColumn(MutableColumnInfo columnInfo) {
                // Customize Column Labels
                for (CustomColumn custom_column : CustomColumn.values()) {
                    if (custom_column.columnName.equalsIgnoreCase(columnInfo.getColumnName())) {
                        // Set the label to the display value in our enum
                        columnInfo.setLabel(custom_column.displayName);

                        // Force Timestamp columns to display as such, rather than dates.
                        if (custom_column.type.equals(PropertyType.DATE_TIME)) {
                            columnInfo.setFormat("DateTime");
                        }
                    }
                }

                // For the user column, add a foreign key to the user table.
                if (columnInfo.getColumnName().equalsIgnoreCase(CustomColumn.ESCALATING_USER.columnName)) {
                    LookupForeignKey fk = new LookupForeignKey("UserId", "DisplayName") {
                        @Override
                        public TableInfo getLookupTableInfo() {
                            return CoreSchema.getInstance().getTableInfoUsers();
                        }
                    };

                    columnInfo.setFk(fk);
                }
            }
        };

        return table;
    }

    /**
     * Returns a list of the default {@link FieldKey}s to display in the Audit Log view.  The other columns
     * can be viewed by customizing the view on the Audit Log page.
     *
     * @return A list of default columns.
     */
    @Override
    public List<FieldKey> getDefaultVisibleColumns() {
        List<String> keys = Arrays.asList(
                CustomColumn.ESCALATING_USER.columnName,
                CustomColumn.START_TIME.columnName,
                CustomColumn.END_TIME.columnName,
                COLUMN_NAME_COMMENT,
                COLUMN_NAME_CONTAINER,
                COLUMN_NAME_CREATED_BY,
                COLUMN_NAME_IMPERSONATED_BY
        );

        // The super method returns null, so don't try to do this to be nice.
        //keys.addAll(super.getDefaultVisibleColumns());

        List<FieldKey> fieldKeys = new ArrayList<>();
        for (String columnName : keys) {
            fieldKeys.add(FieldKey.fromParts(columnName));
        }

        return fieldKeys;
    }

    /**
     * This class represents an individual row in the audit log.  It is a bean that defines the columns needed
     * by {@link SecurityEscalator} for auditing.  Each instance of the audit log needs to extend this and
     * define the {@linkplain #getEventType() event type}.
     */
    public abstract static class SecurityEscalationEvent extends AuditTypeEvent
    {
        private Date startTime;
        private Date endTime;
        private String serviceName;
        private String stackTrace;
        private int escalatingUser;
        private int level;

        // It is essential that you set the container and event type, otherwise the Audit Log will fail at
        // runtime (silently in the case of the "eventType" and noisely in the case of the container).
        public SecurityEscalationEvent() {
            super();
            this.setEventType(this.getEventType());
        }

        /**
         * Returns the same value as its parent Audit Log's {@link SecurityEscalationAuditProvider#getEventType()}.
         *
         * @return The same value as its parent Audit Log's value for {@link SecurityEscalationAuditProvider#getEventType()}
         * @see SecurityEscalationAuditProvider#getEventType()
         */
        @Override
        public abstract String getEventType();

        public Date getStartTime() {
            return startTime;
        }

        public void setStartTime(Date startTime) {
            this.startTime = startTime;
        }

        public Date getEndTime() {
            return this.endTime;
        }

        public void setEndTime(Date endTime) {
            this.endTime = endTime;
        }

        public String getServiceName() {
            return this.serviceName;
        }

        public void setServiceName(String serviceName) {
            this.serviceName = serviceName;
        }

        public int getEscalatingUser() {
            return escalatingUser;
        }

        public void setEscalatingUser(int user) {
            this.escalatingUser = user;
        }

        public int getLevel() {
            return level;
        }

        public void setLevel(int level) {
            this.level = level;
        }

        public String getStackTrace() {
            return this.stackTrace;
        }

        public void setStackTrace(String stackTrace) {
            this.stackTrace = stackTrace;
        }
    }

    /**
     * This class essentially defines the table that will be used to store the audit information.
     */
    public static abstract class SecurityEscalationAuditDomainKind extends AbstractAuditDomainKind
    {
        private String eventType;
        public static String NAMESPACE_PREFIX = "Audit-";

        private static Set<PropertyDescriptor> _fields = null;

        /**
         * To be called by {@link AbstractAuditTypeProvider#getDomainKind()}.
         *
         * @param eventType This should match its parent's {@link SecurityEscalationAuditProvider#getEventType()}.
         */
        public SecurityEscalationAuditDomainKind(String eventType) {
            super(eventType);
            this.eventType = eventType;
        }

        /**
         * Returns the domain name of this audit type.  This is generally this class's name without
         * the "kind" at the end.
         *
         * @return The class's name without the "kind" ending.
         */
        public abstract String getDomainName();

        @Override
        public String getKindName() {
            return eventType;
        }

        @Override
        protected String getNamespacePrefix() {
            return NAMESPACE_PREFIX;
        }

        /**
         * Returns a list of the custom columns to be recorded in the audit log.
         *
         * @return A list of custom columns to store with the audit log.
         */
        @Override
        public Set<PropertyDescriptor> getProperties() {
            if (_fields == null) {
                Set<PropertyDescriptor> fields = new LinkedHashSet<>();

                for (CustomColumn column : CustomColumn.values()) {
                    fields.add(createPropertyDescriptor(column.columnName, column.type));
                }

                _fields = Collections.unmodifiableSet(fields);
            }

            return _fields;
        }
    }
}
