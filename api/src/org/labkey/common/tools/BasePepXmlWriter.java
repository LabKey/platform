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

import java.io.*;
import java.math.BigInteger;
import java.util.List;

import org.apache.log4j.Logger;

import net.systemsbiology.regisWeb.pepXML.*;
import org.w3c.dom.Node;
import org.w3c.dom.Element;
import org.w3c.dom.Attr;
import org.apache.xmlbeans.XmlOptions;
import org.apache.xmlbeans.XmlAnySimpleType;

import javax.xml.stream.events.Attribute;

/**
 * An abstract base class for use as a wrapper for writing PepXml files.  We take advantage of XmlBeans to build
 * the structure of the pepxml file, and search_summaries (for modifications), but we stitch the XmlBeans XML
 * output for features together by hand, writing out to a file as we go, so that we don't have to hold the whole
 * structure in memory.
 *
 * This class is abstract because different implementations are likely to want to use different objects to
 * populate the individual spectrum_queries that are written out.  The only implementing class at the time of
 * this comment is viewer.feature.FeaturePepXmlWriter, which uses an array of viewer.Feature.Feature to
 * populate the spectrum_queries.
 *
 * I can foresee at least two issues arising when another implementation needs to be created (likely on the
 * CPAS side):
 *  1.  More file-level data may need to be written out at the top of the file.  In that case, please discuss
 * with me (Damon) and we'll make sure that anything that's useful for everybody gets stuck in this abstract
 * base class
 *  2.  Something other than MS2Modifications might be desired to populate the modifications.  In that case,
 * this abstract base class can turn into two levels of abstract base classes.  :)
 *
 * Also I think there may be some weirdness with where the MS2Modifications are declared, in the case that there's
 * more than one fraction.  We'll burn that bridge when we come to it.
 */
public abstract class BasePepXmlWriter
{
    static Logger _log = Logger.getLogger(BasePepXmlWriter.class);

    //root node
    protected MsmsPipelineAnalysisDocument _xmlBeansPepXmlDoc = null;
    //analysis node, one per doc
    protected MsmsPipelineAnalysisDocument.MsmsPipelineAnalysis _xmlBeansAnalysis = null;
    //run summaries
    protected MsmsPipelineAnalysisDocument.MsmsPipelineAnalysis.MsmsRunSummary[] _xmlBeansRunSummaryArray = null;
    //first run summary
    protected MsmsPipelineAnalysisDocument.MsmsPipelineAnalysis.MsmsRunSummary _firstRunSummary = null;
    //search summary
    protected MsmsPipelineAnalysisDocument.MsmsPipelineAnalysis.MsmsRunSummary.SearchSummary _searchSummary = null;

    //Strings of xml representing the structure before and after the feature content
    protected String _documentPrefix = null;
    protected String _documentPostscript = null;

    //encapsulates printing options for all fragments
    protected XmlOptions _optionsForPrinting = null;

    //Modification objects to populate the AminoAcidModifications
    protected MS2Modification[] _modifications = null;

    //String to begin all spectrum attributes with
    protected String _spectrumBaseString = "";

    //source file's base name
    protected String _baseName = null;


    public static final String PRECURSOR_MASS_TYPE_MONOISOTOPIC = "monoisotopic";

    //precursor mass type.  Must be an allowed value
    protected String _precursorMassType = PRECURSOR_MASS_TYPE_MONOISOTOPIC;


    public static final String SEARCH_ENGINE_XTANDEM_COMET = "X! Tandem (comet)";

    //search engine
    protected String _searchEngine = SEARCH_ENGINE_XTANDEM_COMET;


    /**
     * Constructor creates the XmlBeans representing the shell of a PepXml document, and
     * creates the "prefix" and "postscript" strings representing that shell
     */
    public BasePepXmlWriter()
    {
        //Construct generic document structure
        _xmlBeansPepXmlDoc = MsmsPipelineAnalysisDocument.Factory.newInstance();
        _xmlBeansAnalysis = _xmlBeansPepXmlDoc.addNewMsmsPipelineAnalysis();

        _firstRunSummary = _xmlBeansAnalysis.addNewMsmsRunSummary();
        _xmlBeansRunSummaryArray = _xmlBeansAnalysis.getMsmsRunSummaryArray();


        //set printing options for xml fragments
        _optionsForPrinting = new XmlOptions();
        _optionsForPrinting.setSaveOuter();
        _optionsForPrinting.setSavePrettyPrint();
        _optionsForPrinting.setSavePrettyPrintOffset(0);


    }

