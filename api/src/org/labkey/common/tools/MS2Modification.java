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
}
