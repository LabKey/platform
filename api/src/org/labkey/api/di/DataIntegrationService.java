/*
 * Copyright (c) 2018 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
package org.labkey.api.di;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.Pair;
import org.labkey.api.view.NotFoundException;
import org.labkey.remoteapi.Connection;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Services provided for running ETLs within the server.
 * User: matthewb
 * Date: 2013-04-03
 */
public interface DataIntegrationService
{
    String MODULE_NAME = "DataIntegration";

    static DataIntegrationService get()
    {
        return ServiceRegistry.get().getService(DataIntegrationService.class);
    }

    static void setInstance(DataIntegrationService impl)
    {
        ServiceRegistry.get().registerService(DataIntegrationService.class, impl);
    }

    void registerStepProviders();
    @Nullable Integer runTransformNow(Container c, User u, String transformId) throws PipelineJobException, NotFoundException;

    boolean resetTransformState(Container c, User user, @NotNull String transformId);

    /** @return a pair with the total number of rows that were deleted across all tables in the ETL, and any error messages */
    Pair<Long, String> truncateTargets(Container c, User user, String transformId);

    RemoteConnection getRemoteConnection(String name, Container c, @Nullable Logger log);

    /**
     * Execute an efficient reimport operation Create a dataIterator based on target.getQueryUpdateService()
     * using a diffing merge operation e.g. ignore unchanged rows in import
     *
     * The provided DataIterator must be scrollable.  Usually it would be backed
     * by a file-based DataLoader, but that is not required.  It _must_ return the same data on each pass!
     *
     * Target table must have columns diModified (TIMESTAMP) and/or diImportHash (VARCHAR)
     *   if it exists diModified column will be matched to input column "modified"
     *   if it exists diImportHash column will be matched against an MD5 hash of the raw (unconverted) data values from input dataiterator
     * NOTE: if dataiterator contains generated data (e.g. LSID, they can be excluded form hash using setHashColumns)
     * NOTE: hash is column order sensitive
     *
     * NOTE! Not the same as QueryUpdateService.ImportOptions.REPLACE.  Each of these
     * enums is a separate operation (ReimportOperations.UPDATE does not imply UPDATE+INSERT like QueryUpdateService.ImportOptions.REPLACE does)
     */
    enum ReimportOperations
    {
        INSERT,
        UPDATE, REPLACE, // pick ONE
        DELETE
    }


    interface ReimportDataBuilder
    {
        void setReimportOptions(Set<ReimportOperations> ops);
        ReimportDataBuilder setTarget(TableInfo tableInfo);
        ReimportDataBuilder setSource(DataIteratorBuilder dib);

        /**
         * Allow caller to specify we should use this alternate unique (NOT NULL) key instead of target.getPkColumns()
         *
         * This _almost_ lets solves the dataset lsid case (e.g. use (ptid,sequencenum)).  However, we allow specifying
         * the sequencenum in so many different ways that this isn't sufficient (see SequenceNumImportHelper)
         */
        void useAlternateKey(List<ColumnInfo> ak);

        /* CONSDIER: extend TableInfo so it can supply this DataIterator (e.g. DataIterator getPrimaryKeyDataiterator(DataIterator in)) */
        void setPrimaryKeyHelper(BiFunction<DataIterator, DataIteratorContext, DataIterator> wrap);

        /* Ignore import data with modified <= modifiedSince.  This option is not supported with ReimportOperations.DELETE */
        void setModifiedSince(Timestamp modifiedSince);

        /** config params to pass to QUS insert/merge */
        void setConfigParameters(Map<Enum, Object> config);

        /** check that this Reimport configuration is valid (valid target table, valid options etc) */
        void validate();
        void execute();

        //void dryRun(BatchValidationException ex);

        // after execute() or dryRun() this method can be used
        // NOTE: if a merge is performed insert/updated are not separated (SPEC?)
        // if merge >= 0 then getInserted() and getUpdated() will == -1 (and vice versa)
        int getProcessed();     // rows read from source, might not be set if there is nothing to do (e.g. delete rows from emtpy table)
        int getInserted();      // rows inserted
        int getUpdated();       // rows updates
        int getMerged();        // in the case of merge, insert and updated rows are combined into one number (sorry)
        int getDeleted();       // rows deleted
    }

    boolean supportsReimport(TableInfo ti);
    ReimportDataBuilder createReimportBuilder(User user, Container container, TableInfo ti, BatchValidationException ex);

    enum Columns
    {
        TransformRunId("diTransformRunId"),
        TransformModified("diModified"),
        TransformModifiedBy("diModifiedBy"),
        TransformRowVersion("diRowVersion"),
        TransformCreated("diCreated"),
        TransformCreatedBy("diCreatedBy"),
        TransformImportHash("diImportHash");

        final String _name;

        Columns(String name)
        {
            _name = name;
        }

        public String getColumnName()
        {
            return _name;
        }
    }

    class RemoteConnection
    {
        public Connection connection;
        public String remoteContainer;
    }
}
