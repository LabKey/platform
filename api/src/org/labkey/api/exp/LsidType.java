/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpProtocolApplication;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;

/**
 * NOTE: The names of these literally are the LSID prefixes. Need to
 * refactor if you want to change these to some other casing or something like that
 * User: migra
 * Date: Oct 3, 2005
 */
public enum LsidType
{
    Experiment
            {
                @Override
                @Nullable
                public ActionURL getDisplayURL(Lsid lsid)
                {
                    ExpExperiment exp = getObject(lsid);
                    return exp == null ? null :
                            PageFlowUtil.urlProvider(ExperimentUrls.class).getExperimentDetailsURL(exp.getContainer(), exp);
                }

                @Override
                public ExpExperiment getObject(Lsid lsid)
                {
                    return ExperimentService.get().getExpExperiment(lsid.toString());
                }
            },

    Protocol
            {
                @Override
                @Nullable
                public ActionURL getDisplayURL(Lsid lsid)
                {
                    ExpProtocol protocol = getObject(lsid);
                    return protocol == null ? null :
                            PageFlowUtil.urlProvider(ExperimentUrls.class).getProtocolDetailsURL(protocol);
                }

                @Override
                public ExpProtocol getObject(Lsid lsid)
                {
                    return ExperimentService.get().getExpProtocol(lsid.toString());
                }
            },

    ProtocolApplication
            {
                @Override
                @Nullable
                public ActionURL getDisplayURL(Lsid lsid)
                {
                    ExpProtocolApplication app = getObject(lsid);
                    return app == null ? null :
                            PageFlowUtil.urlProvider(ExperimentUrls.class).getProtocolApplicationDetailsURL(app);
                }

                @Override
                public ExpProtocolApplication getObject(Lsid lsid)
                {
                    return ExperimentService.get().getExpProtocolApplication(lsid.toString());
                }
            },

    Material
            {
                @Override
                @Nullable
                public ActionURL getDisplayURL(Lsid lsid)
                {
                    ExpMaterial m = getObject(lsid);
                    return m == null ? null :
                            PageFlowUtil.urlProvider(ExperimentUrls.class).getMaterialDetailsURL(m);
                }

                @Override
                public ExpMaterial getObject(Lsid lsid)
                {
                    return ExperimentService.get().getExpMaterial(lsid.toString());
                }
            },

    MaterialSource
            {
                @Override
                @Nullable
                public ActionURL getDisplayURL(Lsid lsid)
                {
                    ExpSampleSet source = getObject(lsid);
                    return source == null ? null :
                            PageFlowUtil.urlProvider(ExperimentUrls.class).getShowSampleSetURL(source);
                }

                @Override
                public ExpSampleSet getObject(Lsid lsid)
                {
                    return ExperimentService.get().getSampleSet(lsid.toString());
                }
            },

    Data
            {
                @Override
                @Nullable
                public ActionURL getDisplayURL(Lsid lsid)
                {
                    ExpData data = getObject(lsid);
                    return data == null ? null :
                            PageFlowUtil.urlProvider(ExperimentUrls.class).getDataDetailsURL(data);
                }

                @Override
                public ExpData getObject(Lsid lsid)
                {
                    return ExperimentService.get().getExpData(lsid.toString());
                }
            },

    DataClass
            {
                @Override
                @Nullable
                public ActionURL getDisplayURL(Lsid lsid)
                {
                    ExpDataClass source = getObject(lsid);
                    return source == null ? null :
                            PageFlowUtil.urlProvider(ExperimentUrls.class).getShowDataClassURL(source.getContainer(), source.getRowId());
                }

                @Override
                public ExpDataClass getObject(Lsid lsid)
                {
                    return ExperimentService.get().getDataClass(lsid.toString());
                }
            },

    ExperimentRun
            {
                @Override
                @Nullable
                public ActionURL getDisplayURL(Lsid lsid)
                {
                    ExpRun run = getObject(lsid);
                    return run == null ? null :
                            PageFlowUtil.urlProvider(ExperimentUrls.class).getShowRunGraphURL(run);
                }

                @Override
                public ExpRun getObject(Lsid lsid)
                {
                    return ExperimentService.get().getExpRun(lsid.toString());
                }
            },

    Fraction
            {
                @Override
                @Nullable
                public ActionURL getDisplayURL(Lsid lsid)
                {
                    return Material.getDisplayURL(lsid);
                }

                @Override
                public ExpMaterial getObject(Lsid lsid)
                {
                    return ExperimentService.get().getExpMaterial(lsid.toString());
                }
            },

    DataInput
            {
                @Override
                public Identifiable getObject(Lsid lsid)
                {
                    return ExperimentService.get().getDataInput(lsid);
                }

                @Override
                public @Nullable ActionURL getDisplayURL(Lsid lsid)
                {
                    return null;
                }
            },

    MaterialInput
            {
                @Override
                public Identifiable getObject(Lsid lsid)
                {
                    return ExperimentService.get().getMaterialInput(lsid);
                }

                @Override
                public @Nullable ActionURL getDisplayURL(Lsid lsid)
                {
                    return null;
                }
            },

    ProtocolInput
            {
                @Override
                public Identifiable getObject(Lsid lsid)
                {
                    return ExperimentService.get().getProtocolInput(lsid);
                }

                @Override
                public @Nullable ActionURL getDisplayURL(Lsid lsid)
                {
                    return null;
                }
            };

    public abstract Identifiable getObject(Lsid lsid);

    @Nullable
    public abstract ActionURL getDisplayURL(Lsid lsid);

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
