/*
 * Copyright (c) 2010-2013 LabKey Corporation
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
package org.labkey.search.umls;

import java.util.HashMap;
import java.util.Map;

/**
 * User: matthewb
 * Date: Mar 10, 2010
 * Time: 10:47:59 AM
 */
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
    final static Map<String, SemanticTree> semanticTreeMap = new HashMap<>();

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
