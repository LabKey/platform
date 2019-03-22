/*
 * Copyright (c) 2008-2017 LabKey Corporation
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
package org.labkey.api.query.snapshot;

import org.labkey.api.data.Container;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.QueryForm;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.BindException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/*
 * User: Karl Lum
 * Date: Jul 8, 2008
 * Time: 1:57:12 PM
 */

public class QuerySnapshotService
{
    public static final String TYPE = "Query Snapshot";

    private static final Map<String, Provider> _providers = new ConcurrentHashMap<>(5); // Small capacity; currently only one provider

    static public Provider get(String schema)
    {
        // todo: add the default provider
        return _providers.get(schema);
    }

    static public void registerProvider(String schema, Provider provider)
    {
        Provider previous = _providers.putIfAbsent(schema, provider);

        if (null != previous)
            throw new IllegalStateException("A snapshot provider for schema :" + schema + " has already been registered");
    }

    public interface Provider
    {
        String getName();
        String getDescription();

        ActionURL getCreateWizardURL(QuerySettings settings, ViewContext context);

        ActionURL getEditSnapshotURL(QuerySettings settings, ViewContext context);

        void createSnapshot(ViewContext context, QuerySnapshotDefinition qsDef, BindException errors) throws Exception;
        ActionURL createSnapshot(QuerySnapshotForm form, BindException errors) throws Exception;

        /**
         * Regenerates the snapshot data, may be invoked either as the result of a manual or
         * automatic update. The implementation is responsible for logging its own audit event.
         */
        ActionURL updateSnapshot(QuerySnapshotForm form, BindException errors) throws Exception;

        /**
         * Regenerates the snapshot data, may be invoked either as the result of a manual or
         * automatic update. The implementation is responsible for logging its own audit event.
         * Allows the caller to suppress recalculation of participant/visit information for perf
         * reasons if multiple datasets are being reloaded in a row.  Note that the last update
         * MUST trigger the participant/visit recalculation or the study may be left in a bad state.
         */
        ActionURL updateSnapshot(QuerySnapshotForm form, BindException errors, boolean suppressVisitManagerRecalc) throws Exception;

        ActionURL updateSnapshotDefinition(ViewContext context, QuerySnapshotDefinition def, BindException errors) throws Exception;

        /**
         * Pauses and collects requiested dataset snapshots for later completion.  This method is used in conjunction
         * with 'resumeUpdates' to improve performance by refreshing all dataset snapshots at once for each study,
         * and then running a single update of participant/visit data. Note that pausing updates for a single source
         * study may affect multiple other studies that contain snapshot datasets which reference the source study.
         * @param sourceContainer The study container expected to generate dataset change events.  Note that this is
         * NOT generally the container that contains the snapshot datasets.
         */
        void pauseUpdates(Container sourceContainer);

        /**
         * Resumes snapshot dataset updates after they have been paused via 'pauseUpdates'.  Calling this method will
         * force an immediate refresh of all snapshot datasets affected since 'pauseUpdates' was called, and then will
         * force a full participant/visit recalculation for each affected study.
         * @param user The user responsible for generating the dataset change events.
         * @param sourceContainer The study container expected to generate dataset change events.  Note that this is
         * NOT generally the container that contains the snapshot datasets.
         */
        void resumeUpdates(User user, Container sourceContainer);
        
        /**
         * Returns the audit history view for a snapshot.
         */
        HttpView createAuditView(QuerySnapshotForm form) throws Exception;

        /**
         * Returns the list of valid display columns for a specified QueryForm. The list of columns
         * is used during the creation and editing of a snapshot, and in any UI where the list of columns
         * pertaining to a snapshot can be modified.  
         */
        List<DisplayColumn> getDisplayColumns(QueryForm queryForm, BindException errors) throws Exception;

        TableInfo getTableInfoQuerySnapshotDef();
    }

    public interface AutoUpdateable
    {
    }
}
