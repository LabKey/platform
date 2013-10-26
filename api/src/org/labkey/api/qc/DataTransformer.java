/*
 * Copyright (c) 2009-2013 LabKey Corporation
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

import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.query.ValidationException;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayRunUploadContext;

/**
 * Takes assay data and runs it through the configured transform scripts. Each script may transform it into a new
 * representation, or simply validate that it meets whatever the custom criteria are.
 *
 * User: klum
 * Date: Sep 22, 2009
 */
public interface DataTransformer<AssayType extends AssayProvider>
{
    TransformResult transformAndValidate(AssayRunUploadContext<AssayType> context, ExpRun run) throws ValidationException;
}
