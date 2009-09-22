/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.study.model;

import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.study.Study;
import org.labkey.api.data.MenuButton;
import org.labkey.api.data.Container;
import org.labkey.api.data.ActionButton;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;
import org.labkey.study.CohortFilter;
import org.labkey.study.controllers.CohortController;

import javax.servlet.ServletException;
import java.sql.SQLException;
import java.util.Map;

/**
 * User: adam
 * Date: May 13, 2009
 * Time: 10:21:20 AM
 */
public class CohortManager
{
    private CohortManager()
    {
    }

    public static void updateAutomaticCohortAssignment(StudyImpl study, User user, Integer participantCohortDataSetId, String participantCohortProperty, boolean advancedCohorts, boolean reassignParticipants) throws SQLException
    {
        study = study.createMutable();

        study.setManualCohortAssignment(false);
        study.setAdvancedCohorts(advancedCohorts);
        study.setParticipantCohortDataSetId(participantCohortDataSetId);
        study.setParticipantCohortProperty(participantCohortProperty);
        StudyManager.getInstance().updateStudy(user, study);
        if (reassignParticipants)
            StudyManager.getInstance().updateParticipantCohorts(user, study);
    }

    public static void updateManualCohortAssignment(StudyImpl study, User user, Map<String, Integer> p2c) throws SQLException
    {
        if (!study.isManualCohortAssignment())
        {
            study = study.createMutable();
            study.setManualCohortAssignment(true);
            StudyManager.getInstance().updateStudy(user, study);
        }

        Participant[] participants = StudyManager.getInstance().getParticipants(study);

        for (Participant p : participants)
        {
            Integer newCohortId = p2c.get(p.getParticipantId());

            if (!nullSafeEqual(newCohortId, p.getCurrentCohortId()))
            {
                if (newCohortId.intValue() == -1) // unassigned cohort
                    p.setCurrentCohortId(null);
                else
                    p.setCurrentCohortId(newCohortId);

                StudyManager.getInstance().updateParticipant(user, p);
            }
        }
    }


    // TODO: Check for null label here?
    public static CohortImpl createCohort(Study study, User user, String newLabel) throws ServletException, SQLException
    {
        CohortImpl cohort = new CohortImpl();

        // Check if there's a conflict
        org.labkey.api.study.Cohort existingCohort = StudyManager.getInstance().getCohortByLabel(study.getContainer(), user, newLabel);

        if (existingCohort != null)
            throw new ServletException("A cohort with the label '" + newLabel + "' already exists");

        cohort.setLabel(newLabel);

        StudyManager.getInstance().createCohort(study, user, cohort);

        return cohort;
    }


    private static <T> boolean nullSafeEqual(T first, T second)
    {
        if (first == null && second == null)
            return true;
        if (first == null)
            return false;
        return first.equals(second);
    }

    public static ActionButton createCohortButton(ViewContext context, CohortFilter currentCohortFilter) throws ServletException
    {
        Container container = context.getContainer();
        User user = context.getUser();
        if (StudyManager.getInstance().showCohorts(container, user))
        {
            CohortImpl[] cohorts = StudyManager.getInstance().getCohorts(container, user);
            if (cohorts.length > 0)
            {
                MenuButton button = new MenuButton("Cohorts");
                ActionURL allCohortsURL = CohortFilter.clearURLParameters(context.cloneActionURL());
                NavTree item = new NavTree("All", allCohortsURL.toString());
                item.setId("Cohorts:All");
                if (currentCohortFilter == null)
                    item.setSelected(true);
                button.addMenuItem(item);

                Study study = StudyManager.getInstance().getStudy(container);

                if (study.isAdvancedCohorts())
                {
                    for (CohortFilter.Type type : CohortFilter.Type.values())
                    {
                        NavTree typeItem = new NavTree(type.getTitle());

                        for (CohortImpl cohort : cohorts)
                        {
                            CohortFilter filter = new CohortFilter(type, cohort.getRowId());
                            ActionURL url = filter.addURLParameters(context.cloneActionURL());
                            item = new NavTree(cohort.getLabel(),url.toString());
                            item.setId("Cohorts:" + type.name() + "_" + cohort.getLabel());
                            if (filter.equals(currentCohortFilter))
                                item.setSelected(true);
                            typeItem.addChild(item);
                        }
                        button.addMenuItem(typeItem);
                    }
                }
                else
                {
                    for (CohortImpl cohort : cohorts)
                    {
                        CohortFilter filter = new CohortFilter(CohortFilter.Type.PTID_CURRENT, cohort.getRowId());
                        ActionURL url = filter.addURLParameters(context.cloneActionURL());
                        item = new NavTree(cohort.getLabel(), url.toString());
                        item.setId("Cohorts:" + CohortFilter.Type.PTID_CURRENT.name() + "_" + cohort.getLabel());
                        if (filter.equals(currentCohortFilter))
                            item.setSelected(true);
                        button.addMenuItem(item);
                    }
                }

                if (container.hasPermission(user, AdminPermission.class))
                {
                    button.addSeparator();
                    button.addMenuItem("Manage Cohorts", new ActionURL(CohortController.ManageCohortsAction.class, container));
                }
                return button;
            }
        }
        return null;
    }

}
