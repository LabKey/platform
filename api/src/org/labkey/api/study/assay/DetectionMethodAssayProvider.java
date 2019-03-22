/*
 * Copyright (c) 2015 LabKey Corporation
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

import java.util.List;

/**
 * Created by aaronr on 3/23/15.
 */
public interface DetectionMethodAssayProvider extends AssayProvider
{
    public void setSelectedDetectionMethod(Container container, ExpProtocol protocol, String method);
    public String getSelectedDetectionMethod(Container container, ExpProtocol protocol);

    public List<String> getAvailableDetectionMethods();
}
