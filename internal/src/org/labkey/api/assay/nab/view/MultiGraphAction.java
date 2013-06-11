package org.labkey.api.assay.nab.view;

import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.assay.dilution.DilutionAssayProvider;
import org.labkey.api.assay.dilution.DilutionAssayRun;
import org.labkey.api.assay.dilution.DilutionSummary;
import org.labkey.api.assay.nab.GraphForm;
import org.labkey.api.assay.nab.NabGraph;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.view.NavTree;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: 6/11/13
 */
public class MultiGraphAction<FormType extends GraphSelectedForm> extends SimpleViewAction<FormType>
{
    public ModelAndView getView(FormType form, BindException errors) throws Exception
    {
        int[] ids = form.getId();
        ExpProtocol protocol = ExperimentService.get().getExpProtocol(form.getProtocolId());
        DilutionAssayProvider provider = (DilutionAssayProvider)AssayService.get().getProvider(protocol);
        Map<DilutionSummary, DilutionAssayRun> summaries = provider.getDataHandler().getDilutionSummaries(getViewContext().getUser(), form.getFitTypeEnum(), ids);
        Set<Integer> cutoffSet = new HashSet<Integer>();
        for (DilutionSummary summary : summaries.keySet())
        {
            for (int cutoff : summary.getAssay().getCutoffs())
                cutoffSet.add(cutoff);
        }

        int[] cutoffs = new int[cutoffSet.size()];
        int i = 0;
        for (int value : cutoffSet)
            cutoffs[i++] = value;

        NabGraph.Config config = getGraphConfig(form);

        config.setCutoffs(cutoffs);
        NabGraph.renderChartPNG(getViewContext().getResponse(), summaries, config);
        return null;
    }

    protected NabGraph.Config getGraphConfig(FormType form)
    {
        NabGraph.Config config = new NabGraph.Config();
        config.setLockAxes(false);
        config.setCaptionColumn(form.getCaptionColumn());
        config.setChartTitle(form.getChartTitle());
        if (form.getHeight() > 0)
            config.setHeight(form.getHeight());
        if (form.getWidth() > 0)
        config.setWidth(form.getWidth());

        return config;
    }

    public NavTree appendNavTrail(NavTree root)
    {
        throw new UnsupportedOperationException();
    }
}
