package org.labkey.api.sequenceanalysis.pipeline;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * This class helps manage the inputs and outputs created during a pipeline job.  It will gather inputs, outputs and intermediate files.
 * On complete, it will handle translation of filepaths from the working directory to the final location.
 *
 * User: bimber
 * Date: 6/20/2014
 * Time: 5:38 PM
 */
public interface TaskFileManager
{
    public void addSequenceOutput(File file, String label, String category, @Nullable Integer readsetId, @Nullable Integer analysisId, @Nullable Integer genomeId);

    public void addOutput(RecordedAction action, String role, File file);

    public void addInput(RecordedAction action, String role, File file);

    public void addStepOutputs(RecordedAction action, PipelineStepOutput output);

    /**
     * Registers a file that will be deleted only at the very end of the protocol
     */
    public void addDeferredIntermediateFile(File file);

    public void deleteDeferredIntermediateFiles();

    public boolean isDeleteIntermediateFiles();

    public void addIntermediateFile(File f);

    public void addIntermediateFiles(Collection<File> files);

    public void deleteIntermediateFiles() throws PipelineJobException;

    public void createSequenceOutputRecords();

    //should be used for remote jobs or local jobs running in a separate working directory
    public void cleanup() throws PipelineJobException;

    public void addFinalOutputFile(File f);

    public void addFinalOutputFiles(Collection<File> files);

    public Set<File> getFinalOutputFiles();

    /**
     * These are output files that are unaltered versions of an input file
     * This is tracked to avoid duplications when inputs are process/archived
     * @param file
     */
    public void addUnalteredOutput(File file);

    public void compressFile(File file);

    public String getInputfileTreatment();

    public void handleInputs() throws PipelineJobException;

    public void processUnzippedInputs();

    public void decompressInputFiles(List<File> inputFiles, List<RecordedAction> actions);
}
