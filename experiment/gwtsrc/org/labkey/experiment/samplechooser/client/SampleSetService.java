/*
 * Copyright (c) 2008-2016 LabKey Corporation
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

package org.labkey.experiment.samplechooser.client;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.rpc.RemoteService;
import org.labkey.api.gwt.client.model.GWTMaterial;
import org.labkey.api.gwt.client.model.GWTSampleSet;
import org.labkey.api.gwt.client.util.ServiceUtil;

import java.util.List;

/**
 * User: jeckels
 * Date: Feb 8, 2008
 */
public interface SampleSetService extends RemoteService
{
    public List<GWTSampleSet> getSampleSets();

    public List<GWTMaterial> getMaterials(GWTSampleSet sampleSet);

    /**
     * Utility/Convenience class.
     * Use SampleSetService.App.getInstance() to access static instance of SampleSetServiceAsync
     */
    public static class App
    {
        private static SampleSetServiceAsync _service = null;

        public static SampleSetServiceAsync getService()
        {
            if (_service == null)
            {
                _service = GWT.create(SampleSetService.class);
                ServiceUtil.configureEndpoint(_service, "sampleSetService", "experiment");
            }
            return _service;
        }
    }
}
