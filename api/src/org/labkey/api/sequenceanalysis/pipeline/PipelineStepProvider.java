package org.labkey.api.sequenceanalysis.pipeline;


import org.json.JSONObject;
import org.labkey.api.view.template.ClientDependency;

import java.util.Collection;
import java.util.List;

/**
 * User: bimber
 * Date: 6/13/2014
 * Time: 1:51 PM
 *
 * This describes a tool in a sequence pipeline, enumerated by PipelineStep.StepType.  It provides description and config used
 * when generating client UI, and also a factory to create a PipelineStep, which is what runs the task.  This step is frequently
 * a wrapper around a command line tool.  Because is it possible for a single underlying tool to provide multiple processing steps,
 * the provider can return true from canCombine(), which would result in a single instance of the tool being called, allowing it to
 * read the config from all steps and run as 1 instance.  FASTQ/BAM tools are the most common example of this.
 */
public interface PipelineStepProvider<StepType extends PipelineStep>
{
    public StepType create(PipelineContext context);

    /**
     * @return The name of this pipeline step.  It should be unique within other tools that are part of this pipeline StepType.
     */
    public String getName();

    /**
     * @return The label to be used for this pipeline step in the UI.
     */
    public String getLabel();

    /**
     * @return Optional.  The name of the underlying tool used in this step
     */
    public String getToolName();

    /**
     * @return Optional.  If provided, the UI will provide a link to this tool's website
     */
    public String getWebsiteURL();

    /**
     * @return A description of this step, which will be shown in the client UI
     */
    public String getDescription();

    /**
     * @return A list of any steps that must run prior to this step
     */
    public List<PipelineStepProvider> getPrerequisites();

    /**
     * @return A list of any addition JS or CSS files required to display this UI on the client.
     */
    public Collection<ClientDependency> getClientDependencies();

    /**
     * @return A list of the input paramters used by this tool.  Most often they correspond to command line parameters, but
     * this does not necessarily need to be the case.
     */
    public List<ToolParameterDescriptor> getParameters();

    /**
     * @return The tool parameter matching the supplied name
     */
    public ToolParameterDescriptor getParameterByName(String name);

    /**
     * Creates the JSON object sent to the client that is used to build the client UI
     */
    public JSONObject toJSON();

    public Class<StepType> getStepClass();
}
