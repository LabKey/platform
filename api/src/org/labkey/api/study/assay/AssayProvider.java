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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Handler;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.IAssayDomainType;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.module.Module;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.qc.DataExchangeHandler;
import org.labkey.api.security.User;
import org.labkey.api.study.actions.AssayRunUploadForm;
import org.labkey.api.study.assay.pipeline.AssayRunAsyncContext;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An AssayProvider is the main implementation point for participating in the overall assay framework. It provides
 * UI, data parsing, configuration, etc for distinct types of assay data. A full, custom AssayProvider implementation
 * will rely on a variety of other implementation classes, but the AssayProvider is the coordinating point that
 * knows what those other classes are. A typical implementation approach is mix and match with custom and standard
 * implementations to hook in assay-specific customizations.
 *
 * User: brittp
 * Date: Jul 11, 2007
 */
public interface AssayProvider extends Handler<ExpProtocol>
{
    /** Level of support provided for re running (starting from an existing run and creating a new run to take its place */
    enum ReRunSupport
    {
        /** No form of re run is supported */
        None,
        /** Assay offers re run, but always deletes the old version of the run */
        ReRunAndDelete,
        /** Assay offers re run, and retains the original version of the run */
        ReRunAndReplace
    }

    /**
     * Creates a schema scoped to the assay provider.
     * This will be exposed a child schema of the top-level 'assay' schema. It includes a separate child schema for
     * each assay design that is in scope. Specific providers may include additional child schemas or queries.
     */
    AssayProviderSchema createProviderSchema(User user, Container container, Container targetStudy);

    /** Get a schema that includes queries like Batch, Run, Results, and any additional tables. */
    AssayProtocolSchema createProtocolSchema(User user, Container container, @NotNull ExpProtocol protocol, @Nullable Container targetStudy);

    Domain getBatchDomain(ExpProtocol protocol);

    Domain getRunDomain(ExpProtocol protocol);

    Domain getResultsDomain(ExpProtocol protocol);

    void changeDomain(User user, ExpProtocol protocol, GWTDomain<? extends GWTPropertyDescriptor> orig, GWTDomain<? extends GWTPropertyDescriptor> update);

    AssayRunCreator getRunCreator();

    /** @return all of the legal data collectors that the user can choose from for the current import attempt */
    List<AssayDataCollector> getDataCollectors(Map<String, File> uploadedFiles, AssayRunUploadForm context);

    /**
     * @return the name of the assay provider.
     * This should not change once assay designs have been created, or they will be orphaned because they will no longer match.
     */
    String getName();

    /** Get the root resource name.  Usually this is the same as the AssayProvider name, but may be shorter
     * or omit special characters. */
    String getResourceName();

    @NotNull
    AssayTableMetadata getTableMetadata(@NotNull ExpProtocol protocol);

    ExpProtocol createAssayDefinition(User user, Container container, String name, String description) throws ExperimentException;

    /**
     * Creates the default set of domains for a new assay definition, pre-populated with their default
     * sets of fields. This usually includes at least batch and run domains, and may include multiple result-
     * level domains as well. The domains are unsaved when they are returned.
     */
    List<Pair<Domain, Map<DomainProperty, Object>>> createDefaultDomains(Container c, User user);

    /** @return a view to plug into the import UI that describes the expected data files.
     * Reasonable things to include might be things like the file type (Excel/TSV/XML), layout (column headers/XSD), etc
     */
    HttpView getDataDescriptionView(AssayRunUploadForm form);

    @Nullable
    Pair<ExpProtocol.AssayDomainTypes, DomainProperty> findTargetStudyProperty(ExpProtocol protocol);

    Container getAssociatedStudyContainer(ExpProtocol protocol, Object dataId);

    /** @return the URL used to import data when the user still needs to upload data files */
    ActionURL getImportURL(Container container, ExpProtocol protocol);

    /** TargetStudy may be null if each row in dataKeys has a non-null AssayPublishKey#getTargetStudy(). */
    ActionURL copyToStudy(User user, Container assayDataContainer, ExpProtocol protocol, @Nullable Container study, Map<Integer, AssayPublishKey> dataKeys, List<String> errors);

    List<ParticipantVisitResolverType> getParticipantVisitResolverTypes();

    List<Pair<Domain, Map<DomainProperty, Object>>> getDomains(ExpProtocol protocol);

    Pair<ExpProtocol, List<Pair<Domain, Map<DomainProperty, Object>>>> getAssayTemplate(User user, Container targetContainer);

    Pair<ExpProtocol, List<Pair<Domain, Map<DomainProperty, Object>>>> getAssayTemplate(User user, Container targetContainer, ExpProtocol toCopy);

    boolean isFileLinkPropertyAllowed(ExpProtocol protocol, Domain domain);

    /**
     * Indicates whether a property is removable in the assay designers.
     * Some assay providers require certain properties to be present in order
     * to provide their baseline functionality
     */
    boolean isMandatoryDomainProperty(Domain domain, String propertyName);

    boolean allowDefaultValues(Domain domain);

    DefaultValueType[] getDefaultValueOptions(Domain domain);

    DefaultValueType getDefaultValueDefault(Domain domain);

    boolean hasCustomView(IAssayDomainType domainType, boolean details);

    ModelAndView createBeginView(ViewContext context, ExpProtocol protocol);

    ModelAndView createBatchesView(ViewContext context, ExpProtocol protocol);

