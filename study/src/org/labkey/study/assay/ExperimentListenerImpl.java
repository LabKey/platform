/*
 * Copyright (c) 2017-2019 LabKey Corporation
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
package org.labkey.study.assay;

import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentListener;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.study.assay.AssayPublishService;

import java.util.ArrayList;
import java.util.List;

public class ExperimentListenerImpl implements ExperimentListener
{
    @Override
    public void afterResultDataCreated(Container container, User user, ExpRun run, ExpProtocol protocol) throws BatchValidationException
    {
        List<ValidationException> errors = new ArrayList<>();
        List<String> copyToStudyErrors = new ArrayList<>();

        AssayPublishService.get().autoCopyResults(protocol, run, user, container, copyToStudyErrors);

        // copy results data to the target study if the protocol is configured to auto copy
        for (String error : copyToStudyErrors)
        {
            errors.add(new ValidationException(error));
        }
        if (!errors.isEmpty())
        {
            throw new BatchValidationException(errors, null);
        }
    }
}
