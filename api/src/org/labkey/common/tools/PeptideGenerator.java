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

import java.io.*;
import java.util.*;

/**
 * User: migra
 * Date: Jun 23, 2004
 * Time: 2:33:14 PM
 *
 */
public class PeptideGenerator implements Runnable
{
    public static double[] AMINO_ACID_AVERAGE_MASSES = getMasses(false);
    public static double[] AMINO_ACID_MONOISOTOPIC_MASSES = getMasses(true);
    //Index of H-ION in acid tables
    public static final int H_ION_INDEX = 0;
    public static final double ELECTRON_MASS = 5.485e-4;

    public static final int DIGEST_TRYPTIC = 1;
    public static final int DIGEST_ALL = 2;

    private String _inputFileName;
    private String _outputFileName;
    private boolean _computePI = true;
    private boolean _computeHp = true;
    private int _hpWindowSize = 9;
    private boolean _computeAverageMass = true;
    private boolean _computeMonoisotopicMass = true;
    private int _digest=DIGEST_TRYPTIC;
    private boolean _countOnly;
    private int _maxMissedCleavages = 0;
    private double _minMass = 400;
    private double _maxMass = 6400;
    private int _maxProteins = Integer.MAX_VALUE;
    private int _minResidues = -1;
    private int _maxResidues = Integer.MAX_VALUE;
    private double[] _massTab = AMINO_ACID_MONOISOTOPIC_MASSES;
    private boolean _async = true;
    private ArrayList _peptides;

    private ArrayList _listeners = new ArrayList();

    private int _protNum = 0;
    private long _pepCount = 0;
    private long _aaCount = 0;


    public static void main(String[] args)
    {
        PeptideGenerator gp = initFromParams(args);
        if (null != gp)
        {
            if (!gp._countOnly)
            {
                System.out.print("prot");
                System.out.print('\t');

                System.out.print("pep");

                if (gp._computeAverageMass)
                {
                    System.out.print('\t');
                    System.out.print("m");
                }
                if (gp._computeMonoisotopicMass)
                {
                    System.out.print('\t');
                    System.out.print("mm");
                }
                if (gp._computePI)
                {
                    System.out.print('\t');
                    System.out.print("pi");
                }
                if(gp._computeHp)
                {
                    System.out.print('\t');
                    System.out.print("hp");
                }
                System.out.println();
            }

            Date d = new Date();
            gp.run();

            if (gp._countOnly)
            {
                System.out.print("Proteins: ");
                System.out.println(gp._protNum);
                System.out.print("Peptides: ");
                System.out.println(gp._pepCount);
                System.out.print("Residues: ");
                System.out.println(gp._aaCount);
            }
            System.err.println("Time: " + String.valueOf(new Date().getTime() - d.getTime()));

        }
    }

