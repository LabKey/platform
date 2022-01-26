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
package org.labkey.api.assay;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.qc.DataState;
import org.labkey.api.security.User;
import org.labkey.api.view.HttpView;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Service to assign a QC state to assay data. QC state will allow administrators to support a workflow where
 * data may not be visible unless it is set to a state where access is allowed. The initial implementation
 * will support run level granularity but could be expanded to data row level as well.
 */
public interface AssayQCService
{
    List<AssayQCService> _providers = new ArrayList<>();
    AssayQCService _defaultProvider = new DefaultQCService();

    static void registerProvider(AssayQCService provider)
    {
        if (_providers.isEmpty())
        {
            _providers.add(provider);
        }
        else
            throw new RuntimeException("An implementation of AssayQCService has previously been registered");
    }

    @NotNull
    static AssayQCService getProvider()
    {
        if (!_providers.isEmpty())
        {
            return _providers.get(0);
        }
        return _defaultProvider;
    }

    /**
     * Return whether this provider supports assay QC functionality. Can be used to enable/disable UI elements
     * based on the presence of a valid QC provider.
     */
    boolean supportsQC();

    /**
     * Returns whether the specified protocol has been configured for QC
     */
    boolean isQCEnabled(ExpProtocol protocol);

    /**
     * Update the QC states for the specified runs
     */
    void setQCStates(ExpProtocol protocol, Container container, User user, List<Integer> runIds, DataState state, String comment);

    /**
     * Get the QC state (if any) associated with the run.
     */
    @Nullable
    DataState getQCState(ExpProtocol protocol, int runId) throws ExperimentException;

    /**
     * Generate the filter condition for the runs table based on the configured QC state.
     */
    SQLFragment getRunsTableCondition(ExpProtocol protocol, Container container, User user);

    /**
     * Generate the filter condition for the data table based on the configured QC state.
     */
    SQLFragment getDataTableCondition(ExpProtocol protocol, Container container, User user);

    /**
     * Returns the list of runs or data rows that have not been QC approved, if the protocol
     * does not support QC or has not been enabled for QC, then the list will be empty.
     *
     * @param runs the list of runs to check
     */
    List<Integer> getUnapprovedRuns(ExpProtocol protocol, List<Integer> runs) throws ExperimentException;
    List<Integer> getUnapprovedData(ExpProtocol protocol, List<Integer> dataIds) throws ExperimentException;

    /**
     * Determine if any assay runs are assigned the specified state
     */
    boolean isQCStateInUse(Container container, DataState state);

    /**
     * Returns the default QCState for imported data
     */
    @Nullable
    DataState getDefaultDataImportState(Container container);
    void setDefaultDataImportState(Container container, DataState state);

    /**
     * Gets/sets whether or not a blank state should be interpreted as public data or not
     */
    boolean isBlankQCStatePublic(Container container);
    void setIsBlankQCStatePublic(Container container, boolean isPublic);

    /**
     * Gets/sets whether or not a comment is required on a QC State change
     */
    boolean isRequireCommentOnQCStateChange(Container container);
    void setRequireCommentOnQCStateChange(Container container, boolean requireCommentOnQCStateChange);

    /**
     * Returns the warnings view if the specified run has a current QC state associated with it
     */
    @Nullable
    HttpView getAssayReImportWarningView(Container container, ExpRun run) throws ExperimentException;

    class DefaultQCService implements AssayQCService
    {
        @Override
        public boolean supportsQC()
        {
            return false;
        }

        @Override
        public boolean isQCEnabled(ExpProtocol protocol)
        {
            return false;
        }

        @Override
        public void setQCStates(ExpProtocol protocol, Container container, User user, List<Integer> runIds, DataState state, String comment)
        {
        }

        @Nullable
        @Override
        public DataState getQCState(ExpProtocol protocol, int runId)
        {
            return null;
        }

        @Override
        public SQLFragment getRunsTableCondition(ExpProtocol protocol, Container container, User user)
        {
            return new SQLFragment();
        }

        @Override
        public SQLFragment getDataTableCondition(ExpProtocol protocol, Container container, User user)
        {
            return new SQLFragment();
        }

        @Override
        public List<Integer> getUnapprovedRuns(ExpProtocol protocol, List<Integer> runs)
        {
            return Collections.emptyList();
        }

        @Override
        public List<Integer> getUnapprovedData(ExpProtocol protocol, List<Integer> dataIds)
        {
            return Collections.emptyList();
        }

        @Override
        public boolean isQCStateInUse(Container container, DataState state)
        {
            return false;
        }

        @Override
        public @Nullable DataState getDefaultDataImportState(Container container)
        {
            return null;
        }

        @Override
        public void setDefaultDataImportState(Container container, DataState state)
        {
        }

        @Override
        public @Nullable HttpView getAssayReImportWarningView(Container container, ExpRun run) throws ExperimentException
        {
            return null;
        }

        @Override
        public boolean isBlankQCStatePublic(Container container)
        {
            return false;
        }

        @Override
        public void setIsBlankQCStatePublic(Container container, boolean isPublic)
        {
        }

        @Override
        public boolean isRequireCommentOnQCStateChange(Container container)
        {
            return false;
        }

        @Override
        public void setRequireCommentOnQCStateChange(Container container, boolean isPublic)
        {
        }
    }
}
