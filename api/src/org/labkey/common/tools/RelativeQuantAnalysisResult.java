/*
 * Copyright (c) 2006-2008 Fred Hutchinson Cancer Research Center
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
package org.labkey.common.tools;

/**
 * Models an analysis result for very minimal relative quantitation
 * of peptides. This is not intended to be instantiated; particular subclasses
 * must implement getAnalysisType();
 */
public abstract class RelativeQuantAnalysisResult extends PepXmlAnalysisResultHandler.PepXmlAnalysisResult
{
    public static final float SENTINEL_NAN = -1.f;
    public static final float SENTINEL_POSITIVE_INFINITY = 999.f;
    public static final float SENTINEL_NEGATIVE_INFINITY = -999.f;

    private int lightFirstscan;
    private int lightLastscan;
    private float lightMass;
    private int heavyFirstscan;
    private int heavyLastscan;
    private float heavyMass;
    private float lightArea;
    private float heavyArea;
    private float decimalRatio;
    private long peptideId;
    private int quantId;

    public long getPeptideId()
    {
        return peptideId;
    }

    public void setPeptideId(long peptideId)
    {
        this.peptideId = peptideId;
    }

    public int getQuantId()
    {
        return quantId;
    }

    public void setQuantId(int quantId)
    {
        this.quantId = quantId;
    }

    public int getLightFirstscan()
    {
        return lightFirstscan;
    }

    public void setLightFirstscan(int lightFirstscan)
    {
        this.lightFirstscan = lightFirstscan;
    }

    public int getLightLastscan()
    {
        return lightLastscan;
    }

    public void setLightLastscan(int lightLastscan)
    {
        this.lightLastscan = lightLastscan;
    }

    public float getLightMass()
    {
        return lightMass;
    }

    public void setLightMass(float lightMass)
    {
        this.lightMass = lightMass;
    }

    public int getHeavyFirstscan()
    {
        return heavyFirstscan;
    }

    public void setHeavyFirstscan(int heavyFirstscan)
    {
        this.heavyFirstscan = heavyFirstscan;
    }

    public int getHeavyLastscan()
    {
        return heavyLastscan;
    }

    public void setHeavyLastscan(int heavyLastscan)
    {
        this.heavyLastscan = heavyLastscan;
    }

    public float getHeavyMass()
    {
        return heavyMass;
    }

    public void setHeavyMass(float heavyMass)
    {
        this.heavyMass = heavyMass;
    }

    public float getLightArea()
    {
        return lightArea;
    }

    public void setLightArea(float lightArea)
    {
        this.lightArea = lightArea;
    }

    public float getHeavyArea()
    {
        return heavyArea;
    }

    public void setHeavyArea(float heavyArea)
    {
        this.heavyArea = heavyArea;
    }

    public float getDecimalRatio()
    {
        return decimalRatio;
    }

    public void setDecimalRatio(float decimalRatio)
    {
        if (Float.isNaN(decimalRatio))
            this.decimalRatio = SENTINEL_NAN;
        else if (Float.isInfinite(decimalRatio))
            this.decimalRatio = decimalRatio > 0 ? SENTINEL_POSITIVE_INFINITY : SENTINEL_NEGATIVE_INFINITY;
        else
            this.decimalRatio = decimalRatio;
    }

    /**
     * Substitute sentinel values when heavyArea goes to zero (infinite or undetermined
     * ratios not consistently reported by applications.
     */
    public float parseDecimalRatio(String decimalRatio, float lightArea, float heavyArea)
    {
        if (heavyArea == 0.f)
            if (lightArea == 0.f)
                return SENTINEL_NAN;
            else
                return SENTINEL_POSITIVE_INFINITY;
        return Float.parseFloat(decimalRatio);
    }

}