    public static PeptideGenerator initFromParams(String[] params)
    {
        if (params.length == 0)
            return paramError();

        PeptideGenerator gp = new PeptideGenerator();
        for (int i = 0; i < params.length; i++)
        {
            if (params[i].startsWith("-compute"))
            {
                HashSet computeSet = new HashSet();
                String[] split1 = params[i].split("=");
                if (split1.length != 2)
                    return paramError();

                String[] split2 = split1[1].split(",");
                for (int j = 0; j < split2.length; j++)
                    computeSet.add(split2[j].trim());

                gp._computeAverageMass =  computeSet.contains("m");
                gp._computeMonoisotopicMass = computeSet.contains("mm");
                gp._computeHp = computeSet.contains("hp");
                gp._computePI = computeSet.contains("pi");
            }
            else if (params[i].startsWith("-digest"))
            {
                String[] split = params[i].split("=");
                if (split.length != 2)
                    return paramError();
                String digest = split[1];
                if ("tryptic".equals(digest))
                    gp._digest = DIGEST_TRYPTIC;
                else if ("all".equals(digest))
                    gp._digest = DIGEST_ALL;
                else
                    return paramError();
            }
            else if (params[i].startsWith("-countOnly"))
            {
                gp._countOnly = true;
            }
            else if (params[i].startsWith("-maxProteins"))
            {
                String[] split = params[i].split("=");
                if (split.length != 2)
                    return paramError();
                gp._maxProteins = Integer.parseInt(split[1]);
            }
            else if (params[i].startsWith("-massRange"))
            {
                String[] split = params[i].split("=");
                if (split.length != 2)
                    return paramError();
                String[] massStrings = split[1].split("-");
                gp._minMass = Double.parseDouble(massStrings[0]);
                gp._maxMass = Double.parseDouble(massStrings[1]);
            }
            else if (params[i].startsWith("-maxMissed"))
            {
                String[] split = params[i].split("=");
                if (split.length != 2)
                    return paramError();

                gp._maxMissedCleavages = Integer.parseInt(split[1]);
            }
            else if (params[i].startsWith("-cys"))
            {
                String[] split = params[i].split("=");
                if (split.length != 2)
                    return paramError();
                double cys = Double.parseDouble(split[1]);
                double[] massTab = getMasses(true);
                massTab['C'] += cys;
                gp._massTab = massTab;
            }
            else if (!params[i].startsWith("-"))
            {
                gp._inputFileName = params[i];
            }
            else
            {
                //Unknown option
                return paramError();
            }
        }

        if (null == gp._inputFileName)
            return paramError();

        File file = new File(gp._inputFileName);
        if (!file.exists())
        {
            System.err.println("Could not find file: " + file.getAbsolutePath());
            return null;
        }

        gp.addListener(gp.getCommandLineListener());


        return gp;
    }

    private static PeptideGenerator paramError()
    {
        System.err.print("syntax: java PeptideGenerator inputFile [-compute={m,mm,pi,hp}] [-digest={tryptic|all}] [-massRange=min-max] [-countOnly] [-cys=m]");
        return null;
    }

    public void run()
    {
        FastaLoader loader = null;
        try
        {
            File fastaFile = new File(_inputFileName);
            loader = new FastaLoader(fastaFile);
        }
        catch (Exception x)
        {
            x.printStackTrace(System.err);
            return;
        }

        Iterator it = loader.iterator();

        double[] massTab = getMasses(false);
        if (_minResidues == -1)
            _minResidues = (int) (_minMass / massTab['W']);
        if (_maxResidues == Integer.MAX_VALUE)
            _maxResidues = (int) (_maxMass / massTab['G']);

        while (it.hasNext())
        {
            _protNum++;
            Protein p = (Protein) it.next();
            byte[] bytes = p.getBytes();
            _aaCount += bytes.length;
            if (_digest == DIGEST_TRYPTIC)
                doProteinDigest(p);
            else
                doProteinAll(p);

            if (_protNum >= _maxProteins)
                break;
        }

        fireHandleDone();

    }

    public Peptide[] digestProtein(Protein protein)
    {
        _async = false;
        _peptides = new ArrayList();

        doProteinDigest(protein);

        return (Peptide[]) _peptides.toArray(new Peptide[_peptides.size()]);
    }


    public void addListener(PeptideListener listener)
    {
        _listeners.add(listener);
    }

    public PeptideListener getCommandLineListener()
    {
        return new CommandLinePeptideListener();
    }

