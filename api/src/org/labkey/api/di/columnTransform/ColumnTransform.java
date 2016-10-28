/*
 * Copyright (c) 2016 LabKey Corporation
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
package org.labkey.api.di.columnTransform;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.dataiterator.CopyConfig;
import org.labkey.api.dataiterator.SimpleTranslator;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.writer.ContainerUser;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * User: tgaluhn
 * Date: 9/22/2016
 *
 * Interface to define column level transformations for an ETL.
 * These could include such operations as mapping field names in flight,
 * modifying a value, applying a non-trivial lookup,
 * or performing some other operation altogether.
 *
 * An instance of the implementing class is created when the ETL xml is parsed, a new instance
 * for each usage of that class anywhere. This same instance is reused for each ETL run.
 *
 * All of the setters here are called when the ETL xml is parsed. These members are all expected
 * to be configuration settings which will be constant across ETL runs, and serializable.
 *
 * If an implementing class needs additional class level members, they MUST either be serializable objects,
 * or declared transient. Note that as the same instance of this class is reused for each run,
 * those members will retain their values unless explicitly reset/cleared.
 */
public interface ColumnTransform extends Serializable
{
    // All of the setters here are called when the ETL xml is parsed. These members are all expected
    // to be configuration settings which will be constant across ETL runs, and serializable.
    void setEtlName(@NotNull String etlName);
    void setStepId(@NotNull String stepId);
    void setSourceSchema(@Nullable SchemaKey sourceSchema);
    void setSourceQuery(@Nullable String sourceQuery);
    void setSourceColumnName(@Nullable String sourceColumnName);
    void setTargetSchema(@Nullable SchemaKey targetSchema);
    void setTargetQuery(@Nullable String targetQuery);
    void setTargetColumnName(@Nullable String targetColumnName);
    void setTargetType(@Nullable CopyConfig.TargetTypes targetType);
    void setConstants(@NotNull Map<String, Object> constants);

    // The getters for the serializable configuration properties
    @NotNull String getEtlName();
    @NotNull String getStepId();
    @Nullable SchemaKey getSourceSchema();
    @Nullable String getSourceQuery();
    @Nullable String getSourceColumnName();
    @Nullable SchemaKey getTargetSchema();
    @Nullable String getTargetQuery();
    @Nullable String getTargetColumnName();
    @Nullable CopyConfig.TargetTypes getTargetType();
    @NotNull Map<String, Object> getConstants();
    @Nullable Object getConstant(String constantName);

    // These getters return properties which are specific to an etl job, not a configuration.
    // The backing properties should be transient and are expected to be initialized in the addTransform() call.
    @NotNull SimpleTranslator getData();
    int getTransformRunId();
    @Nullable Integer getInputPosition();
    @NotNull Set<ColumnInfo> getOutColumns();
    @NotNull ContainerUser getContainerUser();

    /**
     * @return true if the source column attribute is required in the ETL xml
     *
     */
    default boolean requiresSourceColumnName() {return true;}

    /**
     *
     * @return true if the target column attribute is required in the ETL xml
     */
    default boolean requiresTargetColumnName() {return false;}

    /**
     * Returns the row value for the configured source column
     * @return the value in this row
     */
    @Nullable
    Object getInputValue();

    /**
     * Returns the row value for any arbitrary column in the source query
     * @param columnName the name of the column
     * @return the value in this row
     */
    @Nullable
    Object getInputValue(String columnName);

    /**
     * Add a column to the output of the ETL
     * @param name The name of the output column
     * @param supplier Method called to determine value for the output column
     */
    void addOutputColumn(String name, Supplier supplier);

    /**
     * Injects this ColumnTransform into the process of building DataIterators for the ETL job
     *
     * @param cu The ContainerUser context running the ETL
     * @param data SimpleTranslator holding the columns to be output into the ETL destination
     * @param transformRunId transformRunId of this run
     * @param inputPosition the index of the source column in the source query. If null, no source column was specified in the ETL xml
     * @return The set of output columns, if any, added by the transform class implementation
     */
    @NotNull
    Set<ColumnInfo> addTransform(ContainerUser cu, @NotNull SimpleTranslator data, int transformRunId, @Nullable Integer inputPosition);

    /**
     * Wrapping class for exceptions caught in doTransform(). Must be a RuntimeException b/c Java 8 Suppliers
     * don't directly support throwing checked exceptions.
     *
     * A thrown instance of this exception will be unwrapped for the ETL log to show the real cause.
     */
    class ColumnTransformException extends RuntimeException
    {
        public ColumnTransformException(String message, @NotNull Throwable cause)
        {
            super(message, cause);
        }

        public ColumnTransformException(@NotNull Throwable cause)
        {
            super(cause);
        }
    }
}
