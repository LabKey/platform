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

package org.labkey.api.assay;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CollectionUtils;
import org.labkey.api.data.Container;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.qc.TransformResult;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HasHttpRequest;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.ContainerUser;
import org.labkey.vfs.FileLike;

import java.io.File;
import java.util.Map;

import static java.util.Collections.emptyMap;

/**
 * Provides information needed for assay import attempts, such as the values of batch and run fields, the container
 * into which the run should be inserted, etc. Specific assay implementations may need to extend the basic interface
 * to include assay-specific metadata that they require. Different implementations can get the information from
 * different sources. Examples include HTTP POST data, a run that's already in the database, or from files that are
 * already on the server.
*/
public interface AssayRunUploadContext<ProviderType extends AssayProvider> extends ContainerUser, HasHttpRequest
{
    // options for how re-imported data is handled, default is REPLACE
    enum ReImportOption
    {
        REPLACE,                // existing behavior where all results are replaced with new incoming data
        MERGE_DATA              // can be interpreted by the data handler what this means, but for plate based assays merge on a plate boundary within the same plate set
    }

    @NotNull
    ExpProtocol getProtocol();

    /** @return string values of run fields to be inserted as part of the import.  Values will need to be converted into the target type. */
    Map<DomainProperty, String> getRunProperties() throws ExperimentException;

    /** @return string values of batch fields to be inserted as part of the import.  Values will need to be converted into the target type. */
    Map<DomainProperty, String> getBatchProperties() throws ExperimentException;

    String getComments();

    String getName();

    @Nullable
    default Integer getWorkflowTask() {
        return null;
    }

    @Override
    User getUser();

    @Override
    @NotNull
    Container getContainer();

    /**
     * @return null if we're operating in a background thread, divorced from an in-process HTTP request
     */
    @Override
    @Nullable
    HttpServletRequest getRequest();

    ActionURL getActionURL();

    /**
     * Map of file name to uploaded file that will be parsed and imported by the assay's DataHandler.
     */
    @NotNull
    Map<String, FileLike> getUploadedData() throws ExperimentException;

    @Nullable
    default DataIteratorBuilder getRawData()
    {
        return null;
    }

    /**
     * Map of inputs to roles that will be attached to the assay run.
     * The map key will be converted into an ExpData object using {@link org.labkey.api.data.ExpDataFileConverter}
     * The map value is the role of the file.
     * Each input file will be attached as an input ExpData to the imported assay run.
     * NOTE: These files will not be parsed or imported by the assay's DataHandler -- use {@link #getUploadedData()} instead.
     */
    @NotNull
    Map<?, String> getInputDatas();

    @NotNull
    default Map<?, String> getOutputDatas()
    {
        return emptyMap();
    }

    @NotNull
    default Map<?, String> getInputMaterials()
    {
        return emptyMap();
    }

    @NotNull
    default Map<?, String> getOutputMaterials()
    {
        return emptyMap();
    }

    /**
     * Allow importing a file that has been marked as an output of another run.
     * When true, the existing file (exp.data) will be added as an input to the
     * assay run and a new exp.data object will be created for the imported file
     * and attached as the output of the run.
     */
    default boolean isAllowCrossRunFileInputs()
    {
        return false;
    }

    default boolean isAllowLookupByAlternateKey()
    {
        return true;
    }

    ProviderType getProvider();

    String getTargetStudy();

    default String getAuditUserComment() { return null; }

    TransformResult getTransformResult();

    void setTransformResult(TransformResult result);

    /** The RowId for the run that is being deleted and reuploaded, or null if this is a new run */
    Integer getReRunId();

    default ReImportOption getReImportOption()
    {
        return ReImportOption.REPLACE;
    }

    void uploadComplete(ExpRun run) throws ExperimentException;

    default String getJobDescription()
    {
        return null;
    }

    default String getJobNotificationProvider()
    {
        return null;
    }

    default String getPipelineJobGUID()
    {
        return null;
    }

    default void setPipelineJobGUID(String jobGUID)
    {

    }

    @Nullable
    Logger getLogger();

    default void init() throws ExperimentException {}

    /**
     * For files that already existed on the server's file system prior to import, and which have been copied
     * to a temporary directory for processing, the original path to the primary data file.
     * @return null if the file was uploaded as part of the import
     */
    @Nullable
    default File getOriginalFileLocation() { return null; }

    default boolean shouldAutoFillDefaultResultColumns()
    {
        return false;
    }

    default void setAutoFillDefaultResultColumns(boolean autoFill)
    {
    }

    /**
     * Builder pattern for creating a AssayRunUploadContext instance.
     */
    abstract class Factory<ProviderType extends AssayProvider, FACTORY extends Factory<ProviderType, FACTORY>>
    {
        // Required fields
        protected final ExpProtocol _protocol;
        protected final ProviderType _provider;
        protected final User _user;
        protected final Container _container;

