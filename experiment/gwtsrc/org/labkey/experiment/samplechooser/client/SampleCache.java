/*
 * Copyright (c) 2008-2010 LabKey Corporation
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

import org.labkey.api.gwt.client.model.GWTMaterial;
import org.labkey.api.gwt.client.model.GWTSampleSet;
import org.labkey.api.gwt.client.util.ErrorDialogAsyncCallback;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: jeckels
 * Date: Feb 8, 2008
 */
public class SampleCache
{
    /** Map from SampleSet LSID to sample set */
    private Map<String, GWTSampleSet> _sampleSets = new HashMap<String, GWTSampleSet>();
    /** Map from SampleSet LSID to its materials */
    private Map<String, List<GWTMaterial>> _sampleSetMembers = new HashMap<String, List<GWTMaterial>>();

    private SampleInfo[] _sampleInfos;

    public SampleCache(SampleInfo[] sampleInfos)
    {
        _sampleInfos = sampleInfos;

        _sampleSets.put(SampleChooser.NONE_SAMPLE_SET.getLsid(), SampleChooser.NONE_SAMPLE_SET);

        SampleSetServiceAsync service = SampleSetService.App.getService();
        service.getSampleSets(new ErrorDialogAsyncCallback<List<GWTSampleSet>>("Sample set retrieval failed")
        {
            public void onSuccess(List<GWTSampleSet> sets)
            {
                for (GWTSampleSet set : sets)
                {
                    _sampleSets.put(set.getLsid(), set);
                }
                for (SampleInfo _sampleInfo : _sampleInfos)
                {
                    _sampleInfo.updateSampleSets(sets);
                }
            }
        });
        
    }

    public GWTSampleSet getSampleSet(String lsid)
    {
        return _sampleSets.get(lsid);
    }

    public List<GWTMaterial> getMaterials(final GWTSampleSet sampleSet)
    {
        if (!_sampleSetMembers.containsKey(sampleSet.getLsid()))
        {
            _sampleSetMembers.put(sampleSet.getLsid(), null);
            SampleSetService.App.getService().getMaterials(sampleSet, new ErrorDialogAsyncCallback<List<GWTMaterial>>("Sample retrieval failed")
            {
                public void onSuccess(List<GWTMaterial> materials)
                {
                    _sampleSetMembers.put(sampleSet.getLsid(), materials);

                    for (SampleInfo _sampleInfo : _sampleInfos)
                    {
                        _sampleInfo.updateMaterials(sampleSet, materials);
                    }
                }
            });
        }
        return _sampleSetMembers.get(sampleSet.getLsid());
    }

    public void addSampleSet(GWTSampleSet sampleSet)
    {
        _sampleSets.put(sampleSet.getLsid(), sampleSet);
    }
}
