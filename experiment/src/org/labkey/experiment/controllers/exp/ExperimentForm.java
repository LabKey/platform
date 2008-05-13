/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.experiment.controllers.exp;

import org.labkey.experiment.api.Experiment;
import org.labkey.experiment.api.ExperimentServiceImpl;
import org.labkey.api.data.BeanViewForm;

/**
 * User: jeckels
* Date: Dec 17, 2007
*/
public class ExperimentForm extends BeanViewForm<Experiment>
{
    public ExperimentForm()
    {
        super(Experiment.class, ExperimentServiceImpl.get().getTinfoExperiment());
    }

    public ExperimentForm(Experiment exp)
    {
        super(Experiment.class, ExperimentServiceImpl.get().getTinfoExperiment());
        setBean(exp);
    }
}
