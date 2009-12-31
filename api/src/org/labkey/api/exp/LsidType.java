/*
 * Copyright (c) 2005-2008 LabKey Corporation
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
package org.labkey.api.exp;

import org.labkey.api.view.ActionURL;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.*;
import org.labkey.api.util.PageFlowUtil;

/**
 * User: migra
 * Date: Oct 3, 2005
 * Time: 10:24:41 AM
 * <p/>
 * NOTE: The names of these literally are the LSID prefixes. Need to
 * refactor if you want to change these to some other casing or something like that
 */
public enum LsidType
{
    Experiment
            {
                public String getDisplayURL(Lsid lsid)
                {
                    ExpExperiment exp = getObject(lsid);
                    return exp == null ? null :
                            PageFlowUtil.urlProvider(ExperimentUrls.class).getExperimentDetailsURL(exp.getContainer(), exp).toString();
                }

                public ExpExperiment getObject(Lsid lsid)
                {
                    return ExperimentService.get().getExpExperiment(lsid.toString());
                }
            },

    Protocol
            {
                public String getDisplayURL(Lsid lsid)
                {
                    ExpProtocol protocol = getObject(lsid);
                    return protocol == null ? null :
                            PageFlowUtil.urlProvider(ExperimentUrls.class).getProtocolDetailsURL(protocol).toString();
                }

                public ExpProtocol getObject(Lsid lsid)
                {
                    return ExperimentService.get().getExpProtocol(lsid.toString());
                }

            },

    ProtocolApplication
            {
                public String getDisplayURL(Lsid lsid)
                {
                    ExpProtocolApplication app = getObject(lsid);
                    return app == null ? null :
                            PageFlowUtil.urlProvider(ExperimentUrls.class).getProtocolApplicationDetailsURL(app).toString();
                }

                public ExpProtocolApplication getObject(Lsid lsid)
                {
                    return ExperimentService.get().getExpProtocolApplication(lsid.toString());
                }
            },

    Material
            {
                public String getDisplayURL(Lsid lsid)
                {
                    ExpMaterial m = getObject(lsid);
                    return m == null ? null :
                            PageFlowUtil.urlProvider(ExperimentUrls.class).getMaterialDetailsURL(m).toString();
                }

                public ExpMaterial getObject(Lsid lsid)
                {
                    return ExperimentService.get().getExpMaterial(lsid.toString());
                }
            },

    MaterialSource
            {

                public String getDisplayURL(Lsid lsid)
                {
                    ExpSampleSet source = getObject(lsid);
                    return source == null ? null :
                            PageFlowUtil.urlProvider(ExperimentUrls.class).getShowSampleSetURL(source).toString();
                }

                public ExpSampleSet getObject(Lsid lsid)
                {
                    return ExperimentService.get().getSampleSet(lsid.toString());
                }
            },

    Data
            {
                public String getDisplayURL(Lsid lsid)
                {
                    ExpData data = getObject(lsid);
                    return data == null ? null :
                            PageFlowUtil.urlProvider(ExperimentUrls.class).getDataDetailsURL(data).toString();
                }

                public ExpData getObject(Lsid lsid)
                {
                    return ExperimentService.get().getExpData(lsid.toString());
                }
            },

    ExperimentRun
            {
                public String getDisplayURL(Lsid lsid)
                {
                    ExpRun run = getObject(lsid);
                    return run == null ? null :
                            PageFlowUtil.urlProvider(ExperimentUrls.class).getShowRunGraphURL(run).toString();
                }

                public ExpRun getObject(Lsid lsid)
                {
                    return ExperimentService.get().getExpRun(lsid.toString());
                }

            },

    Fraction
            {
                public String getDisplayURL(Lsid lsid)
                {
                    return Material.getDisplayURL(lsid);
                }

                public ExpMaterial getObject(Lsid lsid)
                {
                    return ExperimentService.get().getExpMaterial(lsid.toString());
                }
            };

    public abstract Identifiable getObject(Lsid lsid);

    public abstract String getDisplayURL(Lsid lsid);

    public static LsidType get(String prefix)
    {
        if (prefix == null)
        {
            return null;
        }
        
        try
        {
            return valueOf(prefix);
        }
        catch (IllegalArgumentException x)
        {
            return null;
        }
    }
}
