/*
 * Copyright (c) 2017 LabKey Corporation
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
package org.labkey.api.study.assay;

import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentListener;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;

import java.util.ArrayList;
import java.util.List;

public class ExperimentListenerImpl implements ExperimentListener
{
    @Override
    public List<ValidationException> afterResultDataCreated(Container container, User user, ExpRun run, ExpProtocol protocol)
    {
        List<ValidationException> errors = new ArrayList<>();

        // copy results data to the target study if the protocol is configured to auto copy
        for (String error : AssayPublishService.get().autoCopyResults(protocol, run, user, container))
        {
            errors.add(new ValidationException(error));
        }
        return errors;
    }
}
