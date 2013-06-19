/*
 * Copyright (c) 2010-2013 LabKey Corporation
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

package gwt.client.org.labkey.study.designer.client;

import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.*;
import gwt.client.org.labkey.study.designer.client.model.GWTSampleMeasure;
import gwt.client.org.labkey.study.designer.client.model.GWTSampleType;
import gwt.client.org.labkey.study.designer.client.model.GWTStudyDefinition;

/**
 * User: Mark Igra
 * Date: Jan 26, 2007
 * Time: 11:54:08 AM
 */
public class SampleMeasurePanel extends HorizontalPanel implements SourcesChangeEvents
{
    TextBox tbAmount = new TextBox();
    ListBox lbSampleType = new ListBox();
    ListBox lbUnits = new ListBox();
    GWTSampleMeasure sampleMeasure;
    GWTStudyDefinition studyDef;
    ChangeListenerCollection listeners = new ChangeListenerCollection();


    public SampleMeasurePanel(GWTSampleMeasure measure, GWTStudyDefinition def)
    {
        this.studyDef = def;
        init(measure);
        tbAmount.setName("materialAmount");
        tbAmount.addChangeListener(new ChangeListener()
        {
            public void onChange(Widget sender)
            {
                double val;
                try
                {
                    val = Double.parseDouble(tbAmount.getText());
                    sampleMeasure.setAmount(val);
                    tbAmount.setText(GWTSampleMeasure.formatAmountString(val));
                }
                catch (NumberFormatException x)
                {
                    Window.alert("Please enter a number");
                    tbAmount.setFocus(true);
                    return;
                }
            }
        });

        lbSampleType.setName("materialType");
        lbSampleType.addChangeListener(new ChangeListener()
        {
            public void onChange(Widget sender)
            {
                sampleMeasure.setType(GWTSampleType.fromString(lbSampleType.getItemText(lbSampleType.getSelectedIndex()), studyDef));
                listeners.fireChange(SampleMeasurePanel.this);
            }
        });

        lbUnits.setName("materialUnits");
        lbUnits.addChangeListener(new ChangeListener()
        {
            public void onChange(Widget sender)
            {
                sampleMeasure.setUnit(GWTSampleMeasure.Unit.fromString(lbUnits.getItemText(lbUnits.getSelectedIndex())));
                listeners.fireChange(SampleMeasurePanel.this);
            }
        });
        this.add(tbAmount);
        this.add(lbUnits);
        this.add(lbSampleType);
    }

    private void init(GWTSampleMeasure sampleMeasure)
    {
        this.sampleMeasure = sampleMeasure;
        for (int i = 0; i < studyDef.getSampleTypes().size(); i++)
        {
            GWTSampleType st = (GWTSampleType) studyDef.getSampleTypes().get(i);
            lbSampleType.addItem(st.toString());
            if (st.equals(sampleMeasure.getType()))
                lbSampleType.setSelectedIndex(i);
            lbSampleType.addChangeListener(new ChangeListener()
            {
                public void onChange(Widget sender)
                {
                    listeners.fireChange(SampleMeasurePanel.this);
                }
            });
        }

        for (int i = 0; i < GWTSampleMeasure.Unit.ALL.length; i++)
        {
            GWTSampleMeasure.Unit unit = GWTSampleMeasure.Unit.ALL[i];
            lbUnits.addItem(unit.toString());
            if (unit.equals(sampleMeasure.getUnit()))
                lbUnits.setSelectedIndex(i);
            lbUnits.addChangeListener(new ChangeListener()
            {
                public void onChange(Widget sender)
                {
                    listeners.fireChange(SampleMeasurePanel.this);
                }
            });
        }

        tbAmount.setText(GWTSampleMeasure.formatAmountString(sampleMeasure.getAmount()));
    }


    public GWTSampleMeasure getValue()
    {
        return sampleMeasure;
    }

    public void setEnabled(boolean enabled)
    {
        tbAmount.setEnabled(enabled);
        lbUnits.setEnabled(enabled);
        lbSampleType.setEnabled(enabled);
    }

    public void addChangeListener(ChangeListener listener)
    {
        listeners.add(listener);
    }

    public void removeChangeListener(ChangeListener listener)
    {
        listeners.remove(listener);
    }
}