    private void doProteinDigest(Protein protein)
    {
        byte[] bytes = protein.getBytes();
        ArrayList rkSites = new ArrayList();

        for (int i = 0; i < bytes.length; i++)
        {
            byte b = bytes[i];
            if ((i + 1 == bytes.length || bytes[i + 1] != 'P') && (b == 'R' || b == 'K'))
                    rkSites.add(new Integer(i));
        }
        byte b = bytes[bytes.length -1];
        if (b != 'K' && b != 'R')
            rkSites.add(new Integer(bytes.length - 1));

        Integer[] rk = (Integer[]) rkSites.toArray(new Integer[rkSites.size()]);
        int prevSite = -1;

        int nextSite = 0;
        for (int i = 0; i < rk.length; i++)
        {
            double m = 0;
            for (int j = 0; j <= _maxMissedCleavages && i + j < rk.length; j++)
            {
                nextSite = rk[i + j].intValue();
                if (nextSite - prevSite > _minResidues && nextSite - prevSite < _maxResidues)
                {
                    Peptide pep = new Peptide(protein, prevSite + 1, nextSite - prevSite);
                    m = pep.getMass(_massTab);
                    if (m >= _minMass && m <= _maxMass)
                        if (_async)
                            fireHandlePeptide(pep);
                        else
                            _peptides.add(pep);
                }
                if (m > _maxMass)
                    break;
            }
            prevSite = rk[i].intValue();
        }
    }

    private void doProteinAll(Protein protein)
    {
        byte[] bytes = protein.getBytes();
        double[] massTab = getMasses(false);
        for (int i = 0; i < bytes.length - _minResidues; i++)
        {
            double m = massTab['h'] + massTab['o'] + massTab['h'];

            for (int j = 0; j < _minResidues; j++)
                m +=  massTab[bytes[i + j]];

            for (int j = _minResidues; j <= _maxResidues && i + j < bytes.length; j++)
            {
                m += massTab[bytes[i + j]];
                //m = fireHandlePeptide(bytes, i, j);
                if (m > _maxMass)
                    break;
                else if (m > _minMass)
                {
                    if (_countOnly)
                        _pepCount++;
                    else
                        fireHandlePeptide(new Peptide(protein, i, j + 1));
                }
                if(_pepCount > 0 && _pepCount % 1000000 == 0)
                    System.err.println(_pepCount);
            }
        }
    }


    private void fireHandlePeptide(Peptide peptide)
    {
        Iterator iter = _listeners.iterator();
        while (iter.hasNext())
        {
            PeptideListener listener = (PeptideListener) iter.next();
            listener.handlePeptide(peptide);
        }
    }

    private void fireHandleDone()
    {
        Iterator iter = _listeners.iterator();
        while (iter.hasNext())
        {
            PeptideListener listener = (PeptideListener) iter.next();
            listener.handleDone();
        }
    }



