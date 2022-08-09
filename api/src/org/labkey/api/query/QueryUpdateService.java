/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.security.HasPermission;
import org.labkey.api.security.User;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
/**
 * This interface should be implemented by modules that expose queries
 * that can be updated by the HTTP-based APIs, or any other code that
 * uses maps to work with things that act like rows.
 * <p>
 * This interface has the basic CRUD operations, each taking a reference
 * to the current user, current container, and a
 * <code>Map&lt;String,Object&gt;</code> containing either the primary
 * key value(s) or all column values.
 * </p><p>
 * Implementations may wish to extend {@link AbstractBeanQueryUpdateService},
 * which uses BeanUtils to convert maps into hard-typed beans, and vice-versa.
 * This allows the implementation to work primarily with beans instead of maps.
 * </p>
 * User: Dave
 * Date: Jun 9, 2008
 */
public interface QueryUpdateService extends HasPermission
{
    enum InsertOption
    {
        // interactive/api
        INSERT(false, false, false, false, false),
        UPSERT(false, true, false, false, false),  // like merge, but with reselectids

        // bulk
        IMPORT(true, false, true, false, false),
        /**
         * Insert or updates a subset of columns.
         * NOTE: Not supported for all tables -- tables with auto-increment primary keys in particular.
         */
        MERGE(true, true, true, false, false),
        /**
         * Like MERGE, but will insert or "re-insert" (NULLs values that are not in the import column set.)
         */
        REPLACE(true, true, true, true, false),
        IMPORT_IDENTITY(true, false, true, false, true);

        final public boolean batch;
        final public boolean mergeRows;
        final public boolean useImportAliases;
        final public boolean reselectIds;
        final public boolean replace;
        final public boolean identity_insert;

        InsertOption(boolean batch, boolean merge, boolean aliases, boolean replace, boolean identity_insert)
        {
            this.batch = batch;
            mergeRows = merge;
            useImportAliases = aliases;
            reselectIds = !batch;                           // currently reselectIds and batch are not really independent (batch should probably go away)
            assert !replace || merge;                       // replace is only meaningful for merge
            this.replace = replace;
            assert !identity_insert || (batch && !merge);   // identity_insert is only supported for bulk_insert
            this.identity_insert = identity_insert;
        }
    }

    enum ConfigParameters
    {
        Logger,
        TransactionSize,    // For configurations which support granular transaction boundaries, the # of target rows to write between commits
        TrimString,         // (Bool) Trim strings on insert
        TrimStringRight,     // (Bool) TrimRight strings on insert
        PreserveEmptyString, // (Bool) When source field is an empty string, insert it instead of replacing with null
        // used by Dataspace currently
        TargetMultipleContainers,    // (Bool) allow multi container import
        SkipTriggers,         // (Bool) skip setup and firing of trigger scripts
        SkipRequiredFieldValidation,        // (Bool) skip validation of required fields, used during import when the import of data happens in two hitches (e.g., samples in one file and sample statuses in a second)
        BulkLoad                // (Bool) skips detailed auditing
    }


    /**
     * Returns the rows identified by the keys, existing in the container, as maps.
     * If the row is not found, it is omitted from the returned list.
     * @param user      The current user.
     * @param container The container in which the data should exist.
     * @param keys      A map of primary key values.
     * @return The row data as maps.
     * @throws InvalidKeyException         Thrown if the key value(s) is(are) not valid.
     * @throws SQLException                Thrown if there was an error communicating with the database.
     * @throws QueryUpdateServiceException Thrown for implementation-specific exceptions.
     */
    List<Map<String, Object>> getRows(User user, Container container, List<Map<String, Object>> keys)
            throws InvalidKeyException, QueryUpdateServiceException, SQLException;

