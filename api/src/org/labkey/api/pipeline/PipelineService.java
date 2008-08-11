/*
 * Copyright (c) 2005-2008 LabKey Corporation
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
package org.labkey.api.pipeline;

import org.labkey.api.data.Container;
import org.labkey.api.pipeline.browse.BrowseForm;
import org.labkey.api.pipeline.browse.BrowseView;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.sql.SQLException;
import java.util.List;

/**
 */
abstract public class PipelineService
        implements PipelineStatusFile.StatusReader, PipelineStatusFile.StatusWriter
{
    public static final String MODULE_NAME = "Pipeline";

    static public final String PARAM_Path = "path";
    
    static PipelineService instance;

    public static PipelineService get()
    {
        return instance;
    }

    static public void setInstance(PipelineService instance)
    {
        PipelineService.instance = instance;
    }

    abstract public void registerPipelineProvider(PipelineProvider provider, String... aliases);

    abstract public PipeRoot findPipelineRoot(Container container) throws SQLException;

    abstract public PipeRoot[] getAllPipelineRoots();

    abstract public PipeRoot getPipelineRootSetting(Container container) throws SQLException;

    abstract public URI getPipelineRootSetting(Container container, String type) throws SQLException;

    abstract public PipeRoot[] getOverlappingRoots(Container c) throws SQLException;

    abstract public void setPipelineRoot(User user, Container container, URI root, String type,
                                         GlobusKeyPair globusKeyPair, boolean perlPipeline) throws SQLException;

    abstract public boolean canModifyPipelineRoot(User user, Container container);

    abstract public boolean usePerlPipeline(Container container) throws SQLException;

    abstract public File ensureSystemDirectory(URI root);

    abstract public List<PipelineProvider> getPipelineProviders();

    abstract public PipelineProvider getPipelineProvider(String name);

    abstract public String getButtonHtml(String text, ActionURL href);

    abstract public boolean isEnterprisePipeline();

    abstract public PipelineQueue getPipelineQueue();

    abstract public void queueJob(PipelineJob job) throws IOException;

    abstract public void queueJob(PipelineJob job, String initialState) throws IOException;

    abstract public void setPipelineProperty(Container container, String name, String value) throws SQLException;

    abstract public String getPipelineProperty(Container container, String name) throws SQLException;

    /**
     * For Perl Pipeline use only.  Use PipelineJobService.get().getStatusWriter().setStatusFile() instead.
     *
     * @param info Background info necessary for database access
     * @param sf The StatusFiles record
     * @throws Exception file i/o or database error
     */
    @Deprecated
    abstract public void setStatusFile(ViewBackgroundInfo info, PipelineStatusFile sf) throws Exception;

    abstract public BrowseView getBrowseView(BrowseForm form);

    // TODO: This should be on PipelineProtocolFactory
    abstract public String getLastProtocolSetting(PipelineProtocolFactory factory, Container container, User user);

    // TODO: This should be on PipelineProtocolFactory
    abstract public void rememberLastProtocolSetting(PipelineProtocolFactory factory, Container container,
                                                     User user, String protocolName);

    abstract public String getLastSequenceDbSetting(PipelineProtocolFactory factory, Container container, User user);

    abstract public void rememberLastSequenceDbSetting(PipelineProtocolFactory factory, Container container, User user,
                                                       String sequenceDbPath, String sequenceDb);

    abstract public List<String> getLastSequenceDbPathsSetting(PipelineProtocolFactory factory, Container container, User user);

    abstract public void rememberLastSequenceDbPathsSetting(PipelineProtocolFactory factory, Container container,
                                                            User user, List<String> sequenceDbPaths);

}
