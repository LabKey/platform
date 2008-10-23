/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

import org.apache.log4j.Logger;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PepXmlLoader extends MS2Loader
{
    private PeptideProphetSummary _ppSummary;

    private ArrayList<RelativeQuantAnalysisSummary> _quantSummaries = new ArrayList<RelativeQuantAnalysisSummary>();

    public PepXmlLoader(File f, Logger log) throws FileNotFoundException, XMLStreamException
    {
        init(f, log);
        readAnalysisSummaries();
    }


    // Read xpress, peptide prophet, etc. analysis summaries at the top of the file
    // Starts at the beginning and ends on the first msms_run_summary tag
    public void readAnalysisSummaries() throws XMLStreamException
    {
        while (_parser.hasNext())
        {
            _parser.next();

            if (_parser.isStartElement())
            {
                String element = _parser.getLocalName();

                if (element.equals("msms_run_summary"))
                    return;

                if (element.equals("analysis_summary"))
                {
                    String analysisType = _parser.getAttributeValue(null, "analysis");

                    if (analysisType.equals("peptideprophet"))
                        _ppSummary = PeptideProphetSummary.load(_parser);
                    else if (XPressHandler.analysisType.equals(analysisType))
                        _quantSummaries.add(XPressAnalysisSummary.load(_parser));
                    else if (Q3Handler.analysisType.equals(analysisType))
                        _quantSummaries.add(Q3AnalysisSummary.load(_parser));
                }
            }
        }
    }


    public PeptideProphetSummary getPeptideProphetSummary()
    {
        return _ppSummary;
    }

    public List<RelativeQuantAnalysisSummary> getQuantSummaries()
    {
        return _quantSummaries;
    }

    public FractionIterator getFractionIterator()
    {
        return new FractionIterator();
    }


    public class FractionIterator implements Iterator
    {
        public boolean hasNext()
        {
            boolean hasNext = true;

            // If we're not currently on the start of an msms_run_summary then attempt to skip to the next one
            if (!_parser.isStartElement() || !"msms_run_summary".equals(_parser.getLocalName()))
            {
                try
                {
                    hasNext = _parser.skipToStart("msms_run_summary");
                }
                catch (XMLStreamException e)
                {
                    _log.error("XMLStreamException in hasNext()", e);
                    throw new RuntimeException("XMLStreamException in hasNext()", e);
                }
            }

            return hasNext;
        }


        public Object next()
        {
        try
            {
                return PepXmlFraction.getNextFraction(_parser);
            }
            catch (XMLStreamException e)
            {
                _log.error("XMLStreamException in next()", e);
                throw new RuntimeException("XMLStreamException in next()", e);
            }
        }


        public void remove()
        {
            throw new UnsupportedOperationException();
        }
    }


    public static class PepXmlFraction
    {
        private SimpleXMLStreamReader _parser;

        private String _massSpecType = null;
        private String _searchEngine = null;
        private String _searchEnzyme = null;

        //dhmay adding 2008/01/30
        private int _searchConstraintMaxInternalCleavages;
        private int _searchConstraintMinTermini;

        private String _databaseLocalPath = null;
        private String _dataBasename, _dataSuffix;
        private String _spectrumPath = null;
        private Float _importSpectraMinProbability = null;
        private boolean _loadSpectra = true;
        private MS2ModificationList _modifications = new MS2ModificationList();


        public static PepXmlFraction getNextFraction(SimpleXMLStreamReader parser) throws XMLStreamException
        {
            PepXmlFraction fraction = new PepXmlFraction(parser);
            fraction.assembleRunInfo();
            return fraction;
        }


        private PepXmlFraction(SimpleXMLStreamReader parser)
        {
            _parser = parser;
        }


        // Pull run info together from the msms_run_summary and search_summary
        // We start on an msms_run_summary tag
        protected void assembleRunInfo() throws XMLStreamException
        {
            _massSpecType = null;
            _searchEngine = null;
            _searchEnzyme = null;
            _databaseLocalPath = null;

            handleMsMsRunSummary();

            if (!_parser.skipToStart("search_summary"))
            {
                throw new XMLStreamException("No search_summary to skip to");
            }
            handleSearchSummary();
        }


        private void handleMsMsRunSummary()
        {
            String[] instrument = new String[]{
                    _parser.getAttributeValue(null, "msManufacturer"),
                    _parser.getAttributeValue(null, "msModel"),
            };

            _massSpecType = join(" ", instrument);
        }


        private boolean handleSearchSummary() throws XMLStreamException
        {
            boolean endOfSearchSummary = false;

            while (!endOfSearchSummary)
            {
                if (_parser.isWhiteSpace() || XMLStreamReader.COMMENT == _parser.getEventType())
                {
                    _parser.next();
                    continue;
                }

                String element = _parser.getLocalName();

                if (_parser.isStartElement())
                {
                    if (element.equals("search_query"))
                        endOfSearchSummary = true;
                    else if (element.equals("search_summary"))
                    {
                        _searchEngine = _parser.getAttributeValue(null, "search_engine");
                        _dataBasename = _parser.getAttributeValue(null, "base_name");
                        _dataSuffix = _parser.getAttributeValue(null, "out_data");
                        if (_dataSuffix == null)
                            _dataSuffix = "tgz";
                        else if (_dataSuffix.startsWith("."))
                            _dataSuffix = _dataSuffix.substring(1);
                    }
                    else if (element.equals("search_database"))
                        _databaseLocalPath = _parser.getAttributeValue(null, "local_path");
                    else if (element.equals("aminoacid_modification"))
                        handleModification();
                    else if (element.equals("enzymatic_search_constraint"))
                        handleEnzymaticSearchConstraint();
                    else if (element.equals("parameter"))
                    {
                        String name = _parser.getAttributeValue(null, "name");

                        if ("spectrum, path".equals(name))
                            _spectrumPath = _parser.getAttributeValue(null, "value");

                        if ("pipeline, import spectra min probability".equals(name))
                            _importSpectraMinProbability = Float.parseFloat(_parser.getAttributeValue(null, "value"));

                        if ("pipeline, load spectra".equals(name) || "pipeline, import spectra".equals(name))
                            _loadSpectra = !"no".equalsIgnoreCase(_parser.getAttributeValue(null, "value"));
                    }
                }
                else
                {
                    if (element.equals("search_summary"))
                        endOfSearchSummary = true;
                }

                if (!endOfSearchSummary)
                    _parser.next();
            }

            // Assign symbols to modifications that don't have them
            _modifications.initializeSymbols();

            // We should now have all the run info collected
            return true;
        }


        private void handleModification()
        {
            MS2Modification mod = new MS2Modification();
            mod.setAminoAcid(_parser.getAttributeValue(null, "aminoacid"));
            mod.setMassDiff(Float.parseFloat(_parser.getAttributeValue(null, "massdiff")));
            mod.setVariable("Y".equals(_parser.getAttributeValue(null, "variable")));
            mod.setMass(Float.parseFloat(_parser.getAttributeValue(null, "mass")));

            if (mod.getVariable())
                mod.setSymbol(_parser.getAttributeValue(null, "symbol"));
            else
                mod.setSymbol("?");
//System.err.println("new modification: " + mod);
            _modifications.add(mod);
        }


        private void handleEnzymaticSearchConstraint()
        {
            _searchEnzyme = _parser.getAttributeValue(null, "enzyme");
             String maxCleavagesString = _parser.getAttributeValue(null, "max_num_internal_cleavages");
            if (maxCleavagesString != null)
                _searchConstraintMaxInternalCleavages = Integer.parseInt(maxCleavagesString);
            else
                _searchConstraintMaxInternalCleavages = 0;

            String minTerminiString = _parser.getAttributeValue(null, "min_number_termini");
            if (minTerminiString != null)
                _searchConstraintMinTermini = Integer.parseInt(minTerminiString);
            else
                _searchConstraintMinTermini = 0;
        }


        public String getMassSpecType()
        {
            return _massSpecType;
        }


        public List<MS2Modification> getModifications()
        {
            return _modifications;
        }


        public String getSearchEngine()
        {
            return _searchEngine;
        }


        public String getSearchEnzyme()
        {
            return _searchEnzyme;
        }


        public int getSearchConstraintMaxInternalCleavages()
        {
            return _searchConstraintMaxInternalCleavages;
        }

        public int getSearchConstraintMinTermini()
        {
            return _searchConstraintMinTermini;
        }

        public String getDataBasename()
        {
            return _dataBasename;
        }


        public String getDataSuffix()
        {
            return _dataSuffix;
        }


        public String getDatabaseLocalPath()
        {
            return _databaseLocalPath;
        }


        public String getSpectrumPath()
        {
            return _spectrumPath;
        }

        //The pepXML for sequest does not contain input.xml params.
        public void setSpectrumPath(String spectrumPath)
        {
            this._spectrumPath = spectrumPath;
        }

        public boolean shouldLoadSpectra()
        {
            return _loadSpectra;
        }

        public Float getImportSpectraMinProbability()
        {
            return _importSpectraMinProbability;
        }

        public PeptideIterator getPeptideIterator()
        {
            return new PeptideIterator(_parser, _modifications);
        }
    }


    public static class PeptideIterator implements Iterator<PepXmlPeptide>
    {
        private static Logger _log = Logger.getLogger(PepXmlLoader.class);

        private SimpleXMLStreamReader _parser;
        private PepXmlPeptide _peptide = null;
        private MS2ModificationList _modifications;

        private Map<Character, Integer> _unknownNTerminalModifications = new HashMap<Character, Integer>();
        private Map<Character, Integer> _unknownNonNTerminalModifications = new HashMap<Character, Integer>();

        protected PeptideIterator(SimpleXMLStreamReader parser, MS2ModificationList modifications)
        {
            _parser = parser;
            _modifications = modifications;
        }

        protected void incrementUnknownModCount(Map<Character, Integer> mapToIncrement,
                                                char charToIncrement)
        {
            Integer integerToIncrement = mapToIncrement.get(charToIncrement);
            if (integerToIncrement == null)
                integerToIncrement = 0;
            mapToIncrement.put(charToIncrement, ++integerToIncrement);
        }

        public boolean hasNext()
        {
            try
            {
                _peptide = PepXmlPeptide.getNextPeptide(_parser, _modifications);

                boolean result = (null != _peptide);

                if (result)
                {
                    //Sift through any unknown modifications and record them
                    //dhmay fixing bug 5904, 5/20/2008: adding null-check
                    if (_peptide._unknownModArray != null)
                    {
                        for (int i = 0; i < _peptide._unknownModArray.length; i++)
                        {
                            if (_peptide._unknownModArray[i])
                            {
                                if (i == 0)
                                    incrementUnknownModCount(_unknownNTerminalModifications,
                                            _peptide.getTrimmedPeptide().charAt(0));
                                else
                                {
                                    incrementUnknownModCount(_unknownNonNTerminalModifications,
                                            _peptide.getTrimmedPeptide().charAt(i));
//System.err.println("Unknown mod: " + _peptide.getPeptide() + ", " + i + ", " + _peptide.getTrimmedPeptide().charAt(i));
                                }
                            }
                        }
                    }
                }
                else
                {
                    //End of the line, time to print out any unknown modifications
                    if (!_unknownNTerminalModifications.isEmpty())
                    {
                        _log.error("Error: Unknown N-Terminal Modifications.  Counts per residue:");
                        for (char residue : _unknownNTerminalModifications.keySet())
                        {
                            _log.error("\t" + residue + ": " + _unknownNTerminalModifications.get(residue));
                        }
                    }
                    if (!_unknownNonNTerminalModifications.isEmpty())
                    {
                        _log.error("Error: Unknown non-N-Terminal Modifications:");
                        for (char residue : _unknownNonNTerminalModifications.keySet())
                        {
                            _log.error("\t" + residue + ": " + _unknownNonNTerminalModifications.get(residue));
                        }
                    }
                }


                return result;
            }
            catch (XMLStreamException e)
            {
                _log.error(e);
                return false;
            }
        }


        public PepXmlPeptide next()
        {
            return _peptide;
        }


        public void remove()
        {
            throw new UnsupportedOperationException();
        }
    }


    public static class PepXmlPeptide
    {
        //keeps track of all unknown modifications we find, for later reporting
        protected boolean[] _unknownModArray;

        private SimpleXMLStreamReader _parser;
        private int _scan, _endScan, _charge, _matchedIons, _totalIons, _proteinHits;
        private Double _retentionTime = null;
        private float _ionPercent, _deltaMass;
        private double _calculatedNeutralMass;
        private String _peptide, _prevAA, _trimmedPeptide, _nextAA, _protein, _dtaFileName;
        //dhmay adding _alternativeProteins 04/23/08
        private List<String> _alternativeProteins;
        private Integer hitRank = null;
        private HashMap<String, String> _scores;
        private MS2ModificationList _modifications;
        private static final Pattern SCAN_REGEX = Pattern.compile("\\.??(\\d{1,6})\\.(\\d{1,6})\\.(\\d{1})\\.??[a-zA-z0-9_]*?$");

        //This variable stays null unless there are actually modificationsIf there are,
        //then all elements are null except the actual modifications.  Index+1 = position of mod
        private ModifiedAminoAcid[] _modifiedAminoAcids = null;

        private HashMap<String, PepXmlAnalysisResultHandler.PepXmlAnalysisResult> _analysisResultMap = null;

        private static Logger _log = Logger.getLogger(PepXmlPeptide.class);

        protected static PepXmlPeptide getNextPeptide(SimpleXMLStreamReader parser, MS2ModificationList modifications)
                throws XMLStreamException
        {
            PepXmlPeptide peptide = new PepXmlPeptide(parser, modifications);
            boolean success = peptide.load();

            if (success)
                return peptide;
            else
                return null;
        }


        private PepXmlPeptide(SimpleXMLStreamReader parser, MS2ModificationList modifications)
        {
            _parser = parser;
            _modifications = modifications;
        }

        private static final int UNKNOWN = 0;
        private static final int SEARCH_RESULT = 1;
        private static final int SEARCH_HIT = 2;
        private static final int ALTERNATIVE_PROTEIN = 3;
        private static final int SEARCH_SCORE = 4;
        private static final int ANALYSIS_RESULT = 5;
        private static final int MSMS_RUN_SUMMARY = 6;
        private static final int MODIFICATION_INFO = 7;
        private static final int SPECTRUM_QUERY = 8;
        private boolean endOfSpectrumQuery, endOfRun;
        private static HashMap<String, Integer> elements;

        static
        {
            elements = new HashMap<String, Integer>();
            elements.put("search_result", SEARCH_RESULT);
            elements.put("search_hit", SEARCH_HIT);
            elements.put("alternative_protein", ALTERNATIVE_PROTEIN);
            elements.put("search_score", SEARCH_SCORE);
            elements.put("analysis_result", ANALYSIS_RESULT);
            elements.put("msms_run_summary", MSMS_RUN_SUMMARY);
            elements.put("modification_info", MODIFICATION_INFO);
            elements.put("spectrum_query", SPECTRUM_QUERY);
        }

        protected boolean load() throws XMLStreamException
        {
            endOfSpectrumQuery = false;
            endOfRun = false;
            hitRank = null;
            _scores = new HashMap<String, String>(10);
            _alternativeProteins = new ArrayList<String>();

            while (!endOfSpectrumQuery && !endOfRun)
            {
                if (_parser.isWhiteSpace())
                {
                    _parser.next();
                    continue;
                }

                Integer element = elements.get(_parser.getLocalName());
                int index = (null != element ? element.intValue() : UNKNOWN);

                if (_parser.isStartElement())
                    processStartElement(index);
                else
                    processEndElement(index);

                if (endOfRun)
                    return false;

                _parser.next();
            }

            fixUp();
            return true;
        }


        protected void processStartElement(int index) throws XMLStreamException
        {
            switch (index)
            {
                case(SPECTRUM_QUERY):
                    _dtaFileName = _parser.getAttributeValue(null, "spectrum");
                    _scan = Integer.parseInt(_parser.getAttributeValue(null, "start_scan"));

                    String endScan = _parser.getAttributeValue(null, "end_scan");
                    _endScan = (null == endScan ? _scan : Integer.parseInt(endScan));

                    _charge = Integer.parseInt(_parser.getAttributeValue(null, "assumed_charge"));

                    // Retention time is optional, but if we find it, set it.  If not, we'll retrieve it from the mzXML file and update
                    // the peptide record, but that's much more expensive.
                    String retentionTime = _parser.getAttributeValue(null, "retention_time_sec");
                    _retentionTime = (null != retentionTime ? Double.parseDouble(retentionTime) : null);

                    // Mascot exported pepXML can have start_scan="0" and end_scan="0"
                    if (0 == _scan)
                    {
                        Matcher m = SCAN_REGEX.matcher (_dtaFileName);
                        if (m.find())
                        {
                            // endScan=m.group(2), charge=m.group(3)
                            _scan = Integer.parseInt(m.group(1));
                            _endScan = Integer.parseInt(m.group(2));
                        }
                    }

                    break;
                case(SEARCH_RESULT):
                    // Start over again within each spectrum_query block
                    hitRank = null;
                    break;
                case(SEARCH_HIT):
                    Integer h = Integer.valueOf(_parser.getAttributeValue(null, "hit_rank"));
                    if (hitRank == null || h.compareTo(hitRank) <= 0)
                    {
                        hitRank = h;
                        _prevAA = _parser.getAttributeValue(null, "peptide_prev_aa");
                        _trimmedPeptide = _parser.getAttributeValue(null, "peptide");
                        _nextAA = _parser.getAttributeValue(null, "peptide_next_aa");
                        _proteinHits = Integer.parseInt(_parser.getAttributeValue(null, "num_tot_proteins"));
                        String numMatchedIons = _parser.getAttributeValue(null, "num_matched_ions");

                        //doing some null-checking here, just in case.  Since we're generating
                        //some pepXml files ourselves, some of this stuff might be null
                        if (numMatchedIons != null)
                            _matchedIons = Integer.parseInt(numMatchedIons.trim());
                        else
                            _matchedIons = 0;
                        
                        String totNumIons = _parser.getAttributeValue(null, "tot_num_ions");
                        if (totNumIons!= null)
                            _totalIons = Integer.parseInt(totNumIons.trim());
                        else
                            _totalIons = 0;

                        // Mascot exported pepXML may not report "tot_num_ions"
                        if (0 == _totalIons && _matchedIons > 0)
                        {
                            // let's attempt to guess the total ions as per sashimi
                            _totalIons = (_trimmedPeptide.length() - 1) * 2;
                            if (_charge>2)
                            {
                               // do it iteratively for charge 3, 4, 5, etc
                               for(int ionPerm=2; ionPerm<_charge; ionPerm++)
                                   _totalIons *= 2;
                            }
                        }

                        _calculatedNeutralMass = Double.parseDouble(_parser.getAttributeValue(null, "calc_neutral_pep_mass"));
                        _unknownModArray = new boolean[_trimmedPeptide.length()];

                        // Handle illegal number in pepXML translator
                        String massDiff = _parser.getAttributeValue(null, "massdiff");
                        // For Sequest this needs to be a startsWith, since it outputs "+-0.00000"
                        if (massDiff == null || massDiff.startsWith("+-0.0"))
                            _deltaMass = 0.0f;
                        else
                            _deltaMass = Float.parseFloat(massDiff);

                        // Create protein lookup string that matches the way we import FASTA files (which matches what Comet does)
                        String proteinName = _parser.getAttributeValue(null, "protein");
                        if (proteinName != null)
                        {
                            org.labkey.common.tools.Protein p = new org.labkey.common.tools.Protein(proteinName, new byte[0]);
                            _protein = p.getLookup();
                        }
                        else
                            _protein = null;
                    }
                    else
                    {
                        _parser.skipToEnd("search_hit");   // TODO: Talk to Damon; remove "activeHit"?
                    }
                    break;
                case(MODIFICATION_INFO):
                    _modifiedAminoAcids = new ModifiedAminoAcid[_trimmedPeptide.length()];

                    char[] modChars = new char[_trimmedPeptide.length()];
                    StringBuffer pep = new StringBuffer(_trimmedPeptide);

                    while (true)
                    {
                        _parser.next();

                        if (_parser.isWhiteSpace() || _parser.getEventType() == XMLStreamReader.COMMENT)
                            continue;

                        if ("mod_aminoacid_mass".equals(_parser.getLocalName()))
                        {
                            int position = Integer.parseInt(_parser.getAttributeValue(null, "position")) - 1;
                            char aa = _trimmedPeptide.charAt(position);
                            double modifiedMass = Rounder.round(Double.parseDouble(_parser.getAttributeValue(null, "mass")), 3);

                            MS2Modification mod = _modifications.get(String.valueOf(aa), modifiedMass);

                            // If null, it's either one of the mods that X! Tandem looks for on N-terminal amino acids Q, E, and C, and Tandem2XML isn't spitting out
                            // amino-acid tags OR it's a problem we don't understand
                            if (null == mod)
                            {
//System.err.println("No mod for " + String.valueOf(aa) + ", " + modifiedMass);
                                //record the unknown modification, but don't print out anything yet
                                _unknownModArray[position] = true;
                                _log.debug("Unknown modification at scan " + _scan + ": " + aa +
                                           " " + modifiedMass);
                            }
                            else if (mod.getVariable())
                                modChars[position] =  mod.getSymbol().charAt(0);

                            //paranoia
                            if (position <= _modifiedAminoAcids.length)
                                _modifiedAminoAcids[position] = new ModifiedAminoAcid(aa, modifiedMass);
                            
                            _parser.next();     // end element
                        }
                        else
                        {
                            // Iterate in reverse order, so inserts don't invalidate future positions
                            for (int i = modChars.length - 1; i >= 0; i--)
                                if (0 != modChars[i])
                                    pep.insert(i + 1, modChars[i]);
                            _peptide = pep.toString();
                            break;
                        }
                    }
                    break;
                case(ALTERNATIVE_PROTEIN):
                    //dhmay adding handling for alternative proteins, 04/23/2008
                    String altProteinName = _parser.getAttributeValue(null, "protein");
                    if (altProteinName != null)
                        _alternativeProteins.add(altProteinName);
                    break;
                case(SEARCH_SCORE):
                    String name = _parser.getAttributeValue(null, "name");
                    String value = _parser.getAttributeValue(null, "value");
                    _scores.put(name, value);
                    break;
                case(ANALYSIS_RESULT):
                    PepXmlAnalysisResultHandler.setAnalysisResult(_parser, this);
                    break;
                case(UNKNOWN):
//              _log.debug("unknown: " + parser.getLocalName());
                    break;
                default:
//              _log.debug("known, but no procedure: " + parser.getLocalName());
                    break;
            }
        }


        protected void processEndElement(int index) throws XMLStreamException
        {
            switch (index)
            {
                case(SPECTRUM_QUERY):
                    endOfSpectrumQuery = true;
                    break;
                case(MSMS_RUN_SUMMARY):
                    endOfRun = true;
                    break;
            }
        }        

        /**
         * Called after peptide loading is complete
         */
        private void fixUp()
        {
            if (null == _peptide)
                _peptide = _trimmedPeptide;

            _peptide = (_prevAA != null ? _prevAA + "." : "") +
                    _peptide + (_nextAA != null ? "." + _nextAA : "");

            if (0 == _matchedIons || 0 == _totalIons)
                _ionPercent = 0.0f;
            else
                _ionPercent = (float)(Rounder.round((float) _matchedIons / _totalIons, 2));
        }

        public int getCharge()
        {
            return _charge;
        }

        public Double getRetentionTime()
        {
            return _retentionTime;
        }

        public float getDeltaMass()
        {
            return _deltaMass;
        }

        public String getDtaFileName()
        {
            return _dtaFileName;
        }

        public float getIonPercent()
        {
            return _ionPercent;
        }

        public double getCalculatedNeutralMass()
        {
            return _calculatedNeutralMass;
        }

        public String getNextAA()
        {
            return _nextAA;
        }

        public String getPeptide()
        {
            return _peptide;
        }

        public String getPrevAA()
        {
            return _prevAA;
        }

        public String getProtein()
        {
            return _protein;
        }

        public int getProteinHits()
        {
            return _proteinHits;
        }

        public int getScan()
        {
            return _scan;
        }

        public int getEndScan()
        {
            return _endScan;
        }

        public Map<String, String> getScores()
        {
            return _scores;
        }

        public String getTrimmedPeptide()
        {
            return _trimmedPeptide;
        }


        protected void addAnalysisResult(String analysisType,
                                         PepXmlAnalysisResultHandler.PepXmlAnalysisResult analysisResult)
        {
            if (_analysisResultMap == null)
               _analysisResultMap = new HashMap<String, PepXmlAnalysisResultHandler.PepXmlAnalysisResult>();
            _analysisResultMap.put(analysisType, analysisResult);
        }

        public PepXmlAnalysisResultHandler.PepXmlAnalysisResult getAnalysisResult(String analysisType)
        {
            if (_analysisResultMap == null)
                return null;
            return _analysisResultMap.get(analysisType);
        }

        public PeptideProphetHandler.PeptideProphetResult getPeptideProphetResult()
        {
            return (PeptideProphetHandler.PeptideProphetResult)
                    getAnalysisResult(PeptideProphetHandler.analysisType);
        }


        public XPressHandler.XPressResult getXPressResult()
        {
            return (XPressHandler.XPressResult)
                    getAnalysisResult(XPressHandler.analysisType);
        }

        public Q3Handler.Q3Result getQ3Result()
        {
            return (Q3Handler.Q3Result)
                    getAnalysisResult(Q3Handler.analysisType);
        }

        public ModifiedAminoAcid[] getModifiedAminoAcids()
        {
            return _modifiedAminoAcids;
        }

        /**
         * Never null.  Empty if nothing's there
         * @return
         */
        public List<String> getAlternativeProteins()
        {
            return _alternativeProteins;
        }
    }


    /**
     * TODO: Replace with StringUtils.join?
     */
    private static String join(String delim, String[] strings)
    {
        if (strings == null) return null;
        if (delim == null) delim = "";
        StringBuffer sb = new StringBuffer();
        for (String s : strings)
        {
            if (s != null)
            {
                sb.append(s);
                sb.append(delim);
            }
        }
        if (sb.length() <= delim.length())
            return null;
        return sb.substring(0, sb.length() - delim.length());
    }
}
