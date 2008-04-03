package org.labkey.elispot;

import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionMessage;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataRegion;
import org.labkey.api.exp.*;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.security.ACL;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.study.Plate;
import org.labkey.api.study.PlateTemplate;
import org.labkey.api.study.Position;
import org.labkey.api.study.WellGroup;
import org.labkey.api.study.actions.UploadWizardAction;
import org.labkey.api.study.assay.*;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.InsertView;
import org.labkey.api.view.JspView;
import org.labkey.elispot.plate.ElispotPlateReaderService;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.ServletException;
import java.sql.SQLException;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Jan 9, 2008
 */

@RequiresPermission(ACL.PERM_INSERT)
public class ElispotUploadWizardAction extends UploadWizardAction<ElispotRunUploadForm>
{
    public ElispotUploadWizardAction()
    {
        super(ElispotRunUploadForm.class);
        addStepHandler(new AntigenStepHandler());
    }

    protected InsertView createRunInsertView(ElispotRunUploadForm newRunForm, boolean reshow, BindException errors)
    {
        InsertView parent = super.createRunInsertView(newRunForm, reshow, errors);

        ElispotAssayProvider provider = (ElispotAssayProvider) getProvider(newRunForm);
        ParticipantVisitResolverType resolverType = getSelectedParticipantVisitResolverType(provider, newRunForm);

        PlateSamplePropertyHelper helper = provider.createSamplePropertyHelper(newRunForm.getContainer(), newRunForm.getProtocol(), resolverType);
        helper.addSampleColumns(parent.getDataRegion(), getViewContext().getUser());

        return parent;
    }

    public PlateAntigenPropertyHelper createAntigenPropertyHelper(Container container, ExpProtocol protocol, ElispotAssayProvider provider)
    {
        PlateTemplate template = provider.getPlateTemplate(container, protocol);
        return new PlateAntigenPropertyHelper(provider.getAntigenWellGroupColumns(protocol), template);
    }

    protected void addRunActionButtons(ElispotRunUploadForm newRunForm, InsertView insertView, ButtonBar bbar)
    {
        PropertyDescriptor[] antigenColumns = AbstractAssayProvider.getPropertiesForDomainPrefix(_protocol, ElispotAssayProvider.ASSAY_DOMAIN_ANTIGEN_WELLGROUP);
        if (antigenColumns.length == 0)
        {
            super.addRunActionButtons(newRunForm, insertView, bbar);
        }
        else
        {
            addNextButton(bbar);
            addResetButton(newRunForm, insertView, bbar);
        }
    }

    protected ModelAndView afterRunCreation(ElispotRunUploadForm form, ExpRun run, BindException errors) throws ServletException, SQLException
    {
        PropertyDescriptor[] antigenColumns = AbstractAssayProvider.getPropertiesForDomainPrefix(_protocol, ElispotAssayProvider.ASSAY_DOMAIN_ANTIGEN_WELLGROUP);
        if (antigenColumns.length == 0)
        {
            return super.afterRunCreation(form, run, errors);
        }
        else
        {
            return getAntigenView(form, false, errors);
        }
    }

    private ModelAndView getAntigenView(ElispotRunUploadForm form, boolean reshow, BindException errors) throws ServletException
    {
        Map<PropertyDescriptor, String> map = new LinkedHashMap<PropertyDescriptor, String>();
        InsertView view = createInsertView(ExperimentService.get().getTinfoExperimentRun(),
                "lsid", map, reshow, form.isResetDefaultValues(), AntigenStepHandler.NAME, form, errors);

        try {
            PlateAntigenPropertyHelper antigenHelper = createAntigenPropertyHelper(form.getContainer(), form.getProtocol(), (ElispotAssayProvider)form.getProvider());
            antigenHelper.addSampleColumns(view.getDataRegion(), getViewContext().getUser());

            // add existing page properties
            addHiddenUploadSetProperties(form, view);
            addHiddenRunProperties(form, view);

            ElispotAssayProvider provider = (ElispotAssayProvider) getProvider(form);
            PlateSamplePropertyHelper helper = provider.createSamplePropertyHelper(getContainer(), _protocol,
                    getSelectedParticipantVisitResolverType(provider, form));
            addHiddenProperties(helper.getPostedPropertyValues(form.getRequest()), view);

            PreviouslyUploadedDataCollector collector = new PreviouslyUploadedDataCollector(form.getUploadedData());
            collector.addHiddenFormFields(view, form);

            ButtonBar bbar = new ButtonBar();
            addFinishButtons(form, view, bbar);
            addResetButton(form, view, bbar);
            //addNextButton(bbar);

            ActionButton cancelButton = new ActionButton("Cancel", getSummaryLink(_protocol));
            bbar.add(cancelButton);

            _stepDescription = "Antigen Properties";

            view.getDataRegion().setHorizontalGroups(false);
            view.getDataRegion().setButtonBar(bbar, DataRegion.MODE_INSERT);
        }
        catch (ExperimentException e)
        {
            throw new ServletException(e);
        }
        return view;        
    }

