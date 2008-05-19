/*
 * Copyright (c) 2008 LabKey Corporation
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

package org.labkey.study;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.util.Search;
import org.labkey.api.util.Search.SearchTermParser;
import org.labkey.api.util.SearchHit;
import org.labkey.api.util.SimpleSearchHit;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.*;

import javax.servlet.ServletException;
import java.util.List;
import java.util.Set;

/**
 * User: jgarms
 */
public class StudySearch implements Search.Searchable
{
    private static final String SEARCH_DOMAIN = "study";
    private static final String SEARCH_HIT_TYPE = "labkey/study";

    public void search(SearchTermParser parser, Set<Container> containers, List<SearchHit> hits)
    {
        for (Container c : containers)
        {
            Study study = StudyManager.getInstance().getStudy(c);
            if (study == null)
                continue;

            searchStudy(study, parser, hits);
            searchParticipants(study, parser, hits);
            searchDatasets(study, parser, hits);
        }
    }

    private void searchStudy(Study study, SearchTermParser parser, List<SearchHit> hits)
    {
        if (parser.matches(study.getDisplayString()))
        {
            ActionURL url = new ActionURL(StudyController.OverviewAction.class, study.getContainer());

            hits.add(new SimpleSearchHit(
                    SEARCH_DOMAIN,
                    study.getContainer().getPath(),
                    study.getLabel(),
                    url.getLocalURIString(),
                    SEARCH_HIT_TYPE,
                    "Study"
            ));

            return;
        }

        int previousHits = hits.size();
        // Search our visits to see if we want to display the overview page
        searchVisits(study, parser, hits);
        if (hits.size() > previousHits)
            return;

        // Search our cohorts to see if we want to display the overview page
        searchCohorts(study, parser, hits);
    }

    private void searchParticipants(Study study, SearchTermParser parser, List<SearchHit> hits)
    {
        for (String participantId : StudyManager.getInstance().getParticipantIds(study))
        {
            if (parser.matches(participantId))
            {
                ActionURL url = new ActionURL(StudyController.ParticipantAction.class, study.getContainer());
                url.addParameter("participantId", participantId);

                hits.add(new SimpleSearchHit(
                    SEARCH_DOMAIN,
                    study.getContainer().getPath(),
                    participantId,
                    url.getLocalURIString(),
                    SEARCH_HIT_TYPE,
                    "Study Participant"
                ));
            }
        }
    }

    private void searchDatasets(Study study, SearchTermParser parser, List<SearchHit> hits)
    {
        DataSetDefinition[] defs = study.getDataSets();
def:    for (DataSetDefinition def : defs)
        {
            if (parser.matches(def.getName()) || parser.matches(def.getLabel()) || parser.matches(def.getDescription()))
            {
                ActionURL url = new ActionURL(StudyController.DatasetAction.class, study.getContainer());
                url.addParameter("datasetId", def.getDataSetId());

                hits.add(new SimpleSearchHit(
                    SEARCH_DOMAIN,
                    study.getContainer().getPath(),
                    def.getLabel(),
                    url.getLocalURIString(),
                    SEARCH_HIT_TYPE,
                    "Study Dataset"
                ));
                continue; // No need to search columns if we've already got a hit for this definition
            }

            // Now search column names
            try
            {
                TableInfo table = def.getTableInfo(HttpView.currentContext().getUser(), false, false);
                List<ColumnInfo> columns = table.getColumns();
                for (ColumnInfo column : columns)
                {
                    if (!column.isHidden() && (
                            parser.matches(column.getName()) ||
                            parser.matches(column.getCaption()) ||
                            parser.matches(column.getDescription())
                    ))
                    {
                        ActionURL url = new ActionURL(StudyController.DatasetAction.class, study.getContainer());
                        url.addParameter("datasetId", def.getDataSetId());

                        hits.add(new SimpleSearchHit(
                            SEARCH_DOMAIN,
                            study.getContainer().getPath(),
                            def.getLabel(),
                            url.getLocalURIString(),
                            SEARCH_HIT_TYPE,
                            "Study Dataset"
                        ));
                        continue def;
                    }
                }
            }
            catch (ServletException se)
            {
                throw UnexpectedException.wrap(se);
            }
        }
    }

    private void searchVisits(Study study, SearchTermParser parser, List<SearchHit> hits)
    {
        Visit[] visits = study.getVisits();
        for (Visit visit : visits)
        {
            if (parser.matches(visit.getLabel()) || parser.matches(visit.getDisplayString()))
            {
                ActionURL url = new ActionURL(StudyController.OverviewAction.class, study.getContainer());

                hits.add(new SimpleSearchHit(
                    SEARCH_DOMAIN,
                    study.getContainer().getPath(),
                    study.getLabel(),
                    url.getLocalURIString(),
                    SEARCH_HIT_TYPE,
                    "Study"
                ));
            }
        }
    }

    private void searchCohorts(Study study, SearchTermParser parser, List<SearchHit> hits)
    {
        Cohort[] cohorts = study.getCohorts(HttpView.currentContext().getUser());
        for (Cohort cohort : cohorts)
        {
            if (parser.matches(cohort.getLabel()))
            {
                ActionURL url = new ActionURL(StudyController.OverviewAction.class, study.getContainer());

                hits.add(new SimpleSearchHit(
                    SEARCH_DOMAIN,
                    study.getContainer().getPath(),
                    study.getLabel(),
                    url.getLocalURIString(),
                    SEARCH_HIT_TYPE,
                    "Study"
                ));

                return;
            }
        }
    }

    public String getSearchResultName()
    {
        return "Study";
    }

    public String getDomainName()
    {
        return SEARCH_DOMAIN;
    }
}
