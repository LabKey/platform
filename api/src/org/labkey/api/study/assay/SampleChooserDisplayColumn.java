/*
 * Copyright (c) 2008-2013 LabKey Corporation
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

import org.labkey.api.data.SimpleDisplayColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.view.GWTView;
import org.labkey.api.gwt.client.assay.SampleChooserUtils;
import org.labkey.api.study.ParticipantVisit;

import javax.servlet.http.HttpServletRequest;
import java.io.Writer;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

/**
 * User: jeckels
* Date: Sep 10, 2008
*/
public class SampleChooserDisplayColumn extends SimpleDisplayColumn
{
    private final int _maxSamples;
    private final int _minSamples;
    private final List<ExpMaterial> _matchingMaterials;
    private final int _defaultSampleCount;

    public SampleChooserDisplayColumn(int minSamples, int maxSamples, List<ExpMaterial> matchingMaterials, int defaultSampleCount)
    {
        assert maxSamples >= minSamples : "maxSamples was bigger than minSamples";
        assert defaultSampleCount <= maxSamples && defaultSampleCount >= minSamples : "defaultSampleCount was not between maxSamples and minSamples";
        _maxSamples = maxSamples;
        _minSamples = minSamples;
        _defaultSampleCount = defaultSampleCount;
        _matchingMaterials = matchingMaterials;
        setCaption("Samples");
    }

    public SampleChooserDisplayColumn(int minSamples, int maxSamples, List<ExpMaterial> matchingMaterials)
    {
        this(minSamples, maxSamples, matchingMaterials, maxSamples);
    }

    public boolean isEditable()
    {
        return true;
    }

    public void renderInputHtml(RenderContext ctx, Writer out, Object value) throws IOException
    {
        Map<String, String> props = new HashMap<>();

        out.write("<input type=\"hidden\" name=\"" + SampleChooserUtils.SAMPLE_COUNT_ELEMENT_NAME + "\" id=\"" + SampleChooserUtils.SAMPLE_COUNT_ELEMENT_NAME + "\"/>\n");

        for (int i = 0; i < _maxSamples; i++)
        {
            String lsidID = SampleChooserUtils.getLsidFormElementID(i);
            String nameID = SampleChooserUtils.getNameFormElementID(i);
            out.write("<input type=\"hidden\" name=\"" + lsidID + "\" id=\"" + lsidID + "\"/>\n");
            out.write("<input type=\"hidden\" name=\"" + nameID + "\" id=\"" + nameID + "\"/>\n");
        }

        // Use the value the user selected if it was posted, otherwise use the default
        int defaultSampleCount = getSampleCount(ctx.getRequest(), _defaultSampleCount);

        props.put(SampleChooserUtils.PROP_NAME_MAX_SAMPLE_COUNT, Integer.toString(_maxSamples));
        props.put(SampleChooserUtils.PROP_NAME_MIN_SAMPLE_COUNT, Integer.toString(_minSamples));
        props.put(SampleChooserUtils.PROP_NAME_DEFAULT_SAMPLE_COUNT, Integer.toString(defaultSampleCount));

        if (_matchingMaterials.size() == _maxSamples)
        {
            // If we found exactly the right number of matches, lock the user into those materials
            for (int i = 0; i < _matchingMaterials.size(); i++)
            {
                ExpMaterial material = _matchingMaterials.get(i);
                props.put(SampleChooserUtils.PROP_PREFIX_SELECTED_SAMPLE_LSID + i, material.getLSID());
                props.put(SampleChooserUtils.PROP_PREFIX_SELECTED_SAMPLE_SET_LSID + i, material.getSampleSet().getLSID());
                props.put(SampleChooserUtils.PROP_PREFIX_SELECTED_SAMPLE_LOCKED + i, "true");
            }
        }
        else
        {
            // Otherwise, select the folder's active sample set as the default
            ExpSampleSet sampleSet = ExperimentService.get().lookupActiveSampleSet(ctx.getContainer());
            if (sampleSet != null)
            {
                props.put(SampleChooserUtils.PROP_NAME_DEFAULT_SAMPLE_SET_LSID, sampleSet.getLSID());
                props.put(SampleChooserUtils.PROP_NAME_DEFAULT_SAMPLE_SET_NAME, sampleSet.getName());
                props.put(SampleChooserUtils.PROP_NAME_DEFAULT_SAMPLE_SET_ROW_ID, Integer.toString(sampleSet.getRowId()));
            }

            // Reshow with the same selections
            for (int i = 0; i < _maxSamples; i++)
            {
                ExpMaterial selectedMaterial;
                try
                {
                    selectedMaterial = getMaterial(i, ctx.getContainer(), ctx.getRequest());
                }
                catch (ExperimentException e)
                {
                    selectedMaterial = null;
                }
                // If we find the material that the user selected, tell the GWT app to automatically select it
                if (selectedMaterial != null)
                {
                    props.put(SampleChooserUtils.PROP_PREFIX_SELECTED_SAMPLE_LSID + i, selectedMaterial.getLSID());

                    ExpSampleSet selectedSampleSet = selectedMaterial.getSampleSet();
                    if (selectedSampleSet != null)
                    {
                        props.put(SampleChooserUtils.PROP_PREFIX_SELECTED_SAMPLE_SET_LSID + i, selectedSampleSet.getLSID());
                    }
                }
            }
        }

        GWTView view = new GWTView("org.labkey.experiment.samplechooser.SampleChooser", props);
        try
        {
            view.render(ctx.getRequest(), ctx.getViewContext().getResponse());
        }
        catch (Exception e)
        {
            throw (IOException)new IOException().initCause(e);
        }
    }

    public static String getSampleName(int index, HttpServletRequest request)
    {
        return request.getParameter(SampleChooserUtils.getNameFormElementID(index));
    }

    public static ExpMaterial getMaterial(int index, Container container, HttpServletRequest request) throws ExperimentException
    {
        String lsid = request.getParameter(SampleChooserUtils.getLsidFormElementID(index));
        if (SampleChooserUtils.DUMMY_LSID.equals(lsid))
        {
            throw new ExperimentException("Please select a sample");
        }
        if (lsid != null && !"".equals(lsid))
        {
            ExpMaterial material = ExperimentService.get().getExpMaterial(lsid);
            if (material == null)
            {
                throw new ExperimentException("Could not find sample with LSID " + lsid);
            }
            return material;
        }
        String name = getSampleName(index, request);
        if (name == null || "".equals(name))
        {
            name = "Unknown";
        }
        if (name.length() >= 200)
        {
            throw new ExperimentException("Sample names are limited to 200 characters");
        }

        String materialLSID = new Lsid(ParticipantVisit.ASSAY_RUN_MATERIAL_NAMESPACE, "Folder-" + container.getRowId(), name).toString();
        ExpMaterial material = ExperimentService.get().getExpMaterial(materialLSID);
        if (material == null)
        {
            material = ExperimentService.get().createExpMaterial(container, materialLSID, name);
        }
        return material;
    }

    public static int getSampleCount(HttpServletRequest request, int defaultCount)
    {
        String countString = request.getParameter(SampleChooserUtils.SAMPLE_COUNT_ELEMENT_NAME);
        if (countString != null)
        {
            try
            {
                return Integer.parseInt(countString);
            }
            catch (NumberFormatException e) {}
        }
        return defaultCount;
    }
}