/*
 * Copyright (c) 2008-2010 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.*;
import org.labkey.api.module.FolderType;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.util.Search;
import org.labkey.api.util.Search.SearchTermParser;
import org.labkey.api.util.SearchHit;
import org.labkey.api.util.SimpleSearchHit;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.study.Study;
import org.labkey.api.study.DataSet;
import org.labkey.api.study.Visit;
import org.labkey.api.study.StudyService;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.*;

import javax.servlet.ServletException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * User: jgarms
 */
public class StudySearch implements Search.Searchable
{
    private static final String SEARCH_DOMAIN = "study";
    private static final String SEARCH_HIT_TYPE = "labkey/study";

    public void search(SearchTermParser parser, Set<Container> containers, List<SearchHit> hits, User user)
    {
        List<StudyImpl> studies = new ArrayList<StudyImpl>();
        for (Container c : containers)
        {
            StudyImpl study = StudyManager.getInstance().getStudy(c);
            if (study != null)
                studies.add(study);
        }

        for (StudyImpl study : studies)
        {
            searchAll(study, parser, hits, true);
        }

        if (hits.size() == 0)
        {
            // We've found nothing. In studies, there are common mistakes like searching
            // for a study named m028 by entering mo28. Try some permutations around that
            List<String> permutations = createQueryPermutations(parser.getQuery());
            for (String query : permutations)
            {
                if (hits.size() > 0) // Just search until we find any hit at all
                    break;
                for (StudyImpl study : studies)
                {
                    Search.SearchTermParser newParser = new Search.SearchTermParser(query);
                    searchAll(study, newParser, hits, true);
                }
            }
        }
    }

    private void searchAll(@NotNull StudyImpl study, SearchTermParser parser, List<SearchHit> hits, boolean searchDatasetData)
    {
        searchStudy(study, parser, hits);
        searchParticipants(study, parser, hits);
        searchDatasets(study, parser, hits);
    }

