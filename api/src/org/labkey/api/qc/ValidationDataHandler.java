/*
 * Copyright (c) 2009-2011 LabKey Corporation
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
package org.labkey.api.qc;

import org.apache.log4j.Logger;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.view.ViewBackgroundInfo;

import java.io.File;
import java.util.List;
import java.util.Map;

/*
* User: Karl Lum
* Date: Dec 22, 2008
* Time: 12:11:55 PM
*/
public interface ValidationDataHandler
{
    /**
     * Creates the data map from the uploaded file in a format that can be used by validation and analysis (transform) scripts. The DataType key is
     * used to locate a data handler that can import data in the same tsv format into the DB. This would be the case when an external data transform script
     * is invoked to possibly modify uploaded data.
     */
    public Map<DataType, List<Map<String, Object>>> getValidationDataMap(ExpData data, File dataFile, ViewBackgroundInfo info, Logger log, XarContext context, DataLoaderSettings settings) throws ExperimentException;

}