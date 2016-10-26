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
 * for each usage of that class anywhere.
 * All of the setters here are called when the ETL xml is parsed. These members are all expected
 * to be configuration settings which will be constant across ETL runs, and serializable.
 *
 * If an implementing class needs additional class level members, they MUST either be serializable objects,
 * or declared transient.
 */
public interface ColumnTransform extends Serializable
{
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
     * @return true if the source column attribute is required in the ETL xml
     *
     */
    default boolean requiresSourceColumnName() {return true;}

    /**
     *
     * @return true if the target column attribute is required in the ETL xml
     */
    default boolean requiresTargetColumnName() {return false;}
}