    ModelAndView createBatchDetailsView(ViewContext context, ExpProtocol protocol, ExpExperiment batch);

    ModelAndView createRunsView(ViewContext context, ExpProtocol protocol);

    ModelAndView createRunDetailsView(ViewContext context, ExpProtocol protocol, ExpRun run);

    ModelAndView createResultsView(ViewContext context, ExpProtocol protocol, BindException errors);

    ModelAndView createResultDetailsView(ViewContext context, ExpProtocol protocol, ExpData data, Object dataRowId);

    void deleteProtocol(ExpProtocol protocol, User user) throws ExperimentException;

    /**
     * Get the action that implements the assay designer for this type or null if the assay has no designer.
     */
    @Nullable Class<? extends Controller> getDesignerAction();

    /**
     * Returns the action that implements the data import action for this type when the
     * assay definition does not yet exist.
     */
    Class<? extends Controller> getDataImportAction();

    /**
     * Returns true if the given provider can display a useful details page for dataset data that has been copied.
     * If a provider is a simple GPAT, then it does not have a useful details page
     */
    boolean hasUsefulDetailsPage();

    /**
     * Gets the provider that recognizes data files of the appropriate type on the server's file system so that the
     * user can import files that have already been transferred to the server.
     */
    @Nullable
    PipelineProvider getPipelineProvider();

    /** @return the links to be shown in the header of standard assay views (run and result data grids, for example) */
    List<NavTree> getHeaderLinks(ViewContext viewContext, ExpProtocol protocol, ContainerFilter containerFilter);

    enum Scope
    {
        ALL,
        ASSAY_TYPE,
        ASSAY_DEF,
    }

    /**
     * File based QC and analysis scripts can be added to a protocol and invoked when the validate
     * method is called. Set to an empty list if no scripts exist.
     */
    void setValidationAndAnalysisScripts(ExpProtocol protocol, @NotNull List<File> scripts) throws ExperimentException;

    @NotNull
    List<File> getValidationAndAnalysisScripts(ExpProtocol protocol, Scope scope);

    void setSaveScriptFiles(ExpProtocol protocol, boolean save) throws ExperimentException;
    boolean isSaveScriptFiles(ExpProtocol protocol);

    /** Whether the provider is capable of letting users edit existing result rows */
    boolean supportsEditableResults();
    void setEditableResults(ExpProtocol protocol, boolean editable) throws ExperimentException;
    boolean isEditableResults(ExpProtocol protocol);
    void setEditableRuns(ExpProtocol protocol, boolean editable) throws ExperimentException;
    boolean isEditableRuns(ExpProtocol protocol);

    /** Whether the provider is capable of doing data import in the background using a pipeline job */
    boolean supportsBackgroundUpload();
    void setBackgroundUpload(ExpProtocol protocol, boolean background) throws ExperimentException;
    boolean isBackgroundUpload(ExpProtocol protocol);

    /** What level of re run for assay data, if any, is supported */
    ReRunSupport getReRunSupport();

    /**
     * @return the data type that this run creates for its analyzed results
     */
    @Nullable AssayDataType getDataType();

    /**
    * @return data types that should be given a particular LSID or role, others files which do not match any of the types
    * will have them auto-generated based on their extension
    */
    @NotNull List<AssayDataType> getRelatedDataTypes();

    /**
     * @return a short description of this assay type - what kinds of data it can be used to analyze, etc.
     * HTML is allowed, so the string will not be HTML encoded at render time
     */
    String getDescription();

    /**
     * Return the helper to handle data exchange between the server and external scripts.
     */
    DataExchangeHandler createDataExchangeHandler();
    /** Make a context that knows how to update a run that's already been stored in the database */
    AssayRunDatabaseContext createRunDatabaseContext(ExpRun run, User user, HttpServletRequest request);
    /**
     * Make a context that knows how to do the import in the background, on a separate thread
     * (and therefore detached from the HTTP request that might have spawned it)
     */
    AssayRunAsyncContext createRunAsyncContext(AssayRunUploadContext context) throws IOException, ExperimentException;

    String getRunLSIDPrefix();

    /**
     * Return a SQL pattern that can be used to match a protocol's LSID to this AssayProvider.
     * The pattern must match a protocol's LSID in the same manner as
     * {@link #getPriority(org.labkey.api.exp.api.ExpProtocol)}.
     */
    @Nullable String getProtocolPattern();

    void registerLsidHandler();

    boolean supportsFlagColumnType(ExpProtocol.AssayDomainTypes type);

    /**@ return the module in which this assay provider is declared */
    Module getDeclaringModule();

    /**@ return any modules that will be considered active if there is an instance of this assay in the current container */
    @NotNull
    Set<Module> getRequiredModules();

    /**
     * Supplies the handler that responds to API-based requests to insert or update assay data. Null if the API-based
     * manipulation is not supported
     */
    @Nullable AssaySaveHandler getSaveHandler();

    /**
     * Create a factory to build an AssayRunUploadContext for importing an run.
     */
    AssayRunUploadContext.Factory<? extends AssayProvider, ? extends AssayRunUploadContext.Factory> createRunUploadFactory(ExpProtocol protocol, ViewContext context);
    AssayRunUploadContext.Factory<? extends AssayProvider, ? extends AssayRunUploadContext.Factory> createRunUploadFactory(ExpProtocol protocol, User user, Container c);

}
