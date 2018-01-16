/*
 * Copyright (c) 2005-2017 Fred Hutchinson Cancer Research Center
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
package org.labkey.api.exp;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.cache.BlockingStringKeyCache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.StringKeyCache;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.*;
import org.labkey.api.data.DbScope.Transaction;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exceptions.OptimisticConflictException;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.IPropertyValidator;
import org.labkey.api.exp.property.Lookup;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.property.ValidatorContext;
import org.labkey.api.gwt.client.ui.domain.CancellationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.PropertyValidationError;
import org.labkey.api.query.ValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.test.TestTimeout;
import org.labkey.api.test.TestWhen;
import org.labkey.api.util.CPUTimer;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.webdav.SimpleDocumentResource;
import org.labkey.api.webdav.WebdavResource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.labkey.api.search.SearchService.PROPERTY;

/**
 * Lots of static methods for dealing with domains and property descriptors. Tends to operate primarily on the bean-style
 * classes like {@link PropertyDescriptor} and {@link DomainDescriptor}.
 *
 * When possible, it's usually preferable to use {@link PropertyService}, {@link Domain}, and {@link DomainProperty}
 * instead as they tend to provide higher-level abstractions.
 * User: migra
 * Date: Jun 14, 2005
 */
public class OntologyManager
{
    private static final Logger _log = Logger.getLogger(OntologyManager.class);
    private static final DatabaseCache<Map<String, ObjectProperty>> mapCache = new DatabaseCache<>(getExpSchema().getScope(), 5000, "Property maps");
    private static final DatabaseCache<Integer> objectIdCache = new DatabaseCache<>(getExpSchema().getScope(), 1000, "ObjectIds");
    private static final DatabaseCache<PropertyDescriptor> propDescCache = new DatabaseCache<>(getExpSchema().getScope(), 10000, "Property descriptors");
    private static final DatabaseCache<DomainDescriptor> domainDescByURICache = new DatabaseCache<>(getExpSchema().getScope(), 2000, "Domain descriptors by URI");
    private static final StringKeyCache<DomainDescriptor> domainDescByIDCache = new BlockingStringKeyCache<>(new DatabaseCache<>(getExpSchema().getScope(), 2000, "Domain descriptors by ID"), new DomainDescriptorLoader());
    private static final DatabaseCache<List<Pair<String, Boolean>>> domainPropertiesCache = new DatabaseCache<>(getExpSchema().getScope(), 2000, "Domain properties");
    private static final Container _sharedContainer = ContainerManager.getSharedContainer();

    public static final String MV_INDICATOR_SUFFIX = "mvindicator";

    static
    {
        BeanObjectFactory.Registry.register(ObjectProperty.class, new ObjectProperty.ObjectPropertyObjectFactory());
    }


    private OntologyManager()
    {
    }


    /**
     * @return map from PropertyURI to value
     */
    public static Map<String, Object> getProperties(Container container, String parentLSID)
    {
        Map<String, Object> m = new HashMap<>();
        Map<String, ObjectProperty> propVals = getPropertyObjects(container, parentLSID);
        if (null != propVals)
        {
            for (Map.Entry<String, ObjectProperty> entry : propVals.entrySet())
            {
                m.put(entry.getKey(), entry.getValue().value());
            }
        }

        return m;
    }

    public static final int MAX_PROPS_IN_BATCH = 1000;  // Keep this reasonably small so progress indicator is updated regularly
    public static final int UPDATE_STATS_BATCH_COUNT = 1000;

    /**
     * @return LSIDs/ObjectURIs of inserted objects
     */
    public static List<String> insertTabDelimited(Container c, User user, @Nullable Integer ownerObjectId, ImportHelper helper, Domain domain, List<Map<String, Object>> rows, boolean ensureObjects) throws SQLException, ValidationException
    {
        List<PropertyDescriptor> properties = new ArrayList<>(domain.getProperties().size());
        for (DomainProperty prop : domain.getProperties())
        {
            properties.add(prop.getPropertyDescriptor());
        }
        return insertTabDelimited(c, user, ownerObjectId, helper, properties, rows, ensureObjects);
    }

    /**
     * @return LSIDs/ObjectURIs of inserted objects
     */
    public static List<String> insertTabDelimited(Container c, User user, @Nullable Integer ownerObjectId, ImportHelper helper, List<PropertyDescriptor> descriptors, List<Map<String, Object>> rows, boolean ensureObjects) throws SQLException, ValidationException
    {
        CPUTimer total = new CPUTimer("insertTabDelimited");
        CPUTimer before = new CPUTimer("beforeImport");
        CPUTimer ensure = new CPUTimer("ensureObject");
        CPUTimer insert = new CPUTimer("insertProperties");

        assert total.start();
        assert getExpSchema().getScope().isTransactionActive();
        List<String> resultingLsids = new ArrayList<>(rows.size());
        // Make sure we have enough rows to handle the overflow of the current row so we don't have to resize the list
        List<PropertyRow> propsToInsert = new ArrayList<>(MAX_PROPS_IN_BATCH + descriptors.size());

        ValidatorContext validatorCache = new ValidatorContext(c, user);

        try
        {
            OntologyObject objInsert = new OntologyObject();
            objInsert.setContainer(c);
            objInsert.setOwnerObjectId(ownerObjectId);

            List<ValidationError> errors = new ArrayList<>();
            Map<Integer, List<? extends IPropertyValidator>> validatorMap = new HashMap<>();

            // cache all the property validators for this upload
            for (PropertyDescriptor pd : descriptors)
            {
                List<? extends IPropertyValidator> validators = PropertyService.get().getPropertyValidators(pd);
                if (!validators.isEmpty())
                    validatorMap.put(pd.getPropertyId(), validators);
            }

            int rowCount = 0;
            int batchCount = 0;

            for (Map<String, Object> map : rows)
            {
                // TODO: hack -- should exit and return cancellation status instead of throwing
                if (Thread.currentThread().isInterrupted())
                    throw new CancellationException();

                assert before.start();
                String lsid = helper.beforeImportObject(map);
                resultingLsids.add(lsid);
                assert before.stop();

                assert ensure.start();
                int objectId;
                if (ensureObjects)
                    objectId = ensureObject(c, lsid, ownerObjectId);
                else
                {
                    objInsert.setObjectURI(lsid);
                    Table.insert(null, getTinfoObject(), objInsert);
                    objectId = objInsert.getObjectId();
                }

                for (PropertyDescriptor pd : descriptors)
                {
                    Object value = map.get(pd.getPropertyURI());
                    if (null == value)
                    {
                        if (pd.isRequired())
                            throw new ValidationException("Missing value for required property " + pd.getName());
                        else
                        {
                            continue;
                        }
                    }
                    else
                    {
                        if (validatorMap.containsKey(pd.getPropertyId()))
                            validateProperty(validatorMap.get(pd.getPropertyId()), pd, new ObjectProperty(lsid, c, pd, value), errors, validatorCache);
                    }
                    try
                    {
                        PropertyRow row = new PropertyRow(objectId, pd, value, pd.getPropertyType());
                        propsToInsert.add(row);
                    }
                    catch (ConversionException e)
                    {
                        throw new ValidationException("Could not convert '" + value + "' for field " + pd.getName() + ", should be of type " + pd.getPropertyType().getJavaType().getSimpleName());
                    }
                }
                assert ensure.stop();

                rowCount++;

                if (propsToInsert.size() > MAX_PROPS_IN_BATCH)
                {
                    assert insert.start();
                    insertPropertiesBulk(c, propsToInsert);
                    helper.afterBatchInsert(rowCount);
                    assert insert.stop();
                    propsToInsert = new ArrayList<>(MAX_PROPS_IN_BATCH + descriptors.size());

                    if (++batchCount % UPDATE_STATS_BATCH_COUNT == 0)
                    {
                        getExpSchema().getSqlDialect().updateStatistics(getTinfoObject());
                        getExpSchema().getSqlDialect().updateStatistics(getTinfoObjectProperty());
                        helper.updateStatistics(rowCount);
                    }
                }
            }

            if (!errors.isEmpty())
                throw new ValidationException(errors);

            assert insert.start();
            insertPropertiesBulk(c, propsToInsert);
            helper.afterBatchInsert(rowCount);
            assert insert.stop();
        }
        catch (SQLException x)
        {
            SQLException next = x.getNextException();
            if (x instanceof java.sql.BatchUpdateException && null != next)
                x = next;
            _log.debug("Exception uploading: ", x);
            throw x;
        }

        assert total.stop();
        _log.debug("\t" + total.toString());
        _log.debug("\t" + before.toString());
        _log.debug("\t" + ensure.toString());
        _log.debug("\t" + insert.toString());

        return resultingLsids;
    }


    /**
     * As an incremental step of QueryUpdateService cleanup, this is a version of insertTabDelimited that works on a
     * tableInfo that implements UpdateableTableInfo. Does not support ownerObjectid.
     * <p>
     * This code is made complicated by the fact that while we are trying to move toward a TableInfo/ColumnInfo view
     * of the world, validators are attached to PropertyDescriptors. Also, missing value handling is attached
     * to PropertyDescriptors.
     * <p>
     * The original version of this method expects a data be be a map PropertyURI->value. This version will also
     * accept Name->value.
     * <p>
     * Name->Value is preferred, we are using TableInfo after all.
     */
    public static List<String> insertTabDelimited(TableInfo tableInsert, Container c, User user,
                                                  UpdateableTableImportHelper helper,
                                                  List<Map<String, Object>> rows,
                                                  Logger logger)
            throws SQLException, ValidationException
    {
        return saveTabDelimited(tableInsert, c, user, helper, rows, logger, true);
    }

    public static List<String> updateTabDelimited(TableInfo tableInsert, Container c, User user,
                                                  UpdateableTableImportHelper helper,
                                                  List<Map<String, Object>> rows,
                                                  Logger logger)
            throws SQLException, ValidationException
    {
        return saveTabDelimited(tableInsert, c, user, helper, rows, logger, false);
    }

    private static List<String> saveTabDelimited(TableInfo table, Container c, User user,
                                                 UpdateableTableImportHelper helper,
                                                 List<Map<String, Object>> rows,
                                                 Logger logger,
                                                 boolean insert)
            throws SQLException, ValidationException
    {
        if (!(table instanceof UpdateableTableInfo))
            throw new IllegalArgumentException();

        if (rows.isEmpty())
        {
            return Collections.emptyList();
        }

        DbScope scope = table.getSchema().getScope();

        assert scope.isTransactionActive();
        List<String> resultingLsids = new ArrayList<>(rows.size());

        Domain d = table.getDomain();
        List<? extends DomainProperty> properties = null == d ? Collections.emptyList() : d.getProperties();

        ValidatorContext validatorCache = new ValidatorContext(c, user);

        Connection conn = null;
        Parameter.ParameterMap parameterMap = null;

        Map<String, Object> currentRow = null;

        try
        {
            conn = scope.getConnection();
            if (insert)
            {
                parameterMap = ((UpdateableTableInfo) table).insertStatement(conn, user);
            }
            else
            {
                parameterMap = ((UpdateableTableInfo) table).updateStatement(conn, user, null);
            }
            List<ValidationError> errors = new ArrayList<>();

            Map<String, List<? extends IPropertyValidator>> validatorMap = new HashMap<>();
            Map<String, DomainProperty> propertiesMap = new HashMap<>();

            // cache all the property validators for this upload
            for (DomainProperty dp : properties)
            {
                propertiesMap.put(dp.getPropertyURI(), dp);
                List<? extends IPropertyValidator> validators = dp.getValidators();
                if (!validators.isEmpty())
                    validatorMap.put(dp.getPropertyURI(), validators);
            }

            List<ColumnInfo> columns = table.getColumns();
            PropertyType[] propertyTypes = new PropertyType[columns.size()];
            for (int i = 0; i < columns.size(); i++)
            {
                String propertyURI = columns.get(i).getPropertyURI();
                DomainProperty dp = null == propertyURI ? null : propertiesMap.get(propertyURI);
                PropertyDescriptor pd = null == dp ? null : dp.getPropertyDescriptor();
                if (null != pd)
                    propertyTypes[i] = pd.getPropertyType();
            }

            int rowCount = 0;

            for (Map<String, Object> map : rows)
            {
                currentRow = new CaseInsensitiveHashMap<>(map);

                // TODO: hack -- should exit and return cancellation status instead of throwing
                if (Thread.currentThread().isInterrupted())
                    throw new CancellationException();

                parameterMap.clearParameters();

                String lsid = helper.beforeImportObject(currentRow);
                resultingLsids.add(lsid);

                //
                // NOTE we validate based on columninfo/propertydescriptor
                // However, we bind by name, and there may be parameters that do not correspond to columninfo
                //

                for (int i = 0; i < columns.size(); i++)
                {
                    ColumnInfo col = columns.get(i);
                    if (col.isMvIndicatorColumn() || col.isRawValueColumn()) //TODO col.isNotUpdatableForSomeReasonSoContinue()
                        continue;
                    String propertyURI = col.getPropertyURI();
                    DomainProperty dp = null == propertyURI ? null : propertiesMap.get(propertyURI);
                    PropertyDescriptor pd = null == dp ? null : dp.getPropertyDescriptor();

                    Object value = currentRow.get(col.getName());
                    if (null == value)
                        value = currentRow.get(propertyURI);

                    if (null == value)
                    {
                        // TODO col.isNullable() doesn't seem to work here
                        if (null != pd && pd.isRequired())
                            throw new ValidationException("Missing value for required property " + col.getName());
                    }
                    else
                    {
                        if (null != pd)
                        {
                            try
                            {
                                // Use an ObjectProperty to unwrap MvFieldWrapper, do type conversion, etc
                                ObjectProperty objectProperty = new ObjectProperty(lsid, c, pd, value);
                                if (!validateProperty(validatorMap.get(propertyURI), pd, objectProperty, errors, validatorCache))
                                {
                                    throw new ValidationException(errors);
                                }
                            }
                            catch (ConversionException e)
                            {
                                throw new ValidationException("Could not convert value '" + value + "' for field '" + pd.getName() + "'", pd.getName());
                            }
                        }
                    }

                    // issue 19391: data from R uses "Inf" to represent infinity
                    if (JdbcType.DOUBLE.equals(col.getJdbcType()))
                    {
                        value = "Inf".equals(value) ? "Infinity" : value;
                        value = "-Inf".equals(value) ? "-Infinity" : value;
                    }

                    try
                    {
                        String key = col.getName();
                        if (!parameterMap.containsKey(key))
                            key = propertyURI;
                        if (null == propertyTypes[i])
                        {
                            // some built-in columns won't have parameters (createdby, etc)
                            if (parameterMap.containsKey(key))
                            {
                                assert !(value instanceof MvFieldWrapper);
                                // Handle type coercion for these built-in columns as well, though we don't need to
                                // worry about missing values
                                value = PropertyType.getFromClass(col.getJavaObjectClass()).convert(value);
                                parameterMap.put(key, value);
                            }
                        }
                        else
                        {
                            Pair<Object, String> p = new Pair<>(value, null);
                            convertValuePair(pd, propertyTypes[i], p);
                            parameterMap.put(key, p.first);
                            if (null != p.second)
                            {
                                FieldKey mvName = col.getMvColumnName();
                                if (mvName != null)
                                {
                                    String storageName = table.getColumn(mvName).getSelectName();
                                    parameterMap.put(storageName, p.second);
                                }
                            }
                        }
                    }
                    catch (ConversionException e)
                    {
                        throw new ValidationException("Could not convert '" + value + "' for field " + pd.getName() + ", should be of type " + propertyTypes[i].getJavaType().getSimpleName());
                    }
                }

                helper.bindAdditionalParameters(currentRow, parameterMap);
                parameterMap.execute();
                helper.afterImportObject(currentRow);
                rowCount++;
            }

            if (!errors.isEmpty())
                throw new ValidationException(errors);

            helper.afterBatchInsert(rowCount);
            if (logger != null)
                logger.debug("inserted row " + rowCount + ".");
        }
        catch (SQLException x)
        {
            SQLException next = x.getNextException();
            if (x instanceof java.sql.BatchUpdateException && null != next)
                x = next;
            _log.debug("Exception uploading: ", x);
            if (null != currentRow)
                _log.debug(currentRow.toString());
            throw x;
        }
        finally
        {
            if (null != parameterMap)
                parameterMap.close();
            if (null != conn)
                scope.releaseConnection(conn);
        }

        return resultingLsids;
    }