    public static double computeMass(byte[] bytes, int start, int length, double[] massTab)
    {
        double pepMass = massTab['h'] + massTab['o'] + massTab['h'];
        for (int a = start; a < start + length; a++ )
            pepMass += massTab[bytes[a]];

        return pepMass;
    }

    
    /**
     * Taken from AminoAcidMasses.h on sourceforge.net. Returns 128 character array.
     *
     * Lower case indexes h, o, c, n, p, s are masses for corresponding elements
     * Upper case indexes are Amino Acid Masses.
     *
     * @param monoisotopic If true, return monoisotopic masses, otherwise average masses
     * @return
     */
    public static double[] getMasses(
            boolean monoisotopic)
    {
       double[] aaMasses = new double[128];
       if (!monoisotopic)
       {
          aaMasses['h']=  1.00794;  /* hydrogen */
          aaMasses['o']= 15.9994;   /* oxygen */
          aaMasses['c']= 12.0107;   /* carbon */
          aaMasses['n']= 14.00674;  /* nitrogen */
          aaMasses['p']= 30.973761; /* phosporus */
          aaMasses['s']= 32.066;    /* sulphur */

          aaMasses['G']= 57.05192;
          aaMasses['A']= 71.07880;
          aaMasses['S']= 87.07820;
          aaMasses['P']= 97.11668;
          aaMasses['V']= 99.13256;
          aaMasses['T']=101.10508;
          aaMasses['C']=103.13880; /* 103.1448, 103.14080 */
          aaMasses['L']=113.15944;
          aaMasses['I']=113.15944;
          aaMasses['X']=113.15944;
          aaMasses['N']=114.10384;
          aaMasses['O']=114.14720;
          aaMasses['B']=114.59622;
          aaMasses['D']=115.08860;
          aaMasses['Q']=128.13072;
          aaMasses['K']=128.17408;
          aaMasses['Z']=128.62310;
          aaMasses['E']=129.11548;
          aaMasses['M']=131.19256; /* 131.19456 131.1986 */
          aaMasses['H']=137.14108;
          aaMasses['F']=147.17656;
          aaMasses['R']=156.18748;
          aaMasses['Y']=163.17596;
          aaMasses['W']=186.21320;
       }
       else /* monoisotopic masses */
       {
          aaMasses['h']=  1.0078250;
          aaMasses['o']= 15.9949146;
          aaMasses['c']= 12.0000000;
          aaMasses['n']= 14.0030740;
          aaMasses['p']= 30.9737633;
          aaMasses['s']= 31.9720718;

          aaMasses['G']= 57.0214636;
          aaMasses['A']= 71.0371136;
          aaMasses['S']= 87.0320282;
          aaMasses['P']= 97.0527636;
          aaMasses['V']= 99.0684136;
          aaMasses['T']=101.0476782;
          aaMasses['C']=103.0091854;
          aaMasses['L']=113.0840636;
          aaMasses['I']=113.0840636;
          aaMasses['X']=113.0840636;
          aaMasses['N']=114.0429272;
          aaMasses['O']=114.0793126;
          aaMasses['B']=114.5349350;
          aaMasses['D']=115.0269428;
          aaMasses['Q']=128.0585772;
          aaMasses['K']=128.0949626;
          aaMasses['Z']=128.5505850;
          aaMasses['E']=129.0425928;
          aaMasses['M']=131.0404854;
          aaMasses['H']=137.0589116;
          aaMasses['F']=147.0684136;
          aaMasses['R']=156.1011106;
          aaMasses['Y']=163.0633282;
          aaMasses['W']=186.0793126;
       }

        aaMasses[H_ION_INDEX] = aaMasses['h'] - ELECTRON_MASS;
        return aaMasses;
    } /*ASSIGN_MASS*/


    /**
     * PI Calculation
     */

    private static final double PH_MIN = 0;       /* minimum pH value */
    private static final double PH_MAX =14;      /* maximum pH value */
    private static final double MAXLOOP = 2000;    /* maximum number of iterations */
    private static final double  EPSI   = 0.0001;  /* desired precision */


    /* the 7 amino acid which matter */
        static int R = 'R' - 'A',
                   H = 'H' - 'A',
                   K = 'K' - 'A',
                   D = 'D' - 'A',
                   E = 'E' - 'A',
                   C = 'C' - 'A',
                   Y = 'Y' - 'A';

    /*
     *  table of pk values :
     *  Note: the current algorithm does not use the last two columns. Each
     *  row corresponds to an amino acid starting with Ala. J, O and U are
     *  inexistant, but here only in order to have the complete alphabet.
     *
     *          Ct    Nt   Sm     Sc     Sn
     */

