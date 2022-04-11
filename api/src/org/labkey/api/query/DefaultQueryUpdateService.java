/*
 * Copyright (c) 2009-2019 LabKey Corporation
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
package org.labkey.api.query;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.collections.ArrayListMap;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.Parameter;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.UpdateableTableInfo;
import org.labkey.api.data.validator.ColumnValidator;
import org.labkey.api.data.validator.ColumnValidators;
import org.labkey.api.dataiterator.DataIteratorUtil;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.OntologyObject;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.ValidatorContext;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.Pair;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;
import org.springframework.web.multipart.MultipartFile;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * QueryUpdateService implementation that supports Query TableInfos that are backed by both a hard table and a Domain.
 * To update the Domain, a DomainUpdateHelper is required, otherwise the DefaultQueryUpdateService will only update the
 * hard table columns.
 *
 * User: Dave
 * Date: Jun 18, 2008
 * Time: 11:17:16 AM
 */
public class DefaultQueryUpdateService extends AbstractQueryUpdateService
{
    private final TableInfo _dbTable;
    private DomainUpdateHelper _helper = null;
    /** Map from DbTable column names to QueryTable column names, if they have been aliased */
    protected Map<String, String> _columnMapping = Collections.emptyMap();
    private final ValidatorContext _validatorContext;

    public DefaultQueryUpdateService(@NotNull TableInfo queryTable, TableInfo dbTable)
    {
        super(queryTable);
        _dbTable = dbTable;

        if( queryTable.getUserSchema() == null )
            throw new RuntimeValidationException("User schema not defined for " + queryTable.getName());

        _validatorContext = new ValidatorContext(queryTable.getUserSchema().getContainer(), queryTable.getUserSchema().getUser());
    }

    public DefaultQueryUpdateService(TableInfo queryTable, TableInfo dbTable, DomainUpdateHelper helper)
    {
        this(queryTable, dbTable);
        _helper = helper;
    }

    /** @param columnMapping Map from DbTable column names to QueryTable column names, if they have been aliased */
    public DefaultQueryUpdateService(TableInfo queryTable, TableInfo dbTable, Map<String, String> columnMapping)
    {
        this(queryTable, dbTable);
        _columnMapping = columnMapping;
    }

    protected TableInfo getDbTable()
    {
        return _dbTable;
    }

    protected Domain getDomain()
    {
        return _helper == null ? null : _helper.getDomain();
    }

    protected ColumnInfo getObjectUriColumn()
    {
        return _helper == null ? null : _helper.getObjectUriColumn();
    }

    protected String createObjectURI()
    {
        return _helper == null ? null : _helper.createObjectURI();
    }

    protected Iterable<PropertyColumn> getPropertyColumns()
    {
        return _helper == null ? Collections.emptyList() : _helper.getPropertyColumns();
    }

    protected Map<String, String> getColumnMapping()
    {
        return _columnMapping;
    }

    /**
     * Returns the container that the domain is defined
     */
    protected Container getDomainContainer(Container c)
    {
        return _helper == null ? c : _helper.getDomainContainer(c);
    }

    /**
     * Returns the container to insert/update values into
     */
    protected Container getDomainObjContainer(Container c)
    {
        return _helper == null ? c : _helper.getDomainObjContainer(c);
    }

    protected Set<String> getAutoPopulatedColumns()
    {
        return Table.AUTOPOPULATED_COLUMN_NAMES;
    }

    public interface DomainUpdateHelper
    {
        Domain getDomain();

        ColumnInfo getObjectUriColumn();

        String createObjectURI();

        // Could probably be just Iterable<PropertyDescriptor> or be removed and just get all PropertyDescriptors in the Domain.
        Iterable<PropertyColumn> getPropertyColumns();

        Container getDomainContainer(Container c);

        Container getDomainObjContainer(Container c);
    }

    public class ImportHelper implements OntologyManager.ImportHelper
    {
        ImportHelper()
        {
        }

        @Override
        public String beforeImportObject(Map<String, Object> map)
        {
            ColumnInfo objectUriCol = getObjectUriColumn();

            // Get existing Lsid
            String lsid = (String)map.get(objectUriCol.getName());
            if (lsid != null)
                return lsid;

            // Generate a new Lsid
            lsid = createObjectURI();
            map.put(objectUriCol.getName(), lsid);
            return lsid;
        }