    public MsmsPipelineAnalysisDocument.MsmsPipelineAnalysis.MsmsRunSummary.SearchSummary getSearchSummary()
    {
        if (_searchSummary == null)
        {
            MsmsPipelineAnalysisDocument.MsmsPipelineAnalysis.MsmsRunSummary.SearchSummary[] searchSummaries =
                    _firstRunSummary.getSearchSummaryArray();
            if (searchSummaries != null && searchSummaries.length > 0)
                _searchSummary = searchSummaries[0];
            else
            {
                _log.debug("Adding search summary");
                _searchSummary = _firstRunSummary.addNewSearchSummary();
            }

            _searchSummary.setPrecursorMassType(MassType.Enum.forString(_precursorMassType));

            //have to do this manually because the schema isn't aware of any useful search engines
            Node searchSummAtt =
                    _searchSummary.getDomNode().getOwnerDocument().createAttribute("search_engine");
            searchSummAtt.setNodeValue(_searchEngine);
            _searchSummary.getDomNode().getAttributes().setNamedItem(searchSummAtt);
        }
        return _searchSummary;
    }

    /**
     * Create doc structure, populate features and modifications
     * @param modifications
     */
    public BasePepXmlWriter(MS2Modification[] modifications)
    {
        this();
        setModifications(modifications);
    }

    /**
     * setter for modifications
     * @param modifications
     */
    public void setModifications(MS2Modification[] modifications)
    {
        _modifications = modifications;
    }

    /**
     * setter for basename
     * @param baseName
     */
    public void setBaseName(String baseName)
    {
        _baseName = baseName;
    }

    /**
     * Add modifications to the xml output
     * We assume a reasonably small number of modifications.  No need to write them each out
     * individually and then dispose of the java object
     */
    protected void addModificationsToXML()
    {
        if (_modifications == null || _modifications.length == 0)
            return;
        for (MS2Modification modification : _modifications)
        {
            MsmsPipelineAnalysisDocument.MsmsPipelineAnalysis.MsmsRunSummary.SearchSummary.AminoacidModification
                xmlModification = getSearchSummary().addNewAminoacidModification();
            xmlModification.setAminoacid(modification.getAminoAcid());
            xmlModification.setMassdiff(Float.toString(modification.getMassDiff()));
            //lots of times, pepxml only carries around massdiff, not mass.  So this value is likely
            //to be bogus, i.e., 0.
            if (modification.getMass() > 0)
                xmlModification.setMass(modification.getMass());
            xmlModification.setVariable(modification.getVariable()? "Y" : "N");
            if (modification.getVariable() && modification.getSymbol() != null
                    && modification.getSymbol().length() > 0
                    && !("'".equals(modification.getSymbol())))
            {
                AaSymbolType.Enum xmlSymbol = AaSymbolType.Enum.forString(modification.getSymbol());
                //problems with " as a symbol.  xml doesn't like that.  No time to fix right now
                //For now, just not setting it.
                //TODO: carry forward " as a symbol correctly
                if (xmlSymbol != null)
                {
                    xmlModification.setSymbol(xmlSymbol);                    
                    _log.debug("Adding symbol for mod on var " + modification.getAminoAcid() + ".  getSymbol: " + modification.getSymbol() + ", xml Symbol: " + xmlSymbol);
                }
                else
                    _log.debug("Not adding symbol for null symbol.  Var=" + modification.getAminoAcid() + ", input symbol=" + modification.getSymbol());
            }         
        }
    }

    /**
     * Write out all features immediately
     * @param pw
     */
    protected abstract void writeSpectrumQueries(PrintWriter pw);

    /**
     * Adds an AnalysisResult representing the peptide prophet score
     * @param searchHit
     * @param pprophet
     * @param allNttProb
     * @param fval 0 is a sentinel value meaning absent
     */
    protected void addPeptideProphet(MsmsPipelineAnalysisDocument.MsmsPipelineAnalysis.MsmsRunSummary.SpectrumQuery.SearchResult.SearchHit searchHit,
                                               float pprophet, String allNttProb, float fval)
    {
        MsmsPipelineAnalysisDocument.MsmsPipelineAnalysis.MsmsRunSummary.SpectrumQuery.SearchResult.SearchHit.AnalysisResult analysisResult =
                searchHit.addNewAnalysisResult();
        analysisResult.setAnalysis("peptideprophet");
        Node domNode = analysisResult.getDomNode();
        Element childElement = domNode.getOwnerDocument().createElement("peptideprophet_result");
        childElement.setAttribute("probability", "" + pprophet);

        if (allNttProb == null)
            allNttProb = "(" + pprophet + "," + pprophet + "," + pprophet + ")";
        childElement.setAttribute("all_ntt_prob", allNttProb);
        domNode.appendChild(childElement);

        if (fval != 0)
        {
            Element searchScoreSummaryElement = domNode.getOwnerDocument().createElement("search_score_summary");
            childElement.appendChild(searchScoreSummaryElement);
            Element paramElem = domNode.getOwnerDocument().createElement("parameter");
            searchScoreSummaryElement.appendChild(paramElem);
            paramElem.setAttribute("name", "fval");
            paramElem.setAttribute("value", "" + fval);
        }
    }