    private ModelAndView getPlateSummary(ElispotRunUploadForm form, ExpRun run)
    {
        try {
            AssayProvider provider = form.getProvider();
            PlateTemplate template = provider.getPlateTemplate(form.getContainer(), form.getProtocol());

            if (run != null)
            {
                ExpData[] data = run.getOutputDatas(ElispotDataHandler.ELISPOT_DATA_TYPE);
                assert(data.length == 1);

/*
                Lsid dataRowLsid = new Lsid(data[0].getLSID());
                dataRowLsid.setNamespacePrefix(ElispotDataHandler.ELISPOT_DATA_ROW_LSID_PREFIX);
                dataRowLsid.setObjectId(dataRowLsid.getObjectId() + "-" + "1" + ':' + "1");

                Map<String, ObjectProperty> props = OntologyManager.getPropertyObjects(form.getContainer().getId(), dataRowLsid.toString());
*/
                ModelAndView view = new JspView<ElispotRunUploadForm>("/org/labkey/elispot/view/plateSummary.jsp", form);
                view.addObject("plateTemplate", template);
                view.addObject("dataLsid", data[0].getLSID());
                return view;
            }

        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
        return null;
    }

    protected StepHandler getRunStepHandler()
    {
        return new ElispotRunStepHandler();
    }

    protected class ElispotRunStepHandler extends RunStepHandler
    {
        private Map<PropertyDescriptor, String> _postedSampleProperties = null;

        @Override
        protected boolean validatePost(ElispotRunUploadForm form, BindException errors)
        {
            boolean runPropsValid = super.validatePost(form, errors);
            boolean samplePropsValid = true;

            if (runPropsValid)
            {
                try {
                    form.getUploadedData();
                    ElispotAssayProvider provider = (ElispotAssayProvider) getProvider(form);
                    PlateSamplePropertyHelper helper = provider.createSamplePropertyHelper(getContainer(), _protocol,
                            getSelectedParticipantVisitResolverType(provider, form));
                    _postedSampleProperties = helper.getPostedPropertyValues(form.getRequest());
                    samplePropsValid = validatePostedProperties(_postedSampleProperties, getViewContext().getRequest(), errors);
                }
                catch (ExperimentException e)
                {
                    errors.reject(SpringActionController.ERROR_MSG, e.getMessage());
                    return false;
                }
            }
            return runPropsValid && samplePropsValid;
        }

        @Override
        protected ModelAndView handleSuccessfulPost(ElispotRunUploadForm form, BindException errors) throws SQLException, ServletException
        {
            saveDefaultValues(_postedSampleProperties, form.getRequest(), form.getProvider(), RunStepHandler.NAME);
            return super.handleSuccessfulPost(form, errors);
        }

        protected ExpRun saveExperimentRun(ElispotRunUploadForm form) throws ExperimentException
        {
            // dont save until after the antigen step
            return null;
        }
    }

    public class AntigenStepHandler extends StepHandler<ElispotRunUploadForm>
    {
        public static final String NAME = "ANTIGEN";
        private Map<PropertyDescriptor, String> _postedAntigenProperties = null;

        public ModelAndView handleStep(ElispotRunUploadForm form, BindException errors) throws ServletException, SQLException
        {
            if (!form.isResetDefaultValues() && validatePost(form, errors))
                return handleSuccessfulPost(form, errors);
            else
                return getAntigenView(form, true, errors);

            //return getPlateSummary(form, false);
        }

        protected boolean validatePost(ElispotRunUploadForm form, BindException errors)
        {
            PlateAntigenPropertyHelper helper = createAntigenPropertyHelper(form.getContainer(),
                    form.getProtocol(), (ElispotAssayProvider)form.getProvider());
            _postedAntigenProperties = helper.getPostedPropertyValues(form.getRequest());

            return true;
        }

        protected ModelAndView handleSuccessfulPost(ElispotRunUploadForm form, BindException errors) throws SQLException, ServletException
        {
            ExpRun run = null;
            try {
                AssayProvider provider = form.getProvider();
                run = provider.saveExperimentRun(form);

                saveDefaultValues(_postedAntigenProperties, form.getRequest(), provider, getName());

                ExperimentService.get().getSchema().getScope().beginTransaction();
                ExpData[] data = run.getOutputDatas(ElispotDataHandler.ELISPOT_DATA_TYPE);
                if (data.length != 1)
                    throw new ExperimentException("Elispot should only upload a single file per run.");

                PlateTemplate template = provider.getPlateTemplate(form.getContainer(), form.getProtocol());
                Plate plate = null;

                for (Map.Entry<PropertyDescriptor, String> entry : form.getRunProperties().entrySet())
                {
                    if (ElispotAssayProvider.READER_PROPERTY_NAME.equals(entry.getKey().getName()))
                    {
                        ElispotPlateReaderService.I reader = ElispotDataHandler.getPlateReaderFromName(entry.getValue(), form.getContainer());
                        plate = ElispotDataHandler.initializePlate(data[0].getDataFile(), template, reader);
                        break;
                    }
                }

                PropertyDescriptor[] antigenProps = AbstractAssayProvider.getPropertiesForDomainPrefix(form.getProtocol(), ElispotAssayProvider.ASSAY_DOMAIN_ANTIGEN_WELLGROUP);
                Map<String, String> postedPropMap = new HashMap<String, String>();

                for (Map.Entry<PropertyDescriptor, String> entry : _postedAntigenProperties.entrySet())
                    postedPropMap.put(entry.getKey().getName(), entry.getValue());


                if (plate != null)
                {
                    // we want to 'collapse' all the antigen well groups to 'per well'
                    List<ObjectProperty> results = new ArrayList<ObjectProperty>();
                    for (WellGroup group : plate.getWellGroups(WellGroup.Type.ANTIGEN))
                    {
                        for (Position pos : group.getPositions())
                        {
                            results.clear();
                            Lsid dataRowLsid = ElispotDataHandler.getDataRowLsid(data[0].getLSID(), pos);

                            for (PropertyDescriptor pd : antigenProps)
                            {
                                String key = group.getName().replaceAll(" ", "") + "_" + pd.getName();
                                if (postedPropMap.containsKey(key))
                                {
                                    ObjectProperty op = ElispotDataHandler.getResultObjectProperty(form.getContainer(),
                                            form.getProtocol(),
                                            dataRowLsid.toString(),
                                            pd.getName(),
                                            postedPropMap.get(key),
                                            pd.getPropertyType(),
                                            pd.getFormat());

                                    results.add(op);
                                }
                            }

                            if (!results.isEmpty())
                            {
                                OntologyManager.ensureObject(form.getContainer().getId(), dataRowLsid.toString(),  data[0].getLSID());
                                OntologyManager.insertProperties(form.getContainer().getId(), results.toArray(new ObjectProperty[results.size()]), dataRowLsid.toString());
                            }
                        }
                    }
                }
                ExperimentService.get().getSchema().getScope().commitTransaction();
            }
            catch (ExperimentException e)
            {
                errors.reject(SpringActionController.ERROR_MSG, e.getMessage());
                return getAntigenView(form, true, errors);
            }
            finally
            {
                ExperimentService.get().getSchema().getScope().closeConnection();
            }
            return runUploadComplete(form, errors);
        }

        public String getName()
        {
            return NAME;
        }
    }
}
