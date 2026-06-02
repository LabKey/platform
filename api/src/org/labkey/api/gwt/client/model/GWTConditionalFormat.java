/*
 * Copyright (c) 2010-2026 LabKey Corporation
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
package org.labkey.api.gwt.client.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@EqualsAndHashCode
public class GWTConditionalFormat implements Serializable
{
    public static final String COLOR_REGEX = "[0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f]";
    public static final String DATA_REGION_NAME = "format";
    public static final String COLUMN_NAME = "column";

    private boolean bold = false;
    private boolean italic = false;
    private boolean strikethrough = false;
    private String textColor = null;
    private String backgroundColor = null;
    private String filter;

    public GWTConditionalFormat() {}
    
    public GWTConditionalFormat(GWTConditionalFormat f)
    {
        setBold(f.isBold());
        setItalic(f.isItalic());
        setStrikethrough(f.isStrikethrough());
        setTextColor(f.getTextColor());
        setBackgroundColor(f.getBackgroundColor());
        setFilter(f.getFilter());
    }
}
