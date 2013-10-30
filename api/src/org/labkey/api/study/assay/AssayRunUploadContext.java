/*
 * Copyright (c) 2007-2012 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.qc.TransformResult;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.util.Map;

/**
 * Provides information needed for assay import attempts, such as the values of batch and run fields, the container
 * into which the run should be inserted, etc. Specific assay implementations may need to extend the basic interface
 * to include assay-specific metadata that they require.
 *
 * Different implementations can get the information from different sources. Examples include HTTP POST data, a run
 * that's already in the database, or from files that are already on the server.
 *
 * User: brittp
 * Date: Jul 11, 2007
*/
public interface AssayRunUploadContext<ProviderType extends AssayProvider>
{
    @NotNull
    ExpProtocol getProtocol();

    /** @return values of run fields to be inserted as part of the import */
    Map<DomainProperty, String> getRunProperties() throws ExperimentException;

    /** @return values of batch fields to be inserted as part of the import */
    Map<DomainProperty, String> getBatchProperties();

    String getComments();

    String getName();

    User getUser();

    @NotNull
    Container getContainer();

    @Nullable
    HttpServletRequest getRequest();

    ActionURL getActionURL();

    /** @return Map of file input name to file on disk */
    @NotNull
    Map<String, File> getUploadedData() throws ExperimentException;

    ProviderType getProvider();

    Map<DomainProperty, Object> getDefaultValues(Domain domain, String scope) throws ExperimentException;

    Map<DomainProperty, Object> getDefaultValues(Domain domain) throws ExperimentException;

    void saveDefaultValues(Map<DomainProperty, String> values, String scope) throws ExperimentException;

    void saveDefaultBatchValues() throws ExperimentException;

    void saveDefaultRunValues() throws ExperimentException;

    void clearDefaultValues(Domain domain) throws ExperimentException;

    String getTargetStudy();

    TransformResult getTransformResult();

    void setTransformResult(TransformResult result);

    /** The RowId for the run that is being deleted and reuploaded, or null if this is a new run */
    Integer getReRunId();

    void uploadComplete(ExpRun run) throws ExperimentException;
}
