/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

package org.labkey.api.study.assay;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.query.ExpRunTable;
import org.labkey.api.security.User;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;

import java.util.List;

/**
 * User: jeckels
 * Date: Jul 13, 2007
 */
public class AssayService
{
    static private Interface INSTANCE;

    public static final String RUN_PROPERTIES_COLUMN_NAME = "RunProperties";
    public static final String BATCH_PROPERTIES_COLUMN_NAME = "BatchProperties";
    public static final String BATCH_COLUMN_NAME = "Batch";

    static public synchronized Interface get()
    {
        return INSTANCE;
    }

    static public synchronized void setInstance(Interface impl)
    {
        INSTANCE = impl;
    }

    public interface Interface
    {
        public void registerAssayProvider(AssayProvider provider);
        public AssayProvider getProvider(String providerName);
        public AssayProvider getProvider(ExpProtocol protocol);
        public List<AssayProvider> getAssayProviders();
        public WebPartView createAssayListView(ViewContext context, boolean portalView);

        ExpRunTable createRunTable(ExpProtocol protocol, AssayProvider provider, User user, Container container);

        public AssaySchema createSchema(User user, Container container);

        public String getBatchesTableName(ExpProtocol protocol);
        public String getRunsTableName(ExpProtocol protocol);
        public String getResultsTableName(ExpProtocol protocol);

        List<ExpProtocol> getAssayProtocols(Container container);

        /**
         * Populates the import button with possible containers
         * @param protocol
         * @param user
         * @param currentContainer
         * @param isStudyView true if this view is from a study, and thus should exclude the current container
         * unless it already has assay data in it
         * @return
         */
        public List<ActionButton> getImportButtons(ExpProtocol protocol, User user, Container currentContainer, boolean isStudyView);

        /**
         * Creates a batch object but does not save it to the database
         * @param container location for this batch to live
         * @param name preferred name for the batch. If null, a default name will be assigned. If the name is already in
         * @param protocol
         */
        public ExpExperiment createStandardBatch(Container container, String name, ExpProtocol protocol);

        /**
         * @return the batch object for the assay run, if it has one.
         */
        @Nullable
        public ExpExperiment findBatch(ExpRun run);

        public void indexAssays(Container c);
    }
}
