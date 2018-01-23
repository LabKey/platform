package org.labkey.api.reports.report.r.view;

public class PngOutput extends ImageOutput
{
    public PngOutput()
    {
        super("pngout:");
    }

    @Override
    protected String getExtension()
    {
        return "png";
    }
}
