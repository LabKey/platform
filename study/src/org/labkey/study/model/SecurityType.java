/*
 * Copyright (c) 2008-2015 LabKey Corporation
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

/**
 * User: jgarms
 * Date: Jul 10, 2008
 * Time: 2:51:20 PM
 */
public enum SecurityType
{
    /**
     * Basic security: only admins can add dataset data.
     */
    BASIC_READ("Basic security with read-only datasets",
            "Uses the security settings of the containing folder for dataset security. " +
            "Only administrators can import or delete dataset data.", false),

    /**
     * Anyone with update permissions can add and update dataset data
     */
    BASIC_WRITE("Basic security with editable datasets",
            "Uses the security settings of the containing folder for dataset security, allowing " +
            "non-administrators to import, edit, and delete dataset data based on their folder-assigned roles.", false),

    /**
     * Per-dataset security, read-only
     */
    ADVANCED_READ("Custom security with read-only datasets",
            "Allows the configuration of security on individual datasets. " +
            "Only administrators can import, edit, or delete dataset data. Not supported in shared studies.", true),

    /**
     * Per-dataset security, read and write
     */
    ADVANCED_WRITE("Custom security with editable datasets",
            "Allows the configuration of security on individual datasets, including the ability for " +
            "non-administrators to import, edit, and delete dataset data. Not supported in shared studies.", true);

    private final String label;

    private final String description;

    private final boolean supportsPerDatasetPermissions;

    SecurityType(String label, String description, boolean supportsPerDatasetPermissions)
    {
        this.label = label;
        this.description = description;
        this.supportsPerDatasetPermissions = supportsPerDatasetPermissions;
    }

    public String getLabel()
    {
        return label;
    }

    public String getDescription()
    {
        return description;
    }

    public boolean isSupportsPerDatasetPermissions()
    {
        return supportsPerDatasetPermissions;
    }

    public static String getHTMLDescription()
    {
        StringBuilder sb = new StringBuilder("<table class=\"labkey-pad-cells\">");
        for (SecurityType securityType : values())
        {
            sb.append("<tr><td valign=\"top\"><b>");
            sb.append(securityType.getLabel());
            sb.append("</b></td><td>");
            sb.append(securityType.getDescription());
            sb.append("</td></tr>");
        }
        sb.append("</table>");

        return sb.toString();
    }
}
