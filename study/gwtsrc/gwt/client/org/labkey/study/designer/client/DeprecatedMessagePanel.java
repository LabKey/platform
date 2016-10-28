/*
 * Copyright (c) 2016 LabKey Corporation
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
package gwt.client.org.labkey.study.designer.client;

import com.google.gwt.user.client.ui.HTML;
import org.labkey.api.gwt.client.util.PropertyUtil;

public class DeprecatedMessagePanel extends HTML
{
    public DeprecatedMessagePanel(String panelName)
    {
        String html = "<p class='labkey-error'><b>Notice</b>: this study design editor format has been deprecated. " +
            "Please create any new study designs in the format as defined by the ";

        if (panelName != null && panelName.toLowerCase().equals("vaccine"))
        {
            String manageLinkTxt = "Manage Study Products";
            String manageUrl = PropertyUtil.getRelativeURL("manageStudyProducts", "study-design");
            html += "<a class='labkey-text-link' href='" + manageUrl + "'>" + manageLinkTxt + "</a> link ";
        }
        else if (panelName != null && panelName.toLowerCase().equals("immunizations"))
        {
            String manageLinkTxt = "Manage Treatments";
            String manageUrl = PropertyUtil.getRelativeURL("manageTreatments", "study-design");
            html += "<a class='labkey-text-link' href='" + manageUrl + "'>" + manageLinkTxt + "</a> link ";
        }
        else if (panelName != null && panelName.toLowerCase().equals("assays"))
        {
            String manageLinkTxt = "Manage Assay Schedule";
            String manageUrl = PropertyUtil.getRelativeURL("manageAssaySchedule", "study-design");
            html += "<a class='labkey-text-link' href='" + manageUrl + "'>" + manageLinkTxt + "</a> link ";
        }
        else
        {
            html += "manage links ";
        }

        html += "on the study Manage tab.</p>";

        setHTML(html);
    }
}
