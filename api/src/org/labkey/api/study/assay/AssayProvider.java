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
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Handler;
import org.labkey.api.exp.api.*;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.qc.DataExchangeHandler;
import org.labkey.api.security.User;
import org.labkey.api.study.actions.AssayRunUploadForm;
import org.labkey.api.study.assay.pipeline.AssayRunAsyncContext;
import org.labkey.api.study.query.ResultsQueryView;
import org.labkey.api.study.query.RunListQueryView;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.pipeline.PipelineProvider;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * User: brittp
 * Date: Jul 11, 2007
 * Time: 9:59:49 AM
 */
public interface AssayProvider extends Handler<ExpProtocol>
{
    AssayProviderSchema createProviderSchema(User user, Container container, Container targetStudy);

    /** Get a schema for Batch, Run, Results, and any additional tables. */
    AssayProtocolSchema createProtocolSchema(User user, Container container, @NotNull ExpProtocol protocol, @Nullable Container targetStudy);

    Domain getBatchDomain(ExpProtocol protocol);

    Domain getRunDomain(ExpProtocol protocol);

    Domain getResultsDomain(ExpProtocol protocol);

    AssayRunCreator getRunCreator();

    List<AssayDataCollector> getDataCollectors(Map<String, File> uploadedFiles, AssayRunUploadForm context);

    String getName();

    /** Get the root resource name.  Usually this is the same as the AssayProvider name, but may be shorter or not contain special characters. */
    String getResourceName();

    @NotNull
    AssayTableMetadata getTableMetadata(@NotNull ExpProtocol protocol);

    ExpProtocol createAssayDefinition(User user, Container container, String name, String description) throws ExperimentException;

    List<Pair<Domain, Map<DomainProperty, Object>>> createDefaultDomains(Container c, User user);

    HttpView getDataDescriptionView(AssayRunUploadForm form);

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

    public ModelAndView createResultDetailsView(ViewContext context, ExpProtocol protocol, ExpData data, Object dataRowId);

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

    PipelineProvider getPipelineProvider();

    /** Upgrade from property store to hard table */
    void upgradeAssayDefinitions(User user, ExpProtocol protocol, double targetVersion) throws SQLException;

    List<NavTree> getHeaderLinks(ViewContext viewContext, ExpProtocol protocol, ContainerFilter containerFilter);

    public enum Scope {
        ALL,
        ASSAY_TYPE,
        ASSAY_DEF,
    }

    /**
     * File based QC and analysis scripts can be added to a protocol and invoked when the validate
     * method is called. Set to an empty list if no scripts exist.
     */
    void setValidationAndAnalysisScripts(ExpProtocol protocol, List<File> scripts) throws ExperimentException;

    List<File> getValidationAndAnalysisScripts(ExpProtocol protocol, Scope scope);

    void setSaveScriptFiles(ExpProtocol protocol, boolean save) throws ExperimentException;
    boolean isSaveScriptFiles(ExpProtocol protocol);

    boolean supportsEditableResults();
    void setEditableResults(ExpProtocol protocol, boolean editable) throws ExperimentException;
    boolean isEditableResults(ExpProtocol protocol);
    void setEditableRuns(ExpProtocol protocol, boolean editable) throws ExperimentException;
    boolean isEditableRuns(ExpProtocol protocol);

    boolean supportsBackgroundUpload();
    boolean supportsReRun();
    void setBackgroundUpload(ExpProtocol protocol, boolean background) throws ExperimentException;
    boolean isBackgroundUpload(ExpProtocol protocol);

    /**
     * @return the data type that this run creates for its analyzed results
     */
    AssayDataType getDataType();

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
    /** Make a context that knows how to do the import in the background, on a separate thread from the final HTTP step in the wizard */
    AssayRunAsyncContext createRunAsyncContext(AssayRunUploadContext context) throws IOException, ExperimentException;
    String getRunLSIDPrefix();
    void registerLsidHandler();

    boolean supportsFlagColumnType(ExpProtocol.AssayDomainTypes type);
}
