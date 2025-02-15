package org.labkey.studydesign;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.studydesign.query.StudyPersonnelDomainKind;
import org.labkey.api.studydesign.query.StudyProductAntigenDomainKind;
import org.labkey.api.studydesign.query.StudyProductDomainKind;
import org.labkey.api.studydesign.query.StudyTreatmentDomainKind;
import org.labkey.api.studydesign.query.StudyTreatmentProductDomainKind;
import org.labkey.api.view.WebPartFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class StudyDesignModule extends DefaultModule
{
    public static final String NAME = "StudyDesign";

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    protected void init()
    {
        //addController("study-design", StudyDesignController.class);

        // study design domains
        PropertyService.get().registerDomainKind(new StudyProductDomainKind());
        PropertyService.get().registerDomainKind(new StudyProductAntigenDomainKind());
        PropertyService.get().registerDomainKind(new StudyTreatmentProductDomainKind());
        PropertyService.get().registerDomainKind(new StudyTreatmentDomainKind());
        PropertyService.get().registerDomainKind(new StudyPersonnelDomainKind());
    }

    @Override
    @NotNull
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return Collections.emptyList();
    }

    @Override
    public boolean hasScripts()
    {
        return false;
    }

    @Override
    protected void doStartup(ModuleContext moduleContext)
    {
    }

    @Override
    @NotNull
    public Set<String> getSchemaNames()
    {
        HashSet<String> set = new HashSet<>();
        set.addAll(getProvisionedSchemaNames());

        return set;
    }

    @Override
    @NotNull
    public Set<String> getProvisionedSchemaNames()
    {
        return Collections.singleton("studydesign");
    }


}