        static double[][]  pk = new double[][]
        {
/* A */    {3.55, 7.59, 0.0  , 0.0  , 0.0   },
/* B */    {3.55, 7.50, 0.0  , 0.0  , 0.0   },
/* C */    {3.55, 7.50, 9.00 , 9.00 , 9.00  },
/* D */    {4.55, 7.50, 4.05 , 4.05 , 4.05  },
/* E */    {4.75, 7.70, 4.45 , 4.45 , 4.45  },
/* F */    {3.55, 7.50, 0.0  , 0.0  , 0.0   },
/* G */    {3.55, 7.50, 0.0  , 0.0  , 0.0   },
/* H */    {3.55, 7.50, 5.98 , 5.98 , 5.98  },
/* I */    {3.55, 7.50, 0.0  , 0.0  , 0.0   },
/* J */    {0.00, 0.00, 0.0  , 0.0  , 0.0   },
/* K */    {3.55, 7.50, 10.00, 10.00, 10.00 },
/* L */    {3.55, 7.50, 0.0  , 0.0  , 0.0   },
/* M */    {3.55, 7.00, 0.0  , 0.0  , 0.0   },
/* N */    {3.55, 7.50, 0.0  , 0.0  , 0.0   },
/* O */    {0.00, 0.00, 0.0  , 0.0  , 0.0   },
/* P */    {3.55, 8.36, 0.0  , 0.0  , 0.0   },
/* Q */    {3.55, 7.50, 0.0  , 0.0  , 0.0   },
/* R */    {3.55, 7.50, 12.0 , 12.0 , 12.0  },
/* S */    {3.55, 6.93, 0.0  , 0.0  , 0.0   },
/* T */    {3.55, 6.82, 0.0  , 0.0  , 0.0   },
/* U */    {0.00, 0.00, 0.0  , 0.0  , 0.0   },
/* V */    {3.55, 7.44, 0.0  , 0.0  , 0.0   },
/* W */    {3.55, 7.50, 0.0  , 0.0  , 0.0   },
/* X */    {3.55, 7.50, 0.0  , 0.0  , 0.0   },
/* Y */    {3.55, 7.50, 10.00, 10.00, 10.00 },
/* Z */    {3.55, 7.50, 0.0  , 0.0  , 0.0   },
        };

        static double exp10(double value)
        {
           return Math.pow(10.0,value);
        }

        static double computePI(byte[] seq, int start, int seq_length)
        {
           int[]             comp = new int[26];    /* Amino acid composition of the protein */
           int    nterm_res,   /* N-terminal residue */
                   cterm_res;   /* C-terminal residue */
           int i;
            int charge_increment = 0;
           double charge,
                           ph_mid = 0,
                           ph_min,
                           ph_max;
           double          cter,
                           nter;
           double          carg,
                           clys,
                           chis,
                           casp,
                           cglu,
                           ctyr,
                           ccys;


           for (i = 0; i < seq_length; i++)        /* compute the amino acid composition */
           {
              comp[seq[i + start] - 'A']++;
           }

           nterm_res = seq[start] - 'A';               /* Look up N-terminal residue */
           cterm_res = seq[start + seq_length-1] - 'A';    /* Look up C-terminal residue */

           ph_min = PH_MIN;
           ph_max = PH_MAX;

           for (i = 0, charge = 1.0; i<MAXLOOP && (ph_max - ph_min)>EPSI; i++)
           {
              ph_mid = ph_min + (ph_max - ph_min) / 2.0;

              cter = exp10(-pk[cterm_res][0]) / (exp10(-pk[cterm_res][0]) + exp10(-ph_mid));
              nter = exp10(-ph_mid) / (exp10(-pk[nterm_res][1]) + exp10(-ph_mid));

              carg = comp[R] * exp10(-ph_mid) / (exp10(-pk[R][2]) + exp10(-ph_mid));
              chis = comp[H] * exp10(-ph_mid) / (exp10(-pk[H][2]) + exp10(-ph_mid));
              clys = comp[K] * exp10(-ph_mid) / (exp10(-pk[K][2]) + exp10(-ph_mid));

              casp = comp[D] * exp10(-pk[D][2]) / (exp10(-pk[D][2]) + exp10(-ph_mid));
              cglu = comp[E] * exp10(-pk[E][2]) / (exp10(-pk[E][2]) + exp10(-ph_mid));

              ccys = comp[C] * exp10(-pk[C][2]) / (exp10(-pk[C][2]) + exp10(-ph_mid));
              ctyr = comp[Y] * exp10(-pk[Y][2]) / (exp10(-pk[Y][2]) + exp10(-ph_mid));

              charge = carg + clys + chis + nter + charge_increment
                 - (casp + cglu + ctyr + ccys + cter);

              if (charge > 0.0)
              {
                 ph_min = ph_mid;
              }
              else
              {
                 ph_max = ph_mid;
              }
           }

           return ph_mid;
        }


