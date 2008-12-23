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
                    ActionURL helper = getHelperForContainer(exp.getContainer());

                    return helper.relativeUrl("details", "rowId=" + exp.getRowId());
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
                    if (null == protocol)
                        return null;

                    ActionURL helper = getHelperForContainer(protocol.getContainer());

                    return helper.relativeUrl("protocol", "rowId=" + protocol.getRowId());
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
                    if (null == app)
                        return null;

                    ActionURL helper = getHelperForRun(app.getRun());

                    return helper.relativeUrl("showApplication", "rowId=" + app.getRowId());
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
                    if (null == m)
                        return null;

                    ActionURL helper;
                    if (null != m.getContainer())
                        helper = getHelperForContainer(m.getContainer());
                    else
                        helper = getHelperForRun(m.getRun());

                    return helper.relativeUrl("showMaterial", "rowId=" + m.getRowId());
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
                    if (null == source)
                        return null;

                    ActionURL helper = getHelperForContainer(source.getContainer());

                    return helper.relativeUrl("showMaterialSource", "rowId=" + source.getRowId());
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
                    if (null == data)
                        return null;

                    ActionURL helper;
                    if (null != data.getContainer())
                        helper = getHelperForContainer(data.getContainer());
                    else
                        helper = getHelperForRun(data.getRun());

                    return helper.relativeUrl("showData", "rowId=" + data.getRowId());
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
                    if (null == run)
                        return null;

                    ActionURL helper = getHelperForContainer(run.getContainer());
                    return helper.relativeUrl("showRunGraph", "rowId=" + run.getRowId());
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
                    ExpMaterial m = getObject(lsid);
                    if (null == m)
                        return null;

                    ActionURL helper;
                    if (null != m.getContainer())
                        helper = getHelperForContainer(m.getContainer());
                    else
                        helper = getHelperForRun(m.getRun());

                    return helper.relativeUrl("showMaterial", "rowId=" + m.getRowId());
                }

                public ExpMaterial getObject(Lsid lsid)
                {
                    return ExperimentService.get().getExpMaterial(lsid.toString());
                }


            };

    public abstract Identifiable getObject(Lsid lsid);

    public abstract String getDisplayURL(Lsid lsid);

    private static ActionURL getHelperForContainer(Container c)
    {
        ActionURL helper = new ActionURL();
        helper.setPageFlow("Experiment");

        if (null != c)
            helper.setContainer(c);

        return helper;
    }

    private static ActionURL getHelperForRun(ExpRun run)
    {
        ActionURL helper = new ActionURL();
        helper.setPageFlow("Experiment");

        helper.setContainer(run.getContainer());
        return helper;
    }

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
