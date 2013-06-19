/*
 * Copyright (c) 2007-2012 LabKey Corporation
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

package org.labkey.study.designer;

import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlError;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;
import gwt.client.org.labkey.study.designer.client.model.*;
import org.labkey.study.xml.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * User: Mark Igra
 * Date: Feb 14, 2007
 * Time: 10:30:36 PM
 */
public class XMLSerializer
{
    private static Logger _log = Logger.getLogger(XMLSerializer.class);

    public static GWTStudyDefinition fromXML(String xml)
    {
        return fromXML(xml, null);
    }

    public static GWTStudyDefinition fromXML(String xml, GWTStudyDefinition template)
    {

        StudyDesignDocument doc;
        try
        {
            doc = StudyDesignDocument.Factory.parse(xml);
        } catch (XmlException e)
        {
            _log.error(e);
            throw new RuntimeException(e);
        }
        
        StudyDesign xdesign = doc.getStudyDesign();
        assert validate(doc);
        
        GWTStudyDefinition def = new GWTStudyDefinition();
        def.setStudyName(xdesign.getName());
        def.setGrant(xdesign.getGrantName());
        def.setInvestigator(xdesign.getInvestigator());
        def.setAnimalSpecies(xdesign.getAnimalSpecies());
        if (xdesign.isSetDescription())
            def.setDescription(xdesign.getDescription());

        if (xdesign.isSetSampleTypes())
        {
            SampleType[] xsampleTypes = xdesign.getSampleTypes().getSampleTypeArray();
            List<GWTSampleType> gsampleTypes = new ArrayList<>(xsampleTypes.length);
            for (SampleType sampleType : xsampleTypes)
                gsampleTypes.add(new GWTSampleType(sampleType.getName(), sampleType.getPrimaryType(), sampleType.getCode()));
            def.setSampleTypes(gsampleTypes);
        }

        List<GWTImmunogen> immunogens = new ArrayList<>();
        for (Immunogen immunogen : xdesign.getImmunogens().getImmunogenArray())
        {
            GWTImmunogen gimmunogen = new GWTImmunogen(immunogen.getName(), immunogen.getDose(), immunogen.getType(), immunogen.getAdmin());
            List<GWTAntigen> antigens = new ArrayList<>();
            for (Antigen antigen : immunogen.getAntigens().getAntigenArray())
                antigens.add(new GWTAntigen(antigen.getName(), antigen.getGene(), antigen.getSubtype(), antigen.getGenBankId(), antigen.getSequence()));
            gimmunogen.setAntigens(antigens);
            immunogens.add(gimmunogen);
        }
        def.setImmunogens(immunogens);

        List<GWTAdjuvant> adjuvants = new ArrayList<>();
        for (Adjuvant adjuvant : xdesign.getAdjuvants().getAdjuvantArray())
            adjuvants.add(new GWTAdjuvant(adjuvant.getName(), adjuvant.getDose(), adjuvant.getAdmin()));
        def.setAdjuvants(adjuvants);

        if (null  != template)
        {
            List<GWTAssayDefinition> validatedAssays = new ArrayList<>();
            for (GWTAssayDefinition templateAssay : (List<GWTAssayDefinition>) template.getAssays())
            {
                GWTAssayDefinition copy = new GWTAssayDefinition(templateAssay);
                copy.setLocked(true);
                validatedAssays.add(copy);
            }
            def.setAssays(validatedAssays);
        }
        
        for (AssayDefinition xassayDef : xdesign.getAssayDefinitions().getAssayDefinitionArray())
        {
            if (null != findAssayDefinition(xassayDef.getName(), def.getAssays()))
                continue;

            //TODO: If not in template, mark this as "deprecated" since no longer in global list
            GWTAssayDefinition gassayDef = new GWTAssayDefinition();
            gassayDef.setDefaultMeasure(createGWTSampleMeasure(xassayDef.getSampleMeasure(), def));
            gassayDef.setDefaultLab(xassayDef.getDefaultLab());
            gassayDef.setName(xassayDef.getName());
            if (null != xassayDef.getLabs())
            {
                Lab[] labs = xassayDef.getLabs().getLabArray();
                String[] labNames = new String[labs.length];
                for (int i = 0; i < labs.length; i++)
                    labNames[i] = labs[i].getName();
                gassayDef.setLabs(labNames);
            }

            def.getAssays().add(gassayDef);
        }

        //TODO: XML for cohort descriptions
        for (Cohort cohort : xdesign.getCohorts().getCohortArray())
            def.getGroups().add(new GWTCohort(cohort.getName(), null, cohort.getCount()));

        GWTAssaySchedule gAssaySchedule = new GWTAssaySchedule();
        AssaySchedule xAssaySchedule = xdesign.getAssaySchedule();
        if (null != xAssaySchedule.getDescription())
            gAssaySchedule.setDescription(xAssaySchedule.getDescription());

        AssaySchedule.Assays assays = xAssaySchedule.getAssays();
        if (null != assays)
            for (AssayRef ref : assays.getAssayRefArray())
            {
                GWTAssayDefinition src = findAssayDefinition(ref.getAssayName(), def.getAssays());
                if (null == src) //This should not happen now that deletions to assay list are reflected in assay schedule
                    continue;
                GWTAssayDefinition gad = src;
                gad.setDefaultLab(ref.getLab());
                gAssaySchedule.getAssays().add(gad);
            }

        //Note: timepoint storage is somewhat redundant for backward compatibility
        //The complete list of timepoints is retrieved here to support empty schdules
        //Assay events serialize their entire timepoint descriptor as well
        AssaySchedule.Timepoints timepoints = xAssaySchedule.getTimepoints();
        if (null != timepoints)
            for (Timepoint tp : timepoints.getTimepointArray())
            {
                GWTTimepoint.Unit unit = GWTTimepoint.Unit.fromString(tp.getDisplayUnit());
                GWTTimepoint gtp = new GWTTimepoint(tp.getName(), tp.getDays()/unit.daysPerUnit, unit);
                gAssaySchedule.addTimepoint(gtp);
            }

        for (AssayEvent evt : xAssaySchedule.getAssayEventArray())
        {
            Timepoint tp = evt.getTimepoint();
            GWTTimepoint.Unit unit = GWTTimepoint.Unit.fromString(tp.getDisplayUnit());
            GWTTimepoint gtp = new GWTTimepoint(tp.getName(), tp.getDays()/unit.daysPerUnit, unit);
            GWTAssayDefinition gad = findAssayDefinition(evt.getAssayName(), gAssaySchedule.getAssays());
            if (null == gad)
            {
                gad = findAssayDefinition(evt.getAssayName(), def.getAssays());
                gAssaySchedule.addAssay(gad);
            }
            gAssaySchedule.setAssayPerformed(gad, gtp, new GWTAssayNote(createGWTSampleMeasure(evt.getSampleMeasure(), def)));
        }
        def.setAssaySchedule(gAssaySchedule);

        GWTImmunizationSchedule gImmunizationSchedule = new GWTImmunizationSchedule();
        ImmunizationSchedule.Timepoints immunizationTimepoints = xdesign.getImmunizationSchedule().getTimepoints();
        if (null != immunizationTimepoints)
            for (Timepoint tp : immunizationTimepoints.getTimepointArray())
            {
                GWTTimepoint.Unit unit = GWTTimepoint.Unit.fromString(tp.getDisplayUnit());
                GWTTimepoint gtp = new GWTTimepoint(tp.getName(), tp.getDays()/unit.daysPerUnit, unit);
                gImmunizationSchedule.addTimepoint(gtp);
            }

        for (ImmunizationEvent evt : xdesign.getImmunizationSchedule().getImmunizationEventArray())
        {

            Timepoint tp = evt.getTimepoint();
            GWTTimepoint.Unit unit = GWTTimepoint.Unit.fromString(tp.getDisplayUnit());
            GWTTimepoint gtp = new GWTTimepoint(tp.getName(), tp.getDays() / unit.daysPerUnit, GWTTimepoint.Unit.fromString(tp.getDisplayUnit()));
            GWTImmunization immunization = new GWTImmunization();
            for (ImmunogenRef immunogenRef : evt.getImmunization().getImmunogenRefArray())
            {
                GWTImmunogen gImmunogen = findImmunogen(immunogenRef.getName(), def.getImmunogens());
                if (null != gImmunogen)
                    immunization.immunogens.add(gImmunogen);
            }
            for (AdjuvantRef adjuvantRef : evt.getImmunization().getAdjuvantRefArray())
            {
                GWTAdjuvant gAdjuvant = findAdjuvant(adjuvantRef.getName(), def.getAdjuvants());
                if (null != gAdjuvant)
                    immunization.adjuvants.add(gAdjuvant);
            }
            String groupName = evt.getGroupName();
            gImmunizationSchedule.setImmunization(findCohort(groupName, def.getGroups()), gtp, immunization);
        }
        def.setImmunizationSchedule(gImmunizationSchedule);

        return def;
    }

