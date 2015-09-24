/*
 * Copyright (c) 2006-2015 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.study.Cohort;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.Location;
import org.labkey.api.study.Visit;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.springframework.validation.BindException;

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

        public List<? extends Visit> getVisits(Visit.Order order)
        {
            return getStudy().getVisits(order);
        }

        public List<? extends Dataset> getDatasets()
        {
            return getStudy().getDatasets();
        }

        public List<? extends Location> getSites()
        {
            return getStudy().getLocations();
        }

        public List<? extends Cohort> getCohorts(User user)
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

        public String getDescriptionHtml()
        {
            String html = getStudy().getDescriptionHtml();

            // Hack!  Remove div so we can nestle the edit icon up to the text
            if (html.endsWith("</div>"))
            {
                html = html.replaceFirst("<div .*?>", "");
                html = html.substring(0, html.length() - 6);
            }

            return html;
        }

        public String getInvestigator(){
            return getStudy().getInvestigator();
        }

        public String getGrant(){
            return getStudy().getGrant();
        }
    }

    public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
    {
        if (!portalCtx.hasPermission(ReadPermission.class))
            return new HtmlView(NAME, portalCtx.getUser().isGuest() ? "Please log in to see this data" : "You do not have permission to see this data");

        BindException errors = (BindException) HttpView.currentRequest().getAttribute("errors");
        WebPartView v = new JspView<>("/org/labkey/study/view/studySummary.jsp", new StudySummaryBean(portalCtx), errors);
        v.setTitle(NAME);

        if(portalCtx.getContainer().hasPermission(portalCtx.getUser(), AdminPermission.class))
        {
            ActionURL editMetaDataURL = new ActionURL(StudyController.ManageStudyPropertiesAction.class, portalCtx.getContainer());
            editMetaDataURL.addParameter("returnURL",portalCtx.getActionURL().toString());
            NavTree edit = new NavTree("Edit", editMetaDataURL.toString(), null, "fa fa-pencil");
            v.addCustomMenu(edit);
        }
        return v;
    }
}
