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
    BASIC("Basic Security",
            "Uses the security settings of the containing folder for dataset security. "+
            "Only administrators can import or delete dataset data."),

    /**
     * Anyone with update permissions can add and update dataset data
     */
    EDITABLE_DATASETS("Editable Datasets",
            "Identical to Basic Security, except that individuals with UPDATE permission " +
            "can edit, update, and delete data from datasets."),

    /**
     * Per-dataset security for read and update
     */
    ADVANCED("Advanced Study Security",
            "Allows the configuration of security on individual datasets.");

    private final String label;

    private final String description;

    SecurityType(String label, String description)
    {
        this.label = label;
        this.description = description;
    }

    public String getLabel()
    {
        return label;
    }

    public String getDescription()
    {
        return description;
    }

    public static String getHTMLDescription()
    {
        StringBuilder sb = new StringBuilder("<table>");
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
