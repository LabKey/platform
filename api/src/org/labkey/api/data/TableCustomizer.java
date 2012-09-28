/*
 * Copyright (c) 2012 LabKey Corporation
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
package org.labkey.api.data;

/**
 * A simple interface to provide a hook in .query.xml files for customizing a TableInfo via Java code.
 * To add a reference, include this inside of the &lt;table> element in the XML:
 * &lt;javaCustomizer>org.labkey.ehr.TableCustomizerImpl&lt;/javaCustomizer>
 *
 * User: jeckels
 * Date: 9/25/12
 */
public interface TableCustomizer
{
    public void customize(TableInfo tableInfo);
}