    /**
     * Similar to getRows(), but used by ExistingRecordsDataIterator, this exists because SampleTypeUpdateService is special.
     * @param user      The current user.
     * @param container The container in which the data should exist.
     * @param keys      A map of primary key values for each rowNumber.
     * @return The rows data as maps for each rowNumber.
     * @throws InvalidKeyException         Thrown if the key value(s) is(are) not valid.
     * @throws SQLException                Thrown if there was an error communicating with the database.
     * @throws QueryUpdateServiceException Thrown for implementation-specific exceptions.
     */
    Map<Integer, Map<String, Object>> getExistingRows(User user, Container container, Map<Integer, Map<String, Object>> keys)
            throws InvalidKeyException, QueryUpdateServiceException, SQLException;

    /**
     * Inserts or merges the given values into the source table of this query.
     * The operation to be performed and import behavior is configured by the <code>context</code> parameter.
     *
     * @param user The current user.
     * @param container The container in which the data should exist.
     * @param context The context controls the insert or merge behavior.
     * @param rows The row values provided using a DataIterator.
     * @param extraScriptContext Optional additional bindings to set in the script's context when firing batch triggers.
     *                           passed through from client code (depending on scope of request)
     * @return The number of affected rows
     * @throws SQLException Thrown if there was an error communicating with the database.
     */
    int loadRows(User user, Container container, DataIteratorBuilder rows,
                       DataIteratorContext context, @Nullable Map<String, Object> extraScriptContext) throws SQLException;

    /**
     * Inserts the given values as new rows into the source table of this query.
     * @param user The current user.
     * @param container The container in which the data should exist.
     * @param rows The row values as maps.
     * @param configParameters Extra information about how to configure the requested action. {@link ConfigParameters}
     * @param extraScriptContext Optional additional bindings to set in the script's context when firing batch triggers.
     * @return The row values after insert. If the rows have an automatically-assigned
     * primary key value(s), those should be added to the returned map. However, the
     * implementation should not completely refetch the row data. The caller will use
     * <code>getRows()</code> to refetch if that behavior is necessary.
     * @throws SQLException Thrown if there was an error communicating with the database.
     * @throws BatchValidationException Thrown if the data failed one of the validation checks
     * @throws QueryUpdateServiceException Thrown for implementation-specific exceptions.
     * @throws DuplicateKeyException Thrown if primary key values were supplied in the map
     */
    List<Map<String,Object>> insertRows(User user, Container container, List<Map<String, Object>> rows,
                                               BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, @Nullable Map<String, Object> extraScriptContext)
        throws DuplicateKeyException, BatchValidationException, QueryUpdateServiceException, SQLException;

    /**
     * Inserts the given values as new rows into the source table of this query.  Same as insertRows() except for the use
     * of DataIterator.  importRows() may be implemented using insertRows() or vice versa.
     *
     * @param user The current user.
     * @param container The container in which the data should exist.
     * @param rows The row values provided using a DataIterator.
     * @param extraScriptContext Optional additional bindings to set in the script's context when firing batch triggers.
     *                           passed through from client code (depending on scope of request)
     * @return The number of rows imported
     * @throws SQLException Thrown if there was an error communicating with the database.
     */
    int importRows(User user, Container container, DataIteratorBuilder rows,
          BatchValidationException errors, Map<Enum,Object> configParameters, @Nullable Map<String, Object> extraScriptContext) throws SQLException;

    /**
     * Inserts or updates the given values into the source table of this query, based on whether a row with the same PK
     * already exists. Sometimes referred to as upsert.
     *
     * @param user The current user.
     * @param container The container in which the data should exist.
     * @param rows The row values provided using a DataIterator.
     * @param configParameters Extra information about how to configure the requested action. {@link ConfigParameters}
     * @param extraScriptContext Optional additional bindings to set in the script's context when firing batch triggers,
     *                           passed through from client code (depending on scope of request)
     * @return The number of affected rows
     * @throws SQLException Thrown if there was an error communicating with the database.
     */
    int mergeRows(User user, Container container, DataIteratorBuilder rows,
                         BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, @Nullable Map<String, Object> extraScriptContext)
            throws SQLException;

