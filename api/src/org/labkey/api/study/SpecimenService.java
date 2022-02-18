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

package org.labkey.api.study;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.annotations.Migrate;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.springframework.web.servlet.mvc.Controller;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: brittp
 * Date: Oct 2, 2007
 * Time: 3:35:45 PM
 */
public interface SpecimenService
{
    String SAMPLE_TYPE_NAME = "Study Specimens";

    static void setInstance(SpecimenService serviceImpl)
    {
        ServiceRegistry.get().registerService(SpecimenService.class, serviceImpl);
    }

    static SpecimenService get()
    {
        return ServiceRegistry.get().getService(SpecimenService.class);
    }

    /** Does a search for matching GlobalUniqueIds  */
    ParticipantVisit getSampleInfo(Container studyContainer, User user, String globalUniqueId) throws SQLException;

    Set<ParticipantVisit> getSampleInfo(Container studyContainer, User user, String participantId, Date date) throws SQLException;

    Set<ParticipantVisit> getSampleInfo(Container studyContainer, User user, String participantId, Double visit) throws SQLException;

    Set<Pair<String, Date>> getSampleInfo(Container studyContainer, User user, boolean truncateTime) throws SQLException;

    Set<Pair<String, Double>> getSampleInfo(Container studyContainer, User user) throws SQLException;

    Lsid getSpecimenMaterialLsid(@NotNull Container studyContainer, @NotNull String id);

    String getActiveSpecimenImporter(@NotNull Container studyContainer);

    void importSpecimens(User user, Container container, List<Map<String, Object>> rows, boolean merge) throws SQLException, IOException, ValidationException;

    void registerSpecimenImportStrategyFactory(SpecimenImportStrategyFactory factory);

    Collection<SpecimenImportStrategyFactory> getSpecimenImportStrategyFactories();

    void registerSpecimenTransform(SpecimenTransform transform);

    Collection<SpecimenTransform> getSpecimenTransforms(Container container);

    @Nullable
    SpecimenTransform getSpecimenTransform(String name);

    PipelineJob createSpecimenReloadJob(Container container, User user, SpecimenTransform transform, @Nullable ActionURL url) throws SQLException, IOException, ValidationException;

    void registerSpecimenChangeListener(SpecimenChangeListener listener);

    @Nullable
    TableInfo getTableInfoVial(Container container);

    @Nullable
    TableInfo getTableInfoSpecimen(Container container);

    @Nullable
    TableInfo getTableInfoSpecimenEvent(Container container);

    SpecimenTablesTemplate getSpecimenTablesTemplate();

    Domain getSpecimenVialDomain(Container container, User user);

    Domain getSpecimenEventDomain(Container container, User user);

    /**
     * Returns a map of database column name -> preferred tsv column name (one per column). Does not include import aliases.
     * @return A map of db column name -> preferred tsv column name
     */
    Map<String, String> getSpecimenImporterTsvColumnMap();

    SpecimenRequestCustomizer getRequestCustomizer();

    void registerRequestCustomizer(SpecimenRequestCustomizer customizer);

    /** Hooks to allow other modules to control a few items about how specimens are treated */
    interface SpecimenRequestCustomizer
    {
        /** @return whether or not a specimen request must include at least one vial */
        boolean allowEmptyRequests();

        /** @return null if users should always supply a destination site for a given request, or the site's id if they should all be the same */
        Integer getDefaultDestinationSiteId();

        /** @return true if reports shouldn't give the option to group based on primary, additive, or derivative types */
        boolean omitTypeGroupingsWhenReporting();

        /** @return whether the current user can make changes to the status of the request */
        boolean canChangeStatus(User user);

        /** @return true if a variety of warning types including vial status, the inclusion of vials spanning multiple locations, and more should be suppressed in the UI  */
        boolean hideRequestWarnings();

        /** @return a message to show the user after a request has been submitted */
        HtmlString getSubmittedMessage(Container c, int requestId);
    }

    @Migrate // Remove after specimen module refactor (SpecimenImporter should call the impl)
    void fireSpecimensChanged(Container c, User user, Logger logger);
}
