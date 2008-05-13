/*
 * Copyright (c) 2005-2007 Fred Hutchinson Cancer Research Center
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

import org.labkey.experiment.api.IdentifiableEntity;

import java.io.Serializable;

/**
 * User: migra
 * Date: Jun 14, 2005
 * Time: 2:41:12 PM
 */
public class Experiment extends IdentifiableEntity implements Serializable
{
    private int rowId;
    private String ProtocolLSID;
    private String hypothesis;
    private String experimenter;
    private String experimentLSIDAuthority;
    private String experimentNS;
    private String experimentDescriptionURL;
    private String comments;
    private String contactId;

    public int getRowId()
    {
        return rowId;
    }

    public void setRowId(int rowId)
    {
        this.rowId = rowId;
    }

    public String getProtocolLSID()
    {
        return ProtocolLSID;
    }

    public void setProtocolLSID(String protocolLSID)
    {
        ProtocolLSID = protocolLSID;
    }

    public String getHypothesis()
    {
        return hypothesis;
    }

    public void setHypothesis(String hypothesis)
    {
        this.hypothesis = hypothesis;
    }

    public String getExperimenter()
    {
        return experimenter;
    }

    public void setExperimenter(String experimenter)
    {
        this.experimenter = experimenter;
    }

    public String getExperimentLSIDAuthority()
    {
        return experimentLSIDAuthority;
    }

    public void setExperimentLSIDAuthority(String experimentLSIDAuthority)
    {
        this.experimentLSIDAuthority = experimentLSIDAuthority;
    }

    public String getExperimentNS()
    {
        return experimentNS;
    }

    public void setExperimentNS(String experimentNS)
    {
        this.experimentNS = experimentNS;
    }

    public String getExperimentDescriptionURL()
    {
        return experimentDescriptionURL;
    }

    public void setExperimentDescriptionURL(String experimentDescriptionURL)
    {
        this.experimentDescriptionURL = experimentDescriptionURL;
    }

    public String getComments()
    {
        return comments;
    }

    public void setComments(String comments)
    {
        this.comments = comments;
    }

    public String getContactId()
    {
        return contactId;
    }

    public void setContactId(String contactId)
    {
        this.contactId = contactId;
    }
}
