package org.labkey.study.designer.client.model;

import com.google.gwt.user.client.rpc.IsSerializable;

/**
 * Created by IntelliJ IDEA.
 * User: Mark Igra
 * Date: Dec 14, 2006
 * Time: 1:22:09 PM
 * To change this template use File | Settings | File Templates.
 */
public class GWTAssayNote implements IsSerializable
{
    private GWTSampleMeasure sampleMeasure;

    public GWTAssayNote()
    {

    }

    public GWTAssayNote(GWTAssayDefinition assay)
    {
        this.sampleMeasure = null != assay ? new GWTSampleMeasure(assay.getDefaultMeasure()) : null;
    }

    public GWTAssayNote(GWTSampleMeasure sampleMeasure)
    {
        this.sampleMeasure = sampleMeasure;
    }


    public boolean equals(Object o)
    {
        if (this == o) return true;

        GWTAssayNote that = (GWTAssayNote) o;

        if (!sampleMeasure.equals(that.sampleMeasure)) return false;

        return true;
    }

    public int hashCode()
    {
        return sampleMeasure.hashCode();
    }

    public String toString()
    {
        return "[x] " + sampleMeasure.toString();
    }

    public GWTSampleMeasure getSampleMeasure()
    {
        return sampleMeasure;
    }

    public void setSampleMeasure(GWTSampleMeasure sampleMeasure)
    {
        this.sampleMeasure = sampleMeasure;
    }
}
