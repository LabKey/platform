package org.labkey.search.umls;

import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.*;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.webdav.SimpleDocumentResource;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;
import org.apache.commons.lang.StringUtils;
import org.json.JSONObject;

import java.io.File;
import java.io.Writer;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Mar 4, 2010
 * Time: 1:28:53 PM
 */
public class UmlsController extends SpringActionController
{
    private static final SpringActionController.DefaultActionResolver _actionResolver = new SpringActionController.DefaultActionResolver(UmlsController.class);

    public UmlsController() throws Exception
    {
        setActionResolver(_actionResolver);
    }

    @RequiresSiteAdmin
    public class DebugAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object form, BindException errors) throws Exception
        {
            RRFLoader l = new RRFLoader(new File("/Users/Matthew/Desktop/META"));       // "/Volumes/STORAGE/UMLS/2009AB/META"));
            Iterator<RRFLoader.SemanticType> types = l.getTypes(null);
            TreeMap<String, String> map = new TreeMap<String, String>();
            while (types.hasNext())
            {
                RRFLoader.SemanticType t = types.next();
                map.put(t.STN, t.STY);
            }

            Writer out = getViewContext().getResponse().getWriter();
            out.write("<pre>\n");
            for (Map.Entry<String, String> e : map.entrySet())
            {
                out.write(e.getKey());
                out.write("\t");
                out.write(e.getValue());
                out.write("\n");
            }
            out.write("</pre>\n");
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }


    public static final SearchService.SearchCategory umlsCategory = new SearchService.SearchCategory("umls", "UMLS Concepts");
    public static PollingUtil.PollKey umlsIndexStatusKey = null;

    
    @RequiresSiteAdmin
    public class IndexAction extends FormViewAction
    {
        public PollingUtil.PollKey _key = null;
        
        public void validateCommand(Object target, Errors errors)
        {
        }

        public ModelAndView getView(Object o, boolean reshow, BindException errors) throws Exception
        {
            if (_key == null)
                _key = umlsIndexStatusKey;
            return new JspView<IndexAction>(UmlsController.class,"index.jsp",this,errors);
        }

        public URLHelper getSuccessURL(Object o)
        {
            return null;
        }

        public boolean handlePost(Object o, BindException errors) throws Exception
        {
            final RRFLoader l = new RRFLoader(new File("/Users/Matthew/Desktop/META"));       // "/Volumes/STORAGE/UMLS/2009AB/META"));

            Runnable indexUMLS = new Runnable(){
                public void run()
                {
                    try
                    {
                        SearchService ss = ServiceRegistry.get(SearchService.class);
                        String sharedId = ContainerManager.getSharedContainer().getId();

                        Iterator<RRFLoader.Definition> defs = l.getDefinitions(null); // new Filter<RRFLoader.Definition>(){ public boolean accept(RRFLoader.Definition def) { return !"Y".equals(def.SUPPRESS); } });
                        Iterator<RRFLoader.SemanticType> types = l.getTypes(null);
                        Iterator<RRFLoader.ConceptName> names = l.getNames(new Filter<RRFLoader.ConceptName>()
                        {
                            public boolean accept(RRFLoader.ConceptName c)
                            {
                                return "ENG".equals(c.LAT); // && !"Y".equals(c.SUPPRESS);
                            }
                        });

                        CaseInsensitiveHashSet nameSet = new CaseInsensitiveHashSet();
                        ArrayList<NavTree> links = new ArrayList<NavTree>();

                        RRFLoader.MergeIterator concept = new RRFLoader.MergeIterator(names, defs, types);
                        int count = 0;
                        while (concept.hasNext())
                        {
                            if (Thread.interrupted())
                                return;
                            String CUI = null;
                            String STR = null;
                            String preferredSTR = null;

                            nameSet.clear();
                            links.clear();

                            StringBuilder sbSemanticTypes = new StringBuilder();
                            StringBuilder sbDefinition = new StringBuilder();
                            ArrayList list = concept.next();

                            for (Object o : list)
                            {
                                if (o instanceof RRFLoader.Definition)
                                {
                                    RRFLoader.Definition d = (RRFLoader.Definition) o;
                                    if ("Y".equals(d.SUPPRESS))
                                        continue;
                                    CUI = d.CUI;
                                    sbDefinition.append(d.DEF).append("\n");
                                }

                                if (o instanceof RRFLoader.ConceptName)
                                {
                                    RRFLoader.ConceptName n = (RRFLoader.ConceptName) o;
                                    if (!"Y".equals(n.SUPPRESS) && !StringUtils.isEmpty(n.STR))
                                    {
                                        if (null == preferredSTR && "Y".equals(n.ISPREF))
                                            preferredSTR = n.STR;
                                        if (null == STR)
                                            STR = n.STR;
                                    }
                                }

                                if (o instanceof RRFLoader.SemanticType)
                                {
                                    RRFLoader.SemanticType s = (RRFLoader.SemanticType) o;
                                    CUI = s.CUI;
                                    sbSemanticTypes.append(s.STN).append(" ").append(s.STY).append("\n");

                                    if (SemanticTree.geographicArea.STN.equals(s.STN) && preferredSTR != null)
                                    {
                                        links.add(new NavTree("map", "http://maps.google.com/maps?q=" + PageFlowUtil.encode(preferredSTR)));
                                    }
                                }
                            }

                            String title = CUI + " " + StringUtils.defaultString(preferredSTR, STR);
                            String body = sbSemanticTypes.toString() + "\n" + sbDefinition.toString();

                            Map map = new HashMap();
                            map.put(SearchService.PROPERTY.categories.toString(), umlsCategory.toString());
                            map.put(SearchService.PROPERTY.displayTitle.toString(), title);
                            if (!links.isEmpty())
                            {
                                String nav = NavTree.toJS(links, null, false).toString();
                                map.put(SearchService.PROPERTY.navtrail.toString(), nav);
                            }
                            SimpleDocumentResource r = new SimpleDocumentResource(
                                    new Path("CUI", CUI),
                                    "umls:" + CUI,
                                    sharedId,
                                    "text/plain", body.getBytes(),
                                    new ActionURL("umls", "concept", sharedId).addParameter("cui", CUI),
                                    map
                            );

                            count++;
                            if (0 == (count % 1000))
                            {
                                JSONObject o = new JSONObject();
                                o.put("count",count);
                                o.put("estimate",2178000); // umls 2009
                                _key.setJson(o);
                                if (ss.isBusy())
                                    try {ss.waitForIdle();}catch(InterruptedException x){};
                            }
                            ss.defaultTask().addResource(r, SearchService.PRIORITY.item);
                        }
                        JSONObject o = new JSONObject();
                        o.put("count",count);
                        o.put("done",true);
                        _key.setJson(o);
                    }
                    finally
                    {
                        umlsIndexStatusKey = null; // task done
                    }
                }
            };
            Thread t = new Thread(indexUMLS);
            _key = PollingUtil.createKey(null, null, t);
            umlsIndexStatusKey = _key;
            t.start();

            return true;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }


    private static String h(String s)
    {
        return PageFlowUtil.filter(s);
    }


    static private String format(Object o)
    {
        if (o instanceof RRFLoader.Definition)
        {
            RRFLoader.Definition d = (RRFLoader.Definition) o;
            if ("Y".equals(d.SUPPRESS))
                return "<strike>" + h(d.toString()) + "</strike>";
            return h(d.toString());
        }

        if (o instanceof RRFLoader.ConceptName)
        {
            RRFLoader.ConceptName n = (RRFLoader.ConceptName) o;
            if ("Y".equals(n.SUPPRESS))
                return "<strike>" + h(n.toString()) + "</strike>";
            if ("Y".equals(n.ISPREF))
                return "<b>" + h(n.toString()) + "</b>";
            return h(n.toString());
        }

        if (o instanceof RRFLoader.SemanticType)
        {
            RRFLoader.SemanticType s = (RRFLoader.SemanticType) o;
            return h(s.toString());
        }

        return h(o.toString());
    }


    enum SemanticTree
    {
        entity("A", "Entity"),
        physicalObject("A1", "Physical Object"),
        organism("A1.1", "Organism"),
        plant("A1.1.1", "Plant"),
        alga("A1.1.1.1", "Alga"),
        fungus("A1.1.2", "Fungus"),
        virus("A1.1.3", "Virus"),
        ricksettsiaOrChlamydia("A1.1.4", "Rickettsia or Chlamydia"),
        bacterium("A1.1.5", "Bacterium"),
        archaeon("A1.1.6", "Archaeon"),
        animal("A1.1.7", "Animal"),
        invertibrate("A1.1.7.1", "Invertebrate"),
        vertebrate("A1.1.7.2", "Vertebrate"),
        amphibian("A1.1.7.2.1", "Amphibian"),
        bird("A1.1.7.2.2", "Bird"),
        fish("A1.1.7.2.3", "Fish"),
        reptile("A1.1.7.2.4", "Reptile"),
        mammal("A1.1.7.2.5", "Mammal"),
        human("A1.1.7.2.5.1", "Human"),
        anatomicalStructure("A1.2", "Anatomical Structure"),
        embryonicStructure("A1.2.1", "Embryonic Structure"),
        anatomicalAbnormality("A1.2.2", "Anatomical Abnormality"),
        congenitalAbnormality("A1.2.2.1", "Congenital Abnormality"),
        acquiredAbnormality("A1.2.2.2", "Acquired Abnormality"),
        fullyFormedAnatomicalStructure("A1.2.3", "Fully Formed Anatomical Structure"),
        bodyPart("A1.2.3.1", "Body Part, Organ, or Organ Component"),
        tissue("A1.2.3.2", "Tissue"),
        cell("A1.2.3.3", "Cell"),
        cellComponent("A1.2.3.4", "Cell Component"),
        geneOrGenome("A1.2.3.5", "Gene or Genome"),
        manufacturedObject("A1.3", "Manufactured Object"),
        medicalDevice("A1.3.1", "Medical Device"),
        drugDeliveryDevice("A1.3.1.1", "Drug Delivery Device"),
        researchDevice("A1.3.2", "Research Device"),
        clinicalDrug("A1.3.3", "Clinical Drug"),
        substance("A1.4", "Substance"),
        chemical("A1.4.1", "Chemical"),
        chemicalViewedFunctionally("A1.4.1.1", "Chemical Viewed Functionally"),
        pharmacologicSubstance("A1.4.1.1.1", "Pharmacologic Substance"),
        antibiotic("A1.4.1.1.1.1", "Antibiotic"),
        biomedicalOrDentalMaterial("A1.4.1.1.2", "Biomedical or Dental Material"),
        biologicallyActiveSubstance("A1.4.1.1.3", "Biologically Active Substance"),
        neuroreactiveSubstanceOrBiogenicAmine("A1.4.1.1.3.1", "Neuroreactive Substance or Biogenic Amine"),
        hormone("A1.4.1.1.3.2", "Hormone"),
        enzyme("A1.4.1.1.3.3", "Enzyme"),
        vitamin("A1.4.1.1.3.4", "Vitamin"),
        immunologicFactor("A1.4.1.1.3.5", "Immunologic Factor"),
        receptor("A1.4.1.1.3.6", "Receptor"),
        indicatorReagentOrDiagnosticAid("A1.4.1.1.4", "Indicator, Reagent, or Diagnostic Aid"),
        hazardousOrPoisonousSubstance("A1.4.1.1.5", "Hazardous or Poisonous Substance"),
        chemicalViewedStructurally("A1.4.1.2", "Chemical Viewed Structurally"),
        organicChemical("A1.4.1.2.1", "Organic Chemical"),
        nucleicAcisNucleosideorNuceleotide("A1.4.1.2.1.5", "Nucleic Acid, Nucleoside, or Nucleotide"),
        organophosphorusCompound("A1.4.1.2.1.6", "Organophosphorus Compound"),
        aminoAcidPeptideOrProtein("A1.4.1.2.1.7", "Amino Acid, Peptide, or Protein"),
        carbohydrate("A1.4.1.2.1.8", "Carbohydrate"),
        lipid("A1.4.1.2.1.9", "Lipid"),
        steroid("A1.4.1.2.1.9.1", "Steroid"),
        eicosanoid("A1.4.1.2.1.9.2", "Eicosanoid"),
        inorganicChemical("A1.4.1.2.2", "Inorganic Chemical"),
        elementIonOrIsotope("A1.4.1.2.3", "Element, Ion, or Isotope"),
        bodySubstance("A1.4.2", "Body Substance"),
        food("A1.4.3", "Food"),
        conceptualEntity("A2", "Conceptual Entity"),
        ideaOrConcept("A2.1", "Idea or Concept"),
        temporalConcept("A2.1.1", "Temporal Concept"),
        qualitativeConcept("A2.1.2", "Qualitative Concept"),
        quantitativeConcept("A2.1.3", "Quantitative Concept"),
        functionalConcept("A2.1.4", "Functional Concept"),
        bodySystem("A2.1.4.1", "Body System"),
        spatialConcept("A2.1.5", "Spatial Concept"),
        bodySpaceOrJunction("A2.1.5.1", "Body Space or Junction"),
        bodyLocationOrRegion("A2.1.5.2", "Body Location or Region"),
        molecularSequence("A2.1.5.3", "Molecular Sequence"),
        NucleotideSequence("A2.1.5.3.1", "Nucleotide Sequence"),
        aminoAcidSequence("A2.1.5.3.2", "Amino Acid Sequence"),
        carbohydrateSequence("A2.1.5.3.3", "Carbohydrate Sequence"),
        geographicArea("A2.1.5.4", "Geographic Area"),
        finding("A2.2", "Finding"),
        laboratoryOrTestResult("A2.2.1", "Laboratory or Test Result"),
        signOrSymptom("A2.2.2", "Sign or Symptom"),
        organismAttribute("A2.3", "Organism Attribute"),
        clinicalAttribute("A2.3.1", "Clinical Attribute"),
        intellectualProduct("A2.4", "Intellectual Product"),
        classification("A2.4.1", "Classification"),
        regulationOrLaw("A2.4.2", "Regulation or Law"),
        language("A2.5", "Language"),
        occupationOrDiscipline("A2.6", "Occupation or Discipline"),
        biomedicalOccupationOrDiscipline("A2.6.1", "Biomedical Occupation or Discipline"),
        organization("A2.7", "Organization"),
        healthCareRelatedOrganization("A2.7.1", "Health Care Related Organization"),
        professionalSociety("A2.7.2", "Professional Society"),
        selfHelpOrReliefOrganization("A2.7.3", "Self-help or Relief Organization"),
        groupAttribute("A2.8", "Group Attribute"),
        group("A2.9", "Group"),
        professionalOrOccupationalGroup("A2.9.1", "Professional or Occupational Group"),
        populationGroup("A2.9.2", "Population Group"),
        familyGroup("A2.9.3", "Family Group"),
        ageGroup("A2.9.4", "Age Group"),
        patientOrDisabledGroup("A2.9.5", "Patient or Disabled Group"),
        event("B", "Event"),
        activity("B1", "Activity"),
        behavior("B1.1", "Behavior"),
        socialBehavior("B1.1.1", "Social Behavior"),
        individualBehavior("B1.1.2", "Individual Behavior"),
        dailyOrRecreationalActivity("B1.2", "Daily or Recreational Activity"),
        occupationalActivity("B1.3", "Occupational Activity"),
        healthCareActivity("B1.3.1", "Health Care Activity"),
        laboratoryProcedure("B1.3.1.1", "Laboratory Procedure"),
        diagnosticProcedure("B1.3.1.2", "Diagnostic Procedure"),
        therapeuticOrPreventativeProcedure("B1.3.1.3", "Therapeutic or Preventive Procedure"),
        researchActivity("B1.3.2", "Research Activity"),
        molecularBiologyResearchTechnique("B1.3.2.1", "Molecular Biology Research Technique"),
        governmentalOrRegulatoryActivity("B1.3.3", "Governmental or Regulatory Activity"),
        educationalActivity("B1.3.4", "Educational Activity"),
        machineActivity("B1.4", "Machine Activity"),
        phenomenonOrProcess("B2", "Phenomenon or Process"),
        humanCausedPhenomenonOrProcess("B2.1", "Human-caused Phenomenon or Process"),
        environmentalEffectOfHumans("B2.1.1", "Environmental Effect of Humans"),
        naturalPhenomenonorProcess("B2.2", "Natural Phenomenon or Process"),
        biologicFunction("B2.2.1", "Biologic Function"),
        physiologicFunction("B2.2.1.1", "Physiologic Function"),
        organismFunction("B2.2.1.1.1", "Organism Function"),
        mentalProcess("B2.2.1.1.1.1", "Mental Process"),
        organOrTissueFunction("B2.2.1.1.2", "Organ or Tissue Function"),
        cellFunction("B2.2.1.1.3", "Cell Function"),
        molecularFunction("B2.2.1.1.4", "Molecular Function"),
        geneticFunction("B2.2.1.1.4.1", "Genetic Function"),
        pathologicFunction("B2.2.1.2", "Pathologic Function"),
        diseaseOrSyndrome("B2.2.1.2.1", "Disease or Syndrome"),
        mentalOrBehavioralDysfunction("B2.2.1.2.1.1", "Mental or Behavioral Dysfunction"),
        neoplasticProcess("B2.2.1.2.1.2", "Neoplastic Process"),
        cellOrMolecularDysfunction("B2.2.1.2.2", "Cell or Molecular Dysfunction"),
        experimentalModelOfDisease("B2.2.1.2.3", "Experimental Model of Disease"),
        injuryOrPoisoning("B2.3", "Injury or Poisoning");

        final String STN;
        final String STY;
        SemanticTree _parent;
        final static Map<String,SemanticTree> semanticTreeMap = new HashMap<String, SemanticTree>();

        SemanticTree(String stn, String sty)
        {
            STN = stn;
            STY = sty;
        }

        static
        {
            for (SemanticTree t : SemanticTree.values())
            {
                semanticTreeMap.put(t.STN,t);
                semanticTreeMap.put(t.STY,t);
                semanticTreeMap.put(t.STY.toUpperCase(),t);
            }
            for (SemanticTree t : SemanticTree.values())
            {
                String stn = t.STN;
                if (stn.length() <= 1)
                    continue;
                int dot = stn.lastIndexOf('.');
                String parent = (dot==-1 || stn.length()==2) ? stn.substring(0,1) : stn.substring(0,dot-1);
                t._parent = find(parent);
            }
        }

        public static SemanticTree find(String a)
        {
            SemanticTree t = semanticTreeMap.get(a.toUpperCase());
            return null==t ? semanticTreeMap.get(a.toUpperCase()) : t;
        }

        public SemanticTree parent()
        {
            return _parent;
        }
    }
}