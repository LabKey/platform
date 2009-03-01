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

import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Handler;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.query.ExpRunTable;
import org.labkey.api.exp.api.*;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.study.actions.AssayRunUploadForm;
import org.labkey.api.study.query.ResultsQueryView;
import org.labkey.api.study.query.RunListQueryView;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.common.util.Pair;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: brittp
 * Date: Jul 11, 2007
 * Time: 9:59:49 AM
 */
public interface AssayProvider extends Handler<ExpProtocol>
{
    Domain getBatchDomain(ExpProtocol protocol);

    Domain getRunDomain(ExpProtocol protocol);

    Domain getRunInputDomain(ExpProtocol protocol);

    Domain getRunDataDomain(ExpProtocol protocol);

    /**
     * Creates a run, but does not persist it to the database. Creates the run only, no protocol applications, etc.
     */
    ExpRun createExperimentRun(String name, Container container, ExpProtocol protocol);

    Pair<ExpRun, ExpExperiment> saveExperimentRun(AssayRunUploadContext context, ExpExperiment batch) throws ExperimentException, ValidationException;

    List<PropertyDescriptor> getRunTableColumns(ExpProtocol protocol);

    List<AssayDataCollector> getDataCollectors(Map<String, File> uploadedFiles);

    String getName();

    ExpProtocol createAssayDefinition(User user, Container container, String name, String description) throws ExperimentException;

    List<Pair<Domain, Map<DomainProperty, Object>>> createDefaultDomains(Container c, User user);

    HttpView getDataDescriptionView(AssayRunUploadForm form);

    Container getAssociatedStudyContainer(ExpProtocol protocol, Object dataId);

    Map<String, Class<? extends Controller>> getImportActions();

    TableInfo createDataTable(UserSchema schema, String alias, ExpProtocol protocol);

    ExpRunTable createRunTable(UserSchema schema, String alias, ExpProtocol protocol);

    FieldKey getParticipantIDFieldKey();

    FieldKey getVisitIDFieldKey(Container targetStudy);

    FieldKey getRunIdFieldKeyFromDataRow();

    FieldKey getDataRowIdFieldKey();

    FieldKey getSpecimenIDFieldKey();

    ActionURL copyToStudy(User user, ExpProtocol protocol, Container study, Map<Integer, AssayPublishKey> dataKeys, List<String> errors);

    boolean canCopyToStudy();

    List<ParticipantVisitResolverType> getParticipantVisitResolverTypes();

    List<Pair<Domain, Map<DomainProperty, Object>>> getDomains(ExpProtocol protocol);

    Set<String> getReservedPropertyNames(ExpProtocol protocol, Domain domain);

    Pair<ExpProtocol, List<Pair<Domain, Map<DomainProperty, Object>>>> getAssayTemplate(User user, Container targetContainer);

    Pair<ExpProtocol, List<Pair<Domain, Map<DomainProperty, Object>>>> getAssayTemplate(User user, Container targetContainer, ExpProtocol toCopy);

    boolean isFileLinkPropertyAllowed(ExpProtocol protocol, Domain domain);

    boolean isMandatoryDomainProperty(Domain domain, String propertyName);

    boolean allowUpload(User user, Container container, ExpProtocol protocol);

    boolean allowDefaultValues(Domain domain);

    HttpView getDisallowedUploadMessageView(User user, Container container, ExpProtocol protocol);

    ResultsQueryView createResultsQueryView(ViewContext context, ExpProtocol protocol);

    RunListQueryView createRunQueryView(ViewContext context, ExpProtocol protocol);

    boolean hasCustomView(IAssayDomainType domainType, boolean details);

    ModelAndView createBatchesView(ViewContext context, ExpProtocol protocol);

    ModelAndView createBatchDetailsView(ViewContext context, ExpProtocol protocol, ExpExperiment batch);

    ModelAndView createRunsView(ViewContext context, ExpProtocol protocol);

    ModelAndView createRunDetailsView(ViewContext context, ExpProtocol protocol, ExpRun run);

    ModelAndView createResultsView(ViewContext context, ExpProtocol protocol);

    public ModelAndView createResultDetailsView(ViewContext context, ExpProtocol protocol, ExpData data, Object dataRowId);

    void deleteProtocol(ExpProtocol protocol, User user) throws ExperimentException;

    /**
     * Get the action that implements the assay designer for this type
     */
    Class<? extends Controller> getDesignerAction();

    /**
     * Returns true if the given provider can display a useful details page for dataset data that has been copied.
     * If a provider is a simple GPAT, then it does not have a useful details page
     * @return
     */
    boolean hasUsefulDetailsPage();

    public enum Scope {
        ALL,
        ASSAY_TYPE,
        ASSAY_DEF,
    }

    /**
     * File based QC and analysis scripts can be added to a protocol and invoked when the validate
     * method is called. Set to an empty list if no scripts exist.
     * @param protocol
     * @param scripts
     */
    void setValidationAndAnalysisScripts(ExpProtocol protocol, List<File> scripts) throws ExperimentException;

    List<File> getValidationAndAnalysisScripts(ExpProtocol protocol, Scope scope);

    void validate(AssayRunUploadContext context, ExpRun run) throws ValidationException;

    /**
     * @return the data type that this run creates for its analyzed results
     */
    DataType getDataType();

    /** @return a short description of this assay type - what kinds of data it can be used to analyze, etc */
    String getDescription();
}
