/*
 * Copyright (c) 2004-2008 Fred Hutchinson Cancer Research Center
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
 * User: migra
 * Date: Jun 23, 2004
 * Time: 10:35:50 PM
 *
 */
public class Peptide
{
    Protein protein;
    int start;
    int length;
    double[] _massTab = null;
    double _mass; //According to the _massTab
    private int _hashCode = 0;

    public int hashCode()
    {
        int off = start;
        byte[] bytes = protein.getBytes();
        if (0 == _hashCode)
        {
            int h = 0;
            for (int i = 0; i < length; i++)
                h = 31*h + bytes[off++];

            _hashCode = h;
        }

        return _hashCode;
    }

    public boolean equals(Object o)
    {
        if (!(o instanceof Peptide))
            return false;

        Peptide p = (Peptide) o;
        if(p.length != this.length)
            return false;

        byte[] bytes1 = protein.getBytes();
        byte[] bytes2 = p.protein.getBytes();

        for (int i = start, j = p.start; i < start + length; i++, j++)
            if (bytes1[i] != bytes2[j])
                return false;

        return true;
    }

    public Peptide(Protein protein, int start, int length)
    {
        this.start = start;
        this.length = length;
        this.protein = protein;
    }

    public double getPi()
    {
        return PeptideGenerator.computePI(protein.getBytes(), start, length);
    }

    public double getHydrophobicity()
    {
        return getHydrophobicity(protein.getBytes(), start, length);
    }

    public double getHydrophobicity3()
    {
        return Hydrophobicity3.TSUM3(new String(protein.getBytes(),start,length));
    }

    static final double[] rc = new double[]
    {
        /* A */  0.8, /* B */  0.0, /* C */ -0.8, /* D */ -0.5, /* E */  0.0, /* F */ 10.5,
        /* G */ -0.9, /* H */ -1.3, /* I */  8.4, /* J */  0.0, /* K */ -1.9, /* L */  9.6,
        /* M */  5.8, /* N */ -1.2, /* O */  0.0, /* P */  0.2, /* Q */ -0.9, /* R */ -1.3,
        /* S */ -0.8, /* T */  0.4, /* U */  0.0, /* V */  5.0, /* W */ 11.0, /* X */  0.0,
        /* Y */  4.0, /* Z */  0.0
    };


    static final double[] rcnt = new double[]
    {
        /* A */ -1.5, /* B */  0.0, /* C */  4.0, /* D */  9.0, /* E */  7.0, /* F */ -7.0,
        /* G */  5.0, /* H */  4.0, /* I */ -8.0, /* J */  0.0, /* K */  4.6, /* L */ -9.0,
        /* M */ -5.5, /* N */  5.0, /* O */  0.0, /* P */  4.0, /* Q */  1.0, /* R */  8.0,
        /* S */  5.0, /* T */  5.0, /* U */  0.0, /* V */ -5.5, /* W */ -4.0, /* X */  0.0,
        /* Y */ -3.0, /* Z */  0.0
    };


    static final double[] nt = {.42, .22, .05};


    /**
     * Implementation of Version 1.0 hydrophobicity algorithm by Oleg V. Krokhin, et al.
     * See http://hs2.proteome.ca/SSRCalc/SSRCalc.html for more details
     */
    public static double getHydrophobicity(byte[] bytes, int start, int n)
    {
        double kl = 1;

        if (n < 10)
            kl = 1 - 0.027 * (10 - n);
        else
            if (n > 20)
//                kl = 1 - 0.014 * (n - 20);      // As published in paper
                kl = 1 / (1 + 0.015 * (n -20));   // Revision from paper's author

        double h = 0;

        for (int i=0; i<n; i++)
        {
            char c = (char) bytes[i + start];
            h += rc[c - 'A'];
            if (i < 3)
                h += nt[i] * rcnt[c - 'A'];
        }

        h *= kl;

        if (h < 38)
            return h;
        else
            return h - .3 * (h - 38);
    }


    // Call version 3.0 hydrophobicity algorithm by Krokhin, et al
    public static double getHydrophobicity3(String peptide)
    {
        return Hydrophobicity3.TSUM3(peptide);
    }

    /**
     * Returns the mass according to the default mass table.
     * Default mass table is the FIRST one used to compute a mass on this peptide.
     * If mass has not been computed, mass table is set to monoisotopic, unmodified table.
     * @return mass using default mass table
     */
    public double getMass()
    {
        if (_mass != 0)
            return _mass;

        if (null != _massTab)
            return getMass(_massTab);

        throw new UnsupportedOperationException("GetMass without mass table");
    }

    public double getMass(double[] massTab)
    {
        if (_massTab == massTab && _mass != 0)
            return _mass;

        if (null == _massTab)
        {
            _massTab = massTab;
            _mass = PeptideGenerator.computeMass(protein.getBytes(), start, length, massTab);
            return _mass;
        }
        else
            return PeptideGenerator.computeMass(protein.getBytes(), start, length, massTab);
    }

    public double getAverageMass()
    {
       return getMass(PeptideGenerator.AMINO_ACID_AVERAGE_MASSES);
    }

    public double getMonoisotopicMass()
    {
        return getMass(PeptideGenerator.AMINO_ACID_MONOISOTOPIC_MASSES);
    }

    public char[] getChars()
    {
        byte[] bytes = protein.getBytes();
        char[] chars = new char[length];
        for(int i = 0; i < length; i++)
            chars[i] = (char) bytes[start + i];

        return chars;
    }

    public String toString()
    {
        return new String(protein.getBytes(), start, length);
    }

    public Protein getProtein()
    {
        return protein;
    }

    public void setProtein(Protein protein)
    {
        this.protein = protein;
    }

    public int getStart()
    {
        return start;
    }

    public void setStart(int start)
    {
        this.start = start;
    }

    public int getLength()
    {
        return length;
    }

    public void setLength(int length)
    {
        this.length = length;
    }
}
