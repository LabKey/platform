package org.labkey.api.study;

import org.labkey.api.study.DilutionCurve;

import java.util.List;
import java.util.Set;

/**
 * User: brittp
 * Date: Oct 20, 2006
 * Time: 10:18:16 AM
 */
public interface WellGroup extends WellData, WellGroupTemplate
{
    public enum Type
    {
        CONTROL,
        SPECIMEN,
        REPLICATE,
        ANTIGEN,
        OTHER
    }

    List<WellData> getWellData(boolean combineReplicates);

    Type getType();

    boolean contains(Position position);

    Set<WellGroup> getOverlappingGroups();

    Set<WellGroup> getOverlappingGroups(Type type);

    List<Position> getPositions();

    Double getMinDilution();

    Double getMaxDilution();

    DilutionCurve getDilutionCurve(DilutionCurve.PercentCalculator calculator, boolean expectedDecreasing, DilutionCurve.FitType fitType);
}
