/*
 * Copyright (c) 2013 LabKey Corporation
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

import com.google.gwt.user.client.ui.ClickListener;
import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;
import org.labkey.api.gwt.client.ui.ImageButton;
import org.labkey.api.gwt.client.util.PropertyUtil;

/**
 * User: cnathe
 * Date: 7/26/13
 */
public class DesignerLookupConfigDialog extends DialogBox
{
    // NOTE: this is temparary UI until we convert to using Ext4 for the study designer
    public DesignerLookupConfigDialog(boolean includeVaccineProps, boolean includeAssayProps)
    {
        this.setText("Configure Dropdown Options");
        VerticalPanel vp = new VerticalPanel();

        String containerPath = PropertyUtil.getContainerPath();
        String baseFolderURL = PropertyUtil.getContextPath() + "/query" + containerPath + "/executeQuery.view?schemaName=study&query.queryName=";
        String projectPath = containerPath.indexOf("/", 1) == -1 ? containerPath : containerPath.substring(0, containerPath.indexOf("/", 1));
        String baseProjectURL = PropertyUtil.getContextPath() + "/query" + projectPath + "/executeQuery.view?schemaName=study&query.queryName=";
        boolean isProject = containerPath.equals(projectPath);
        String html = "Configure dropdown options at the project level to be shared across<br/>study designs or within this folder for study specific properties.<br/><br/><table>";

        if (includeVaccineProps)
        {
            String projectLink = baseProjectURL + "StudyDesignImmunogenTypes";
            String folderLink = baseFolderURL + "StudyDesignImmunogenTypes";
            html += "<tr><td>Immunogen Types:</td><td>[<a href='" + projectLink + "'>project</a>]</td>"
                    + (!isProject ? "<td>[<a href='" + folderLink + "'>folder</a>]</td>" : "")
                    + "</tr>";

            projectLink = baseProjectURL + "StudyDesignRoutes";
            folderLink = baseFolderURL + "StudyDesignRoutes";
            html += "<tr><td>Routes:</td><td>[<a href='" + projectLink + "'>project</a>]</td>"
                    + (!isProject ? "<td>[<a href='" + folderLink + "'>folder</a>]</td>" : "")
                    + "</tr>";

            projectLink = baseProjectURL + "StudyDesignGenes";
            folderLink = baseFolderURL + "StudyDesignGenes";
            html += "<tr><td>Genes:</td><td>[<a href='" + projectLink + "'>project</a>]</td>"
                    + (!isProject ? "<td>[<a href='" + folderLink + "'>folder</a>]</td>" : "")
                    + "</tr>";

            projectLink = baseProjectURL + "StudyDesignSubTypes";
            folderLink = baseFolderURL + "StudyDesignSubTypes";
            html += "<tr><td>SubTypes:</td><td>[<a href='" + projectLink + "'>project</a>]</td>"
                    + (!isProject ? "<td>[<a href='" + folderLink + "'>folder</a>]</td>" : "")
                    + "</tr>";
        }

        if (includeAssayProps)
        {
            String projectLink = baseProjectURL + "StudyDesignAssays";
            String folderLink = baseFolderURL + "StudyDesignAssays";
            html += "<tr><td>Assays:</td><td>[<a href='" + projectLink + "'>project</a>]</td>"
                    + (!isProject ? "<td>[<a href='" + folderLink + "'>folder</a>]</td>" : "")
                    + "</tr>";

            projectLink = baseProjectURL + "StudyDesignLabs";
            folderLink = baseFolderURL + "StudyDesignLabs";
            html += "<tr><td>Labs:</td><td>[<a href='" + projectLink + "'>project</a>]</td>"
                    + (!isProject ? "<td>[<a href='" + folderLink + "'>folder</a>]</td>" : "")
                    + "</tr>";

            projectLink = baseProjectURL + "StudyDesignUnits";
            folderLink = baseFolderURL + "StudyDesignUnits";
            html += "<tr><td>Units:</td><td>[<a href='" + projectLink + "'>project</a>]</td>"
                    + (!isProject ? "<td>[<a href='" + folderLink + "'>folder</a>]</td>" : "")
                    + "</tr>";

            projectLink = baseProjectURL + "StudyDesignSampleTypes";
            folderLink = baseFolderURL + "StudyDesignSampleTypes";
            html += "<tr><td>Sample Types:</td><td>[<a href='" + projectLink + "'>project</a>]</td>"
                    + (!isProject ? "<td>[<a href='" + folderLink + "'>folder</a>]</td>" : "")
                    + "</tr>";
        }

        vp.add(new HTML(html));

        HorizontalPanel hp = new HorizontalPanel();
        hp.add(new ImageButton("Done", new ClickListener()
        {
            public void onClick(Widget sender)
            {
                DesignerLookupConfigDialog.this.hide();
            }
        }));
        vp.add(hp);
        this.setWidget(vp);
    }
}
