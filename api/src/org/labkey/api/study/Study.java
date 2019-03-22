/*
 * Copyright (c) 2009-2017 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * User: kevink
 * Date: May 27, 2009
 */
public interface Study extends StudyEntity
{
    List<? extends Visit> getVisits(Visit.Order order);

    Map<String, Double> getVisitAliases();

    Dataset getDataset(int id);

    Dataset getDatasetByName(String name);

    Dataset getDatasetByLabel(String label);

    List<? extends Dataset> getDatasets();

    List<? extends Dataset> getDatasetsByType(String... types);

    List<? extends Location> getLocations();

    List<? extends Cohort> getCohorts(User user);

    boolean hasGWTStudyDesign(Container c, User user);

    List<? extends AssaySpecimenConfig> getAssaySpecimenConfigs(String sortCol);

    List<? extends Visit> getVisitsForAssaySchedule();

    List<? extends Product> getStudyProducts(User user, String role);

    List<? extends Treatment> getStudyTreatments(User user);

    List<? extends TreatmentVisitMap> getStudyTreatmentVisitMap(Container container, @Nullable Integer cohortId);

    List<? extends Visit> getVisitsForTreatmentSchedule();

    List<? extends ParticipantCategory> getParticipantCategories(User user);

    boolean isAdvancedCohorts();

    TimepointType getTimepointType();

    Date getStartDate();

    String getSubjectNounSingular();

    String getSubjectNounPlural();

    String getSubjectColumnName();

    String getSearchDisplayTitle();

    String getSearchKeywords();

    String getSearchBody();

    String getDescription();

    String getDescriptionRendererType();

    String getDescriptionHtml();

    String getInvestigator();

    String getGrant();

    String getAssayPlan();

    void attachProtocolDocument(List<AttachmentFile> files , User user)  throws SQLException, IOException;

    void removeProtocolDocument(String name, User user)  throws SQLException, IOException;

    List<Attachment> getProtocolDocuments ();

    boolean isAncillaryStudy();

    boolean hasSourceStudy();

    boolean isSnapshotStudy();

    Study getSourceStudy();

    StudySnapshotType getStudySnapshotType();

    boolean isEmptyStudy();

    /**
     * Attempts to resolve an existing visit.
     * @param visitID is used for visit-based studies.
     * @param date used for date-based.
     * @param participantID used for date-based.
     * @param returnPotentialTimepoints return timepoints that have not been created yet, but would be automatically
     * created if such a dataset or specimen row existed
     */
    Visit getVisit(String participantID, Double visitID, Date date, boolean returnPotentialTimepoints);

    /** For date-based studies, the default duration for timepoints, in days */
    int getDefaultTimepointDuration();

    String getAlternateIdPrefix();

    int getAlternateIdDigits();

    boolean isAllowReqLocRepository();
    boolean isAllowReqLocClinic();
    boolean isAllowReqLocSal();
    boolean isAllowReqLocEndpoint();

    // "is" prefix doesn't work with "Boolean", use get
    /**
     * @return true if this study is at the project-level and any studies in subfolders MAY have shared datasets.
     */
    Boolean getShareDatasetDefinitions();

    /**
     * @return true if this study is at the project-level and any studies in subfolders MUST have shared visits.
     */
    Boolean getShareVisitDefinitions();

    /**
     * @return true if this study is at the project-level and is a DataspaceStudyFolderType (TODO: don't rely on DataspaceStudyFolderType)
     */
    boolean isDataspaceStudy();

    /**
     * @return true if this study can be exported, i.e. it is not a dataspace project/study and the user has admin permissions
 *              OR it is a dataspace project folder with a visit based study that uses both shared datasets and shared visits.
     */
    boolean allowExport(User user);
}