    public static StudyDesignDocument toXML(GWTStudyDefinition def)
    {
        try
        {
            StudyDesignDocument doc = StudyDesignDocument.Factory.newInstance();
            StudyDesign x = doc.addNewStudyDesign();
            x.setGrantName(def.getGrant());
            x.setInvestigator(def.getInvestigator());
            x.setName(def.getStudyName());
            x.setAnimalSpecies(def.getAnimalSpecies());
            if (null != def.getDescription())
                x.setDescription(def.getDescription());

            StudyDesign.SampleTypes xSampleTypes = x.addNewSampleTypes();
            for (GWTSampleType gsampleType : (List<GWTSampleType>) def.getSampleTypes())
            {
                SampleType xsampleType = xSampleTypes.addNewSampleType();
                xsampleType.setCode(gsampleType.getShortCode());
                xsampleType.setName(gsampleType.getName());
                xsampleType.setPrimaryType(gsampleType.getPrimaryType());
            }

            StudyDesign.Immunogens immunogens = x.addNewImmunogens();
            for (int i = 0; i < def.getImmunogens().size(); i++)
            {
                GWTImmunogen gImmunogen = (GWTImmunogen) def.getImmunogens().get(i);
                Immunogen xImmunogen = immunogens.addNewImmunogen();
                xImmunogen.setAdmin(gImmunogen.getAdmin());
                xImmunogen.setName(gImmunogen.getName());
                xImmunogen.setDose(gImmunogen.getDose());
                xImmunogen.setType(gImmunogen.getType());
                Immunogen.Antigens antigens = xImmunogen.addNewAntigens();
                for (Object o : gImmunogen.getAntigens())
                {
                    GWTAntigen gAntigen = (GWTAntigen) o;
                    Antigen antigen = antigens.addNewAntigen();
                    antigen.setGene(gAntigen.getGene());
                    antigen.setName(gAntigen.getName());
                    if (null != gAntigen.getSequence())
                        antigen.setSequence(gAntigen.getSequence());
                    if (null != gAntigen.getGenBankId())
                        antigen.setGenBankId(gAntigen.getGenBankId());
                    antigen.setSubtype(gAntigen.getSubtype());
                }
            }

            StudyDesign.Adjuvants adjuvants = x.addNewAdjuvants();
            for (int i = 0; i < def.getAdjuvants().size(); i++)
            {
                GWTAdjuvant gAdjuvant = (GWTAdjuvant) def.getAdjuvants().get(i);
                Adjuvant xImmunogen = adjuvants.addNewAdjuvant();
                xImmunogen.setAdmin(gAdjuvant.admin);
                xImmunogen.setName(gAdjuvant.getName());
                xImmunogen.setDose(gAdjuvant.getDose());
            }


            StudyDesign.Cohorts cohorts = x.addNewCohorts();
            for (int i = 0; i < def.getGroups().size(); i++)
            {
                GWTCohort gwtCohort = (GWTCohort) def.getGroups().get(i);
                Cohort cohort = cohorts.addNewCohort();
                cohort.setName(gwtCohort.getName());
                cohort.setCount(gwtCohort.getCount());
            }

            StudyDesign.AssayDefinitions assayDefinitions = x.addNewAssayDefinitions();
            for (GWTAssayDefinition gAssayDefinition : (List<GWTAssayDefinition>) def.getAssays())
            {
                AssayDefinition xAssayDefinition = assayDefinitions.addNewAssayDefinition();
                xAssayDefinition.setName(gAssayDefinition.getName());
                xAssayDefinition.setSampleMeasure(createSampleMeasure(gAssayDefinition.getDefaultMeasure()));
                xAssayDefinition.setDefaultLab(gAssayDefinition.getDefaultLab());
                AssayDefinition.Labs labs = xAssayDefinition.addNewLabs();
                if (null != gAssayDefinition.getLabs())
                    for (String labName : gAssayDefinition.getLabs())
                        labs.addNewLab().setName(labName);
            }

            ImmunizationSchedule xImmunizationSchedule = x.addNewImmunizationSchedule();
            GWTImmunizationSchedule gSchedule = def.getImmunizationSchedule();

            ImmunizationSchedule.Timepoints timepointsElem = xImmunizationSchedule.addNewTimepoints();
            List<Timepoint> immunizationTimepoints = new ArrayList<>();
            for (GWTTimepoint gtp : gSchedule.getTimepoints())
                immunizationTimepoints.add(createTimepoint(gtp));
            timepointsElem.setTimepointArray(immunizationTimepoints.toArray(new Timepoint[immunizationTimepoints.size()]));

            for (GWTCohort gCohort : (List<GWTCohort>) def.getGroups())
            {
                for (GWTTimepoint gtp : (List<GWTTimepoint>) gSchedule.getTimepoints())
                {
                    GWTImmunization gImmunization = gSchedule.getImmunization(gCohort, gtp);
                    if (null != gImmunization)
                    {
                        ImmunizationEvent evt = xImmunizationSchedule.addNewImmunizationEvent();
                        Immunization immunization = evt.addNewImmunization();
                        for (GWTImmunogen gImmunogen : (List<GWTImmunogen>) gImmunization.immunogens)
                            immunization.addNewImmunogenRef().setName(gImmunogen.getName());

                        for (GWTAdjuvant gAdjuvant : (List<GWTAdjuvant>) gImmunization.adjuvants)
                            immunization.addNewAdjuvantRef().setName(gAdjuvant.getName());

                        evt.setGroupName(gCohort.getName());
                        evt.setTimepoint(createTimepoint(gtp));
                    }
                }
            }

            AssaySchedule xAssaySchedule = x.addNewAssaySchedule();
            AssaySchedule.Assays assays = xAssaySchedule.addNewAssays();
            AssaySchedule.Timepoints timepoints = xAssaySchedule.addNewTimepoints();

            GWTAssaySchedule gAssaySchedule = def.getAssaySchedule();
            if (null != gAssaySchedule.getDescription())
                xAssaySchedule.setDescription(gAssaySchedule.getDescription());

            List<Timepoint> scheduleTimepoints = new ArrayList<>();
            for (GWTTimepoint gtp : gAssaySchedule.getTimepoints())
                scheduleTimepoints.add(createTimepoint(gtp));
            timepoints.setTimepointArray(scheduleTimepoints.toArray(new Timepoint[scheduleTimepoints.size()]));

            for (GWTAssayDefinition gwtAssayDefinition : (List<GWTAssayDefinition>) gAssaySchedule.getAssays())
            {
                AssayRef assayRef = assays.addNewAssayRef();
                assayRef.setAssayName(gwtAssayDefinition.getName());
                assayRef.setLab(gwtAssayDefinition.getDefaultLab());
                
                for (GWTTimepoint gwtTimepoint : (List<GWTTimepoint>) gAssaySchedule.getTimepoints())
                {
                    GWTAssayNote gwtAssayNote = gAssaySchedule.getAssayPerformed(gwtAssayDefinition, gwtTimepoint);
                    if (null != gwtAssayNote)
                    {
                        AssayEvent evt = xAssaySchedule.addNewAssayEvent();
                        evt.setAssayName(gwtAssayDefinition.getName());
                        evt.setTimepoint(createTimepoint(gwtTimepoint));
                        evt.setSampleMeasure(createSampleMeasure(gwtAssayNote.getSampleMeasure()));
                    }
                }
            }

            assert doc.validate();
            return doc;
        }
        catch (Exception e)
        {
            _log.error(e);
            throw new RuntimeException(e);
        }
    }