    private void searchStudy(StudyImpl study, SearchTermParser parser, List<SearchHit> hits)
    {
        if (parser.matches(study.getDisplayString()))
        {
            hits.add(new SimpleSearchHit(
                    SEARCH_DOMAIN,
                    study.getContainer().getPath(),
                    study.getLabel(),
                    getStudyURL(study).getLocalURIString(),
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
                    "Study Subject (" + StudyService.get().getSubjectNounSingular(study.getContainer()) + ")"
                ));
            }
        }
    }

    private void searchDatasets(StudyImpl study, SearchTermParser parser, List<SearchHit> hits)
    {
        User user = HttpView.currentContext().getUser();
        DataSetDefinition[] defs = study.getDataSets();
def:    for (DataSetDefinition def : defs)
        {
            // Check if the user can view this dataset
            if (!def.canRead(user))
                continue;

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
                            parser.matches(column.getLabel()) ||
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

                // Search the actual data
                searchDatasetData(def, parser, hits);
            }
            catch (ServletException se)
            {
                throw UnexpectedException.wrap(se);
            }
        }
    }

    private void searchDatasetData(DataSet def, Search.SearchTermProvider termProvider, List<SearchHit> hits) throws ServletException
    {
        TableInfo tInfo = def.getTableInfo(HttpView.currentContext().getUser());
        SqlDialect dialect = tInfo.getSchema().getSqlDialect();

        // We already know the dataset, so all we need to get back is the participant
        String selectColumn = StudyService.get().getSubjectColumnName(def.getContainer());

        // Which columns to search? Everything user-defined
        List<String> columnsToSearch = new ArrayList<String>();
        for (ColumnInfo column : tInfo.getColumns())
        {
            if (!DataSetDefinition.isDefaultFieldName(column.getName(), def.getStudy()))
            {
                // We can't search boolean or date columns due to sql limitations
                Class columnClass = column.getJavaClass();
                if (columnClass != Boolean.class && columnClass != Boolean.TYPE && columnClass != Date.class)
                    columnsToSearch.add(column.getSelectName());
            }
        }

        // It's possible to have an empty dataset -- don't search as it causes a SQL exception
        if (columnsToSearch.size() == 0)
            return;

        String[] searchColumnNames = columnsToSearch.toArray(new String[columnsToSearch.size()]);

        String from = tInfo.toString();

        // datasets don't include the container in their columns
        Set<Container> containers = Collections.emptySet();

        SQLFragment sqlFragment = Search.getSQLFragment(
            selectColumn,
            selectColumn,
            from,
            "container",
            null,
            containers,
            termProvider,
            dialect,
            searchColumnNames);

        ResultSet rs = null;
        try
        {
            rs = Table.executeQuery(tInfo.getSchema(), sqlFragment);
            while(rs.next())
            {
                String ptid = rs.getString(1);

                ActionURL url = new ActionURL(StudyController.DatasetAction.class, def.getContainer());
                url.addFilter("Dataset", FieldKey.fromParts(selectColumn), CompareType.EQUAL, ptid);
                url.addParameter("datasetId", def.getDataSetId());

                hits.add(new SimpleSearchHit(
                    SEARCH_DOMAIN,
                    def.getContainer().getPath(),
                    def.getLabel() + " - " + ptid,
                    url.getLocalURIString(),
                    SEARCH_HIT_TYPE,
                    "Study Dataset Data"
                ));
            }
        }
        catch (SQLException se)
        {
            throw UnexpectedException.wrap(se);
        }
        finally
        {
            if (rs != null) try {rs.close();} catch (SQLException se) {}
        }
    }

    private void searchVisits(StudyImpl study, SearchTermParser parser, List<SearchHit> hits)
    {
        VisitImpl[] visits = study.getVisits(Visit.Order.SEQUENCE_NUM);
        for (VisitImpl visit : visits)
        {
            if (parser.matches(visit.getLabel()) || parser.matches(visit.getDisplayString()))
            {
                hits.add(new SimpleSearchHit(
                    SEARCH_DOMAIN,
                    study.getContainer().getPath(),
                    study.getLabel(),
                    getStudyURL(study).getLocalURIString(),
                    SEARCH_HIT_TYPE,
                    "Study"
                ));
            }
        }
    }

    private void searchCohorts(Study study, SearchTermParser parser, List<SearchHit> hits)
    {
        User user = HttpView.currentContext().getUser();
        
        // If user cannot view cohorts, don't search them
        if (!StudyManager.getInstance().showCohorts(study.getContainer(), user))
            return;

        org.labkey.api.study.Cohort[] cohorts = study.getCohorts(user);
        for (org.labkey.api.study.Cohort cohort : cohorts)
        {
            if (parser.matches(cohort.getLabel()))
            {
                hits.add(new SimpleSearchHit(
                    SEARCH_DOMAIN,
                    study.getContainer().getPath(),
                    study.getLabel(),
                    getStudyURL(study).getLocalURIString(),
                    SEARCH_HIT_TYPE,
                    "Study"
                ));

                return;
            }
        }
    }

    public String getSearchResultNamePlural()
    {
        return "Studies";
    }

    public String getDomainName()
    {
        return SEARCH_DOMAIN;
    }

    private ActionURL getStudyURL(Study study)
    {
        Container container = study.getContainer();
        FolderType folderType = container.getFolderType();
        if (folderType instanceof StudyFolderType)
        {
            // Customized portal page
            User user = HttpView.currentContext().getUser();
            return folderType.getStartURL(container, user);
        }
        else // return the standard start page
            return new ActionURL(StudyController.BeginAction.class, container);
    }

    /**
     * Returns any permutations of the provided query string.
     * e.g. m028 -> mo28
     */
    private List<String> createQueryPermutations(String query)
    {
        List<String> permutations = new ArrayList<String>();
        query = query.toLowerCase();
        if (query.contains("0"))
        {
            permutations.add(query.replaceAll("0","o"));
        }
        if (query.contains("o"))
        {
            permutations.add(query.replaceAll("o","0"));
        }
        if (query.contains("1")) // number one
        {
            permutations.add(query.replaceAll("1","l")); // replace one with L
        }
        if (query.contains("l")) // letter L
        {
            permutations.add(query.replaceAll("l","1")); // replace L with one
        }

        return permutations;
    }
}
