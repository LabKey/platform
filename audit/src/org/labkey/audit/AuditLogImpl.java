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

package org.labkey.audit;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.AuditLogService.AuditViewFactory;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.audit.query.AuditLogQueryView;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.MultiValueMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DatabaseTableType;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.ObjectFactory;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.LimitedUser;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.Pair;
import org.labkey.api.util.StartupListener;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.DefaultContainerUser;
import org.labkey.audit.model.LogManager;
import org.labkey.audit.query.AuditLogTable;
import org.labkey.audit.query.AuditQuerySchema;
import org.labkey.audit.query.AuditQueryViewImpl;

import javax.servlet.ServletContext;
import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * User: Karl Lum
 * Date: Oct 4, 2007
 */
public class AuditLogImpl implements AuditLogService.I, StartupListener
{
    private static final AuditLogImpl _instance = new AuditLogImpl();

    private static final Logger _log = Logger.getLogger(AuditLogImpl.class);
    private static final String OBJECT_XML_KEY = "objectXML";
    private static final Map<String, Boolean> _factoryInitialized = new HashMap<>();

    private Queue<Pair<User, AuditLogEvent>> _eventQueue = new LinkedList<>();
    private Queue<Pair<User, AuditTypeEvent>> _eventTypeQueue = new LinkedList<>();
    private AtomicBoolean  _logToDatabase = new AtomicBoolean(false);
    private static final Object STARTUP_LOCK = new Object();

    public static AuditLogImpl get()
    {
        return _instance;
    }

    private AuditLogImpl()
    {
        ContextListener.addStartupListener(this);
    }

    public void moduleStartupComplete(ServletContext servletContext)
    {
        synchronized (STARTUP_LOCK)
        {
            _logToDatabase.set(true);

            while (!_eventQueue.isEmpty())
            {
                Pair<User, AuditLogEvent> event = _eventQueue.remove();
                _addEvent(event.first, event.second);
            }

            while (!_eventTypeQueue.isEmpty())
            {
                Pair<User, AuditTypeEvent> event = _eventTypeQueue.remove();
                _addEvent(event.first, event.second);
            }
        }
    }

    public boolean isViewable()
    {
        return true;
    }

    public AuditLogEvent addEvent(ViewContext context, String eventType, String key, String message)
    {
        AuditLogEvent event = _createEvent(context);
        event.setEventType(eventType);
        event.setKey1(key);
        event.setComment(message);

        return _addEvent(context.getUser(), event);
    }

    public AuditLogEvent addEvent(User user, Container c, String eventType, String key, String message)
    {
        AuditLogEvent event = _createEvent(user, c);
        event.setEventType(eventType);
        event.setKey1(key);
        event.setComment(message);

        return _addEvent(user, event);
    }

    public AuditLogEvent addEvent(User user, Container c, String eventType, String key1, String key2, String message)
    {
        AuditLogEvent event = _createEvent(user, c);
        event.setEventType(eventType);
        event.setKey1(key1);
        event.setKey2(key2);
        event.setComment(message);

        return _addEvent(user, event);
    }
    
    public AuditLogEvent addEvent(User user, Container c, String eventType, int key, String message)
    {
        AuditLogEvent event = _createEvent(user, c);
        event.setEventType(eventType);
        event.setIntKey1(key);
        event.setComment(message);

        return _addEvent(user, event);
    }

    public AuditLogEvent addEvent(AuditLogEvent event)
    {
        User user = event.getCreatedBy();
        if (user == null)
        {
            _log.warn("user was not specified, defaulting to guest user.");
            user = UserManager.getGuestUser();
            event.setCreatedBy(user);
        }

/*
        BeanObjectFactory factory = new BeanObjectFactory(event.getClass());
        Map<String, Object> map = new HashMap();
        factory.toMap(event, map);

        if (!event.getClass().equals(AuditLogEvent.class))
            event.setObjectXML(getObjectDescriptor(map));
*/
        return _addEvent(user, event);
    }

    public <K extends AuditTypeEvent> K addEvent(User user, K type)
    {
        return _addEvent(user, type);
    }