    static GWTSampleMeasure createGWTSampleMeasure(SampleMeasure measure, GWTStudyDefinition parent)
    {
        return new GWTSampleMeasure(measure.getAmount(), GWTSampleMeasure.Unit.fromString(measure.getUnit()), GWTSampleType.fromString(measure.getType(), parent));
    }

    static Timepoint createTimepoint(GWTTimepoint gtp)
    {
        Timepoint tp = Timepoint.Factory.newInstance();
        tp.setDays(gtp.getDays());
        tp.setDisplayUnit(gtp.getUnit().toString());
        if (null != gtp.getName())
            tp.setName(gtp.getName());

        return tp;
    }

    static SampleMeasure createSampleMeasure(GWTSampleMeasure gwtSampleMeasure)
    {
        SampleMeasure sm = SampleMeasure.Factory.newInstance();
        sm.setAmount(gwtSampleMeasure.getAmount());
        if (null != gwtSampleMeasure.getType())
            sm.setType(gwtSampleMeasure.getType().toString());
        if (null != gwtSampleMeasure.getUnit())
            sm.setUnit(gwtSampleMeasure.getUnit().getStorageName());

        return sm;
    }

    static GWTAssayDefinition findAssayDefinition(String assayName, List<GWTAssayDefinition> assays)
    {
        for (GWTAssayDefinition def : assays)
            if (def.getName().equals(assayName))
                return def;

        return null;
    }

    static GWTImmunogen findImmunogen(String name, List<GWTImmunogen> immunogens)
    {
        for (GWTImmunogen i : immunogens)
            if (i.getName().equals(name))
                return i;

        return null;
    }

    static GWTAdjuvant findAdjuvant(String name, List<GWTAdjuvant> adjuvants)
    {
        for (GWTAdjuvant i : adjuvants)
            if (i.getName().equals(name))
                return i;

        return null;
    }

    static GWTCohort findCohort(String name, List<GWTCohort> cohorts)
    {
        for (GWTCohort c : cohorts)
            if (c.getName().equals(name))
                return c;

        return null;
    }

    static boolean validate(StudyDesignDocument doc)
    {
        XmlOptions opts = new XmlOptions();
        ArrayList<XmlError> errors = new ArrayList();
        opts.setErrorListener(errors);
        boolean valid = doc.validate(opts);
        for (XmlError err : errors)
            System.out.println(err.getMessage());

        return valid;
    }
}