    // TODO: Consolidate with ColumnValidator
    public static boolean validateProperty(List<? extends IPropertyValidator> validators, PropertyDescriptor prop, ObjectProperty objectProperty,
                                           List<ValidationError> errors, ValidatorContext validatorCache)
    {
        boolean ret = true;

        Object value = objectProperty.getObjectValue();

        if (prop.isRequired() && value == null && objectProperty.getMvIndicator() == null)
        {
            errors.add(new PropertyValidationError("Field '" + prop.getName() + "' is required", prop.getName()));
            ret = false;
        }

        // Check if the string is too long. Use either the PropertyDescriptor's scale or VARCHAR(4000) for ontology managed values
        int stringLengthLimit = prop.getScale() > 0 ? prop.getScale() : getTinfoObjectProperty().getColumn("StringValue").getScale();
        int stringLength = value == null ? 0 : value.toString().length();
        if (value != null && prop.isStringType() && stringLength > stringLengthLimit)
        {
            String s = stringLength < 100 ? value.toString() : value.toString().substring(0, 100);
            errors.add(new PropertyValidationError("Field '" + prop.getName() + "' is limited to " + stringLengthLimit + " characters, but the value is " + stringLength + " characters. (The value starts with '" + s + "...')", prop.getName()));
            ret = false;
        }

        // TODO: check date is within postgres date range

        // Don't validate null values, #15683
        if (null != value && validators != null)
        {
            for (IPropertyValidator validator : validators)
                if (!validator.validate(prop, value, errors, validatorCache)) ret = false;
        }
        return ret;
    }

    public interface ImportHelper
    {
        /**
         * may modify map
         *
         * @return LSID for new or existing Object
         */
        String beforeImportObject(Map<String, Object> map) throws SQLException;

        void afterBatchInsert(int currentRow) throws SQLException;

        void updateStatistics(int currentRow) throws SQLException;
    }


    public interface UpdateableTableImportHelper extends ImportHelper
    {
        /**
         * may be used to process attachments, for auditing, etc etc
         */
        void afterImportObject(Map<String, Object> map) throws SQLException;

        /**
         * may set parameters directly for columns that are not exposed by tableinfo
         * e.g. "_key"
         * <p>
         * TODO maybe this can be handled declaratively? see UpdateableTableInfo
         */
        void bindAdditionalParameters(Map<String, Object> map, Parameter.ParameterMap target) throws ValidationException;
    }


    /**
     * @return map from PropertyURI to ObjectProperty
     */
    public static Map<String, ObjectProperty> getPropertyObjects(Container container, String objectLSID)
    {
        Map<String, ObjectProperty> m = mapCache.get(objectLSID);
        if (null != m)
            return m;

        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("ObjectURI"), objectLSID);
        if (container != null)
        {
            filter.addCondition(FieldKey.fromParts("Container"), container);
        }

        if (_log.isDebugEnabled())
        {
            try (ResultSet rs = new TableSelector(getTinfoObjectPropertiesView(), filter, null).getResultSet())
            {
                ResultSetUtil.logData(rs);
            }
            catch (SQLException x)
            {
                throw new RuntimeException(x);
            }
        }

        m = new HashMap<>();
        for (ObjectProperty value : new TableSelector(getTinfoObjectPropertiesView(), filter, null).getArrayList(ObjectProperty.class))
        {
            m.put(value.getPropertyURI(), value);
        }

