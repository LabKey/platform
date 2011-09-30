/*
 * Copyright (c) 2006-2010 LabKey Corporation
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

package org.labkey.study.view;

import org.labkey.api.attachments.Attachment;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.study.Cohort;
import org.labkey.api.study.DataSet;
import org.labkey.api.study.Site;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.Visit;
import org.labkey.api.view.*;
import org.labkey.study.controllers.BaseStudyController;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.CohortImpl;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.SiteImpl;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.VisitImpl;
import org.springframework.validation.BindException;

import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.List;

/**
 * User: Mark Igra
 * Date: Aug 9, 2006
 * Time: 4:38:42 PM
 */
public class StudySummaryWebPartFactory extends BaseWebPartFactory
{
    public static String NAME = "Study Overview";

    public StudySummaryWebPartFactory()
    {
        super(NAME);
    }

    public static class StudySummaryBean
    {
        private Container _container;
        private StudyImpl _study;
        private String _currentURL;

        public StudySummaryBean(ViewContext portalCtx)
        {
            _container = portalCtx.getContainer();
            _currentURL = portalCtx.getActionURL().getLocalURIString();
        }

        public StudyImpl getStudy()
        {
            if (_study == null)
                _study = StudyManager.getInstance().getStudy(_container);
            return _study;
        }

        public Visit[] getVisits(Visit.Order order)
        {
            return getStudy().getVisits(order);
        }

        public List<? extends DataSet> getDataSets()
        {
            return getStudy().getDataSets();
        }

        public Site[] getSites() throws SQLException
        {
            return getStudy().getSites();
        }

        public Cohort[] getCohorts(User user) throws SQLException
        {
            return getStudy().getCohorts(user);
        }

        public long getSubjectCount()
        {
            return StudyManager.getInstance().getParticipantCount(getStudy());
        }

        public List<Attachment> getProtocolDocuments()
        {
            return getStudy().getProtocolDocuments();
        }

        public String getCurrentURL()
        {
            return _currentURL;
        }
    }

    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException
    {
        if (!portalCtx.hasPermission(ReadPermission.class))
            return new HtmlView(NAME, portalCtx.getUser().isGuest() ? "Please log in to see this data" : "You do not have permission to see this data");

        BindException errors = (BindException) HttpView.currentRequest().getAttribute("errors");
        WebPartView v = new JspView<StudySummaryBean>("/org/labkey/study/view/studySummary.jsp", new StudySummaryBean(portalCtx), errors);
        v.setTitle(NAME);

        return v;
    }
}