        @Override
        public void afterBatchInsert(int currentRow)
        {
        }

        @Override
        public void updateStatistics(int currentRow)
        {
        }
    }

    @Override
    protected Map<String, Object> getRow(User user, Container container, Map<String, Object> keys)
            throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        aliasColumns(_columnMapping, keys);
        Map<String,Object> row = _select(container, getKeys(keys, container));

        //PostgreSQL includes a column named _row for the row index, but since this is selecting by
        //primary key, it will always be 1, which is not only unnecessary, but confusing, so strip it
        if (null != row)
        {
            if (row instanceof ArrayListMap)
                ((ArrayListMap<?, ?>)row).getFindMap().remove("_row");
            else
                row.remove("_row");
        }

        return row;
    }

    protected Map<String, Object> _select(Container container, Object[] keys) throws SQLException, ConversionException
    {
        TableInfo table = getDbTable();
        Object[] typedParameters = convertToTypedValues(keys, table.getPkColumns());

        Map<String, Object> row = new TableSelector(table).getMap(typedParameters);


        ColumnInfo objectUriCol = getObjectUriColumn();
        Domain domain = getDomain();
        if (objectUriCol != null && domain != null && !domain.getProperties().isEmpty() && row != null)
        {
            String lsid = (String)row.get(objectUriCol.getName());
            if (lsid != null)
            {
                Map<String, Object> propertyValues = OntologyManager.getProperties(getDomainObjContainer(container), lsid);
                if (propertyValues.size() > 0)
                {
                    // convert PropertyURI->value map into "Property name"->value map
                    Map<String, DomainProperty> propertyMap = domain.createImportMap(false);
                    for (Map.Entry<String, Object> entry : propertyValues.entrySet())
                    {
                        String propertyURI = entry.getKey();
                        DomainProperty dp = propertyMap.get(propertyURI);
                        PropertyDescriptor pd = dp != null ? dp.getPropertyDescriptor() : null;
                        if (pd != null)
                            row.put(pd.getName(), entry.getValue());
                    }
                }
            }
            else
            {
                throw new IllegalStateException("LSID value not found in table - " + table.getName());
            }
        }

        return row;
    }


    private Object[] convertToTypedValues(Object[] keys, List<ColumnInfo> cols)
    {
        Object[] typedParameters = new Object[keys.length];
        int t=0;
        for (int i=0 ; i<keys.length ; i++)
        {
            if (i >= cols.size() || keys[i] instanceof Parameter.TypedValue)
            {
                typedParameters[t++] = keys[i];
                continue;
            }
            Object v = keys[i];
            JdbcType type = cols.get(i).getJdbcType();
            if (v instanceof String)
                v = type.convert(v);
            Parameter.TypedValue tv = new Parameter.TypedValue(v,type);
            typedParameters[t++] = tv;
        }
        return typedParameters;
    }


    @Override
    protected Map<String, Object> insertRow(User user, Container container, Map<String, Object> row)
            throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        aliasColumns(_columnMapping, row);
        convertTypes(container, row);
        setSpecialColumns(container, row, user, InsertPermission.class);
        validateInsertRow(row);
        return _insert(user, container, row);
    }

    protected Map<String, Object> _insert(User user, Container c, Map<String, Object> row)
            throws SQLException, ValidationException
    {
        ColumnInfo objectUriCol = getObjectUriColumn();
        Domain domain = getDomain();
        if (objectUriCol != null && domain != null && !domain.getProperties().isEmpty())
        {
            // convert "Property name"->value map into PropertyURI->value map
            List<PropertyDescriptor> pds = new ArrayList<>();
            Map<String, Object> values = new HashMap<>();
            for (PropertyColumn pc : getPropertyColumns())
            {
                PropertyDescriptor pd = pc.getPropertyDescriptor();
                pds.add(pd);
                Object value = getPropertyValue(row, pd);
                values.put(pd.getPropertyURI(), value);
            }

            List<String> lsids = OntologyManager.insertTabDelimited(getDomainObjContainer(c), user, null, new ImportHelper(), pds, Collections.singletonList(values), true);
            String lsid = lsids.get(0);

            // Add the new lsid to the row map.
            row.put(objectUriCol.getName(), lsid);
        }

        try
        {
            return Table.insert(user, getDbTable(), row);
        }
        catch (RuntimeValidationException e)
        {
            throw e.getValidationException();
        }
    }

    @Override
    protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, @NotNull Map<String, Object> oldRow)
            throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        return updateRow(user, container, row, oldRow, false, false);
    }

    protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, @NotNull Map<String, Object> oldRow, boolean allowOwner)
            throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        return updateRow(user, container, row, oldRow, allowOwner, false);
    }

    protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, @NotNull Map<String, Object> oldRow, boolean allowOwner, boolean retainCreation)
            throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        Map<String,Object> rowStripped = new CaseInsensitiveHashMap<>(row.size());

        // Flip the key/value pairs around for easy lookup
        Map<String, String> queryToDb = new CaseInsensitiveHashMap<>();
        for (Map.Entry<String, String> entry : _columnMapping.entrySet())
        {
            queryToDb.put(entry.getValue(), entry.getKey());
        }

        setSpecialColumns(container, row, user, UpdatePermission.class);

        Map<String,ColumnInfo> tableAliasesMap = DataIteratorUtil.createTableMap(getQueryTable(), true);
        Map<ColumnInfo, Pair<String,Object>> colFrequency = new HashMap<>();

        //resolve passed in row including columns in the table and other properties (vocabulary properties) not in the Domain/table
        for (Map.Entry<String, Object> entry: row.entrySet())
        {
            if (!rowStripped.containsKey(entry.getKey()))
            {
                ColumnInfo col = getQueryTable().getColumn(entry.getKey());

                if (null == col)
                {
                    col = tableAliasesMap.get(entry.getKey());
                }

               if (null != col)
               {
                   final String name = col.getName();

                   // Skip readonly and wrapped columns.  The wrapped column is usually a pk column and can't be updated.
                   if (col.isReadOnly() || col.isCalculated())
                       continue;

                   //when updating a row, we should strip the following fields, as they are
                   //automagically maintained by the table layer, and should not be allowed
                   //to change once the record exists.
                   //unfortunately, the Table.update() method doesn't strip these, so we'll
                   //do that here.
                   // Owner, CreatedBy, Created, EntityId
                   if ((!retainCreation && (name.equalsIgnoreCase("CreatedBy") || name.equalsIgnoreCase("Created")))
                           || (!allowOwner && name.equalsIgnoreCase("Owner"))
                           || name.equalsIgnoreCase("EntityId"))
                       continue;

                   // Throw error if more than one row properties having different values match up to the same column.
                   if (!colFrequency.containsKey(col))
                   {
                       colFrequency.put(col, Pair.of(entry.getKey(),entry.getValue()));
                   }
                   else
                   {
                       if (!Objects.equals(colFrequency.get(col).second, entry.getValue()))
                       {
                           throw new ValidationException("Property key - " + colFrequency.get(col).first + " and " + entry.getKey() + " matched for the same column.");
                       }
                   }

                   // We want a map using the DbTable column names as keys, so figure out the right name to use
                   String dbName = queryToDb.getOrDefault(name, name);
                   rowStripped.put(dbName, entry.getValue());
               }
            }
        }

        convertTypes(container, rowStripped);
        validateUpdateRow(rowStripped);

        if (row.get("container") != null)
        {
            Container rowContainer = UserSchema.translateRowSuppliedContainer(row.get("container"), container, user, getQueryTable(), UpdatePermission.class, null);
            if (rowContainer == null)
            {
                throw new ValidationException("Unknown container: " + row.get("container"));
            }
            else
            {
                Container oldContainer = UserSchema.translateRowSuppliedContainer(new CaseInsensitiveHashMap<>(oldRow).get("container"), container, user, getQueryTable(), UpdatePermission.class, null);
                if (null != oldContainer && !rowContainer.equals(oldContainer))
                    throw new UnauthorizedException("The row is from the wrong container.");
            }
        }

        Map<String,Object> updatedRow = _update(user, container, rowStripped, oldRow, oldRow == null ? getKeys(row, container) : getKeys(oldRow, container));

        //when passing a map for the row, the Table layer returns the map of fields it updated, which excludes
        //the primary key columns as well as those marked read-only. So we can't simply return the map returned
        //from Table.update(). Instead, we need to copy values from updatedRow into row and return that.
        row.putAll(updatedRow);
        return row;
    }

    protected void validateValue(ColumnInfo column, Object value) throws ValidationException
    {
        DomainProperty dp = getDomain() == null ? null : getDomain().getPropertyByName(column.getColumnName());
        List<ColumnValidator> validators = ColumnValidators.create(column, dp);
        for (ColumnValidator v : validators)
        {
            String msg = v.validate(-1, value, _validatorContext);
            if (msg != null)
                throw new ValidationException(msg, column.getName());
        }
    }

    protected void validateInsertRow(Map<String, Object> row) throws ValidationException
    {
        for (ColumnInfo col : getQueryTable().getColumns())
        {
            Object value = row.get(col.getColumnName());

            // Check required values aren't null or empty
            if (null == value || value instanceof String && 0 == ((String) value).length())
            {
                if (!col.isAutoIncrement() && col.isRequired() &&
                        !getAutoPopulatedColumns().contains(col.getName()) &&
                        col.getJdbcDefaultValue() == null)
                {
                    throw new ValidationException("A value is required for field '" + col.getName() + "'", col.getName());
                }
            }
            else
            {
                validateValue(col, value);
            }
        }
    }

    private void validateUpdateRow(Map<String, Object> row) throws ValidationException
    {
        for (ColumnInfo col : getQueryTable().getColumns())
        {
            // Only validate incoming values
            if (row.containsKey(col.getColumnName()))
            {
                Object value = row.get(col.getColumnName());
                validateValue(col, value);
            }
        }
    }

    protected Map<String, Object> _update(User user, Container c, Map<String, Object> row, Map<String, Object> oldRow, Object[] keys)
            throws SQLException, ValidationException
    {
        ColumnInfo objectUriCol = getObjectUriColumn();
        Domain domain = getDomain();

        // The lsid may be null for the row until a property has been inserted
        String lsid = null;
        if (objectUriCol != null)
            lsid = (String)oldRow.get(objectUriCol.getName());

        List<PropertyDescriptor> tableProperties = new ArrayList<>();
        if (objectUriCol != null && domain != null && !domain.getProperties().isEmpty())
        {
            // convert "Property name"->value map into PropertyURI->value map
            Map<String, Object> newValues = new HashMap<>();

            for (PropertyColumn pc : getPropertyColumns())
            {
                PropertyDescriptor pd = pc.getPropertyDescriptor();
                tableProperties.add(pd);

                if (lsid != null && hasProperty(oldRow, pd))
                    OntologyManager.deleteProperty(lsid, pd.getPropertyURI(), getDomainObjContainer(c), getDomainContainer(c));

                Object value = getPropertyValue(row, pd);
                if (value != null)
                    newValues.put(pd.getPropertyURI(), value);
            }

            // Note: copy lsid into newValues map so it will be found by the ImportHelper.beforeImportObject()
            newValues.put(objectUriCol.getName(), lsid);
            List<String> lsids = OntologyManager.insertTabDelimited(getDomainObjContainer(c), user, null, new ImportHelper(), tableProperties, Collections.singletonList(newValues), true);

            // Update the lsid in the row: the lsid may have not existed in the row before the update.
            lsid = lsids.get(0);
            row.put(objectUriCol.getName(), lsid);
        }

        // Get lsid value if it hasn't been set.
        // This should only happen if the QueryUpdateService doesn't have a DomainUpdateHelper (DataClass and SampleType)
        if (lsid == null && getQueryTable() instanceof UpdateableTableInfo)
        {
            String objectUriColName = ((UpdateableTableInfo)getQueryTable()).getObjectURIColumnName();
            if (objectUriColName != null)
                lsid = (String)row.getOrDefault(objectUriColName, oldRow.get(objectUriColName));
        }

        // handle vocabulary properties
        if (lsid != null)
        {
            for (Map.Entry<String, Object> rowEntry : row.entrySet())
            {
                String colName = rowEntry.getKey();
                Object value = rowEntry.getValue();

                ColumnInfo col = getQueryTable().getColumn(colName);
                if (col instanceof PropertyColumn)
                {
                    PropertyColumn propCol = (PropertyColumn) col;
                    PropertyDescriptor pd = propCol.getPropertyDescriptor();
                    if (pd.isVocabulary() && !tableProperties.contains(pd))
                    {
                        OntologyManager.updateObjectProperty(user, c, pd, lsid, value, null, false);
                    }
                }
            }
        }

        return Table.update(user, getDbTable(), row, keys);
    }

    // Get value from row map where the keys are column names.
    private Object getPropertyValue(Map<String, Object> row, PropertyDescriptor pd)
    {
        if (row.containsKey(pd.getName()))
            return row.get(pd.getName());

        if (row.containsKey(pd.getLabel()))
            return row.get(pd.getLabel());

        Set<String> aliases = pd.getImportAliasSet();
        if (aliases.size() > 0)
        {
            for (String alias : aliases)
            {
                if (row.containsKey(alias))
                    return row.get(alias);
            }
        }

        return null;
    }

    // Checks a value exists in the row map (value may be null)
    private boolean hasProperty(Map<String, Object> row, PropertyDescriptor pd)
    {
        if (row.containsKey(pd.getName()))
            return true;

        if (row.containsKey(pd.getLabel()))
            return true;

        Set<String> aliases = pd.getImportAliasSet();
        if (aliases.size() > 0)
        {
            for (String alias : aliases)
            {
                if (row.containsKey(alias))
                    return true;
            }
        }

        return false;
    }

    @Override
    protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRowMap) throws QueryUpdateServiceException, SQLException, InvalidKeyException
    {
        if (oldRowMap == null)
            return null;

        aliasColumns(_columnMapping, oldRowMap);

        if (container != null && getDbTable().getColumn("container") != null)
        {
            // UNDONE: 9077: check container permission on each row before delete
            Container rowContainer = UserSchema.translateRowSuppliedContainer(new CaseInsensitiveHashMap<>(oldRowMap).get("container"), container, user, getQueryTable(), DeletePermission.class, null);
            if (null != rowContainer && !container.equals(rowContainer))
            {
                //Issue 15301: allow workbooks records to be deleted/updated from the parent container
                if (container.allowRowMutationForContainer(rowContainer))
                    container = rowContainer;
                else
                    throw new UnauthorizedException("The row is from the container: " + rowContainer.getId() + " which does not allow deletes from the container: " + container.getPath());
            }
        }

        _delete(container, oldRowMap);
        return oldRowMap;
    }

    protected void _delete(Container c, Map<String, Object> row) throws InvalidKeyException
    {
        ColumnInfo objectUriCol = getObjectUriColumn();
        if (objectUriCol != null)
        {
            String lsid = (String)row.get(objectUriCol.getName());
            if (lsid != null)
            {
                OntologyObject oo = OntologyManager.getOntologyObject(c, lsid);
                if (oo != null)
                    OntologyManager.deleteProperties(c, oo.getObjectId());
            }
        }
        Table.delete(getDbTable(), getKeys(row, c));
    }

    // classes should override this method if they need to do more work than delete all the rows from the table
    // this implementation will delete all rows from the table for the given container as well as delete
    // any properties associated with the table
    @Override
    protected int truncateRows(User user, Container container) throws QueryUpdateServiceException, SQLException
    {
        // get rid of the properties for this table
        if (null != getObjectUriColumn())
        {
            SQLFragment lsids = new SQLFragment()
                    .append("SELECT t.").append(getObjectUriColumn().getColumnName())
                    .append(" FROM ").append(getDbTable(), "t")
                    .append(" WHERE t.").append(getObjectUriColumn().getColumnName()).append(" IS NOT NULL");
            if (null != getDbTable().getColumn("container"))
            {
                lsids.append(" AND t.Container = ?");
                lsids.add(container.getId());
            }

            OntologyManager.deleteOntologyObjects(ExperimentService.get().getSchema(), lsids, container, false);
        }

        // delete all the rows in this table, scoping to the container if the column
        // is available
        if (null != getDbTable().getColumn("container"))
            return Table.delete(getDbTable(), SimpleFilter.createContainerFilter(container));

       return Table.delete(getDbTable());
    }

    protected Object[] getKeys(Map<String, Object> map, Container container) throws InvalidKeyException
    {
        //build an array of pk values based on the table info
        TableInfo table = getDbTable();
        List<ColumnInfo> pks = table.getPkColumns();
        Object[] pkVals = new Object[pks.size()];

        if (map == null || map.isEmpty())
            return pkVals;

        for (int idx = 0; idx < pks.size(); ++idx)
        {
            ColumnInfo pk = pks.get(idx);
            Object pkValue = map.get(pk.getName());
            // Check the type and coerce if needed
            if (pkValue != null && !pk.getJavaObjectClass().isInstance(pkValue))
            {
                try
                {
                    pkValue = ConvertUtils.convert(pkValue.toString(), pk.getJavaObjectClass());
                }
                catch (ConversionException ignored) { /* Maybe the database can do the conversion */ }
            }
            pkVals[idx] = pkValue;
            if (null == pkVals[idx] && pk.getColumnName().equalsIgnoreCase("Container"))
            {
                pkVals[idx] = container;
            }
            if(null == pkVals[idx])
            {
                throw new InvalidKeyException("Value for key field '" + pk.getName() + "' was null or not supplied!", map);
            }
        }
        return pkVals;
    }


    final protected void convertTypes(Container c, Map<String,Object> row) throws ValidationException
    {
        convertTypes(c, row,  getDbTable(), null);
    }

    protected void convertTypes(Container c, Map<String,Object> row, TableInfo t, @Nullable String file_link_dir_name) throws ValidationException
    {
        for (ColumnInfo col : t.getColumns())
        {
            Object value = row.get(col.getName());
            if (null != value)
            {
                // Issue 13951: PSQLException from org.labkey.api.query.DefaultQueryUpdateService._update()
                // improve handling of conversion errors
                try
                {
                    switch (col.getJdbcType())
                    {
                        case DATE, TIME, TIMESTAMP -> row.put(col.getName(), value instanceof Date ? value : ConvertUtils.convert(value.toString(), Date.class));
                        default -> {
                            if (PropertyType.FILE_LINK == col.getPropertyType() && (value instanceof MultipartFile || value instanceof AttachmentFile))
                            {
                                value = saveFile(c, col.getName(), value, file_link_dir_name);
                            }
                            row.put(col.getName(), ConvertUtils.convert(value.toString(), col.getJdbcType().getJavaClass()));
                        }
                    }
                }
                catch (ConversionException e)
                {
                    String type = ColumnInfo.getFriendlyTypeName(col.getJdbcType().getJavaClass());
                    throw new ValidationException("Unable to convert value '" + value.toString() + "' to " + type, col.getName());
                }
                catch (QueryUpdateServiceException e)
                {
                    throw new ValidationException("Save file link failed: " + col.getName());
                }
            }
        }
    }


    /**
     * Override this method to alter the row before insert or update.
     * For example, you can automatically adjust certain column values based on context.
     * @param container The current container
     * @param row The row data
     * @param user The current user
     * @param clazz A permission class to test
     */
    protected void setSpecialColumns(Container container, Map<String,Object> row, User user, Class<? extends Permission> clazz)
    {
        if (null != container)
        {
            //Issue 15301: allow workbooks records to be deleted/updated from the parent container
            if (row.get("container") != null)
            {
                Container rowContainer = UserSchema.translateRowSuppliedContainer(row.get("container"), container, user, getQueryTable(), clazz, null);
                if (rowContainer != null && container.allowRowMutationForContainer(rowContainer))
                {
                    row.put("container", rowContainer.getId()); //normalize to container ID
                    return;  //accept the row-provided value
                }
            }
            row.put("container", container.getId());
        }
    }

    protected boolean hasAttachmentProperties()
    {
        Domain domain = getDomain();
        if (null != domain)
        {
            for (DomainProperty dp : domain.getProperties())
                if (null != dp && isAttachmentProperty(dp))
                    return true;
        }
        return false;
    }

    protected boolean isAttachmentProperty(@NotNull DomainProperty dp)
    {
        PropertyDescriptor pd = dp.getPropertyDescriptor();
        return PropertyType.ATTACHMENT.equals(pd.getPropertyType());
    }

    protected boolean isAttachmentProperty(String name)
    {
        DomainProperty dp = getDomain().getPropertyByName(name);
        if (dp != null)
            return isAttachmentProperty(dp);
        return false;
    }

}