    /**
     * Add a search score.  Doesn't make any attempt to prevent duplicates
     * @param searchHit
     * @param searchScoreName
     * @param searchScoreValue
     */
    protected void addSearchScore(MsmsPipelineAnalysisDocument.MsmsPipelineAnalysis.MsmsRunSummary.SpectrumQuery.SearchResult.SearchHit searchHit,
                                   String searchScoreName, String searchScoreValue)
    {
        NameValueType newSearchScore = searchHit.addNewSearchScore();
        newSearchScore.setName(searchScoreName);
        XmlAnySimpleType valueSimpleType = XmlAnySimpleType.Factory.newInstance();
        valueSimpleType.setStringValue(searchScoreValue);
        newSearchScore.setValue(valueSimpleType);
    }

    /**
     * Utility method to create a spectrumQuery, added to the first run summary
     * @param scanFirst
     * @param scanLast
     * @param charge
     * @return
     */
    protected MsmsPipelineAnalysisDocument.MsmsPipelineAnalysis.MsmsRunSummary.SpectrumQuery
        addSpectrumQuery(int scanFirst, int scanLast, int charge, int index)
    {
        MsmsPipelineAnalysisDocument.MsmsPipelineAnalysis.MsmsRunSummary.SpectrumQuery spectrumQuery =
                _firstRunSummary.addNewSpectrumQuery();
        spectrumQuery.setIndex(index);
        spectrumQuery.setStartScan(scanFirst);
        spectrumQuery.setEndScan(scanLast);
        spectrumQuery.setAssumedCharge(new BigInteger(Integer.toString(charge)));

        return spectrumQuery;
    }

    /**
     * Utility method for adding modified aminoacids
     * @param searchHit
     * @param modifiedAminoAcids
     */
    protected void addModifiedAminoAcidsToSearchHit(MsmsPipelineAnalysisDocument.MsmsPipelineAnalysis.MsmsRunSummary.SpectrumQuery.SearchResult.SearchHit searchHit,
                                                    List<ModifiedAminoAcid>[] modifiedAminoAcids)
    {
        if (modifiedAminoAcids != null)
        {
            MsmsPipelineAnalysisDocument.MsmsPipelineAnalysis.MsmsRunSummary.SpectrumQuery.SearchResult.SearchHit.ModificationInfo
               xmlBeansModInfo =searchHit.addNewModificationInfo();

            String peptideSequence = searchHit.getPeptide();
            StringBuffer modPeptideStringBuf = new StringBuffer();

            //modifiedAminoAcids.length guaranteed == peptideSequence.length();
            for (int i=0; i<modifiedAminoAcids.length; i++)
            {
                modPeptideStringBuf.append(peptideSequence.charAt(i));
                if (modifiedAminoAcids[i] == null)
                    continue;
                //there may be multiple modifications.  If so, too bad.  Keep the highest modification mass
                double greatestModMass = 0f;
                for (ModifiedAminoAcid mod : modifiedAminoAcids[i])
                {
                    MsmsPipelineAnalysisDocument.MsmsPipelineAnalysis.MsmsRunSummary.SpectrumQuery.SearchResult.SearchHit.ModificationInfo.ModAminoacidMass
                            xmlBeansModAminoacidMass = xmlBeansModInfo.addNewModAminoacidMass();
                    //0-based to 1-based array indexing conversion
                    xmlBeansModAminoacidMass.setPosition(BigInteger.valueOf(i + 1));
                    xmlBeansModAminoacidMass.setMass(mod.getMass());
                    if (mod.getMass() > greatestModMass)
                        greatestModMass = mod.getMass();
                }
                if (greatestModMass > 0f)
                    modPeptideStringBuf.append("[" + Math.round(greatestModMass) + "]");
                    
            }
            xmlBeansModInfo.setModifiedPeptide(modPeptideStringBuf.toString());
        }
    }

