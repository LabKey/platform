package org.labkey.study.reports;

import org.labkey.api.reports.report.QueryReportDescriptor;

import java.util.Arrays;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Nov 17, 2006
 */
public class CrosstabReportDescriptor extends QueryReportDescriptor {
    public static final String TYPE = "crosstabDescriptor";
    public static final String STATS = "stats";

    public CrosstabReportDescriptor()
    {
        setDescriptorType(TYPE);
        setStats(new String[]{"Count"});
        setProperty("statField", "SequenceNum");
    }

    public void setStats(String[] stats){_props.put(STATS, Arrays.asList(stats));}
    public String[] getStats()
    {
        final Object stats = _props.get(STATS);
        if (stats instanceof List)
            return ((List<String>)stats).toArray(new String[0]);
        else if (stats instanceof String)
            return new String[]{(String)stats};

        return new String[]{""};
    }

    public boolean isArrayType(String prop)
    {
        if (!super.isArrayType(prop))
        {
            return STATS.equals(prop);
        }
        return true;
    }
}