        // Optional fields
        protected Logger _logger;
        protected ViewContext _context;
        protected String _comments;
        protected String _name;
        protected Integer _workflowTask;
        protected String _targetStudy;
        protected Integer _reRunId;
        protected AssayRunUploadContext.ReImportOption _reImportOption;
        protected Map<String, Object> _rawRunProperties;
        protected Map<String, Object> _rawBatchProperties;
        protected Map<?, String> _inputDatas;
        protected Map<?, String> _outputDatas;
        protected Map<?, String> _inputMaterials;
        protected Map<?, String> _outputMaterials;
        protected boolean _allowCrossRunFileInputs;
        protected boolean _allowLookupByAlternateKey = true;
        protected DataIteratorBuilder _rawData;
        protected Map<String, FileLike> _uploadedData;
        protected String _jobDescription;
        protected String _jobNotificationProvider;
        protected String _auditUserComment;

        public Factory(
                @NotNull ExpProtocol protocol,
                @NotNull ProviderType provider,
                @NotNull ViewContext context)
        {
            this(protocol, provider, context.getUser(), context.getContainer());
            setViewContext(context);
        }

        public Factory(
                @NotNull ExpProtocol protocol,
                @NotNull ProviderType provider,
                @NotNull User user,
                @NotNull Container container)
        {
            _protocol = protocol;
            _provider = provider;
            _user = user;
            _container = container;
        }

        public FACTORY setLogger(Logger logger)
        {
            _logger = logger;
            return self();
        }

        public FACTORY setViewContext(ViewContext context)
        {
            _context = context;
            return self();
        }

        public FACTORY setComments(String comments)
        {
            _comments = comments;
            return self();
        }

        public final FACTORY setName(String name)
        {
            _name = name;
            return self();
        }

        public final FACTORY setWorkflowTask(Integer workflowTask)
        {
            _workflowTask = workflowTask;
            return self();
        }

        public final FACTORY setTargetStudy(String targetStudy)
        {
            _targetStudy = targetStudy;
            return self();
        }

        public final FACTORY setReRunId(Integer reRunId)
        {
            _reRunId = reRunId;
            return self();
        }

        public final FACTORY setReImportOption(AssayRunUploadContext.ReImportOption option)
        {
            _reImportOption = option;
            return self();
        }

        /**
         * HTML form POSTed or JSON submitted batch property values.
         */
        public final FACTORY setBatchProperties(Map<String, Object> rawProperties)
        {
            _rawBatchProperties = rawProperties;
            return self();
        }

        /**
         * HTML form POSTed or JSON submitted run property values.
         */
        public final FACTORY setRunProperties(Map<String, Object> rawProperties)
        {
            _rawRunProperties = rawProperties;
            return self();
        }

        /**
         * Map of inputs to roles that will be attached to the assay run.
         * The map key will be converted into an ExpData object using {@link org.labkey.api.data.ExpDataFileConverter}
         * The map value is the role of the file.
         * Each input file will be attached as an input ExpData to the imported assay run.
         * NOTE: These files will not be parsed or imported by the assay's DataHandler -- use {@link #getUploadedData()} instead.
         */
        public final FACTORY setInputDatas(Map<?, String> inputDatas)
        {
            _inputDatas = inputDatas;
            return self();
        }

        public final FACTORY setOutputDatas(Map<?, String> outputDatas)
        {
            _outputDatas = outputDatas;
            return self();
        }

        public final FACTORY setInputMaterials(Map<?, String> inputMaterials)
        {
            _inputMaterials = inputMaterials;
            return self();
        }

        public final FACTORY setOutputMaterials(Map<?, String> outputMaterials)
        {
            _outputMaterials = outputMaterials;
            return self();
        }

        public final FACTORY setAllowCrossRunFileInputs(boolean allowCrossRunFileInputs)
        {
            _allowCrossRunFileInputs = allowCrossRunFileInputs;
            return self();
        }

        public final FACTORY setAllowLookupByAlternateKey(boolean allowLookupByAlternateKey)
        {
            _allowLookupByAlternateKey = allowLookupByAlternateKey;
            return self();
        }

        /**
         * Map of file name to uploaded file that will be parsed and imported by the assay's DataHandler.
         * One of either uploadedData or rawData can be used, not both.
         */
        public final FACTORY setUploadedData(Map<String, FileLike> uploadedData)
        {
            _uploadedData = CollectionUtils.checkValueClass(uploadedData,FileLike.class);
            return self();
        }

        /**
         * Result data to import.
         * One of either uploadedData or rawData can be used, not both.
         */
        public FACTORY setRawData(DataIteratorBuilder rawData)
        {
            _rawData = rawData;
            return self();
        }

        public FACTORY setJobDescription(String jobDescription)
        {
            _jobDescription = jobDescription;
            return self();
        }

        public FACTORY setJobNotificationProvider(String jobNotificationProvider)
        {
            _jobNotificationProvider = jobNotificationProvider;
            return self();
        }

        public FACTORY setAuditUserComment(String auditUserComment)
        {
            _auditUserComment = auditUserComment;
            return self();
        }

        /** FACTORY and self() make it easier to chain setters while returning the correct subclass type. */
        public abstract FACTORY self();

        public abstract AssayRunUploadContext<ProviderType> create();
    }

    default Map<String, Object> getUnresolvedRunProperties() { return  emptyMap(); }
}
