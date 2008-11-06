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
package org.labkey.experiment.api;

import org.labkey.api.exp.api.ExpRunInput;
import org.labkey.api.exp.api.ExpProtocolApplication;
import org.labkey.api.exp.api.ExperimentService;

/**
 * User: jeckels
 * Date: Oct 31, 2008
 */
public abstract class ExpRunInputImpl<InputType extends AbstractRunInput> implements ExpRunInput
{
    protected InputType _input;

    public ExpRunInputImpl(InputType input)
    {
        _input = input;
    }

    public ExpProtocolApplication getTargetApplication()
    {
        return ExperimentService.get().getExpProtocolApplication(_input.getTargetApplicationId());
    }

    public String getRole()
    {
        return _input.getRole();
    }
}