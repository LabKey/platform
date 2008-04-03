package org.labkey.study.designer.client;

import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.*;
import org.labkey.study.designer.client.model.GWTSampleMeasure;
import org.labkey.study.designer.client.model.GWTSampleType;
import org.labkey.study.designer.client.model.GWTStudyDefinition;

/**
 * Created by IntelliJ IDEA.
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

        lbSampleType.addChangeListener(new ChangeListener()
        {
            public void onChange(Widget sender)
            {
                sampleMeasure.setType(GWTSampleType.fromString(lbSampleType.getItemText(lbSampleType.getSelectedIndex()), studyDef));
                listeners.fireChange(SampleMeasurePanel.this);
            }
        });

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
