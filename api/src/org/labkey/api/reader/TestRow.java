/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.api.reader;

import java.util.Date;

/** Test class for JUnit tests **/
public class TestRow
{
    private Date date;
    private int scan;
    private double time;
    private double mz;
    private boolean accurateMZ;
    private double mass;
    private double intensity;
    private int chargeStates;
    private double kl;
    private double background;
    private double median;
    private int peaks;
    private int scanFirst;
    private int scanLast;
    private int scanCount;
    private String description;

    public boolean isAccurateMZ()
    {
        return accurateMZ;
    }

    public void setAccurateMZ(boolean accurateMZ)
    {
        this.accurateMZ = accurateMZ;
    }

    public double getBackground()
    {
        return background;
    }

    public void setBackground(double background)
    {
        this.background = background;
    }

    public int getChargeStates()
    {
        return chargeStates;
    }

    public void setChargeStates(int chargeStates)
    {
        this.chargeStates = chargeStates;
    }

    public Date getDate()
    {
        return date;
    }

    public void setDate(Date date)
    {
        this.date = date;
    }

    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public double getIntensity()
    {
        return intensity;
    }

    public void setIntensity(double intensity)
    {
        this.intensity = intensity;
    }

    public double getKl()
    {
        return kl;
    }

    public void setKl(double kl)
    {
        this.kl = kl;
    }

    public double getMass()
    {
        return mass;
    }

    public void setMass(double mass)
    {
        this.mass = mass;
    }

    public double getMedian()
    {
        return median;
    }

    public void setMedian(double median)
    {
        this.median = median;
    }

    public double getMz()
    {
        return mz;
    }

    public void setMz(double mz)
    {
        this.mz = mz;
    }

    public int getPeaks()
    {
        return peaks;
    }

    public void setPeaks(int peaks)
    {
        this.peaks = peaks;
    }

    public int getScan()
    {
        return scan;
    }

    public void setScan(int scan)
    {
        this.scan = scan;
    }

    public int getScanCount()
    {
        return scanCount;
    }

    public void setScanCount(int scanCount)
    {
        this.scanCount = scanCount;
    }

    public int getScanFirst()
    {
        return scanFirst;
    }

    public void setScanFirst(int scanFirst)
    {
        this.scanFirst = scanFirst;
    }

    public int getScanLast()
    {
        return scanLast;
    }

    public void setScanLast(int scanLast)
    {
        this.scanLast = scanLast;
    }

    public double getTime()
    {
        return time;
    }

    public void setTime(double time)
    {
        this.time = time;
    }
}