    /**
     * Factory method to get an audit event from a property map.
     * @param clz - the event class to create.
     * @return
     */
    public <K extends AuditLogEvent> K createFromMap(Map<String, Object> map, Class<K> clz)
    {
        if (map.containsKey(OBJECT_XML_KEY))
        {
            map = new CaseInsensitiveHashMap<>(map);
            addObjectProperties((String)map.get(OBJECT_XML_KEY), map);
        }
        return ObjectFactory.Registry.getFactory(clz).fromMap(map);
    }

    private void addObjectProperties(String objectXML, Map<String, Object> map)
    {
        map.putAll(decodeFromXML(objectXML));
    }

    private String getObjectDescriptor(Map<String, Object> properties)
    {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            XMLEncoder enc = new XMLEncoder(new BufferedOutputStream(baos));
            enc.writeObject(properties);
            enc.close();
            return baos.toString();
        }
        catch (Exception e)
        {
            _log.error("error serializing object properties to XML", e);
        }
        return null;
    }

    private void initializeAuditFactory(User user, AuditLogEvent event) throws Exception
    {
        synchronized (STARTUP_LOCK)
        {
            if (!_factoryInitialized.containsKey(event.getEventType()))
            {
                _log.info("Initializing audit event factory for: " + event.getEventType());
                AuditViewFactory factory = getAuditViewFactory(event.getEventType());
                if (factory != null)
                {
                    Container c = ContainerManager.getForId(event.getContainerId());
                    factory.initialize(new DefaultContainerUser(c, user));
                }
                _factoryInitialized.put(event.getEventType(), true);
            }
        }
    }

    private AuditLogEvent _addEvent(User user, AuditLogEvent event)
    {
        try
        {
            assert event.getContainerId() != null : "Container cannot be null";

            if (event.getContainerId() == null)
            {
                _log.warn("container was not specified, defaulting to root container.");
                Container root = ContainerManager.getRoot();
                event.setContainerId(root.getId());
            }

            if (!_logToDatabase.get())
            {
                /**
                 * This is necessary because audit log service needs to be registered in the constructor
                 * of the audit module, but the schema may not be created or updated at that point.  Events
                 * that occur before startup is complete are therefore queued up and recorded after startup.
                 */
                synchronized (STARTUP_LOCK)
                {
                    if (_logToDatabase.get())
                    {
                        initializeAuditFactory(user, event);
                        return LogManager.get().insertEvent(user, event);
                    }
                    else
                        _eventQueue.add(new Pair<>(user, event));
                }
            }
            else
            {
                initializeAuditFactory(user, event);
                return LogManager.get().insertEvent(user, event);
            }
        }
        catch (Exception e)
        {
            _log.error("Failed to insert audit log event", e);
            throw new RuntimeException(e);
        }
        return null;
    }

    /**
     * Currently only use for testing new audit providers, eventually this will replace the legacy addEvent method
     * but only once the migration code is in place.
     * @param user
     * @param event
     * @param <K>
     * @return
     */
    private <K extends AuditTypeEvent> K _addEvent(User user, K event)
    {
        try
        {
            assert event.getContainer() != null : "Container cannot be null";

            if (event.getContainer() == null)
            {
                _log.warn("container was not specified, defaulting to root container.");
                Container root = ContainerManager.getRoot();
                event.setContainer(root.getId());
            }

            if (!_logToDatabase.get())
            {
                /**
                 * This is necessary because audit log service needs to be registered in the constructor
                 * of the audit module, but the schema may not be created or updated at that point.  Events
                 * that occur before startup is complete are therefore queued up and recorded after startup.
                 */
                synchronized (STARTUP_LOCK)
                {
                    if (_logToDatabase.get())
                    {
                        LogManager.get()._insertEvent(user, event);
                    }
                    else
                        _eventTypeQueue.add(new Pair<>(user, (AuditTypeEvent)event));
                }
            }
            else
            {
                LogManager.get()._insertEvent(user, event);
            }
        }
        catch (Exception e)
        {
            _log.error("Failed to insert audit log event", e);
            throw new RuntimeException(e);
        }
        return null;
    }

    private AuditLogEvent _createEvent(ViewContext context)
    {
        AuditLogEvent event = new AuditLogEvent();
        event.setCreated(new Date());
        event.setCreatedBy(context.getUser());
        Container c = context.getContainer();
        event.setContainerId(c.getId());
        if (null != c.getProject())
            event.setProjectId(c.getProject().getId());

        return event;
    }

    private AuditLogEvent _createEvent(User user, Container c)
    {
        if (user == null)
        {
            _log.warn("user was not specified, defaulting to guest user.");
            user = UserManager.getGuestUser();
        }
        AuditLogEvent event = new AuditLogEvent();
        event.setCreated(new Date());
        event.setCreatedBy(user);

        if (c != null)
        {
            event.setContainerId(c.getId());
            if (c.getProject() != null)
                event.setProjectId(c.getProject().getId());
        }

        return event;
    }

    private Map<String, Object> decodeFromXML(String objectXML)
    {
        try {
            XMLDecoder dec = new XMLDecoder(new ByteArrayInputStream(objectXML.getBytes("UTF-8")));
            Object o = dec.readObject();
            if (Map.class.isAssignableFrom(o.getClass()))
                return (Map<String, Object>)o;

            dec.close();
        }
        catch (Exception e)
        {
            _log.error("An error occurred parsing the object xml", e);
        }
        return Collections.emptyMap();
    }

    public AuditLogQueryView createQueryView(ViewContext context, SimpleFilter filter)
    {
        return createQueryView(context, filter, AuditQuerySchema.AUDIT_TABLE_NAME);
    }

    public AuditLogQueryView createQueryView(ViewContext context, @Nullable SimpleFilter filter, String viewFactoryName)
    {
        AuditQuerySchema schema = new AuditQuerySchema(context.getUser(), context.getContainer());
        QuerySettings settings = schema.getSettings(context, AuditQuerySchema.AUDIT_TABLE_NAME, viewFactoryName);
        settings.setBaseFilter(filter);

        // if the user is an admin, they should see everything, else default to current folder
        if (context.getUser().isAdministrator())
            settings.setContainerFilterName(ContainerFilter.Type.AllFolders.name());
        AuditQueryViewImpl view = new AuditQueryViewImpl(schema, settings, filter);
        view.setButtonBarPosition(DataRegion.ButtonBarPosition.TOP);
        view.setShowImportDataButton(false);

        return view;
    }

    public String getTableName()
    {
        return AuditQuerySchema.AUDIT_TABLE_NAME;
    }

    public TableInfo getTable(ViewContext context, String name)
    {
        UserSchema schema = createSchema(context.getUser(), context.getContainer());
        return schema.getTable(name);
    }

    public UserSchema createSchema(User user, Container container)
    {
        return new AuditQuerySchema(user, container);
    }

    public List<AuditLogEvent> getEvents(String eventType, String key)
    {
        SimpleFilter filter = new SimpleFilter("EventType", eventType);
        filter.addCondition("Key1", key);

        return getEvents(filter);
    }

    public List<AuditLogEvent> getEvents(String eventType, int key)
    {
        SimpleFilter filter = new SimpleFilter("EventType", eventType);
        filter.addCondition("IntKey1", key);

        return getEvents(filter);
    }

    public List<AuditLogEvent> getEvents(SimpleFilter filter)
    {
        return getEvents(filter, null);
    }

    public List<AuditLogEvent> getEvents(SimpleFilter filter, Sort sort)
    {
        if (LogManager.get().getTinfoAuditLog().getTableType() != DatabaseTableType.NOT_IN_DB)
            return LogManager.get().getEvents(filter, sort);

        return Collections.emptyList();
    }

    public AuditLogEvent getEvent(int rowId)
    {
        /**
         * need to check for the physical table to be in existence because the audit log service needs
         * to be registered in the constructor of the audit module.
         */
        if (LogManager.get().getTinfoAuditLog().getTableType() != DatabaseTableType.NOT_IN_DB)
            return LogManager.get().getEvent(rowId);

        return null;
    }

    @Nullable
    @Override
    public <K extends AuditTypeEvent> K getAuditEvent(User user, String eventType, int rowId)
    {
        if (AuditLogService.enableHardTableLogging())
        {
            return LogManager.get().getAuditEvent(user, eventType, rowId);
        }
        return null;
    }

    public void addAuditViewFactory(AuditViewFactory factory)
    {
        AuditLogService.addAuditViewFactory(factory);
    }

    public AuditViewFactory getAuditViewFactory(String eventType)
    {
        return AuditLogService.getAuditViewFactory(eventType);
    }

    public List<AuditViewFactory> getAuditViewFactories()
    {
        return AuditLogService.getAuditViewFactories();
    }

    @Override
    public void registerAuditType(AuditTypeProvider provider)
    {
        AuditLogService.registerAuditType(provider);
    }

    @Override
    public List<AuditTypeProvider> getAuditProviders()
    {
        return AuditLogService.getAuditProviders();
    }

    @Override
    public AuditTypeProvider getAuditProvider(String eventType)
    {
        return AuditLogService.getAuditProvider(eventType);
    }

    public <K extends AuditTypeEvent> AuditLogEvent addEvent(AuditLogEvent event, Map<String, Object> dataMap, String domainURI)
    {
        if (AuditLogService.enableHardTableLogging())
        {
            AuditTypeProvider provider = getAuditProvider(event.getEventType());
            if (provider != null)
            {
                K bean = provider.convertEvent(event, dataMap);
                addEvent(event.getCreatedBy(), bean);
            }
            return event;
        }
        else
        {
            DbSchema schema = AuditSchema.getInstance().getSchema();

            try (DbScope.Transaction transaction = schema.getScope().ensureTransaction())
            {
                event = addEvent(event);
                if (event != null)
                {
                    String parentLsid = domainURI + ':' + event.getRowId();

                    SQLFragment updateSQL = new SQLFragment();
                    updateSQL.append("UPDATE " + LogManager.get().getTinfoAuditLog() + " SET lsid = ? WHERE rowid = ?");
                    updateSQL.add(parentLsid);
                    updateSQL.add(event.getRowId());
                    new SqlExecutor(LogManager.get().getSchema()).execute(updateSQL);

                    addEventProperties(parentLsid, domainURI, dataMap);
                }
                transaction.commit();

                return event;
            }
        }
    }

    public static void addEventProperties(String parentLsid, String domainURI, Map<String, Object> dataMap)
    {
        Container c = ContainerManager.getSharedContainer();
        Domain domain = PropertyService.get().getDomain(c, domainURI);

        if (domain == null)
            throw new IllegalStateException("Domain does not exist: " + domainURI);

        ObjectProperty[] properties = new ObjectProperty[dataMap.size()];
        int i=0;
        for (Map.Entry<String, Object> entry : dataMap.entrySet())
        {
            DomainProperty prop = domain.getPropertyByName(entry.getKey());
            if (prop != null)
            {
                properties[i++] = new ObjectProperty(null, c, prop.getPropertyURI(), entry.getValue());
            }
            else
            {
                throw new IllegalStateException("Specified property: " + entry.getKey() + " is not available in domain: " + domainURI);
            }
        }
        for (ObjectProperty prop : properties)
            prop.setObjectURI(parentLsid);

        try {
            OntologyManager.insertProperties(c, parentLsid, properties);
        }
        catch (ValidationException e)
        {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public String getDomainURI(String eventType)
    {
        return new Lsid("AuditLogService", eventType).toString();
    }

    @Override
    public String getPropertyURI(String eventType, String propertyName)
    {
        return new Lsid("AuditLogService", eventType).toString() + '#' + propertyName;
    }

    @Override
    public void migrateProvider(AuditTypeProvider provider, Domain domain)
    {
        if (ModuleLoader.getInstance().isNewInstall())
            return;

        // Get the old audit table table (requires admin priviledges to see all rows in all containers)
        User user = new LimitedUser(UserManager.getGuestUser(), new int[0], Collections.singleton(RoleManager.siteAdminRole), true);
        Container rootContainer = ContainerManager.getRoot();
        UserSchema schema = AuditLogService.get().createSchema(user, rootContainer);
        AuditLogTable sourceTable = new AuditLogTable(schema, LogManager.get().getTinfoAuditLog(), provider.getEventName());
        sourceTable.setContainerFilter(ContainerFilter.EVERYTHING);

        // UNDONE: Don't perform migration if audit.auditlog table has already been deleted from the database

        _log.info("Migrating audit type " + provider.getEventName());

        // Get the new audit provisioned table
        DbSchema dbSchema = DbSchema.get(AuditSchema.SCHEMA_NAME);
        SchemaTableInfo targetTable = StorageProvisioner.createTableInfo(domain, dbSchema);


        // Create list of sourceColumns on sourceTable
        List<FieldKey> sourceFields = new ArrayList<>();
        for (ColumnInfo c : sourceTable.getColumns())
            sourceFields.add(c.getFieldKey());

        // Include sourceColumns from the sourceTable's domain
        Domain legacyDomain = PropertyService.get().getDomain(ContainerManager.getSharedContainer(), getDomainURI(provider.getEventName())); // assumes event name doesn't change
        if (legacyDomain != null)
        {
            for (DomainProperty dp : legacyDomain.getProperties())
                sourceFields.add(FieldKey.fromParts("Property", dp.getName()));
        }

        Map<FieldKey, ColumnInfo> sourceColumns = QueryService.get().getColumns(sourceTable, sourceFields);


        // Create map of columns (multiple sourceColumns may be mapped to the same targetColumn)
        Map<FieldKey, String> legacyNameMap = provider.legacyNameMap();
        MultiValueMap<String, ColumnInfo> colMap = new MultiValueMap<String, ColumnInfo>(new CaseInsensitiveHashMap<Collection<ColumnInfo>>())
        {
            @Override
            protected Collection<ColumnInfo> createValueCollection()
            {
                return new LinkedList<>();
            }
        };
        for (ColumnInfo c : sourceColumns.values())
        {
            if (null != c.getPropertyURI())
                colMap.put(c.getPropertyURI(), c);
            colMap.put(c.getName(), c);

            // mapping from 'intKey1' or 'Property/Foo' to new column name
            String newName = legacyNameMap.get(c.getFieldKey());
            if (newName != null)
                colMap.put(newName, c);
        }


        // Generate SQL insert
        SQLFragment insertInto = new SQLFragment("INSERT INTO ").append(targetTable.getSelectName()).append(" (");
        SQLFragment insertSelect = new SQLFragment("SELECT ");
        String sep = "";

        SqlDialect dialect = targetTable.getSqlDialect();
        for (ColumnInfo targetCol : targetTable.getColumns())
        {
            Collection<ColumnInfo> fromCols = colMap.get(targetCol.getPropertyURI());
            if (null == fromCols)
                fromCols = colMap.get(targetCol.getName());

            if (null == fromCols || fromCols.size() == 0)
            {
                _log.warn(String.format("Could not copy column '%s'", targetCol.getName()));
                continue;
            }

            insertInto.append(sep).append(targetCol.getSelectName());

            insertSelect.append(sep);

            // COALESCE multiple columns into a single value (e.g. oldRecordMap and oldRecord)
            String coalesceSep = "";
            if (fromCols.size() > 1)
                insertSelect.append("COALESCE(");

            for (ColumnInfo fromCol : fromCols)
            {
                insertSelect.append(coalesceSep);
                // DatasetAuditProvider has a 'HasDetails' boolean column that must be converted from an int
                boolean castNeeded = targetCol.getJdbcType() != fromCol.getJdbcType();
                if (castNeeded)
                    insertSelect.append("CAST(");
                insertSelect.append(fromCol.getAlias());
                if (castNeeded)
                    insertSelect.append(" AS ").append(dialect.sqlTypeNameFromJdbcType(targetCol.getJdbcType())).append(")");
                coalesceSep = ", ";
            }

            if (fromCols.size() > 1)
                insertSelect.append(")");

            sep = ", ";
        }
        insertInto.append(")\n");
        insertInto.append(insertSelect);
        insertInto.append("\n FROM (\n");
        insertInto.append(QueryService.get().getSelectSQL(sourceTable, sourceColumns.values(), null, null, Table.ALL_ROWS, 0, true));
        insertInto.append(") x\n");

        _log.info(insertInto);

        SqlExecutor exec = new SqlExecutor(dbSchema);
        int count = exec.execute(insertInto);

        _log.info(String.format("Migrated %d rows for audit type %s", count, provider.getEventName()));
    }

}
