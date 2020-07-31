/*
 * Copyright (c) 2005-2018 Fred Hutchinson Cancer Research Center
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.exp.api.ExpExperiment;

import java.io.Serializable;

/**
 * Bean class for the exp.experiment table. Also referred to as "run groups" or "batches" in assay usage cases.
 * User: migra
 * Date: Jun 14, 2005
 */
public class Experiment extends IdentifiableEntity implements Serializable
{
    private String _hypothesis;
    private String _experimenter;
    private String _experimentDescriptionURL;
    private String _comments;
    private String _contactId;
    private Integer _batchProtocolId;
    private boolean _hidden;

    public String getHypothesis()
    {
        return _hypothesis;
    }

    public void setHypothesis(String hypothesis)
    {
        _hypothesis = hypothesis;
    }

    public String getExperimenter()
    {
        return _experimenter;
    }

    public void setExperimenter(String experimenter)
    {
        _experimenter = experimenter;
    }

    public String getExperimentDescriptionURL()
    {
        return _experimentDescriptionURL;
    }

    public void setExperimentDescriptionURL(String experimentDescriptionURL)
    {
        _experimentDescriptionURL = experimentDescriptionURL;
    }

    public String getComments()
    {
        return _comments;
    }

    public void setComments(String comments)
    {
        _comments = comments;
    }

    public String getContactId()
    {
        return _contactId;
    }

    public void setContactId(String contactId)
    {
        _contactId = contactId;
    }

    public boolean isHidden()
    {
        return _hidden;
    }

    public void setHidden(boolean hidden)
    {
        _hidden = hidden;
    }

    public Integer getBatchProtocolId()
    {
        return _batchProtocolId;
    }

    public void setBatchProtocolId(Integer batchProtocolId)
    {
        _batchProtocolId = batchProtocolId;
    }

    @Override
    public @Nullable ExpExperimentImpl getExpObject()
    {
        return new ExpExperimentImpl(this);
    }
}