    public String getInputFileName()
    {
        return _inputFileName;
    }

    public void setInputFileName(String inputFileName)
    {
        this._inputFileName = inputFileName;
    }

    public String getOutputFileName()
    {
        return _outputFileName;
    }

    public void setOutputFileName(String outputFileName)
    {
        this._outputFileName = outputFileName;
    }

    public boolean isComputePI()
    {
        return _computePI;
    }

    public void setComputePI(boolean computePI)
    {
        this._computePI = computePI;
    }

    public boolean isComputeHp()
    {
        return _computeHp;
    }

    public void setComputeHp(boolean computeHp)
    {
        this._computeHp = computeHp;
    }

    public int getHpWindowSize()
    {
        return _hpWindowSize;
    }

    public void setHpWindowSize(int hpWindowSize)
    {
        this._hpWindowSize = hpWindowSize;
    }

    public boolean isComputeAverageMass()
    {
        return _computeAverageMass;
    }

    public void setComputeAverageMass(boolean computeAverageMass)
    {
        this._computeAverageMass = computeAverageMass;
    }

    public boolean isComputeMonoisotopicMass()
    {
        return _computeMonoisotopicMass;
    }

    public void setComputeMonoisotopicMass(boolean computeMonoisotopicMass)
    {
        this._computeMonoisotopicMass = computeMonoisotopicMass;
    }

    public int getDigest()
    {
        return _digest;
    }

    public void setDigest(int digest)
    {
        this._digest = digest;
    }

    public boolean isCountOnly()
    {
        return _countOnly;
    }

    public void setCountOnly(boolean countOnly)
    {
        this._countOnly = countOnly;
    }

    public int getMaxMissedCleavages()
    {
        return _maxMissedCleavages;
    }

    public void setMaxMissedCleavages(int maxMissedCleavages)
    {
        _maxMissedCleavages = maxMissedCleavages;
    }

    public double getMinMass()
    {
        return _minMass;
    }

    public void setMinMass(double minMass)
    {
        _minMass = minMass;
    }

    public double getMaxMass()
    {
        return _maxMass;
    }

    public void setMaxMass(double maxMass)
    {
        _maxMass = maxMass;
    }

    public int getMinResidues()
    {
        return _minResidues;
    }

    public void setMinResidues(int minResidues)
    {
        _minResidues = minResidues;
    }

    public int getMaxResidues()
    {
        return _maxResidues;
    }

    public void setMaxResidues(int maxResidues)
    {
        _maxResidues = maxResidues;
    }

    public double[] getMassTable()
    {
        return _massTab;
    }

    public void setMassTable(double[] massTab)
    {
        _massTab = massTab;
    }

    public class CommandLinePeptideListener implements PeptideListener
    {
        PrintStream out;

        public CommandLinePeptideListener()
        {
            out = System.out;
        }

        public void handlePeptide(Peptide peptide)
        {
            double m = 0;

            m = peptide.getMass();
            if (m > _minMass && m <= _maxMass)
            {
                _pepCount++;

                if (!_countOnly)
                {
                    out.print(_protNum);
                    out.print('\t');

                    out.print(peptide.getChars());

                    if (_computeAverageMass)
                    {
                        out.print('\t');
                        out.print(m);
                    }
                    if (_computeMonoisotopicMass)
                    {
                        out.print('\t');
                        out.print(peptide.getMonoisotopicMass());
                    }
                    if (_computePI)
                    {
                        out.print('\t');
                        out.print(peptide.getPi());
                    }
                    if(_computeHp)
                    {
                        out.print('\t');
                        out.print(peptide.getHydrophobicity());
                    }
                    out.println();
                }
            }
        }

        public void handleDone()
        {

        }
    }

    public static interface PeptideListener
    {
        public void handlePeptide(Peptide peptide);
        public void handleDone();
    }
}
