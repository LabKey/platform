/*
 * Copyright (c) 2014 LabKey Corporation
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
package org.labkey.api.sequenceanalysis;

import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;

/**
 * Created by bimber on 8/21/2014.
 */
public interface GenomeTrigger
{
    public String getName();

    public void onCreate(Container c, User u, Logger log, int genomeId);

    public void onRecreate(Container c, User u, Logger log, int genomeId);

    public void onDelete(Container c, User u, Logger log, int genomeId);
}
