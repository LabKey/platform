/*
 * Copyright (c) 2012-2016 LabKey Corporation
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
package org.labkey.api.laboratory;

import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.ClientDependency;

import java.util.List;
import java.util.Set;

/**
 * User: bimber
 * Date: 10/3/12
 * Time: 1:46 PM
 */
public interface DataProvider
{
    /**
     * Return the name of this DataProvider
     */
    String getName();

    /**
     * Returns a key used to identify this provider
     */
    String getKey();

    /**
     * Return the URL holding instructions / help information
     */
    ActionURL getInstructionsUrl(Container c, User u);

    /**
     * Return the list of NavItems that will appear in the list of data types
     */
    List<NavItem> getDataNavItems(Container c, User u);

    /**
     * Return the list of NavItems that will appear in the list of samples
     */
    List<NavItem> getSampleNavItems(Container c, User u);

    /**
     * Return the list SettingsItems that will appear in the UI
     */
    List<NavItem> getSettingsItems(Container c, User u);

    /**
     * Return the list of ReportItems that will appear in the list of reports
     */
    List<NavItem> getReportItems(Container c, User u);

    /**
     * Return the list of ReportItems that will appear in the tabbed report UI
     */
    List<TabbedReportItem> getTabbedReportItems(Container c, User u);

    /**
     * Return the list of NavItems that will appear under the Misc section in the UI
     */
    List<NavItem> getMiscItems(Container c, User u);

    /**
     * A metadata config object that will be applied to the fields on the run template page
     */
    JSONObject getTemplateMetadata(ViewContext ctx);

    /**
     * @return Optional.  Returns a set of ClientDependencies that will be loaded on the request page for this assay
     */
    Set<ClientDependency> getClientDependencies();

    /**
     * @return The module which provides this DataProvider
     */
    Module getOwningModule();

    List<SummaryNavItem> getSummary(Container c, User u);

    List<NavItem> getSubjectIdSummary(Container c, User u, String subjectId);
}