    /**
     * Updates a set of rows in the source table for this query.
     * @param user The current user.
     * @param container The container in which the row should exist.
     * @param rows The new row values as maps.
     * @param oldKeys (Optional) A map containing old key values. If the keys are
     * not automatically assigned by the database, and if the row map contains
     * modified key values, the client may pass the old key values in this parameter.
     * If the key values cannot or did not change, pass null.
     * @param configParameters Extra information about how to configure the requested action. {@link ConfigParameters}
     * @param extraScriptContext Optional additional bindings to set in the script's context when firing batch triggers.
     *                           passed through from client code (depending on scope of request)
     * @return The row values after update. However, the implementation should not
     * completely refetch the row data for this returned map. The caller will use
     * the <code>getRow()</code> method to refetch if that behavior is necessary.
     * @throws InvalidKeyException Thrown if the keys (or old keys) are not valid.
     * @throws BatchValidationException Thrown if the data fails one of the validation checks
     * @throws QueryUpdateServiceException Thrown for implementation-specific exceptions.
     * @throws SQLException Thrown if there was an error communicating with the database.
     */
    List<Map<String,Object>> updateRows(User user, Container container, List<Map<String, Object>> rows,
                                               List<Map<String, Object>> oldKeys, @Nullable Map<Enum, Object> configParameters, @Nullable Map<String, Object> extraScriptContext)
            throws InvalidKeyException, BatchValidationException, QueryUpdateServiceException, SQLException;

    /**
     * Deletes a set of rows from the source table for this query.
     * @param user The current user.
     * @param container The container in which the row should exist.
     * @param keys The list of primary key values as maps.
     * @param configParameters Extra information about how to configure the requested action. {@link ConfigParameters}
     * @param extraScriptContext Optional additional bindings to set in the script's context when firing batch triggers.
     *                           passed through from client code (depending on scope of request)
     * @return The primary key values passed as the keys parameter.
     * @throws InvalidKeyException Thrown if the primary key values are not valid.
     * @throws BatchValidationException Thrown if the data fails one of the validation checks
     * @throws QueryUpdateServiceException Thrown for implementation-specific exceptions.
     * @throws SQLException Thrown if there was an error communicating with the database.
     */
    List<Map<String,Object>> deleteRows(User user, Container container, List<Map<String, Object>> keys, @Nullable Map<Enum, Object> configParameters, @Nullable Map<String, Object> extraScriptContext)
            throws InvalidKeyException, BatchValidationException, QueryUpdateServiceException, SQLException;

    /**
     * Deletes all rows from the source table for this query.  This operation is comparable to a delete without a
     * where clause.  Note that this function will fire batch triggers but not row-level triggers.
     * @param user The current user.
     * @param container The container in which the row should exist.
     * @param configParameters Extra information about how to configure the requested action. {@link ConfigParameters}
     * @param extraScriptContext Optional additional bindings to set in the script's context when firing batch triggers.
     *                           passed through from client code (depending on scope of request)
     * @return the number of rows deleted
     * @throws BatchValidationException Thrown if the data fails one of the validation checks
     * @throws QueryUpdateServiceException Thrown for implementation-specific exceptions.
     * @throws SQLException Thrown if there was an error communicating with the database.
     */
    int truncateRows(User user, Container container, @Nullable Map<Enum, Object> configParameters, @Nullable Map<String, Object> extraScriptContext)
            throws BatchValidationException, QueryUpdateServiceException, SQLException;

    /**
     * If true, disables expensive optional activity for this updater.
     * @param bulkLoad whether to write audit log, and do per insert housekeeping
     */
    void setBulkLoad(boolean bulkLoad);

    /**
     * implementations should use this to decide whether to do optional expensive operations upon updates.
     */
    boolean isBulkLoad();

    /** Setup the data iterator for any special behavior needed for the target table */
    default void configureDataIteratorContext(DataIteratorContext context) {}
}