    /**
     * Better only call once
     * @param databasePath
     */
    public void setSearchDatabase(String databasePath)
    {
        MsmsPipelineAnalysisDocument.MsmsPipelineAnalysis.MsmsRunSummary.SearchSummary.SearchDatabase searchDatabase =
                getSearchSummary().addNewSearchDatabase();
        searchDatabase.setLocalPath(databasePath);
    }

    /**
     * Better only call once.
     * TODO: un-hardcode trypsin
     * @param maxCleavages
     * @param        minTermini
     */
    public void setSearchConstraints(int maxCleavages, int minTermini)
    {
        MsmsPipelineAnalysisDocument.MsmsPipelineAnalysis.MsmsRunSummary.SearchSummary.EnzymaticSearchConstraint
                enzSearchConstraint = getSearchSummary().addNewEnzymaticSearchConstraint();
        enzSearchConstraint.setMaxNumInternalCleavages(BigInteger.valueOf(maxCleavages));
        enzSearchConstraint.setMinNumberTermini(BigInteger.valueOf(minTermini));
        enzSearchConstraint.setEnzyme("trypsin");
    }

    protected void preWrite()
    {
        if (_baseName != null)
            _firstRunSummary.setBaseName(_baseName);
    }


    /**
     * Write out the full document, with all modifications and features, to a file
     * @param file
     * @throws IOException
     */
    public void write(File file) throws IOException
    {
        preWrite();

        //add a sentinel node that tells us where to split the document to insert features and modifications,
        //which, conveniently, is the same place for both
        Node runSummaryNode = _firstRunSummary.getDomNode();

        //if there isn't an explicitly defined base string for all spectra, create it from
        //the first part of the filename (up to the /last/ "."), with a "." at the end
        if ("".equals(_spectrumBaseString))
        {
            _spectrumBaseString = file.getName();
            if (_spectrumBaseString.contains("."))
                _spectrumBaseString = _spectrumBaseString.substring(0, _spectrumBaseString.lastIndexOf("."));
            _spectrumBaseString = _spectrumBaseString + ".";
        }

        //add required namespace element
//        Element namespaceElement = runSummaryNode.getOwnerDocument().createElement("xmlns");
        Attr nsAttr = _xmlBeansAnalysis.getDomNode().getOwnerDocument().createAttribute("xmlns");
        nsAttr.setValue("http://regis-web.systemsbiology.net/pepXML");
//        namespaceElement.setNodeValue("http://regis-web.systemsbiology.net/pepXML");
        _xmlBeansAnalysis.getDomNode().getAttributes().setNamedItem(nsAttr);


        Node featureLocationNode = runSummaryNode.getOwnerDocument().createElement("SENTINEL_FEATURE_LOCATION");

        runSummaryNode.appendChild(featureLocationNode);

        addModificationsToXML();

        //create and break up the xml that defines the document structure
        String documentShell = _xmlBeansPepXmlDoc.xmlText(_optionsForPrinting);
        //By default, namespace stuff will be written to every opening and closing tag.  This gives
        //some "parsers" heartburn
        documentShell = documentShell.replaceAll("<pep:","<");
        documentShell = documentShell.replaceAll("</pep:","</");

        String[] halves = documentShell.split("<SENTINEL_FEATURE_LOCATION[^\\/]*\\/>");
        if (halves.length != 2)
        {
            _log.error("Failed to create document shell for writing");
            return;
        }

        _documentPrefix = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + halves[0];
        _documentPostscript = halves[1];

        //remove our dummy node
        runSummaryNode.removeChild(featureLocationNode);


        PrintWriter pw = new PrintWriter(file);
        pw.print(_documentPrefix);

        writeSpectrumQueries(pw);
        pw.print(_documentPostscript);
        pw.flush();
    }


    public String getSpectrumBaseString()
    {
        return _spectrumBaseString;
    }

    public void setSpectrumBaseString(String spectrumBaseString)
    {
        this._spectrumBaseString = spectrumBaseString;
    }


    public String get_precursorMassType()
    {
        return _precursorMassType;
    }

    public void set_precursorMassType(String _precursorMassType)
    {
        this._precursorMassType = _precursorMassType;
    }


    public String get_searchEngine()
    {
        return _searchEngine;
    }

    public void set_searchEngine(String _searchEngine)
    {
        this._searchEngine = _searchEngine;
    }
}
