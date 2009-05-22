/*
 * Copyright (c) 2008-2009 LabKey Corporation
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

import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.*;
import org.labkey.api.gwt.client.assay.SampleChooserUtils;
import org.labkey.api.gwt.client.model.GWTMaterial;
import org.labkey.api.gwt.client.model.GWTSampleSet;
import org.labkey.api.gwt.client.ui.FormUtil;

import java.util.Collections;
import java.util.List;

/**
 * User: jeckels
 * Date: Feb 8, 2008
 */
public class SampleInfo
{
    private Label _label;
    private ListBox _sampleSetListBox;
    private ListBox _materialListBox;
    private TextBox _materialTextBox;
    private String _name;
    private SampleCache _cache;
    private int _index;
    private boolean _selected;
    private GWTSampleSet _sampleSet;
    private final String _defaultSampleLSID;
    private final String _defaultSampleSetLSID;

    public SampleInfo(int index, SampleCache cache, String defaultSampleLSID, String sampleSetLSID)
    {
        _defaultSampleLSID = defaultSampleLSID;
        _defaultSampleSetLSID = sampleSetLSID;
        _name = "Sample " + (index + 1);
        _index = index;
        _cache = cache;

        _label = new Label(_name);

        _sampleSetListBox = new ListBox();
        DOM.setElementAttribute(_sampleSetListBox.getElement(), "id", "sampleSetListBox" + index);
        _materialListBox = new ListBox();
        DOM.setElementAttribute(_materialListBox.getElement(), "id", "sampleListBox" + index);
        _materialTextBox = new TextBox();
        DOM.setElementAttribute(_materialTextBox.getElement(), "id", "sampleTextBox" + index);

        _sampleSetListBox.addChangeListener(new ChangeListener()
        {
            public void onChange(Widget sender)
            {
                updateMaterialListBox(getSelectedSampleSet());
            }
        });

        ChangeListener changeListener = new ChangeListener()
        {
            public void onChange(Widget sender)
            {
                pushToForm();
            }
        };
        _sampleSetListBox.addChangeListener(changeListener);
        _materialListBox.addChangeListener(changeListener);
        _materialTextBox.addChangeListener(changeListener);

        KeyboardListener keyListener = new KeyboardListenerAdapter()
        {
            public void onKeyPress(Widget sender, char keyCode, int modifiers)
            {
                pushToForm();
            }
        };
        _materialTextBox.addKeyboardListener(keyListener);

        setSampleSets(Collections.<GWTSampleSet>emptyList(), SampleChooser.NONE_SAMPLE_SET);
    }

    public void setSampleSets(List<GWTSampleSet> sets, GWTSampleSet selectedSet)
    {
        _sampleSetListBox.clear();
        _sampleSetListBox.addItem(SampleChooser.NONE_SAMPLE_SET.getName(), SampleChooser.NONE_SAMPLE_SET.getLsid());
        for (GWTSampleSet set : sets)
        {
            // If we already know which material to reference, override the folder's default sample set
            if (set.getLsid().equals(_defaultSampleSetLSID))
            {
                selectedSet = set;
                break;
            }
        }
        for (GWTSampleSet set : sets)
        {
            _sampleSetListBox.addItem(set.getName(), set.getLsid());
            if (set.equals(selectedSet))
            {
                _sampleSetListBox.setSelectedIndex(_sampleSetListBox.getItemCount() - 1);
            }
        }
        updateMaterialListBox(selectedSet);
    }

    public String getName()
    {
        return _name;
    }

    public Label getLabel()
    {
        return _label;
    }

    public ListBox getSampleSetListBox()
    {
        return _sampleSetListBox;
    }

    public ListBox getMaterialListBox()
    {
        return _materialListBox;
    }

    public TextBox getMaterialTextBox()
    {
        return _materialTextBox;
    }

    private void pushToForm()
    {
        String lsid;
        String name;
        if (_materialListBox.isVisible() && _materialListBox.getSelectedIndex() != -1)
        {
            lsid = _materialListBox.getValue(_materialListBox.getSelectedIndex());
            name = "";
        }
        else
        {
            lsid = "";
            name = _materialTextBox.getText();
        }
        
        FormUtil.setValueInForm(lsid, DOM.getElementById(SampleChooserUtils.getLsidFormElementID(_index)));
        FormUtil.setValueInForm(name, DOM.getElementById(SampleChooserUtils.getNameFormElementID(_index)));
    }

    private void updateMaterialListBox(final GWTSampleSet sampleSet)
    {
        _materialListBox.clear();
        _sampleSet = sampleSet;

        if (!SampleChooser.NONE_SAMPLE_SET.equals(_sampleSet))
        {
            populateMaterials(_cache.getMaterials(_sampleSet));
        }
        refreshVisibility();
    }

    private void refreshVisibility()
    {
        if (_selected)
        {
            DOM.setStyleAttribute(_label.getElement(), "visibility", "visible");
            DOM.setStyleAttribute(_sampleSetListBox.getElement(), "visibility", "visible");
            DOM.setStyleAttribute(_materialListBox.getElement(), "visibility", "visible");

            if (SampleChooser.NONE_SAMPLE_SET.equals(_sampleSet))
            {
                _materialListBox.setVisible(false);
                // Do this to prevent layout changes
                DOM.setStyleAttribute(_materialTextBox.getElement(), "visibility", "visible");
            }
            else
            {
                populateMaterials(_cache.getMaterials(_sampleSet));

                _materialListBox.setVisible(true);
                // Do this to prevent layout changes
                DOM.setStyleAttribute(_materialTextBox.getElement(), "visibility", "hidden");
            }
        }
        else
        {
            DOM.setStyleAttribute(_label.getElement(), "visibility", "hidden");
            DOM.setStyleAttribute(_sampleSetListBox.getElement(), "visibility", "hidden");
            DOM.setStyleAttribute(_materialListBox.getElement(), "visibility", "hidden");
            DOM.setStyleAttribute(_materialTextBox.getElement(), "visibility", "hidden");
        }
    }

    private void populateMaterials(List<GWTMaterial> materials)
    {
        if (materials == null)
        {
            _materialListBox.addItem("<Loading...>");
            _materialListBox.setEnabled(false);
        }
        else
        {
            _materialListBox.clear();
            _materialListBox.addItem("<None>", SampleChooser.DUMMY_LSID);
            _materialListBox.setSelectedIndex(0);
            _materialListBox.setEnabled(true);
            for (GWTMaterial material : materials)
            {
                _materialListBox.addItem(material.getName(), material.getLsid());
                if (material.getLsid().equals(_defaultSampleLSID))
                {
                    _materialListBox.setSelectedIndex(_materialListBox.getItemCount() - 1);
                    _materialListBox.setEnabled(false);
                    _sampleSetListBox.setEnabled(false);
                }
            }
            pushToForm();
        }
    }

    private GWTSampleSet getSelectedSampleSet()
    {
        int index = _sampleSetListBox.getSelectedIndex();
        if (index != -1)
        {
            return _cache.getSampleSet(_sampleSetListBox.getValue(index));
        }
        return null;
    }

    public void updateMaterials(GWTSampleSet sampleSet, List<GWTMaterial> materials)
    {
        if (sampleSet.equals(getSelectedSampleSet()))
        {
            populateMaterials(materials);
        }
    }

    public void updateSampleSets(List<GWTSampleSet> sets)
    {
        setSampleSets(sets, getSelectedSampleSet());
    }

    public void setVisible(boolean selected)
    {
        _selected = selected;
        refreshVisibility();
    }
}
