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
package org.labkey.api.sequenceanalysis.pipeline;

import org.labkey.api.exp.api.ExpData;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.api.sequenceanalysis.model.Readset;

import java.io.File;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Created by bimber on 8/7/2014.
 */
public interface SequenceAnalysisJobSupport extends Serializable
{
    public ReferenceGenome getReferenceGenome();

    public void cacheExpData(ExpData data);

    public File getCachedData(int dataId);

    public Map<Integer, File> getAllCachedData();

    public Readset getCachedReadset(Integer rowId);

    public AnalysisModel getCachedAnalysis(int rowId);

    public List<Readset> getCachedReadsets();

    public List<AnalysisModel> getCachedAnalyses();

    public ReferenceGenome getCachedGenome(int genomeId);

    public PipelineJob getJob();
}
