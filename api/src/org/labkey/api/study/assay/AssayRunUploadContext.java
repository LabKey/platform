/*
 * Copyright (c) 2007-2017 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.qc.TransformResult;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.ContainerUser;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyMap;

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
public interface AssayRunUploadContext<ProviderType extends AssayProvider> extends ContainerUser
{
    @NotNull
    ExpProtocol getProtocol();

    /** @return string values of run fields to be inserted as part of the import.  Values will need to be converted into the target type. */
    Map<DomainProperty, String> getRunProperties() throws ExperimentException;

    /** @return string values of batch fields to be inserted as part of the import.  Values will need to be converted into the target type. */
    Map<DomainProperty, String> getBatchProperties() throws ExperimentException;

    String getComments();

    String getName();

    User getUser();

    @NotNull
    Container getContainer();

    /**
     * @return null if we're operating in a background thread, divorced from an in-process HTTP request
     */
    @Nullable
    HttpServletRequest getRequest();

    ActionURL getActionURL();

    /**
     * Map of file name to uploaded file that will be parsed and imported by the assay's DataHandler.
     */
    @NotNull
    Map<String, File> getUploadedData() throws ExperimentException;

    @Nullable
    default List<Map<String, Object>> getRawData()
    {
        return null;
    }

    /**
     * Map of inputs to roles that will be attached to the assay run.
     * The map key will be converted into an ExpData object using {@link org.labkey.api.data.ExpDataFileConverter}
     * The map value is the role of the file.
     * Each input file will be attached as an input ExpData to the imported assay run.
     *
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
    default Map<?, String> getInputMaterials() throws ExperimentException
    {
        return emptyMap();
    }

    @NotNull
    default Map<?, String> getOutputMaterials()
    {
        return emptyMap();
    }

    ProviderType getProvider();

    String getTargetStudy();

    TransformResult getTransformResult();

    void setTransformResult(TransformResult result);

    /** The RowId for the run that is being deleted and reuploaded, or null if this is a new run */
    Integer getReRunId();

    void uploadComplete(ExpRun run) throws ExperimentException;

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
        protected String _targetStudy;
        protected Integer _reRunId;
        protected Map<String, Object> _rawRunProperties;
        protected Map<String, Object> _rawBatchProperties;
        protected Map<?, String> _inputDatas;
        protected Map<?, String> _outputDatas;
        protected Map<?, String> _inputMaterials;
        protected Map<?, String> _outputMaterials;
        protected List<Map<String, Object>> _rawData;
        protected Map<String, File> _uploadedData;

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
         *
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

        /**
         * Map of file name to uploaded file that will be parsed and imported by the assay's DataHandler.
         * One of either uploadedData or rawData can be used, not both.
         */
        public final FACTORY setUploadedData(Map<String, File> uploadedData)
        {
            _uploadedData = uploadedData;
            return self();
        }

        /**
         * Result data to import.
         * One of either uploadedData or rawData can be used, not both.
         */
        public FACTORY setRawData(List<Map<String, Object>> rawData)
        {
            _rawData = rawData;
            return self();
        }



        /** FACTORY and self() make it easier to chain setters while returning the correct subclass type. */
        public abstract FACTORY self();

        public abstract AssayRunUploadContext<ProviderType> create();
    }
}
