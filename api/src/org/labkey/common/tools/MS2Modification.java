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

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.labkey.common.util.Pair;

import java.util.ArrayList;
import java.util.List;

public class MS2Modification
{
    // UNDONE: Change Strings to chars... JDBC seems to have a problem with chars right now
    protected int run = 0;
    protected String aminoAcid = "";
    protected float massDiff = 0;
    protected float mass = 0;
    protected boolean variable = false;
    protected String symbol = null;


    public MS2Modification()
    {
    }


    public String toString()
    {
        return run + " " + aminoAcid + " " + massDiff + " " + variable + " " + symbol;
    }

    /**
     * dhmay adding for msInspect.  This is kind of hacky; if CPAS needs
     * something like it, talk to me.
     * @param modAsString
     * @return
     * @throws IllegalArgumentException
     */
    public static MS2Modification parseString(String modAsString)
            throws IllegalArgumentException
    {
        String[] chunks = modAsString.split(" ");
        if (chunks.length != 5 && chunks.length != 4)
            throw new IllegalArgumentException("Bad modification string: " + modAsString);
        int run = Integer.parseInt(chunks[0]);
        String aminoAcid = chunks[1];
        float massDiff = Float.parseFloat(chunks[2]);
        boolean variable = Boolean.parseBoolean(chunks[3]);
        String symbol = null;
        if (chunks.length == 5)
            symbol = chunks[4];

        MS2Modification result = new MS2Modification();
        result.setRun(run);
        result.setAminoAcid(aminoAcid);
        result.setMassDiff(massDiff);
        result.setVariable(variable);
        result.setSymbol(symbol);

        return result;
    }


    public int getRun()
    {
        return run;
    }


    public void setRun(int run)
    {
        this.run = run;
    }


    public String getAminoAcid()
    {
        return aminoAcid;
    }


    public void setAminoAcid(String aminoAcid)
    {
        if (aminoAcid.length() != 1 || aminoAcid.compareTo("A") < 0 || "Z".compareTo(aminoAcid) < 0)
            throw new RuntimeException("Invalid amino acid specified: \"" + aminoAcid + "\"");

        this.aminoAcid = aminoAcid;
    }


    public float getMassDiff()
    {
        return massDiff;
    }


    public void setMassDiff(float massDiff)
    {
        this.massDiff = massDiff;
    }


    public float getMass()
    {
        return mass;
    }


    public void setMass(float mass)
    {
        this.mass = mass;
    }


    public boolean getVariable()
    {
        return variable;
    }


    public void setVariable(boolean variable)
    {
        this.variable = variable;
    }


    public String getSymbol()
    {
        return symbol;
    }


    public void setSymbol(String symbol)
    {
        this.symbol = symbol;
    }


    private static final String VALID_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    public static class MS2ModificationTest extends TestCase
    {
        public void test()
        {
            List<Pair<String, Boolean>> testAminoAcids = new ArrayList<Pair<String, Boolean>>(300);

            // Add all characters 0 - 255, marking each as valid or not
            for (int i = 0; i < 256; i++)
            {
                String aa = String.valueOf((char)i);
                testAminoAcids.add(new Pair<String, Boolean>(aa, VALID_CHARS.contains(aa)));
            }

            // Add a few more bogus amino acids
            testAminoAcids.add(new Pair<String, Boolean>("", false));
            testAminoAcids.add(new Pair<String, Boolean>("AA", false));
            testAminoAcids.add(new Pair<String, Boolean>("z", false));
            testAminoAcids.add(new Pair<String, Boolean>("a", false));
            testAminoAcids.add(new Pair<String, Boolean>("[", false));
            testAminoAcids.add(new Pair<String, Boolean>("]", false));
            testAminoAcids.add(new Pair<String, Boolean>("0", false));
            testAminoAcids.add(new Pair<String, Boolean>("9", false));
            testAminoAcids.add(new Pair<String, Boolean>("hello", false));
            testAminoAcids.add(new Pair<String, Boolean>("$", false));

            MS2Modification mod = new MS2Modification();

            // Test all characters
            for (Pair<String, Boolean> pair : testAminoAcids)
            {
                Boolean success;

                try
                {
                    mod.setAminoAcid(pair.first);
                    success = true;
                }
                catch (Exception e)
                {
                    success = false;
                }

                if (pair.second != success)
                {
                    throw new RuntimeException("Amino acid \"" + pair.first + "\" failed validation in setAminoAcid()");
                }
            }
        }


        public static Test suite()
        {
            return new TestSuite(MS2ModificationTest.class);
        }
    }
}
