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
 * This class models a modified amino acid.  It is not aware of its position in the peptide sequence --
 * this generally needs to be modeled at a higher level, and duplication here would just waste space
 */
public class ModifiedAminoAcid
{
    protected char mAminoAcid = ' ';
    protected double mMass = 0;

    public ModifiedAminoAcid()
    {
    }

    public ModifiedAminoAcid(char aminoAcid, double mass)
    {
        mAminoAcid = aminoAcid;
        mMass = mass;
    }

    public String toString()
    {
        return mAminoAcid + " " + mMass;
    }

    public char getAminoAcid()
    {
        return mAminoAcid;
    }

    public String getAminoAcidAsString()
    {
        return "" + mAminoAcid;
    }

    public void setAminoAcid(char aminoAcid)
    {
        this.mAminoAcid = aminoAcid;
    }

    public double getMass()
    {
        return mMass;
    }

    public void setMass(double mass)
    {
        this.mMass = mass;
    }
}