        m = Collections.unmodifiableMap(m);
        mapCache.put(objectLSID, m);
        return m;
    }

    public static int ensureObject(Container container, String objectURI)
    {
        return ensureObject(container, objectURI, (Integer) null);
    }

    public static int ensureObject(Container container, String objectURI, String ownerURI)
    {
        Integer ownerId = null;
        if (null != ownerURI)
            ownerId = ensureObject(container, ownerURI, (Integer) null);
        return ensureObject(container, objectURI, ownerId);
    }

    public static int ensureObject(Container container, String objectURI, Integer ownerId)
    {
        //TODO: (marki) Transact?
        Integer i = objectIdCache.get(objectURI);
        if (null != i)
            return i.intValue();

        OntologyObject o = getOntologyObject(container, objectURI);
        if (null == o)
        {
            o = new OntologyObject();
            o.setContainer(container);
            o.setObjectURI(objectURI);
            o.setOwnerObjectId(ownerId);
            o = Table.insert(null, getTinfoObject(), o);
        }

        objectIdCache.put(objectURI, o.getObjectId());
        return o.getObjectId();
    }


    public static OntologyObject getOntologyObject(Container container, String uri)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("ObjectURI"), uri);
        if (container != null)
        {
            filter.addCondition(FieldKey.fromParts("Container"), container.getId());
        }
        return new TableSelector(getTinfoObject(), filter, null).getObject(OntologyObject.class);
    }


    // UNDONE: optimize (see deleteOntologyObjects(Integer[])
    public static void deleteOntologyObjects(Container c, String... uris)
    {
        if (uris.length == 0)
            return;

        try
        {
            DbSchema schema = getExpSchema();
            String sql = getSqlDialect().execute(getExpSchema(), "deleteObject", "?, ?");
            SqlExecutor executor = new SqlExecutor(schema);

            for (String uri : uris)
            {
                executor.execute(sql, c.getId(), uri);
            }
        }
        finally
        {
            mapCache.clear();
            objectIdCache.clear();
        }
    }


    public static void deleteOntologyObjects(DbSchema schema, SQLFragment sub, Container c, boolean deleteOwnedObjects)
    {
        // we have different levels of optimization possible here deleteOwned=true/false, scope=/<>exp

        // let's handle one case
        if (!schema.getScope().equals(getExpSchema().getScope()))
            throw new UnsupportedOperationException("can only use with same DbScope");

        // CONSIDER: use temp table for objectids?

        if (deleteOwnedObjects)
        {
            throw new UnsupportedOperationException("Don't do this yet either");
        }
        else
        {
            SQLFragment sqlDeleteProperties = new SQLFragment();
            sqlDeleteProperties.append("DELETE FROM ").append(getTinfoObjectProperty().getSelectName()).append(" WHERE ObjectId IN\n").append("(SELECT ObjectId FROM ").append(String.valueOf(getTinfoObject())).append("\n").append(" WHERE Container = ? AND ObjectURI IN (");
            sqlDeleteProperties.add(c.getId());
            sqlDeleteProperties.append(sub);
            sqlDeleteProperties.append("))");
            new SqlExecutor(getExpSchema()).execute(sqlDeleteProperties);

            SQLFragment sqlDeleteObjects = new SQLFragment();
            sqlDeleteObjects.append("DELETE FROM ").append(getTinfoObject().getSelectName()).append(" WHERE Container = ? AND ObjectURI IN (");
            sqlDeleteObjects.add(c.getId());
            sqlDeleteObjects.append(sub);
            sqlDeleteObjects.append(")");
            new SqlExecutor(getExpSchema()).execute(sqlDeleteObjects);
        }
    }


    public static void deleteOntologyObjects(Container c, boolean deleteOwnedObjects, int... objectIds)
    {
        deleteOntologyObjects(c, deleteOwnedObjects, true, objectIds);
    }

    private static void deleteOntologyObjects(Container c, boolean deleteOwnedObjects, boolean deleteObjects, int... objectIds)
    {
        if (objectIds.length == 0)
            return;

        try
        {
            // if uris is long, split it up
            if (objectIds.length > 1000)
            {
                int countBatches = objectIds.length / 1000;
                int lenBatch = 1 + objectIds.length / (countBatches + 1);

                for (int s = 0; s < objectIds.length; s += lenBatch)
                {
                    int[] sub = new int[Math.min(lenBatch, objectIds.length - s)];
                    System.arraycopy(objectIds, s, sub, 0, sub.length);
                    deleteOntologyObjects(c, deleteOwnedObjects, deleteObjects, sub);
                }

                return;
            }

            StringBuilder in = new StringBuilder();

            for (int objectId : objectIds)
            {
                in.append(objectId);
                in.append(", ");
            }

            in.setLength(in.length() - 2);

            if (deleteOwnedObjects)
            {
                // NOTE: owned objects should never be in a different container than the owner, that would be a problem
                StringBuilder sqlDeleteOwnedProperties = new StringBuilder();
                sqlDeleteOwnedProperties.append("DELETE FROM ").append(getTinfoObjectProperty()).append(" WHERE ObjectId IN (SELECT ObjectId FROM ").append(getTinfoObject()).append(" WHERE Container = '").append(c.getId()).append("' AND OwnerObjectId IN (");
                sqlDeleteOwnedProperties.append(in);
                sqlDeleteOwnedProperties.append("))");
                new SqlExecutor(getExpSchema()).execute(sqlDeleteOwnedProperties);

                StringBuilder sqlDeleteOwnedObjects = new StringBuilder();
                sqlDeleteOwnedObjects.append("DELETE FROM ").append(getTinfoObject()).append(" WHERE Container = '").append(c.getId()).append("' AND OwnerObjectId IN (");
                sqlDeleteOwnedObjects.append(in);
                sqlDeleteOwnedObjects.append(")");
                new SqlExecutor(getExpSchema()).execute(sqlDeleteOwnedObjects);
            }

            if (deleteObjects)
            {
                deleteProperties(c, objectIds);

                StringBuilder sqlDeleteObjects = new StringBuilder();
                sqlDeleteObjects.append("DELETE FROM ").append(getTinfoObject()).append(" WHERE Container = '").append(c.getId()).append("' AND ObjectId IN (");
                sqlDeleteObjects.append(in);
                sqlDeleteObjects.append(")");
                new SqlExecutor(getExpSchema()).execute(sqlDeleteObjects);
            }
        }
        finally
        {
            mapCache.clear();
            objectIdCache.clear();
        }
    }


    public static void deleteOntologyObject(String objectURI, Container container, boolean deleteOwnedObjects)
    {
        OntologyObject ontologyObject = getOntologyObject(container, objectURI);

        if (null != ontologyObject)
        {
            deleteOntologyObjects(container, deleteOwnedObjects, true, ontologyObject.getObjectId());
        }
    }


    public static OntologyObject getOntologyObject(int id)
    {
        return new TableSelector(getTinfoObject()).getObject(id, OntologyObject.class);
    }

    //todo: review this. this doesn't delete the underlying data objects. should it?
    public static void deleteObjectsOfType(String domainURI, Container container)
    {
        DomainDescriptor dd = null;
        if (null != domainURI)
            dd = getDomainDescriptor(domainURI, container);
        if (null == dd)
        {
            _log.debug("deleteObjectsOfType called on type not found in database:  " + domainURI);
            return;
        }

        try (Transaction t = getExpSchema().getScope().ensureTransaction())
        {


            // until we set a domain on objects themselves, we need to create a list of objects to
            // delete based on existing entries in ObjectProperties before we delete the objectProperties
            // which we need to do before we delete the objects
            String selectObjectsToDelete = "SELECT DISTINCT O.ObjectId " +
                    " FROM " + getTinfoObject() + " O " +
                    " INNER JOIN " + getTinfoObjectProperty() + " OP ON(O.ObjectId = OP.ObjectId) " +
                    " INNER JOIN " + getTinfoPropertyDomain() + " PDM ON (OP.PropertyId = PDM.PropertyId) " +
                    " INNER JOIN " + getTinfoDomainDescriptor() + " DD ON (PDM.DomainId = DD.DomainId) " +
                    " INNER JOIN " + getTinfoPropertyDescriptor() + " PD ON (PD.PropertyId = PDM.PropertyId) " +
                    " WHERE DD.DomainId = " + dd.getDomainId() +
                    " AND PD.Container = DD.Container";
            Integer[] objIdsToDelete = new SqlSelector(getExpSchema(), selectObjectsToDelete).getArray(Integer.class);

            String sep;
            StringBuilder sqlIN = null;
            Integer[] ownerObjIds = null;

            if (objIdsToDelete.length > 0)
            {
                //also need list of owner objects whose subobjects are going to be deleted
                // Seems cheaper but less correct to delete the subobjects then cleanup any owner objects with no children
                sep = "";
                sqlIN = new StringBuilder();
                for (Integer id : objIdsToDelete)
                {
                    sqlIN.append(sep).append(id);
                    sep = ", ";
                }

                String selectOwnerObjects = "SELECT O.ObjectId FROM " + getTinfoObject() + " O " +
                        " WHERE ObjectId IN " +
                        " (SELECT DISTINCT SUBO.OwnerObjectId FROM " + getTinfoObject() + " SUBO " +
                        " WHERE SUBO.ObjectId IN ( " + sqlIN.toString() + " ) )";

                ownerObjIds = new SqlSelector(getExpSchema(), selectOwnerObjects).getArray(Integer.class);
            }

            String deleteTypePropsSql = "DELETE FROM " + getTinfoObjectProperty() +
                    " WHERE PropertyId IN " +
                    " (SELECT PDM.PropertyId FROM " + getTinfoPropertyDomain() + " PDM " +
                    " INNER JOIN " + getTinfoPropertyDescriptor() + " PD ON (PDM.PropertyId = PD.PropertyId) " +
                    " INNER JOIN " + getTinfoDomainDescriptor() + " DD ON (PDM.DomainId = DD.DomainId) " +
                    " WHERE DD.DomainId = " + dd.getDomainId() +
                    " AND PD.Container = DD.Container " +
                    " ) ";
            new SqlExecutor(getExpSchema()).execute(deleteTypePropsSql);

            if (objIdsToDelete.length > 0)
            {
                // now cleanup the object table entries from the list we made, but make sure they don't have
                // other properties attached to them
                String deleteObjSql = "DELETE FROM " + getTinfoObject() +
                        " WHERE ObjectId IN ( " + sqlIN.toString() + " ) " +
                        " AND NOT EXISTS (SELECT * FROM " + getTinfoObjectProperty() + " OP " +
                        " WHERE  OP.ObjectId = " + getTinfoObject() + ".ObjectId)";
                new SqlExecutor(getExpSchema()).execute(deleteObjSql);

                if (ownerObjIds.length > 0)
                {
                    sep = "";
                    sqlIN = new StringBuilder();
                    for (Integer id : ownerObjIds)
                    {
                        sqlIN.append(sep).append(id);
                        sep = ", ";
                    }
                    String deleteOwnerSql = "DELETE FROM " + getTinfoObject() +
                            " WHERE ObjectId IN ( " + sqlIN.toString() + " ) " +
                            " AND NOT EXISTS (SELECT * FROM " + getTinfoObject() + " SUBO " +
                            " WHERE SUBO.OwnerObjectId = " + getTinfoObject() + ".ObjectId)";
                    new SqlExecutor(getExpSchema()).execute(deleteOwnerSql);
                }
            }
            // whew!
            clearCaches();
            t.commit();
        }
    }

    public static void deleteDomain(String domainURI, Container container) throws DomainNotFoundException
    {
        DomainDescriptor dd = getDomainDescriptor(domainURI, container);
        String msg;

        if (null == dd)
            throw new DomainNotFoundException(domainURI);

        if (!dd.getContainer().getId().equals(container.getId()))
        {
            // this domain was not created in this folder. Allow if in the project-level root
            if (!dd.getProject().getId().equals(container.getId()))
            {
                msg = "DeleteDomain: Domain can only be deleted in original container or from the project root "
                        + "\nDomain: " + domainURI + " project " + dd.getProject().getName() + " original container " + dd.getContainer().getPath();
                _log.error(msg);
                throw new RuntimeException(msg);
            }
        }
        try (Transaction transaction = getExpSchema().getScope().ensureTransaction())
        {
            String selectPDsToDelete = "SELECT DISTINCT PDM.PropertyId " +
                    " FROM " + getTinfoPropertyDomain() + " PDM " +
                    " INNER JOIN " + getTinfoDomainDescriptor() + " DD ON (PDM.DomainId = DD.DomainId) " +
                    " WHERE DD.DomainId = ? ";

            Integer[] pdIdsToDelete = new SqlSelector(getExpSchema(), selectPDsToDelete, dd.getDomainId()).getArray(Integer.class);

            String deletePDMs = "DELETE FROM " + getTinfoPropertyDomain() +
                    " WHERE DomainId =  " +
                    " (SELECT DD.DomainId FROM " + getTinfoDomainDescriptor() + " DD " +
                    " WHERE DD.DomainId = ? )";
            new SqlExecutor(getExpSchema()).execute(deletePDMs, dd.getDomainId());

            if (pdIdsToDelete.length > 0)
            {
                String sep = "";
                StringBuilder sqlIN = new StringBuilder();
                for (Integer id : pdIdsToDelete)
                {
                    PropertyService.get().deleteValidatorsAndFormats(container, id);

                    sqlIN.append(sep);
                    sqlIN.append(id);
                    sep = ", ";
                }

                String deletePDs = "DELETE FROM " + getTinfoPropertyDescriptor() +
                        " WHERE PropertyId IN ( " + sqlIN.toString() + " ) " +
                        "AND Container = ? " +
                        "AND NOT EXISTS (SELECT * FROM " + getTinfoObjectProperty() + " OP " +
                        "WHERE OP.PropertyId = " + getTinfoPropertyDescriptor() + ".PropertyId) " +
                        "AND NOT EXISTS (SELECT * FROM " + getTinfoPropertyDomain() + " PDM " +
                        "WHERE PDM.PropertyId = " + getTinfoPropertyDescriptor() + ".PropertyId)";

                new SqlExecutor(getExpSchema()).execute(deletePDs, dd.getContainer().getId());
            }

            String deleteDD = "DELETE FROM " + getTinfoDomainDescriptor() +
                    " WHERE DomainId = ? " +
                    "AND NOT EXISTS (SELECT * FROM " + getTinfoPropertyDomain() + " PDM " +
                    "WHERE PDM.DomainId = " + getTinfoDomainDescriptor() + ".DomainId)";

            new SqlExecutor(getExpSchema()).execute(deleteDD, dd.getDomainId());
            clearCaches();

            transaction.commit();
        }
    }


    public static void deleteAllObjects(Container c, User user) throws ValidationException
    {
        Container projectContainer = c.getProject();
        if (null == projectContainer)
            projectContainer = c;

        try (Transaction transaction = getExpSchema().getScope().ensureTransaction())
        {
            if (!c.equals(projectContainer))
            {
                copyDescriptors(c, projectContainer);
            }

            SqlExecutor executor = new SqlExecutor(getExpSchema());

            // Owned objects should be in same container, so this should work
            String deleteObjPropSql = "DELETE FROM " + getTinfoObjectProperty() + " WHERE  ObjectId IN (SELECT ObjectId FROM " + getTinfoObject() + " WHERE Container = ?)";
            executor.execute(deleteObjPropSql, c);
            String deleteObjSql = "DELETE FROM " + getTinfoObject() + " WHERE Container = ?";
            executor.execute(deleteObjSql, c);

            // delete property validator references on property descriptors
            PropertyService.get().deleteValidatorsAndFormats(c);

            // Drop tables directly and allow bulk delete calls below to clean up rows in exp.propertydescriptor,
            // exp.domaindescriptor, etc
            String selectSQL = "SELECT * FROM " + getTinfoDomainDescriptor() + " WHERE Container = ?";
            Collection<DomainDescriptor> dds = new SqlSelector(getExpSchema(), selectSQL, c).getCollection(DomainDescriptor.class);
            for (DomainDescriptor dd : dds)
            {
                StorageProvisioner.drop(PropertyService.get().getDomain(dd.getDomainId()));
            }

            String deletePropDomSqlPD = "DELETE FROM " + getTinfoPropertyDomain() + " WHERE PropertyId IN (SELECT PropertyId FROM " + getTinfoPropertyDescriptor() + " WHERE Container = ?)";
            executor.execute(deletePropDomSqlPD, c);
            String deletePropDomSqlDD = "DELETE FROM " + getTinfoPropertyDomain() + " WHERE DomainId IN (SELECT DomainId FROM " + getTinfoDomainDescriptor() + " WHERE Container = ?)";
            executor.execute(deletePropDomSqlDD, c);
            String deleteDomSql = "DELETE FROM " + getTinfoDomainDescriptor() + " WHERE Container = ?";
            executor.execute(deleteDomSql, c);
            // now delete the prop descriptors that are referenced in this container only
            String deletePropSql = "DELETE FROM " + getTinfoPropertyDescriptor() + " WHERE Container = ?";
            executor.execute(deletePropSql, c);

            clearCaches();
            transaction.commit();
        }
    }

    private static void copyDescriptors(final Container c, final Container project) throws ValidationException
    {
        _log.debug("OntologyManager.copyDescriptors  " + c.getName() + " " + project.getName());

        // if c is (was) a project, then nothing to do
        if (c.getId().equals(project.getId()))
            return;

        // check to see if any Properties defined in this folder are used in other folders.
        // if so we will make a copy of all PDs and DDs to ensure no orphans
        String sql = " SELECT O.ObjectURI, O.Container, PD.PropertyId, PD.PropertyURI  " +
                " FROM " + getTinfoPropertyDescriptor() + " PD " +
                " INNER JOIN " + getTinfoObjectProperty() + " OP ON PD.PropertyId = OP.PropertyId" +
                " INNER JOIN " + getTinfoObject() + " O ON (O.ObjectId = OP.ObjectId) " +
                " WHERE PD.Container = ? " +
                " AND O.Container <> PD.Container ";

        final Map<String, ObjectProperty> mObjsUsingMyProps = new HashMap<>();
        final StringBuilder sqlIn = new StringBuilder();
        final StringBuilder sep = new StringBuilder();

        if (_log.isDebugEnabled())
        {
            try (ResultSet rs = new SqlSelector(getExpSchema(), sql, c).getResultSet())
            {
                ResultSetUtil.logData(rs);
            }
            catch (SQLException x)
            {
                throw new RuntimeException(x);
            }
        }

        new SqlSelector(getExpSchema(), sql, c).forEach(new Selector.ForEachBlock<ResultSet>()
        {
            @Override
            public void exec(ResultSet rs) throws SQLException
            {
                String objURI = rs.getString(1);
                String objContainer = rs.getString(2);
                Integer propId = rs.getInt(3);
                String propURI = rs.getString(4);

                sqlIn.append(sep).append(propId);

                if (sep.length() == 0)
                    sep.append(", ");

                Map<String, ObjectProperty> mtemp = getPropertyObjects(ContainerManager.getForId(objContainer), objURI);

                if (null != mtemp)
                {
                    for (Map.Entry<String, ObjectProperty> entry : mtemp.entrySet())
                    {
                        entry.getValue().setPropertyId(0);
                        if (entry.getValue().getPropertyURI().equals(propURI))
                            mObjsUsingMyProps.put(entry.getKey(), entry.getValue());
                    }
                }
            }
        });

        // For each property that is referenced outside its container, get the
        // domains that it belongs to and the other properties in those domains
        // so we can make copies of those domains and properties
        // Restrict it to properties and domains also in the same container

        if (mObjsUsingMyProps.size() > 0)
        {
            sql = "SELECT PD.PropertyURI, DD.DomainURI " +
                    " FROM " + getTinfoPropertyDescriptor() + " PD " +
                    " LEFT JOIN (" + getTinfoPropertyDomain() + " PDM " +
                    " INNER JOIN " + getTinfoPropertyDomain() + " PDM2 ON (PDM.DomainId = PDM2.DomainId) " +
                    " INNER JOIN " + getTinfoDomainDescriptor() + " DD ON (DD.DomainId = PDM.DomainId)) " +
                    " ON (PD.PropertyId = PDM2.PropertyId) " +
                    " WHERE PDM.PropertyId IN (" + sqlIn.toString() + ") " +
                    " OR PD.PropertyId IN (" + sqlIn.toString() + ") ";

            if (_log.isDebugEnabled())
            {
                try (ResultSet rs = new SqlSelector(getExpSchema(), sql).getResultSet())
                {
                    ResultSetUtil.logData(rs, _log);
                }
                catch (SQLException x)
                {
                    throw new RuntimeException(x);
                }
            }

            new SqlSelector(getExpSchema(), sql).forEach(new Selector.ForEachBlock<ResultSet>()
            {
                @Override
                public void exec(ResultSet rsMyProps) throws SQLException
                {
                    String propUri = rsMyProps.getString(1);
                    String domUri = rsMyProps.getString(2);
                    PropertyDescriptor pd = getPropertyDescriptor(propUri, c);

                    if (pd.getContainer().getId().equals(c.getId()))
                    {
                        propDescCache.remove(getCacheKey(pd));
                        domainPropertiesCache.clear();
                        pd.setContainer(project);
                        pd.setProject(project);
                        pd.setPropertyId(0);
                        pd = ensurePropertyDescriptor(pd);
                    }

                    if (null != domUri)
                    {
                        DomainDescriptor dd = getDomainDescriptor(domUri, c);
                        if (dd.getContainer().getId().equals(c.getId()))
                        {
                            domainDescByURICache.remove(getURICacheKey(dd));
                            domainDescByIDCache.remove(getIDCacheKey(dd));
                            domainPropertiesCache.clear();
                            dd = dd.edit()
                                    .setContainer(project)
                                    .setProject(project)
                                    .setDomainId(0)
                                    .build();
                            dd = ensureDomainDescriptor(dd);
                            ensurePropertyDomain(pd, dd);
                        }
                    }
                }
            });

            clearCaches();

            // now unhook the objects that refer to my properties and rehook them to the properties in their own project
            for (ObjectProperty op : mObjsUsingMyProps.values())
            {
                deleteProperty(op.getObjectURI(), op.getPropertyURI(), op.getContainer(), c);
                insertProperties(op.getContainer(), op.getObjectURI(), op);
            }
        }
    }


    public static void moveContainer(@NotNull final Container c, @NotNull Container oldParent, @NotNull Container newParent) throws SQLException
    {
        _log.debug("OntologyManager.moveContainer  " + c.getName() + " " + oldParent.getName() + "->" + newParent.getName());

        final Container oldProject = oldParent.getProject();
        Container newProject = newParent.getProject();
        if (null == newProject) // if container is promoted to a project
            newProject = c.getProject();

        if ((null != oldProject) && oldProject.getId().equals(newProject.getId()))
        {
            //the folder is being moved within the same project. No problems here
            return;
        }

        try (Transaction transaction = getExpSchema().getScope().ensureTransaction())
        {
            clearCaches();

            if (_log.isDebugEnabled())
            {
                try (ResultSet rs = new SqlSelector(getExpSchema(), "SELECT * FROM " + getTinfoPropertyDescriptor() + " WHERE Container='" + c.getId() + "'").getResultSet())
                {
                    ResultSetUtil.logData(rs, _log);
                }
            }

            // update project of any descriptors in folder just moved
            TableInfo pdTable = getTinfoPropertyDescriptor();
            String sql = "UPDATE " + pdTable + " SET Project = ? WHERE Container = ?";

            // TODO The IN clause is a temporary work around solution to avoid unique key violation error when moving study folders.
            // Issue 30477: exclude project level properties descriptors (such as Study) that already exist
            sql += " AND PropertyUri NOT IN (SELECT PropertyUri FROM " + pdTable + " WHERE Project = ? AND PropertyUri IN (SELECT PropertyUri FROM " + pdTable + " WHERE Container = ?))";

            new SqlExecutor(getExpSchema()).execute(sql, newProject, c, newProject, c);

            if (_log.isDebugEnabled())
            {
                try (ResultSet rs = new SqlSelector(getExpSchema(), "SELECT * FROM " + getTinfoDomainDescriptor() + " WHERE Container='" + c.getId() + "'").getResultSet())
                {
                    ResultSetUtil.logData(rs, _log);
                }
            }

            TableInfo ddTable = getTinfoDomainDescriptor();
            sql = "UPDATE " + ddTable + " SET Project = ? WHERE Container = ?";

            // TODO The IN clause is a temporary work around solution to avoid unique key violation error when moving study folders.
            // Issue 30477: exclude project level domain descriptors (such as Study) that already exist
            sql += " AND DomainUri NOT IN (SELECT DomainUri FROM " + ddTable + " WHERE Project = ? AND DomainUri IN (SELECT DomainUri FROM " + ddTable + " WHERE Container = ?))";

            new SqlExecutor(getExpSchema()).execute(sql, newProject, c, newProject, c);

            if (null == oldProject) // if container was a project & demoted I'm done
            {
                transaction.commit();
                return;
            }

            // this method makes sure I'm not getting rid of descriptors used by another folder
            // it is shared by ContainerDelete
            copyDescriptors(c, oldProject);

            // if my objects refer to project-scoped properties I need a copy of those properties
            sql = " SELECT O.ObjectURI, PD.PropertyURI, PD.PropertyId, PD.Container  " +
                    " FROM " + getTinfoPropertyDescriptor() + " PD " +
                    " INNER JOIN " + getTinfoObjectProperty() + " OP ON PD.PropertyId = OP.PropertyId" +
                    " INNER JOIN " + getTinfoObject() + " O ON (O.ObjectId = OP.ObjectId) " +
                    " WHERE O.Container = ? " +
                    " AND O.Container <> PD.Container " +
                    " AND PD.Project NOT IN (?,?) ";

            if (_log.isDebugEnabled())
            {
                try (ResultSet rs = new SqlSelector(getExpSchema(), sql, c, _sharedContainer, newProject).getResultSet())
                {
                    ResultSetUtil.logData(rs, _log);
                }
            }


            final Map<String, ObjectProperty> mMyObjsThatRefProjProps = new HashMap<>();
            final StringBuilder sqlIn = new StringBuilder();
            final StringBuilder sep = new StringBuilder();

            new SqlSelector(getExpSchema(), sql, c, _sharedContainer, newProject).forEach(new Selector.ForEachBlock<ResultSet>()
            {
                @Override
                public void exec(ResultSet rs) throws SQLException
                {
                    String objURI = rs.getString(1);
                    String propURI = rs.getString(2);
                    Integer propId = rs.getInt(3);

                    sqlIn.append(sep).append(propId);

                    if (sep.length() == 0)
                        sep.append(", ");

                    Map<String, ObjectProperty> mtemp = getPropertyObjects(c, objURI);

                    if (null != mtemp)
                    {
                        for (Map.Entry<String, ObjectProperty> entry : mtemp.entrySet())
                        {
                            if (entry.getValue().getPropertyURI().equals(propURI))
                                mMyObjsThatRefProjProps.put(entry.getKey(), entry.getValue());
                        }
                    }
                }
            });

            // this sql gets all properties i ref and the domains they belong to and the
            // other properties in those domains
            //todo  what about materialsource ?
            if (mMyObjsThatRefProjProps.size() > 0)
            {
                sql = "SELECT PD.PropertyURI, DD.DomainURI, PD.PropertyId " +
                        " FROM " + getTinfoPropertyDescriptor() + " PD " +
                        " LEFT JOIN (" + getTinfoPropertyDomain() + " PDM " +
                        " INNER JOIN " + getTinfoPropertyDomain() + " PDM2 ON (PDM.DomainId = PDM2.DomainId) " +
                        " INNER JOIN " + getTinfoDomainDescriptor() + " DD ON (DD.DomainId = PDM.DomainId)) " +
                        " ON (PD.PropertyId = PDM2.PropertyId) " +
                        " WHERE PDM.PropertyId IN (" + sqlIn + " ) ";

                if (_log.isDebugEnabled())
                {
                    try (ResultSet rs = new SqlSelector(getExpSchema(), sql).getResultSet())
                    {
                        ResultSetUtil.logData(rs, _log);
                    }
                }

                final Container fNewProject = newProject;

                new SqlSelector(getExpSchema(), sql).forEach(new Selector.ForEachBlock<ResultSet>()
                {
                    @Override
                    public void exec(ResultSet rsPropsRefdByMe) throws SQLException
                    {
                        String propUri = rsPropsRefdByMe.getString(1);
                        String domUri = rsPropsRefdByMe.getString(2);
                        PropertyDescriptor pd = getPropertyDescriptor(propUri, oldProject);

                        if (null != pd)
                        {
                            // To prevent iterating over a property descriptor update more than once
                            // we check to make sure both the container and project are equivalent to the updated
                            // location
                            if (!pd.getContainer().equals(c) || !pd.getProject().equals(fNewProject))
                            {
                                pd.setContainer(c);
                                pd.setProject(fNewProject);
                                pd.setPropertyId(0);
                            }

                            pd = ensurePropertyDescriptor(pd);
                        }

                        if (null != domUri)
                        {
                            DomainDescriptor dd = getDomainDescriptor(domUri, oldProject);

                            // To prevent iterating over a domain descriptor update more than once
                            // we check to make sure both the container and project are equivalent to the updated
                            // location
                            if (!dd.getContainer().equals(c) || !dd.getProject().equals(fNewProject))
                            {
                                dd = dd.edit().setContainer(c).setProject(fNewProject).setDomainId(0).build();
                            }

                            dd = ensureDomainDescriptor(dd);
                            ensurePropertyDomain(pd, dd);
                        }
                    }
                });

                for (ObjectProperty op : mMyObjsThatRefProjProps.values())
                {
                    deleteProperty(op.getObjectURI(), op.getPropertyURI(), op.getContainer(), oldProject);
                    // Treat it as new so it's created in the target container as needed
                    op.setPropertyId(0);
                    insertProperties(op.getContainer(), op.getObjectURI(), op);
                }
                clearCaches();
            }

            transaction.commit();
        }
        catch (ValidationException ve)
        {
            throw new SQLException(ve.getMessage());
        }
    }

    private static PropertyDescriptor ensurePropertyDescriptor(String propertyURI, PropertyType type, String name, Container container) throws SQLException
    {
        PropertyDescriptor pdNew = new PropertyDescriptor(propertyURI, type, name, container);
        return ensurePropertyDescriptor(pdNew);
    }


    private static PropertyDescriptor ensurePropertyDescriptor(PropertyDescriptor pdIn)
    {
        if (null == pdIn.getContainer())
        {
            assert false : "Container should be set on PropertyDescriptor";
            pdIn.setContainer(_sharedContainer);
        }

        PropertyDescriptor pd = getPropertyDescriptor(pdIn.getPropertyURI(), pdIn.getContainer());
        if (null == pd)
        {
            assert pdIn.getPropertyId() == 0;
            /* return 1 if inserted 0 if not inserted, uses OUT parameter for new PropertyDescriptor */
            PropertyDescriptor[] out = new PropertyDescriptor[1];
            int rowcount = insertPropertyIfNotExists(null, pdIn, out);
            pd = out[0];
            if (1 == rowcount && null != pd)
            {
                propDescCache.put(getCacheKey(pd), pd);
                return pd;
            }
            if (null == pd)
            {
                throw OptimisticConflictException.create(Table.ERROR_DELETED);
            }
        }

        if (pd.equals(pdIn))
        {
            return pd;
        }
        else
        {
            List<String> colDiffs = comparePropertyDescriptors(pdIn, pd);

            if (colDiffs.size() == 0)
            {
                // if the descriptor differs by container only and the requested descriptor is in the project fldr
                if (!pdIn.getContainer().getId().equals(pd.getContainer().getId()) &&
                        pdIn.getContainer().getId().equals(pdIn.getProject().getId()))
                {
                    pdIn.setPropertyId(pd.getPropertyId());
                    pd = updatePropertyDescriptor(pdIn);
                }
                return pd;
            }

            // you are allowed to update if you are coming from the project root, or if  you are in the container
            // in which the descriptor was created
            boolean fUpdateIfExists = false;
            if (pdIn.getContainer().getId().equals(pd.getContainer().getId())
                    || pdIn.getContainer().getId().equals(pdIn.getProject().getId()))
                fUpdateIfExists = true;


            boolean fMajorDifference = false;
            if (colDiffs.toString().contains("RangeURI") || colDiffs.toString().contains("PropertyType"))
                fMajorDifference = true;

            String errmsg = "ensurePropertyDescriptor:  descriptor In different from Found for " + colDiffs.toString() +
                    "\n\t Descriptor In: " + pdIn +
                    "\n\t Descriptor Found: " + pd;

            if (fUpdateIfExists)
            {
                //todo:  pass list of cols to update
                pdIn.setPropertyId(pd.getPropertyId());
                pd = updatePropertyDescriptor(pdIn);
                if (fMajorDifference)
                    _log.debug(errmsg);
            }
            else
            {
                if (fMajorDifference)
                    _log.error(errmsg);
                else
                    _log.debug(errmsg);
            }
        }
        return pd;
    }


    private static int insertPropertyIfNotExists(User user, PropertyDescriptor pd, PropertyDescriptor[] out)
    {
        TableInfo t = getTinfoPropertyDescriptor();
        SQLFragment sql = new SQLFragment();
        sql.append("INSERT INTO exp.propertydescriptor (" +
                "propertyuri, ontologyuri, name, storagecolumnname, description, rangeuri, concepturi, label, searchterms, semantictype, " +
                "format, container, project, lookupcontainer, lookupschema, lookupquery, defaultvaluetype, hidden, " +
                "mvenabled, importaliases, url, shownininsertview, showninupdateview, shownindetailsview, dimension, " +
                "measure, scale, recommendedvariable, defaultscale, createdby, created, modifiedby, modified, facetingbehaviortype, " +
                "phi, redactedText, excludefromshifting, mvindicatorstoragecolumnname)\n");
        sql.append("SELECT " +
                "? as propertyuri, " +
                "? as ontolotyuri, " +
                "? as name, " +
                "? as storagecolumnname, " +
                "? as description, " +
                "? as rangeuri, " +
                "? as concepturi, " +
                "? as label, " +
                "? as searchterms, " +
                "? as semantictype, " +
                "? as format, " +
                "? as container, " +
                "? as project, " +
                "? as lookupcontainer, " +
                "? as lookupschema, " +
                "? as lookupquery, " +
                "? as defaultvaluetype, " +
                "? as hidden, " +
                "? as mvenabled, " +
                "? as importaliases, " +
                "? as url, " +
                "? as shownininsertview, " +
                "? as showninupdateview, " +
                "? as shownindetailsview, " +
                "? as dimension, " +
                "? as measure, " +
                "? as scale, " +
                "? as recommendedvariable, " +
                "? as defaultscale, " +
                "cast(? as int)  as createdby, " +
                "{fn now()} as created, " +
                "cast(? as int) as modifiedby, " +
                "{fn now()} as modified, " +
                "? as facetingbehaviortype, " +
                "? as phi, " +
                "? as redactedText, " +
                "? as excludefromshifting, " +
                "? as mvindicatorstoragecolumnname\n");
        sql.append("WHERE NOT EXISTS (SELECT propertyid FROM exp.propertydescriptor WHERE propertyuri=? AND container=?);\n");

        sql.add(pd.getPropertyURI());
        sql.add(pd.getOntologyURI());
        sql.add(pd.getName());
        sql.add(pd.getStorageColumnName());
        sql.add(pd.getDescription());
        sql.add(pd.getRangeURI());
        sql.add(pd.getConceptURI());
        sql.add(pd.getLabel());
        sql.add(pd.getSearchTerms());
        sql.add(pd.getSemanticType());
        sql.add(pd.getFormat());
        sql.add(pd.getContainer());
        sql.add(pd.getProject());
        sql.add(pd.getLookupContainer());
        sql.add(pd.getLookupSchema());
        sql.add(pd.getLookupQuery());
        sql.add(pd.getDefaultValueType());
        sql.add(pd.isHidden());
        sql.add(pd.isMvEnabled());
        sql.add(pd.getImportAliases());
        sql.add(pd.getURL());
        sql.add(pd.isShownInInsertView());
        sql.add(pd.isShownInUpdateView());
        sql.add(pd.isShownInDetailsView());
        sql.add(pd.isDimension());
        sql.add(pd.isMeasure());
        sql.add(pd.getScale());
        sql.add(pd.isRecommendedVariable());
        sql.add(pd.getDefaultScale());
        sql.add(user); // createdby
        // created
        sql.add(user); // modifiedby
        // modified
        sql.add(pd.getFacetingBehaviorType());
        sql.add(pd.getPHI());
        sql.add(pd.getRedactedText());
        sql.add(pd.isExcludeFromShifting());
        sql.add(pd.getMvIndicatorStorageColumnName());
        // WHERE
        sql.add(pd.getPropertyURI());
        sql.add(pd.getContainer());

        int rowcount = (new SqlExecutor(t.getSchema())).execute(sql);

        SQLFragment reselect = new SQLFragment("SELECT * FROM exp.propertydescriptor WHERE propertyuri=? AND container=?", pd.getPropertyURI(), pd.getContainer());
        out[0] = (new SqlSelector(t.getSchema(), reselect).getObject(PropertyDescriptor.class));
        return rowcount;
    }


    private static List<String> comparePropertyDescriptors(PropertyDescriptor pdIn, PropertyDescriptor pd)
    {
        List<String> colDiffs = new ArrayList<>();

        // if the returned pd is in a different project, it better be the shared project
        if (!pd.getProject().equals(pdIn.getProject()) && !pd.getProject().equals(_sharedContainer))
            colDiffs.add("Project");

        // check the pd values that can't change
        if (!pd.getRangeURI().equals(pdIn.getRangeURI()))
            colDiffs.add("RangeURI");
        if (!pd.getPropertyType().equals(pdIn.getPropertyType()))
            colDiffs.add("PropertyType");

        if (pdIn.getPropertyId() != 0 && !(pd.getPropertyId() == pdIn.getPropertyId()))
            colDiffs.add("PropertyId");

        if (Objects.equals(pdIn.getName(), pd.getName()))
            colDiffs.add("Name");

        if (Objects.equals(pdIn.getConceptURI(), pd.getConceptURI()))
            colDiffs.add("ConceptURI");

        if (Objects.equals(pdIn.getDescription(), pd.getDescription()))
            colDiffs.add("Description");

        if (Objects.equals(pdIn.getFormat(), pd.getFormat()))
            colDiffs.add("Format");

        if (Objects.equals(pdIn.getLabel(), pd.getLabel()))
            colDiffs.add("Label");

        if (Objects.equals(pdIn.getOntologyURI(), pd.getOntologyURI()))
            colDiffs.add("OntologyURI");

        if (Objects.equals(pdIn.getSearchTerms(), pd.getSearchTerms()))
            colDiffs.add("SearchTerms");

        if (Objects.equals(pdIn.getSemanticType(), pd.getSemanticType()))
            colDiffs.add("SemanticType");

        if (pdIn.isHidden() != pd.isHidden())
            colDiffs.add("IsHidden");

        if (pdIn.isMvEnabled() != pd.isMvEnabled())
            colDiffs.add("IsMvEnabled");

        if (Objects.equals(pdIn.getLookupContainer(), pd.getLookupContainer()))
            colDiffs.add("LookupContainer");

        if (Objects.equals(pdIn.getLookupSchema(), pd.getLookupSchema()))
            colDiffs.add("LookupSchema");

        if (Objects.equals(pdIn.getLookupQuery(), pd.getLookupQuery()))
            colDiffs.add("LookupQuery");

        return colDiffs;
    }

    public static DomainDescriptor ensureDomainDescriptor(String domainURI, String name, Container container)
    {
        DomainDescriptor dd = new DomainDescriptor.Builder(domainURI, container).setName(name).build();
        return ensureDomainDescriptor(dd);
    }

    @NotNull
    private static DomainDescriptor ensureDomainDescriptor(DomainDescriptor ddIn)
    {
        DomainDescriptor dd = getDomainDescriptor(ddIn.getDomainURI(), ddIn.getContainer());
        String ddInContainerId = ddIn.getContainer().getId();

        if (null == dd)
        {
            try
            {
                Table.insert(null, getTinfoDomainDescriptor(), ddIn);
                return getDomainDescriptor(ddIn.getDomainURI(), ddIn.getContainer());
            }
            catch (RuntimeSQLException x)
            {
                // might be an optimistic concurrency problem see 16126
                dd = getDomainDescriptor(ddIn.getDomainURI(), ddIn.getContainer());
                if (null == dd)
                    throw x;
            }
        }

        List<String> colDiffs = compareDomainDescriptors(ddIn, dd);

        if (colDiffs.size() == 0)
        {
            // if the descriptor differs by container only and the requested descriptor is in the project fldr
            if (!ddInContainerId.equals(dd.getContainer().getId()) && ddInContainerId.equals(ddIn.getProject().getId()))
            {
                dd = updateDomainDescriptor(ddIn.edit().setDomainId(dd.getDomainId()).build());
            }
            return dd;
        }

        // you are allowed to update if you are coming from the project root, or if  you are in the container
        // in which the descriptor was created
        boolean fUpdateIfExists = false;
        if (ddInContainerId.equals(dd.getContainer().getId()) || ddInContainerId.equals(ddIn.getProject().getId()))
            fUpdateIfExists = true;

        boolean fMajorDifference = false;
        if (colDiffs.toString().contains("RangeURI") || colDiffs.toString().contains("PropertyType"))
            fMajorDifference = true;

        String errmsg = "ensureDomainDescriptor:  descriptor In different from Found for " + colDiffs.toString() +
                "\n\t Descriptor In: " + ddIn +
                "\n\t Descriptor Found: " + dd;

        if (fUpdateIfExists)
        {
            dd = updateDomainDescriptor(ddIn.edit().setDomainId(dd.getDomainId()).build());
            if (fMajorDifference)
                _log.debug(errmsg);
        }
        else
        {
            if (fMajorDifference)
                _log.error(errmsg);
            else
                _log.debug(errmsg);
        }

        return dd;
    }

    private static List<String> compareDomainDescriptors(DomainDescriptor ddIn, DomainDescriptor dd)
    {
        List<String> colDiffs = new ArrayList<>();

        if (ddIn.getDomainId() != 0 && !(dd.getDomainId() == ddIn.getDomainId()))
            colDiffs.add("DomainId");

        if (ddIn.getName() != null && !dd.getName().equals(ddIn.getName()))
            colDiffs.add("Name");

        return colDiffs;
    }

    private static void ensurePropertyDomain(PropertyDescriptor pd, DomainDescriptor dd)
    {
        ensurePropertyDomain(pd, dd, 0);
    }

    public static PropertyDescriptor ensurePropertyDomain(PropertyDescriptor pd, DomainDescriptor dd, int sortOrder)
    {
        if (null == pd)
            throw new IllegalArgumentException("Must supply a PropertyDescriptor");
        if (null == dd)
            throw new IllegalArgumentException("Must supply a DomainDescriptor");

        // Consider: We should check that the pd and dd have been persisted (aka have a non-zero id)

        if (!pd.getContainer().equals(dd.getContainer())
                && !pd.getProject().equals(_sharedContainer))
            throw new IllegalStateException("ensurePropertyDomain:  property " + pd.getPropertyURI() + " not in same container as domain " + dd.getDomainURI());

        SQLFragment sqlInsert = new SQLFragment("INSERT INTO " + getTinfoPropertyDomain() + " ( PropertyId, DomainId, Required, SortOrder ) " +
                " SELECT ?, ?, ?, ? WHERE NOT EXISTS (SELECT * FROM " + getTinfoPropertyDomain() +
                " WHERE PropertyId=? AND DomainId=?)");
        sqlInsert.add(pd.getPropertyId());
        sqlInsert.add(dd.getDomainId());
        sqlInsert.add(pd.isRequired());
        sqlInsert.add(sortOrder);
        sqlInsert.add(pd.getPropertyId());
        sqlInsert.add(dd.getDomainId());
        int count = new SqlExecutor(getExpSchema()).execute(sqlInsert);
        // if 0 rows affected, we should do an update to make sure required is correct
        if (count == 0)
        {
            SQLFragment sqlUpdate = new SQLFragment("UPDATE " + getTinfoPropertyDomain() + " SET Required = ?, SortOrder = ? WHERE PropertyId=? AND DomainId= ?");
            sqlUpdate.add(pd.isRequired());
            sqlUpdate.add(sortOrder);
            sqlUpdate.add(pd.getPropertyId());
            sqlUpdate.add(dd.getDomainId());
            new SqlExecutor(getExpSchema()).execute(sqlUpdate);
        }
        domainPropertiesCache.remove(getURICacheKey(dd));
        return pd;
    }


    private static void insertProperties(Container c, List<ObjectProperty> props) throws SQLException, ValidationException
    {
        HashMap<String, PropertyDescriptor> descriptors = new HashMap<>();
        HashMap<String, Integer> objects = new HashMap<>();
        List<ValidationError> errors = new ArrayList<>();
        // assert !c.equals(ContainerManager.getRoot());
        // TODO - make user a parameter to this method 
        User user = HttpView.hasCurrentView() ? HttpView.currentContext().getUser() : null;
        ValidatorContext validatorCache = new ValidatorContext(c, user);

        for (ObjectProperty property : props)
        {
            if (null == property)
                continue;

            PropertyDescriptor pd = descriptors.get(property.getPropertyURI());
            if (0 == property.getPropertyId())
            {
                if (null == pd)
                {
                    PropertyDescriptor pdIn = new PropertyDescriptor(property.getPropertyURI(), property.getPropertyType(), property.getName(), c);
                    pdIn.setFormat(property.getFormat());
                    pd = getPropertyDescriptor(pdIn.getPropertyURI(), pdIn.getContainer());

                    if (null == pd)
                        pd = ensurePropertyDescriptor(pdIn);

                    descriptors.put(property.getPropertyURI(), pd);
                }
                property.setPropertyId(pd.getPropertyId());
            }
            if (0 == property.getObjectId())
            {
                Integer objectId = objects.get(property.getObjectURI());
                if (null == objectId)
                {
                    // I'm assuming all properties are in the same container
                    objectId = ensureObject(property.getContainer(), property.getObjectURI(), property.getObjectOwnerId());
                    objects.put(property.getObjectURI(), objectId);
                }
                property.setObjectId(objectId);
            }
            if (pd == null)
            {
                pd = getPropertyDescriptor(property.getPropertyId());
            }
            validateProperty(PropertyService.get().getPropertyValidators(pd), pd, property, errors, validatorCache);
        }
        if (!errors.isEmpty())
            throw new ValidationException(errors);
        insertPropertiesBulk(c, props);
    }


    private static void insertPropertiesBulk(Container container, List<? extends PropertyRow> props) throws SQLException
    {
        List<List<?>> floats = new ArrayList<>();
        List<List<?>> dates = new ArrayList<>();
        List<List<?>> strings = new ArrayList<>();
        List<List<?>> mvIndicators = new ArrayList<>();

        for (PropertyRow property : props)
        {
            if (null == property)
                continue;

            int objectId = property.getObjectId();
            int propertyId = property.getPropertyId();
            String mvIndicator = property.getMvIndicator();
            assert mvIndicator == null || MvUtil.isMvIndicator(mvIndicator, container) : "Attempt to insert an invalid missing value indicator: " + mvIndicator;

            if (null != property.getFloatValue())
                floats.add(Arrays.asList(objectId, propertyId, property.getFloatValue(), mvIndicator));
            else if (null != property.getDateTimeValue())
                dates.add(Arrays.asList(objectId, propertyId, new java.sql.Timestamp(property.getDateTimeValue().getTime()), mvIndicator));
            else if (null != property.getStringValue())
            {
                String string = property.getStringValue();
                // UNDONE - handle truncation in some other way?
                if (string.length() > PropertyStorageSpec.DEFAULT_SIZE)
                {
                    throw new SQLException("String value too long in field " + getPropertyDescriptor(propertyId).getName() + ": " + (string.length() < 150 ? string : string.substring(0, 149) + "..."));
                }
                strings.add(Arrays.asList(objectId, propertyId, string, mvIndicator));
            }
            else if (null != mvIndicator)
            {
                mvIndicators.add(Arrays.<Object>asList(objectId, propertyId, property.getTypeTag(), mvIndicator));
            }
        }

        assert getExpSchema().getScope().isTransactionActive();

        if (dates.size() > 0)
        {
            String sql = "INSERT INTO " + getTinfoObjectProperty().toString() + " (ObjectId, PropertyId, TypeTag, DateTimeValue, MvIndicator) VALUES (?,?,'d',?, ?)";
            Table.batchExecute(getExpSchema(), sql, dates);
        }

        if (floats.size() > 0)
        {
            String sql = "INSERT INTO " + getTinfoObjectProperty().toString() + " (ObjectId, PropertyId, TypeTag, FloatValue, MvIndicator) VALUES (?,?,'f',?, ?)";
            Table.batchExecute(getExpSchema(), sql, floats);
        }

        if (strings.size() > 0)
        {
            String sql = "INSERT INTO " + getTinfoObjectProperty().toString() + " (ObjectId, PropertyId, TypeTag, StringValue, MvIndicator) VALUES (?,?,'s',?, ?)";
            Table.batchExecute(getExpSchema(), sql, strings);
        }

        if (mvIndicators.size() > 0)
        {
            String sql = "INSERT INTO " + getTinfoObjectProperty().toString() + " (ObjectId, PropertyId, TypeTag, MvIndicator) VALUES (?,?,?,?)";
            Table.batchExecute(getExpSchema(), sql, mvIndicators);
        }

        clearPropertyCache();
    }

    public static void deleteProperty(String objectURI, String propertyURI, Container objContainer, Container propContainer)
    {
        OntologyObject o = getOntologyObject(objContainer, objectURI);
        if (o == null)
            return;

        PropertyDescriptor pd = getPropertyDescriptor(propertyURI, propContainer);
        if (pd == null)
            return;

        deleteProperty(o, pd);
    }

    public static void deleteProperty(OntologyObject o, PropertyDescriptor pd)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("ObjectId"), o.getObjectId());
        filter.addCondition(FieldKey.fromParts("PropertyId"), pd.getPropertyId());
        Table.delete(getTinfoObjectProperty(), filter);
        clearPropertyCache(o.getObjectURI());
    }

    /**
     * Delete properties owned by the objects.
     */
    public static void deleteProperties(Container objContainer, int... objectIDs)
    {
        SimpleFilter filter = new SimpleFilter(new SimpleFilter.InClause(FieldKey.fromParts("ObjectID"), Arrays.asList(ArrayUtils.toObject(objectIDs))));
        String[] objectURIs = new TableSelector(getTinfoObject(), Collections.singleton("ObjectURI"), filter, null).getArray(String.class);

        StringBuilder in = new StringBuilder();
        for (Integer objectID : objectIDs)
        {
            in.append(objectID);
            in.append(",");
        }
        in.setLength(in.length() - 1);

        StringBuilder sqlDeleteProperties = new StringBuilder();
        sqlDeleteProperties.append("DELETE FROM " + getTinfoObjectProperty() + " WHERE  ObjectId IN (SELECT ObjectId FROM " + getTinfoObject() + " WHERE Container = '").append(objContainer.getId()).append("' AND ObjectId IN (");
        sqlDeleteProperties.append(in);
        sqlDeleteProperties.append("))");
        new SqlExecutor(getExpSchema()).execute(sqlDeleteProperties);

        for (String uri : objectURIs)
        {
            clearPropertyCache(uri);
        }
    }

    /**
     * Removes the property from a single domain, and completley deletes it if there are no other references
     */
    public static void removePropertyDescriptorFromDomain(DomainProperty domainProp)
    {
        SQLFragment deletePropDomSql = new SQLFragment("DELETE FROM " + getTinfoPropertyDomain() + " WHERE PropertyId = ? AND DomainId = ?", domainProp.getPropertyId(), domainProp.getDomain().getTypeId());
        SqlExecutor executor = new SqlExecutor(getExpSchema());
        DbScope dbScope = getExpSchema().getScope();
        try (Transaction transaction = dbScope.ensureTransaction())
        {
            executor.execute(deletePropDomSql);
            // Check if there are any other usages
            SQLFragment otherUsagesSQL = new SQLFragment("SELECT DomainId FROM " + getTinfoPropertyDomain() + " WHERE PropertyId = ?", domainProp.getPropertyId());
            if (!new SqlSelector(dbScope, otherUsagesSQL).exists())
            {
                deletePropertyDescriptor(domainProp.getPropertyDescriptor());
            }
            transaction.commit();
        }
    }

    /**
     * Completely deletes the property from the database
     */
    public static void deletePropertyDescriptor(PropertyDescriptor pd)
    {
        int propId = pd.getPropertyId();
        String key = getCacheKey(pd);

        SQLFragment deleteObjPropSql = new SQLFragment("DELETE FROM " + getTinfoObjectProperty() + " WHERE PropertyId = ?", propId);
        SQLFragment deletePropDomSql = new SQLFragment("DELETE FROM " + getTinfoPropertyDomain() + " WHERE PropertyId = ?", propId);
        SQLFragment deletePropSql = new SQLFragment("DELETE FROM " + getTinfoPropertyDescriptor() + " WHERE PropertyId = ?", propId);

        DbScope dbScope = getExpSchema().getScope();
        SqlExecutor executor = new SqlExecutor(getExpSchema());
        try (Transaction transaction = dbScope.ensureTransaction())
        {
            executor.execute(deleteObjPropSql);
            executor.execute(deletePropDomSql);
            executor.execute(deletePropSql);
            propDescCache.remove(key);
            domainPropertiesCache.clear();
            transaction.commit();
        }
    }

    public static void insertProperties(Container container, String ownerObjectLsid, ObjectProperty... properties) throws ValidationException
    {
        try (Transaction transaction = getExpSchema().getScope().ensureTransaction())
        {
            Integer parentId = ownerObjectLsid == null ? null : ensureObject(container, ownerObjectLsid);
            for (ObjectProperty oprop : properties)
            {
                oprop.setObjectOwnerId(parentId);
            }
            insertProperties(container, Arrays.asList(properties));

            transaction.commit();
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }


    public static PropertyDescriptor getPropertyDescriptor(int propertyId)
    {
        return new TableSelector(getTinfoPropertyDescriptor()).getObject(propertyId, PropertyDescriptor.class);
    }


    public static PropertyDescriptor getPropertyDescriptor(String propertyURI, Container c)
    {
        // cache lookup by project. if not found at project level, check to see if global
        String key = getCacheKey(propertyURI, c);
        PropertyDescriptor pd = propDescCache.get(key);
        if (null != pd)
            return pd;

        key = getCacheKey(propertyURI, _sharedContainer);
        pd = propDescCache.get(key);
        if (null != pd)
            return pd;

        Container proj = c.getProject();
        if (null == proj)
            proj = c;

        //TODO: Currently no way to edit these descriptors. But once there is, need to invalidate the cache.
        String sql = " SELECT * FROM " + getTinfoPropertyDescriptor() + " WHERE PropertyURI = ? AND Project IN (?,?)";
        List<PropertyDescriptor> pdArray = new SqlSelector(getExpSchema(), sql, propertyURI,
                proj,
                _sharedContainer.getId()).getArrayList(PropertyDescriptor.class);
        if (!pdArray.isEmpty())
        {
            pd = pdArray.get(0);

            // if someone has explicitly inserted a descriptor with the same URI as an existing one ,
            // and one of the two is in the shared project, use the project-level descriiptor.
            if (pdArray.size() > 1)
            {
                _log.debug("Multiple PropertyDescriptors found for " + propertyURI);
                if (pd.getProject().equals(_sharedContainer))
                    pd = pdArray.get(1);
            }

            key = getCacheKey(pd);
            propDescCache.put(key, pd);
        }

        return pd;
    }

    private static class DomainDescriptorLoader implements CacheLoader<String, DomainDescriptor>
    {
        @Override
        public DomainDescriptor load(String key, @Nullable Object argument)
        {
            int id = Integer.valueOf(key);
            return new TableSelector(getTinfoDomainDescriptor()).getObject(id, DomainDescriptor.class);
        }
    }


    public static DomainDescriptor getDomainDescriptor(int id)
    {
        return domainDescByIDCache.get(String.valueOf(id));
    }

    public static DomainDescriptor getDomainDescriptor(String domainURI, Container c)
    {
        // cache lookup by project. if not found at project level, check to see if global
        String key = getCacheKey(domainURI, c);
        DomainDescriptor dd = domainDescByURICache.get(key);
        if (null != dd)
            return dd;

        key = getCacheKey(domainURI, _sharedContainer);
        dd = domainDescByURICache.get(key);
        if (null != dd)
            return dd;

        Container proj = c.getProject();
        if (null == proj)
            proj = c;

        String sql = " SELECT * FROM " + getTinfoDomainDescriptor() + " WHERE DomainURI = ? AND Project IN (?,?) ";
        List<DomainDescriptor> ddArray = new SqlSelector(getExpSchema(), sql, domainURI,
                proj,
                _sharedContainer.getId()).getArrayList(DomainDescriptor.class);
        if (!ddArray.isEmpty())
        {
            dd = ddArray.get(0);

            // if someone has explicitly inserted a descriptor with the same URI as an existing one ,
            // and one of the two is in the shared project, use the project-level descriptor.
            if (ddArray.size() > 1)
            {
                _log.debug("Multiple DomainDescriptors found for " + domainURI);
                if (dd.getProject().equals(_sharedContainer))
                    dd = ddArray.get(0);
            }
            key = getURICacheKey(dd);
            domainDescByURICache.put(key, dd);
        }
        _log.debug("getDomainDescriptor for " + domainURI + " container= " + c.getPath());
        return dd;
    }

    /**
     * Get all the domains in the same project as the specified container. They may not be in use in the container directly
     */
    public static Collection<DomainDescriptor> getDomainDescriptors(Container container)
    {
        Map<String, DomainDescriptor> ret = new LinkedHashMap<>();
        String sql = "SELECT * FROM " + getTinfoDomainDescriptor() + " WHERE Project = ?";
        Container project = container.getProject();

        if (null != project)
        {
            for (DomainDescriptor dd : new SqlSelector(getExpSchema(), sql, project).getArrayList(DomainDescriptor.class))
            {
                ret.put(dd.getDomainURI(), dd);
            }
            if (!project.equals(_sharedContainer))
            {
                for (DomainDescriptor dd : new SqlSelector(getExpSchema(), sql, ContainerManager.getSharedContainer()).getArrayList(DomainDescriptor.class))
                {
                    if (!ret.containsKey(dd.getDomainURI()))
                    {
                        ret.put(dd.getDomainURI(), dd);
                    }
                }
            }
        }
        return Collections.unmodifiableCollection(ret.values());
    }

    public static String getURICacheKey(DomainDescriptor dd)
    {
        return getCacheKey(dd.getDomainURI(), dd.getContainer());
    }


    public static String getIDCacheKey(DomainDescriptor dd)
    {
        return String.valueOf(dd.getDomainId());
    }


    public static String getCacheKey(PropertyDescriptor pd)
    {
        return getCacheKey(pd.getPropertyURI(), pd.getContainer());
    }


    public static String getCacheKey(String uri, Container c)
    {
        Container proj = c.getProject();
        String projId;

        if (null == proj)
            projId = c.getId();
        else
            projId = proj.getId();

        return uri + "|" + projId;
    }

    //TODO: DbCache semantics. This loads the cache but does not fetch cause need to get them all together
    public static List<PropertyDescriptor> getPropertiesForType(String typeURI, Container c)
    {
        List<PropertyDescriptor> result = getCachedPropertyDescriptorsForDomain(typeURI, c);
        if (result != null)
        {
            return result;
        }

        SQLFragment sql = new SQLFragment(" SELECT PD.*,Required " +
                " FROM " + getTinfoPropertyDescriptor() + " PD " +
                "   INNER JOIN " + getTinfoPropertyDomain() + " PDM ON (PD.PropertyId = PDM.PropertyId) " +
                "   INNER JOIN " + getTinfoDomainDescriptor() + " DD ON (DD.DomainId = PDM.DomainId) " +
                "  WHERE DD.DomainURI = ?  AND DD.Project IN (?,?) ORDER BY PDM.SortOrder, PD.PropertyId");

        sql.addAll(
                typeURI,
                // If we're in the root, just double-up the shared project's id
                c.getProject() == null ? _sharedContainer.getProject().getId() : c.getProject().getId(),
                _sharedContainer.getProject().getId()
        );
        result = Collections.unmodifiableList(new SqlSelector(getExpSchema(), sql).getArrayList(PropertyDescriptor.class));
        //NOTE: cached descriptors may have differing values of isRequired() as that is a per-domain setting
        //Descriptors returned from this method come direct from DB and have correct values.
        List<Pair<String, Boolean>> propertyURIs = new ArrayList<>(result.size());
        for (PropertyDescriptor pd : result)
        {
            propDescCache.put(getCacheKey(pd), pd);
            propertyURIs.add(new Pair<>(pd.getPropertyURI(), pd.isRequired()));
        }
        domainPropertiesCache.put(getCacheKey(typeURI, c), propertyURIs);

        return result;
    }

    private static List<PropertyDescriptor> getCachedPropertyDescriptorsForDomain(String typeURI, Container c)
    {
        List<Pair<String, Boolean>> propertyURIs = domainPropertiesCache.get(getCacheKey(typeURI, c));
        if (propertyURIs != null)
        {
            List<PropertyDescriptor> result = new ArrayList<>(propertyURIs.size());
            for (Pair<String, Boolean> propertyURI : propertyURIs)
            {
                PropertyDescriptor pd = propDescCache.get(getCacheKey(propertyURI.getKey(), c));
                if (pd == null)
                {
                    return null;
                }
                // NOTE: cached descriptors may have differing values of isRequired() as that is a per-domain setting
                // Descriptors returned from this method will have their required bit set as appropriate for this domain 

                // Clone so nobody else messes up our copy
                pd = pd.clone();
                pd.setRequired(propertyURI.getValue().booleanValue());
                result.add(pd);
            }
            return Collections.unmodifiableList(result);
        }
        return null;
    }

    public static void deleteType(String domainURI, Container c) throws DomainNotFoundException
    {
        if (null == domainURI)
            return;

        try (Transaction transaction = getExpSchema().getScope().ensureTransaction())
        {
            try

            {
                deleteObjectsOfType(domainURI, c);
                deleteDomain(domainURI, c);
            }
            catch (DomainNotFoundException x)
            {
                // throw exception but do not kill enclosing transaction
                transaction.commit();
                throw x;
            }

            transaction.commit();
        }
    }

    public static PropertyDescriptor insertOrUpdatePropertyDescriptor(PropertyDescriptor pd, DomainDescriptor dd, int sortOrder)
            throws ChangePropertyDescriptorException
    {
        validatePropertyDescriptor(pd);
        DbScope scope = getExpSchema().getScope();
        try (Transaction transaction = scope.ensureTransaction())
        {
            DomainDescriptor dexist = ensureDomainDescriptor(dd);

            if (!dexist.getContainer().equals(pd.getContainer())
                    && !pd.getProject().equals(_sharedContainer))
            {
                // domain is defined in a different container.
                //ToDO  define property in the domains container?  what security?
                throw new ChangePropertyDescriptorException("Attempt to define property for a domain definition that exists in a different folder\n" +
                        "domain folder = " + dexist.getContainer().getPath() + "\n" +
                        "property folder = " + pd.getContainer().getPath());
            }

            PropertyDescriptor pexist = ensurePropertyDescriptor(pd);
            pexist.setRequired(pd.isRequired());

            ensurePropertyDomain(pexist, dexist, sortOrder);

            transaction.commit();
            return pexist;
        }
    }


    static final String parameters = "propertyuri,ontologyuri,name,description,rangeuri,concepturi,label,searchterms," +
            "semantictype,format,container,project,lookupcontainer,lookupschema,lookupquery,defaultvaluetype,hidden," +
            "mvenabled,importaliases,url,shownininsertview,showninupdateview,shownindetailsview,measure,dimension,scale,recommendedvariable";
    static final String[] parametersArray = parameters.split(",");
    static final String insertSql;
    static final String updateSql;

    static
    {
        insertSql = "INSERT INTO exp.propertydescriptor (" + parameters + ")\nVALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        StringBuilder sb = new StringBuilder("UPDATE exp.propertydescriptor SET");
        String comma = " ";
        for (String p : parametersArray)
        {
            sb.append(comma).append(p).append("=?");
            comma = ",";
        }
        sb.append("\nWHERE propertyid=?");
        updateSql = sb.toString();
    }


    public static void insertPropertyDescriptors(List<PropertyDescriptor> pds) throws SQLException
    {
        if (null == pds || 0 == pds.size())
            return;
        PreparedStatement stmt = getExpSchema().getScope().getConnection().prepareStatement(insertSql);
        ObjectFactory<PropertyDescriptor> f = ObjectFactory.Registry.getFactory(PropertyDescriptor.class);
        Map<String, Object> m = null;
        for (PropertyDescriptor pd : pds)
        {
            m = f.toMap(pd, m);
            for (int i = 0; i < parametersArray.length; i++)
            {
                String p = parametersArray[i];
                Object o = m.get(p);
                if (o == null)
                    stmt.setNull(i + 1, Types.VARCHAR);
                else if (o instanceof String)
                    stmt.setString(i + 1, (String) o);
                else if (o instanceof Integer)
                    stmt.setInt(i + 1, ((Integer) o).intValue());
                else if (o instanceof Container)
                    stmt.setString(i + 1, ((Container) o).getId());
                else if (o instanceof Boolean)
                    stmt.setBoolean(i + 1, ((Boolean) o).booleanValue());
                else
                    assert false : o.getClass().getName();
            }
            stmt.addBatch();
        }
        stmt.executeBatch();
    }


    public static void updatePropertyDescriptors(List<PropertyDescriptor> pds) throws SQLException
    {
        if (null == pds || 0 == pds.size())
            return;
        PreparedStatement stmt = getExpSchema().getScope().getConnection().prepareStatement(updateSql);
        ObjectFactory<PropertyDescriptor> f = ObjectFactory.Registry.getFactory(PropertyDescriptor.class);
        Map<String, Object> m = null;
        for (PropertyDescriptor pd : pds)
        {
            m = f.toMap(pd, m);
            for (int i = 0; i < parametersArray.length; i++)
            {
                String p = parametersArray[i];
                Object o = m.get(p);
                if (o == null)
                    stmt.setNull(i + 1, Types.VARCHAR);
                else if (o instanceof String)
                    stmt.setString(i + 1, (String) o);
                else if (o instanceof Integer)
                    stmt.setInt(i + 1, ((Integer) o).intValue());
                else if (o instanceof Container)
                    stmt.setString(i + 1, ((Container) o).getId());
                else if (o instanceof Boolean)
                    stmt.setBoolean(i + 1, ((Boolean) o).booleanValue());
                else
                    assert false : o.getClass().getName();
            }
            stmt.setInt(parametersArray.length + 1, pd.getPropertyId());
            stmt.addBatch();
        }
        stmt.executeBatch();
    }


    public static PropertyDescriptor insertPropertyDescriptor(PropertyDescriptor pd) throws ChangePropertyDescriptorException
    {
        assert pd.getPropertyId() == 0;
        validatePropertyDescriptor(pd);
        pd = Table.insert(null, getTinfoPropertyDescriptor(), pd);
        propDescCache.put(getCacheKey(pd), pd);
        return pd;
    }


    //todo:  we automatically update a pd to the last  one in?
    public static PropertyDescriptor updatePropertyDescriptor(PropertyDescriptor pd)
    {
        assert pd.getPropertyId() != 0;
        pd = Table.update(null, getTinfoPropertyDescriptor(), pd, pd.getPropertyId());
        propDescCache.put(getCacheKey(pd), pd);
        // It's possible that the propertyURI has changed, thus breaking our reference
        domainPropertiesCache.clear();
        return pd;
    }


    public static DomainDescriptor updateDomainDescriptor(DomainDescriptor dd)
    {
        assert dd.getDomainId() != 0;
        dd = Table.update(null, getTinfoDomainDescriptor(), dd, dd.getDomainId());
        domainDescByURICache.remove(getURICacheKey(dd));
        domainDescByIDCache.remove(getIDCacheKey(dd));
        domainPropertiesCache.remove(getURICacheKey(dd));
        return dd;
    }

    public static void clearCaches()
    {
        ExperimentService.get().clearCaches();
        domainDescByURICache.clear();
        domainDescByIDCache.clear();
        domainPropertiesCache.clear();
        propDescCache.clear();
        mapCache.clear();
        objectIdCache.clear();
    }


    public static void clearPropertyCache(String parentObjectURI)
    {
        mapCache.remove(parentObjectURI);
    }


    public static void clearPropertyCache()
    {
        mapCache.clear();
    }

    public static class ImportPropertyDescriptor
    {
        public final String domainName;
        public final String domainURI;
        public final PropertyDescriptor pd;
        public final List<? extends IPropertyValidator> validators;
        public final List<ConditionalFormat> formats;
        public final String defaultValue;

        private ImportPropertyDescriptor(String domainName, String domainURI, PropertyDescriptor pd, @Nullable List<? extends IPropertyValidator> validators, @Nullable List<ConditionalFormat> formats, String defaultValue)
        {
            this.domainName = domainName;
            this.domainURI = domainURI;
            this.pd = pd;
            this.validators = null != validators ? validators : Collections.emptyList();
            this.formats = null != formats ? formats : Collections.emptyList();
            this.defaultValue = defaultValue;
        }
    }


    public static class ImportPropertyDescriptorsList
    {
        public final ArrayList<ImportPropertyDescriptor> properties = new ArrayList<>();

        void add(String domainName, String domainURI, PropertyDescriptor pd, @Nullable List<? extends IPropertyValidator> validators, @Nullable List<ConditionalFormat> formats, String defaultValue)
        {
            properties.add(new ImportPropertyDescriptor(domainName, domainURI, pd, validators, formats, defaultValue));
        }
    }

    /**
     * Updates an existing domain property with an import property descriptor generated
     * by _propertyDescriptorFromRowMap below. Properties we don't set are explicitly
     * called out
     */
    public static void updateDomainPropertyFromDescriptor(DomainProperty p, PropertyDescriptor pd)
    {
        // don't setName
        p.setPropertyURI(pd.getPropertyURI());
        p.setLabel(pd.getLabel());
        p.setConceptURI(pd.getConceptURI());
        p.setRangeURI(pd.getRangeURI());
        // don't setContainer
        p.setDescription(pd.getDescription());
        p.setURL((pd.getURL() != null) ? pd.getURL().toString() : null);
        p.setImportAliasSet(ColumnRenderProperties.convertToSet(pd.getImportAliases()));
        p.setRequired(pd.isRequired());
        p.setHidden(pd.isHidden());
        p.setShownInInsertView(pd.isShownInInsertView());
        p.setShownInUpdateView(pd.isShownInUpdateView());
        p.setShownInDetailsView(pd.isShownInDetailsView());
        p.setDimension(pd.isDimension());
        p.setMeasure(pd.isMeasure());
        p.setRecommendedVariable(pd.isRecommendedVariable());
        p.setDefaultScale(pd.getDefaultScale());
        p.setScale(pd.getScale());
        p.setFormat(pd.getFormat());
        p.setMvEnabled(pd.isMvEnabled());

        Lookup lookup = new Lookup();
        lookup.setQueryName(pd.getLookupQuery());
        lookup.setSchemaName(pd.getLookupSchema());
        String lookupContainerId = pd.getLookupContainer();
        if (lookupContainerId != null)
        {
            Container container = ContainerManager.getForId(lookupContainerId);
            if (container == null)
                lookup = null;
            else
                lookup.setContainer(container);
        }
        p.setLookup(lookup);
        p.setFacetingBehavior(pd.getFacetingBehaviorType());
        p.setPhi(pd.getPHI());
        p.setRedactedText(pd.getRedactedText());
        p.setExcludeFromShifting(pd.isExcludeFromShifting());
        p.setDefaultValueTypeEnum(pd.getDefaultValueTypeEnum());
    }

    @TestWhen(TestWhen.When.BVT)
    @TestTimeout(120)
    public static class TestCase extends Assert
    {
        @Test
        public void testSchema()
        {
            assertNotNull(getExpSchema());
            assertNotNull(getTinfoPropertyDescriptor());
            assertNotNull(ExperimentService.get().getTinfoMaterialSource());

            assertEquals(getTinfoPropertyDescriptor().getColumns("PropertyId,PropertyURI,OntologyURI,RangeURI,Name,Description").size(), 6);
            assertEquals(getTinfoObject().getColumns("ObjectId,ObjectURI,Container,OwnerObjectId").size(), 4);
            assertEquals(getTinfoObjectPropertiesView().getColumns("ObjectId,ObjectURI,Container,OwnerObjectId,Name,PropertyURI,RangeURI,TypeTag,StringValue,DateTimeValue,FloatValue").size(), 11);
            assertEquals(ExperimentService.get().getTinfoMaterialSource().getColumns("RowId,Name,LSID,MaterialLSIDPrefix,Description,Created,CreatedBy,Modified,ModifiedBy,Container").size(), 10);
        }


        @Test
        public void testBasicPropertiesObject() throws ValidationException
        {
            Container c = ContainerManager.ensureContainer("/_ontologyManagerTest");
            String parentObjectLsid = new Lsid("Junit", "OntologyManager", "parent").toString();
            String childObjectLsid = new Lsid("Junit", "OntologyManager", "child").toString();

            //First delete in case test case failed before
            deleteOntologyObjects(c, parentObjectLsid);
            assertNull(getOntologyObject(c, parentObjectLsid));
            assertNull(getOntologyObject(c, childObjectLsid));
            ensureObject(c, childObjectLsid, parentObjectLsid);
            OntologyObject oParent = getOntologyObject(c, parentObjectLsid);
            assertNotNull(oParent);
            OntologyObject oChild = getOntologyObject(c, childObjectLsid);
            assertNotNull(oChild);
            assertNull(oParent.getOwnerObjectId());
            assertEquals(oChild.getContainer(), c);
            assertEquals(oParent.getContainer(), c);

            String strProp = new Lsid("Junit", "OntologyManager", "stringProp").toString();
            insertProperties(c, parentObjectLsid, new ObjectProperty(childObjectLsid, c, strProp, "The String"));

            String intProp = new Lsid("Junit", "OntologyManager", "intProp").toString();
            insertProperties(c, parentObjectLsid, new ObjectProperty(childObjectLsid, c, intProp, 5));

            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.MILLISECOND, 0);
            String dateProp = new Lsid("Junit", "OntologyManager", "dateProp").toString();
            insertProperties(c, parentObjectLsid, new ObjectProperty(childObjectLsid, c, dateProp, cal.getTime()));

            Map m = getProperties(c, oChild.getObjectURI());
            assertNotNull(m);
            assertEquals(m.size(), 3);
            assertEquals(m.get(strProp), "The String");
            assertEquals(m.get(intProp), 5);
            assertEquals(m.get(dateProp), cal.getTime());


            deleteOntologyObjects(c, parentObjectLsid);
            assertNull(getOntologyObject(c, parentObjectLsid));
            assertNull(getOntologyObject(c, childObjectLsid));

            m = getProperties(c, oChild.getObjectURI());
            assertTrue(null == m || m.size() == 0);
        }

        @Test
        public void testContainerDelete() throws ValidationException
        {
            Container c = ContainerManager.ensureContainer("/_ontologyManagerTest");
            //Clean up last time's mess
            deleteAllObjects(c, TestContext.get().getUser());
            assertEquals(0L, getObjectCount(c));

            String ownerObjectLsid = new Lsid("Junit", "OntologyManager", "parent").toString();
            String childObjectLsid = new Lsid("Junit", "OntologyManager", "child").toString();

            ensureObject(c, childObjectLsid, ownerObjectLsid);
            OntologyObject oParent = getOntologyObject(c, ownerObjectLsid);
            assertNotNull(oParent);
            OntologyObject oChild = getOntologyObject(c, childObjectLsid);
            assertNotNull(oChild);

            String strProp = new Lsid("Junit", "OntologyManager", "stringProp").toString();
            insertProperties(c, ownerObjectLsid, new ObjectProperty(childObjectLsid, c, strProp, "The String"));

            String intProp = new Lsid("Junit", "OntologyManager", "intProp").toString();
            insertProperties(c, ownerObjectLsid, new ObjectProperty(childObjectLsid, c, intProp, 5));

            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.MILLISECOND, 0);
            String dateProp = new Lsid("Junit", "OntologyManager", "dateProp").toString();
            insertProperties(c, ownerObjectLsid, new ObjectProperty(childObjectLsid, c, dateProp, cal.getTime()));

            deleteAllObjects(c, TestContext.get().getUser());
            assertEquals(0L, getObjectCount(c));
            assertTrue(ContainerManager.delete(c, TestContext.get().getUser()));
        }

        private void defineCrossFolderProperties(Container fldr1a, Container fldr1b) throws SQLException
        {
            try
            {
                String fa = fldr1a.getPath();
                String fb = fldr1b.getPath();

                //object, prop descriptor in folder being moved
                String objP1Fa = new Lsid("OntologyObject", "JUnit", fa.replace('/', '.')).toString();
                ensureObject(fldr1a, objP1Fa);
                String propP1Fa = fa + "PD1";
                PropertyDescriptor pd1Fa = ensurePropertyDescriptor(propP1Fa, PropertyType.STRING, "PropertyDescriptor 1" + fa, fldr1a);
                insertProperties(fldr1a, null, new ObjectProperty(objP1Fa, fldr1a, propP1Fa, "same fldr"));

                //object in folder not moving, prop desc in folder moving
                String objP2Fb = new Lsid("OntologyObject", "JUnit", fb.replace('/', '.')).toString();
                ensureObject(fldr1b, objP2Fb);
                insertProperties(fldr1b, null, new ObjectProperty(objP2Fb, fldr1b, propP1Fa, "object in folder not moving, prop desc in folder moving"));

                //object in folder moving, prop desc in folder not moving
                String propP2Fb = fb + "PD1";
                ensurePropertyDescriptor(propP2Fb, PropertyType.STRING, "PropertyDescriptor 1" + fb, fldr1b);
                insertProperties(fldr1a, null, new ObjectProperty(objP1Fa, fldr1a, propP2Fb, "object in folder moving, prop desc in folder not moving"));

                // third prop desc in folder that is moving;  shares domain with first prop desc
                String propP1Fa3 = fa + "PD3";
                PropertyDescriptor pd1Fa3 = ensurePropertyDescriptor(propP1Fa3, PropertyType.STRING, "PropertyDescriptor 3" + fa, fldr1a);
                String domP1Fa = fa + "DD1";
                DomainDescriptor dd1 = ensureDomainDescriptor(domP1Fa, "DomDesc 1" + fa, fldr1a);
                ensurePropertyDomain(pd1Fa, dd1);
                ensurePropertyDomain(pd1Fa3, dd1);

                //second domain desc in folder that is moving
                // second prop desc in folder moving, belongs to 2nd domain
                String propP1Fa2 = fa + "PD2";
                PropertyDescriptor pd1Fa2 = ensurePropertyDescriptor(propP1Fa2, PropertyType.STRING, "PropertyDescriptor 2" + fa, fldr1a);
                String domP1Fa2 = fa + "DD2";
                DomainDescriptor dd2 = ensureDomainDescriptor(domP1Fa2, "DomDesc 2" + fa, fldr1a);
                ensurePropertyDomain(pd1Fa2, dd2);
            }
            catch (ValidationException ve)
            {
                throw new SQLException(ve.getMessage());
            }
        }

        @Test
        public void testContainerMove() throws Exception
        {
            deleteMoveTestContainers();

            Container proj1 = ContainerManager.ensureContainer("/_ontMgrTestP1");
            Container proj2 = ContainerManager.ensureContainer("/_ontMgrTestP2");
            doMoveTest(proj1, proj2);
            deleteMoveTestContainers();

            proj1 = ContainerManager.ensureContainer("/");
            proj2 = ContainerManager.ensureContainer("/_ontMgrTestP2");
            doMoveTest(proj1, proj2);
            deleteMoveTestContainers();

            proj1 = ContainerManager.ensureContainer("/_ontMgrTestP1");
            proj2 = ContainerManager.ensureContainer("/");
            doMoveTest(proj1, proj2);
            deleteMoveTestContainers();

        }

        private void doMoveTest(Container proj1, Container proj2) throws Exception
        {
            String p1Path = proj1.getPath() + "/";
            String p2Path = proj2.getPath() + "/";
            if (p1Path.equals("//")) p1Path = "/_ontMgrDemotePromote";
            if (p2Path.equals("//")) p2Path = "/_ontMgrDemotePromote";

            Container fldr1a = ContainerManager.ensureContainer(p1Path + "Fa");
            Container fldr1b = ContainerManager.ensureContainer(p1Path + "Fb");
            ContainerManager.ensureContainer(p2Path + "Fc");
            Container fldr1aa = ContainerManager.ensureContainer(p1Path + "Fa/Faa");
            Container fldr1aaa = ContainerManager.ensureContainer(p1Path + "Fa/Faa/Faaa");

            defineCrossFolderProperties(fldr1a, fldr1b);
            //defineCrossFolderProperties(fldr1a, fldr2c);
            defineCrossFolderProperties(fldr1aa, fldr1b);
            defineCrossFolderProperties(fldr1aaa, fldr1b);

            fldr1a.getProject().getPath();
            String f = fldr1a.getPath();
            String propId = f + "PD1";
            assertNull(getPropertyDescriptor(propId, proj2));
            ContainerManager.move(fldr1a, proj2, TestContext.get().getUser());

            // if demoting a folder
            if (proj1.isRoot())
            {
                assertNotNull(getPropertyDescriptor(propId, proj2));

                propId = f + "PD2";
                assertNotNull(getPropertyDescriptor(propId, proj2));

                propId = f + "PD3";
                assertNotNull(getPropertyDescriptor(propId, proj2));

                String domId = f + "DD1";
                assertNotNull(getDomainDescriptor(domId, proj2));

                domId = f + "DD2";
                assertNotNull(getDomainDescriptor(domId, proj2));
            }
            // if promoting a folder,
            else if (proj2.isRoot())
            {
                assertNotNull(getPropertyDescriptor(propId, proj1));

                propId = f + "PD2";
                assertNull(getPropertyDescriptor(propId, proj1));

                propId = f + "PD3";
                assertNotNull(getPropertyDescriptor(propId, proj1));

                String domId = f + "DD1";
                assertNotNull(getDomainDescriptor(domId, proj1));

                domId = f + "DD2";
                assertNull(getDomainDescriptor(domId, proj1));
            }
            else
            {
                assertNotNull(getPropertyDescriptor(propId, proj1));
                assertNotNull(getPropertyDescriptor(propId, proj2));

                propId = f + "PD2";
                assertNull(getPropertyDescriptor(propId, proj1));
                assertNotNull(getPropertyDescriptor(propId, proj2));

                propId = f + "PD3";
                assertNotNull(getPropertyDescriptor(propId, proj1));
                assertNotNull(getPropertyDescriptor(propId, proj2));

                String domId = f + "DD1";
                assertNotNull(getDomainDescriptor(domId, proj1));
                assertNotNull(getDomainDescriptor(domId, proj2));

                domId = f + "DD2";
                assertNull(getDomainDescriptor(domId, proj1));
                assertNotNull(getDomainDescriptor(domId, proj2));
            }
        }

        @Test
        public void testDeleteFoldersWithSharedProps() throws SQLException
        {
            deleteMoveTestContainers();

            Container proj1 = ContainerManager.ensureContainer("/_ontMgrTestP1");
            String p1Path = proj1.getPath() + "/";

            Container fldr1a = ContainerManager.ensureContainer(p1Path + "Fa");
            Container fldr1b = ContainerManager.ensureContainer(p1Path + "Fb");
            Container fldr1aa = ContainerManager.ensureContainer(p1Path + "Fa/Faa");
            Container fldr1aaa = ContainerManager.ensureContainer(p1Path + "Fa/Faa/Faaa");

            defineCrossFolderProperties(fldr1a, fldr1b);
            defineCrossFolderProperties(fldr1aa, fldr1b);
            defineCrossFolderProperties(fldr1aaa, fldr1b);

            deleteContainers(TestContext.get().getUser(), "/_ontMgrTestP1/Fb",
                    "/_ontMgrTestP1/Fa/Faa/Faaa", "/_ontMgrTestP1/Fa/Faa", "/_ontMgrTestP1/Fa", "/_ontMgrTestP1");
        }

        private void deleteMoveTestContainers() throws SQLException
        {
            deleteContainers(TestContext.get().getUser(),
                    "/_ontMgrTestP2/Fc", "/_ontMgrTestP1/Fb",
                    "/_ontMgrTestP1/Fa/Faa/Faaa", "/_ontMgrTestP2/Fa/Faa/Faaa",
                    "/_ontMgrTestP1/Fa/Faa", "/_ontMgrTestP2/Fa/Faa",
                    "/_ontMgrTestP1/Fa", "/_ontMgrTestP2/Fa",
                    "/_ontMgrTestP2/_ontMgrDemotePromoteFa/Faa/Faaa", "/_ontMgrTestP2/_ontMgrDemotePromoteFa/Faa", "/_ontMgrTestP2/_ontMgrDemotePromoteFa",
                    "/_ontMgrTestP2", "/_ontMgrTestP1",
                    "/_ontMgrDemotePromoteFc", "/_ontMgrDemotePromoteFb", "/_ontMgrDemotePromoteFa/Faa/Faaa", "/_ontMgrDemotePromoteFa/Faa", "/_ontMgrDemotePromoteFa",
                    "/Fa/Faa/Faaa", "/Fa/Faa", "/Fa"
            );
        }

        private void deleteContainers(User user, String... paths)
        {
            for (String path : paths)
                deleteContainerIfExists(user, path);

            for (String path : paths)
                assertNull("Container " + path + " was not deleted", ContainerManager.getForPath(path));
        }

        private void deleteContainerIfExists(User user, String path)
        {
            Container c = ContainerManager.getForPath(path);

            if (null != c)
                assertTrue(ContainerManager.delete(c, user));
        }

        @Test
        public void testTransactions() throws SQLException
        {
            try
            {
                Container c = ContainerManager.ensureContainer("/_ontologyManagerTest");
                //Clean up last time's mess
                deleteAllObjects(c, TestContext.get().getUser());
                assertEquals(0L, getObjectCount(c));

                String ownerObjectLsid = new Lsid("Junit", "OntologyManager", "parent").toString();
                String childObjectLsid = new Lsid("Junit", "OntologyManager", "child").toString();

                //Create objects in a transaction & make sure they are all gone.
                OntologyObject oParent;
                OntologyObject oChild;
                String strProp;
                String intProp;

                try (Transaction ignored = getExpSchema().getScope().beginTransaction())
                {
                    ensureObject(c, childObjectLsid, ownerObjectLsid);
                    oParent = getOntologyObject(c, ownerObjectLsid);
                    assertNotNull(oParent);
                    oChild = getOntologyObject(c, childObjectLsid);
                    assertNotNull(oChild);

                    strProp = new Lsid("Junit", "OntologyManager", "stringProp").toString();
                    insertProperties(c, ownerObjectLsid, new ObjectProperty(childObjectLsid, c, strProp, "The String"));

                    intProp = new Lsid("Junit", "OntologyManager", "intProp").toString();
                    insertProperties(c, ownerObjectLsid, new ObjectProperty(childObjectLsid, c, intProp, 5));
                }

                assertEquals(0L, getObjectCount(c));
                oParent = getOntologyObject(c, ownerObjectLsid);
                assertNull(oParent);

                ensureObject(c, childObjectLsid, ownerObjectLsid);
                oParent = getOntologyObject(c, ownerObjectLsid);
                assertNotNull(oParent);
                oChild = getOntologyObject(c, childObjectLsid);
                assertNotNull(oChild);

                strProp = new Lsid("Junit", "OntologyManager", "stringProp").toString();
                insertProperties(c, ownerObjectLsid, new ObjectProperty(childObjectLsid, c, strProp, "The String"));

                //Rollback transaction for one new property
                try (Transaction ignored = getExpSchema().getScope().beginTransaction())
                {
                    intProp = new Lsid("Junit", "OntologyManager", "intProp").toString();
                    insertProperties(c, ownerObjectLsid, new ObjectProperty(childObjectLsid, c, intProp, 5));
                }

                oChild = getOntologyObject(c, childObjectLsid);
                assertNotNull(oChild);
                Map<String, Object> m = getProperties(c, childObjectLsid);
                assertNotNull(m.get(strProp));
                assertNull(m.get(intProp));

                try (Transaction transaction = getExpSchema().getScope().beginTransaction())
                {
                    intProp = new Lsid("Junit", "OntologyManager", "intProp").toString();
                    insertProperties(c, ownerObjectLsid, new ObjectProperty(childObjectLsid, c, intProp, 5));
                    transaction.commit();
                }

                m = getProperties(c, childObjectLsid);
                assertNotNull(m.get(strProp));
                assertNotNull(m.get(intProp));

                deleteAllObjects(c, TestContext.get().getUser());
                assertEquals(0L, getObjectCount(c));
                assertTrue(ContainerManager.delete(c, TestContext.get().getUser()));
            }
            catch (ValidationException ve)
            {
                throw new SQLException(ve.getMessage());
            }
        }

        @Test
        public void testDomains() throws Exception
        {
            Container c = ContainerManager.ensureContainer("/_ontologyManagerTest");
            //Clean up last time's mess
            deleteAllObjects(c, TestContext.get().getUser());
            assertEquals(0L, getObjectCount(c));
            String ownerObjectLsid = new Lsid("Junit", "OntologyManager", "parent").toString();
            String childObjectLsid = new Lsid("Junit", "OntologyManager", "child").toString();

            ensureObject(c, childObjectLsid, ownerObjectLsid);
            OntologyObject oParent = getOntologyObject(c, ownerObjectLsid);
            assertNotNull(oParent);
            OntologyObject oChild = getOntologyObject(c, childObjectLsid);
            assertNotNull(oChild);

            String domURIa = new Lsid("Junit", "DD", "Domain1").toString();
            String strPropURI = new Lsid("Junit", "PD", "Domain1.stringProp").toString();
            String intPropURI = new Lsid("Junit", "PD", "Domain1.intProp").toString();

            DomainDescriptor dd = ensureDomainDescriptor(domURIa, "Domain1", c);
            assertNotNull(dd);

            PropertyDescriptor pdStr = new PropertyDescriptor();
            pdStr.setPropertyURI(strPropURI);
            pdStr.setRangeURI(PropertyType.STRING.getTypeUri());
            pdStr.setContainer(c);
            pdStr.setName("Domain1.stringProp");

            pdStr = ensurePropertyDescriptor(pdStr);
            assertNotNull(pdStr);

            PropertyDescriptor pdInt = ensurePropertyDescriptor(intPropURI, PropertyType.INTEGER, "Domain1.intProp", c);

            ensurePropertyDomain(pdStr, dd);
            ensurePropertyDomain(pdInt, dd);

            List<PropertyDescriptor> pds = getPropertiesForType(domURIa, c);
            assertEquals(2, pds.size());
            Map<String, PropertyDescriptor> mPds = new HashMap<>();
            for (PropertyDescriptor pd1 : pds)
                mPds.put(pd1.getPropertyURI(), pd1);

            assertTrue(mPds.containsKey(strPropURI));
            assertTrue(mPds.containsKey(intPropURI));

            ObjectProperty strProp = new ObjectProperty(childObjectLsid, c, strPropURI, "String value");
            ObjectProperty intProp = new ObjectProperty(childObjectLsid, c, intPropURI, 42);
            insertProperties(c, ownerObjectLsid, strProp);
            insertProperties(c, ownerObjectLsid, intProp);

            deleteType(domURIa, c);
            assertEquals(0L, getObjectCount(c));
            assertTrue(ContainerManager.delete(c, TestContext.get().getUser()));
        }
    }


    private static long getObjectCount(Container c)
    {
        return new TableSelector(getTinfoObject(), SimpleFilter.createContainerFilter(c), null).getRowCount();
    }


    /**
     * v.first value IN/OUT parameter
     * v.second mvIndicator OUT parameter
     */
    public static void convertValuePair(PropertyDescriptor pd, PropertyType pt, Pair<Object, String> v)
    {
        if (v.first == null)
            return;

        // Handle field-level QC
        if (v.first instanceof MvFieldWrapper)
        {
            MvFieldWrapper mvWrapper = (MvFieldWrapper) v.first;
            v.second = mvWrapper.getMvIndicator();
            v.first = mvWrapper.getValue();
        }
        else if (pd.isMvEnabled())
        {
            // Not all callers will have wrapped an MV value if there isn't also
            // a real value
            if (MvUtil.isMvIndicator(v.first.toString(), pd.getContainer()))
            {
                v.second = v.first.toString();
                v.first = null;
            }
        }

        if (null != v.first && null != pt)
            v.first = pt.convert(v.first);
    }


    public static class PropertyRow
    {
        protected int objectId;
        protected int propertyId;
        protected char typeTag;
        protected Double floatValue;
        protected String stringValue;
        protected Date dateTimeValue;
        protected String mvIndicator;

        public PropertyRow()
        {
        }

        public PropertyRow(int objectId, PropertyDescriptor pd, Object value, PropertyType pt)
        {
            this.objectId = objectId;
            this.propertyId = pd.getPropertyId();
            this.typeTag = pt.getStorageType();

            Pair<Object, String> p = new Pair<>(value, null);
            convertValuePair(pd, pt, p);
            mvIndicator = p.second;

            switch (pt)
            {
                case STRING:
                case MULTI_LINE:
                case ATTACHMENT:
                case FILE_LINK:
                case RESOURCE:
                    this.stringValue = (String) p.first;
                    break;
                case DATE_TIME:
                    this.dateTimeValue = (java.util.Date) p.first;
                    break;
                case INTEGER:
                case DOUBLE:
                    Number n = (Number) p.first;
                    if (null != n)
                        this.floatValue = n.doubleValue();
                    break;
                case BOOLEAN:
                    Boolean b = (Boolean) p.first;
                    this.floatValue = b == Boolean.TRUE ? 1.0 : 0.0;
                    break;
                default:
                    throw new IllegalArgumentException("Unknown property type: " + pt);
            }
        }

        public int getObjectId()
        {
            return objectId;
        }

        public void setObjectId(int objectId)
        {
            this.objectId = objectId;
        }

        public int getPropertyId()
        {
            return propertyId;
        }

        public void setPropertyId(int propertyId)
        {
            this.propertyId = propertyId;
        }

        public char getTypeTag()
        {
            return typeTag;
        }

        public void setTypeTag(char typeTag)
        {
            this.typeTag = typeTag;
        }

        public Double getFloatValue()
        {
            return floatValue;
        }

        public Boolean getBooleanValue()
        {
            if (floatValue == null)
            {
                return null;
            }
            return floatValue.doubleValue() == 1.0;
        }

        public void setFloatValue(Double floatValue)
        {
            this.floatValue = floatValue;
        }

        public String getStringValue()
        {
            return stringValue;
        }

        public void setStringValue(String stringValue)
        {
            this.stringValue = stringValue;
        }

        public Date getDateTimeValue()
        {
            return dateTimeValue;
        }

        public void setDateTimeValue(Date dateTimeValue)
        {
            this.dateTimeValue = dateTimeValue;
        }

        public String getMvIndicator()
        {
            return mvIndicator;
        }

        public void setMvIndicator(String mvIndicator)
        {
            this.mvIndicator = mvIndicator;
        }

        public Object getObjectValue()
        {
            return stringValue != null ? stringValue : floatValue != null ? floatValue : dateTimeValue;
        }

        @Override
        public String toString()
        {
            StringBuilder sb = new StringBuilder();
            sb.append("PropertyRow: ");

            sb.append("objectId=").append(objectId);
            sb.append(", propertyId=").append(propertyId);
            sb.append(", value=");

            if (stringValue != null)
                sb.append(stringValue);
            else if (floatValue != null)
                sb.append(floatValue);
            else if (dateTimeValue != null)
                sb.append(dateTimeValue);
            else
                sb.append("null");

            if (mvIndicator != null)
                sb.append(", mvIndicator=").append(mvIndicator);

            return sb.toString();
        }
    }

    public static DbSchema getExpSchema()
    {
        return DbSchema.get("exp", DbSchemaType.Module);
    }

    public static SqlDialect getSqlDialect()
    {
        return getExpSchema().getSqlDialect();
    }

    public static TableInfo getTinfoPropertyDomain()
    {
        return getExpSchema().getTable("PropertyDomain");
    }

    public static TableInfo getTinfoObject()
    {
        return getExpSchema().getTable("Object");
    }

    public static TableInfo getTinfoObjectProperty()
    {
        return getExpSchema().getTable("ObjectProperty");
    }

    public static TableInfo getTinfoPropertyDescriptor()
    {
        return getExpSchema().getTable("PropertyDescriptor");
    }

    public static TableInfo getTinfoDomainDescriptor()
    {
        return getExpSchema().getTable("DomainDescriptor");
    }

    public static TableInfo getTinfoObjectPropertiesView()
    {
        return getExpSchema().getTable("ObjectPropertiesView");
    }

    public static String doProjectColumnCheck(boolean bFix) throws SQLException
    {
        StringBuilder msgBuffer = new StringBuilder();
        String descriptorTable = getTinfoPropertyDescriptor().toString();
        String uriColumn = "PropertyURI";
        String idColumn = "PropertyID";
        doProjectColumnCheck(descriptorTable, uriColumn, idColumn, msgBuffer, bFix);

        descriptorTable = getTinfoDomainDescriptor().toString();
        uriColumn = "DomainURI";
        idColumn = "DomainID";
        doProjectColumnCheck(descriptorTable, uriColumn, idColumn, msgBuffer, bFix);

        return msgBuffer.toString();
    }

    private static void doProjectColumnCheck(final String descriptorTable, final String uriColumn, final String idColumn, final StringBuilder msgBuilder, final boolean bFix) throws SQLException
    {
        // get all unique combos of Container, project

        String sql = "SELECT Container, Project FROM " + descriptorTable + " GROUP BY Container, Project";

        new SqlSelector(getExpSchema(), sql).forEach(new Selector.ForEachBlock<ResultSet>()
        {
            @Override
            public void exec(ResultSet rs) throws SQLException
            {
                String containerId = rs.getString("Container");
                String projectId = rs.getString("Project");
                Container container = ContainerManager.getForId(containerId);
                if (null == container)
                    return;  // should be handled by container check
                String newProjectId = container.getProject() == null ? container.getId() : container.getProject().getId();
                if (!projectId.equals(newProjectId))
                {
                    if (bFix)
                    {
                        try
                        {
                            fixProjectColumn(descriptorTable, uriColumn, idColumn, container, projectId, newProjectId);
                            msgBuilder.append("<br/>&nbsp;&nbsp;&nbsp;Fixed inconsistent project ids found for ")
                                    .append(descriptorTable).append(" in folder ")
                                    .append(ContainerManager.getForId(containerId).getPath());

                        }
                        catch (SQLException se)
                        {
                            msgBuilder.append("<br/>&nbsp;&nbsp;&nbsp;ERROR: Failed to fix inconsistent project ids found for ")
                                    .append(descriptorTable).append(" due to ").append(se.getMessage());
                        }
                    }
                    else
                        msgBuilder.append("<br/>&nbsp;&nbsp;&nbsp;ERROR: Inconsistent project ids found for ")
                                .append(descriptorTable).append(" in folder ").append(container.getPath());
                }
            }
        });
    }

    private static void fixProjectColumn(String descriptorTable, String uriColumn, String idColumn, Container container, String projectId, String newProjId) throws SQLException
    {
        final SqlExecutor executor = new SqlExecutor(getExpSchema());

        String sql = "UPDATE " + descriptorTable + " SET Project= ? WHERE Project = ? AND Container=? AND " + uriColumn + " NOT IN " +
                "(SELECT " + uriColumn + " FROM " + descriptorTable + " WHERE Project = ?)";
        executor.execute(sql, newProjId, projectId, container.getId(), newProjId);

        // now check to see if there is already an existing descriptor in the target (correct) project.
        // this can happen if a folder containning a descriptor is moved to another project
        // and the OntologyManager's containerMoved handler fails to fire for some reason. (note not in transaction)
        //  If this is the case, the descriptor is redundant and it should be deleted, after we move the objects that depend on it.

        sql = " SELECT prev." + idColumn + " AS PrevIdCol, cur." + idColumn + " AS CurIdCol FROM " + descriptorTable + " prev "
                + " INNER JOIN " + descriptorTable + " cur ON (prev." + uriColumn + "=  cur." + uriColumn + " ) "
                + " WHERE cur.Project = ? AND prev.Project= ? AND prev.Container = ? ";
        final String updsql1 = " UPDATE " + getTinfoObjectProperty() + " SET " + idColumn + " = ? WHERE " + idColumn + " = ? ";
        final String updsql2 = " UPDATE " + getTinfoPropertyDomain() + " SET " + idColumn + " = ? WHERE " + idColumn + " = ? ";
        final String delSql = " DELETE FROM " + descriptorTable + " WHERE " + idColumn + " = ? ";

        new SqlSelector(getExpSchema(), sql, newProjId, projectId, container).forEach(new Selector.ForEachBlock<ResultSet>()
        {
            @Override
            public void exec(ResultSet rs) throws SQLException
            {
                int prevPropId = rs.getInt(1);
                int curPropId = rs.getInt(2);
                executor.execute(updsql1, curPropId, prevPropId);
                executor.execute(updsql2, curPropId, prevPropId);
                executor.execute(delSql, prevPropId);
            }
        });
    }

    static public PropertyDescriptor updatePropertyDescriptor(User user, DomainDescriptor dd, PropertyDescriptor pdOld, PropertyDescriptor pdNew, int sortOrder) throws ChangePropertyDescriptorException
    {
        PropertyType oldType = pdOld.getPropertyType();
        PropertyType newType = pdNew.getPropertyType();
        if (oldType.getStorageType() != newType.getStorageType() && new TableSelector(getTinfoObjectProperty(), new SimpleFilter(FieldKey.fromParts("PropertyId"), pdOld.getPropertyId()), null).exists())
            throw new ChangePropertyDescriptorException("This property type cannot be changed because there are existing values.");

        validatePropertyDescriptor(pdNew);
        PropertyDescriptor update = Table.update(user, getTinfoPropertyDescriptor(), pdNew, pdOld.getPropertyId());

        ensurePropertyDomain(pdNew, dd, sortOrder);

        return update;
    }

    private static void validatePropertyDescriptor(PropertyDescriptor pd) throws ChangePropertyDescriptorException
    {
        String name = pd.getName();
        validateValue(name, "Name", null);
        validateValue(pd.getPropertyURI(), "PropertyURI", "Please use a shorter field name. Name = " + name);
        validateValue(pd.getLabel(), "Label", null);
        validateValue(pd.getImportAliases(), "ImportAliases", null);
        validateValue(pd.getURL() != null ? pd.getURL().getSource() : null, "URL", null);
        validateValue(pd.getOntologyURI(), "OntologyURI", null);
        validateValue(pd.getConceptURI(), "ConceptURI", null);
        validateValue(pd.getSemanticType(), "SemanticType", null);
        validateValue(pd.getRangeURI(), "RangeURI", null);

        // Issue 15484: adding a column ending in 'mvIndicator' is problematic if another column w/ the same
        // root exists, or if you later enable mvIndicators on a column w/ the same root
        if (pd.getName() != null && pd.getName().toLowerCase().endsWith(MV_INDICATOR_SUFFIX))
        {
            throw new ChangePropertyDescriptorException("Field name cannot end with the suffix 'mvIndicator': " + pd.getName());
        }
        if (null != name)
        {
            for (char ch : name.toCharArray())
            {
                if (Character.isWhitespace(ch) && ' ' != ch)
                    throw new ChangePropertyDescriptorException("Field name cannot contain whitespace other than ' ' (space)");
            }
        }
    }

    private static void validateValue(String value, String columnName, String extraMessage) throws ChangePropertyDescriptorException
    {
        int maxLength = getTinfoPropertyDescriptor().getColumn(columnName).getScale();
        if (value != null && value.length() > maxLength)
        {
            throw new ChangePropertyDescriptorException(columnName + " cannot exceed " + maxLength + " characters, but was " + value.length() + " characters long. " + (extraMessage == null ? "" : extraMessage));
        }
    }

    static public boolean checkObjectExistence(String lsid)
    {
        return new TableSelector(getTinfoObject(), new SimpleFilter(FieldKey.fromParts("ObjectURI"), lsid), null).exists();
    }


    static public void indexConcepts(SearchService.IndexTask task)
    {
        if (1 == 1)
            return;
        if (null == task)
        {
            SearchService ss = SearchService.get();
            task = null == ss ? null : ss.createTask("Index Concepts");
            if (null == task)
                return;
        }

        final SearchService.IndexTask t = task;
        task.addRunnable(() -> _indexConcepts(t), SearchService.PRIORITY.bulk);
    }


    final public static SearchService.SearchCategory conceptCategory = new SearchService.SearchCategory("concept", "Concepts and Property Descriptors");

    private static void _indexConcepts(final SearchService.IndexTask task)
    {
        new SqlSelector(getExpSchema(), "SELECT * FROM exp.PropertyDescriptor WHERE Container=? AND rangeuri='xsd:nil'",
                _sharedContainer.getId()).forEachMap(m ->
        {
            String propertyURI = (String) m.get("propertyUri");
            m.put(PROPERTY.title.toString(), propertyURI);

            String desc = (String) m.get("description");
            String label = (String) m.get("label");
            String name = (String) m.get("name");
            String body = StringUtils.trimToEmpty(name) + " " +
                    StringUtils.trimToEmpty(label) + " " +
                    StringUtils.trimToEmpty(desc);

            ActionURL url = new ActionURL("experiment-types", "findConcepts", _sharedContainer);
            url.addParameter("concept", propertyURI);
            WebdavResource r = new SimpleDocumentResource(
                    new Path(propertyURI),
                    "concept:" + propertyURI,
                    _sharedContainer.getId(),
                    "text/plain", body,
                    url,
                    m
            );
            task.addResource(r, SearchService.PRIORITY.item);
        });
    }
}
