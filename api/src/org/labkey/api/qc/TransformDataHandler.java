/*
 * Copyright (c) 2009-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.api.qc;

import org.labkey.api.assay.AssayRunUploadContext;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpRun;

/**
 * User: klum
 * Date: May 5, 2009
 */
public interface TransformDataHandler extends ValidationDataHandler
{
    /**
     * Imports the data map which may have been transformed by an external script.
     */
    void importTransformDataMap(ExpData data, AssayRunUploadContext<?> context, ExpRun run, DataIteratorBuilder dataMap) throws ExperimentException;
}
