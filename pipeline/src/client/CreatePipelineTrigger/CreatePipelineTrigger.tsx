import React, { ChangeEvent, Dispatch, FC, Reducer, useCallback, useState, useReducer } from 'react';
import { ActionURL, Ajax, Utils } from '@labkey/api';
import { naturalSort, FormSchema, AutoForm } from '@labkey/components';
import { Alert, ListGroup, ListGroupItem } from 'react-bootstrap';

// eslint-disable-next-line import/no-unassigned-import
import './CreatePipelineTrigger.scss';

const HELP_TEXT = 'Fields marked with an asterisk * are required. ';
const PIPELINE_FOLDER = '/@pipeline';

interface Details {
    'assay provider': string;
    description: string;
    enabled: boolean;
    name: string;
    pipelineId: string;
    type: string;
    username: string;
}

interface TriggerConfiguration {
    copy: string;
    filePattern: string;
    location: string;
    move: string; // server side only, split into moveDirectory and moveContainer on init.
    moveDirectory: string; // client side only.
    moveContainer: string; // client side only.
    parameterFunction: string;
    quiet: number;
    recursive: boolean;
    rowId: number;
}

type CustomConfiguration = Record<string, string>;

interface CustomParameterModel {
    key: string;
    value: string;
    id: number; // used internally as a key for react
}

enum View {
    DETAILS,
    CONFIGURATION,
}

interface FormState {
    // the custom parameters known by the FormSchema (see FileAnalysisTaskPipeline.getCustomFields)
    customConfig: CustomConfiguration;
    // customFieldFormSchemas are required so we can properly set defaultValues when Details.pipelineId changes
    customFieldFormSchemas: Record<string, FormSchema>;
    // the parameters not known by the FormSchema, added via "add custom parameter" in UI
    customParameters: Record<number, CustomParameterModel>;
    details: Details;
    detailsFormSchema: FormSchema;
    rowId?: number;
    saveError: string;
    saving: boolean;
    taskFormSchemas: Record<string, FormSchema>;
    triggerConfig: TriggerConfiguration;
    view: View;
}

enum ActionType {
    SET_VIEW = 'SET_VIEW',
    UPDATE_DETAILS = 'UPDATE_DETAILS',
    UPDATE_TRIGGER_CONFIG = 'UPDATE_TRIGGER_CONFIG',
    UPDATE_CUSTOM_CONFIG = 'UPDATE_CUSTOM_CONFIG',
    UPDATE_CUSTOM_PARAM = 'UPDATE_CUSTOM_PARAM',
    ADD_CUSTOM_PARAM = 'ADD_CUSTOM_PARAM',
    REMOVE_CUSTOM_PARAM = 'REMOVE_CUSTOM_PARAM',
    SET_SAVING = 'SET_SAVING',
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
interface FieldAction<T = any> {
    type: ActionType.UPDATE_DETAILS | ActionType.UPDATE_TRIGGER_CONFIG | ActionType.UPDATE_CUSTOM_CONFIG;
    field: string;
    value: T;
}

interface ViewAction {
    type: ActionType.SET_VIEW;
    view: View;
}

interface AddCustomParamAction {
    type: ActionType.ADD_CUSTOM_PARAM;
}

interface RemoveCustomParamAction {
    type: ActionType.REMOVE_CUSTOM_PARAM;
    id: number;
}

interface UpdateCustomParamAction {
    type: ActionType.UPDATE_CUSTOM_PARAM;
    id: number;
    key: string;
    value: string;
}

interface SetSavingAction {
    type: ActionType.SET_SAVING;
    saveError: string;
    saving: boolean;
}

type FormStateAction =
    | FieldAction
    | ViewAction
    | AddCustomParamAction
    | RemoveCustomParamAction
    | UpdateCustomParamAction
    | SetSavingAction;

const formStateReducer = (state: FormState, action: FormStateAction): FormState => {
    switch (action.type) {
        case ActionType.SET_VIEW: {
            const { name, pipelineId } = state.details;

            // Don't navigate to the Configuration form if the user hasn't filled out he required fields in Details.
            if (name === null || name.trim() === '' || pipelineId === null) {
                return state;
            }

            return { ...state, view: action.view };
        }
        case ActionType.UPDATE_DETAILS: {
            const { customConfig, customFieldFormSchemas, details, taskFormSchemas, triggerConfig } = state;
            const { field, value } = action;

            let resetCustomConfig = customConfig;
            let resetTriggerConfig = triggerConfig;

            if (field === 'pipelineId') {
                // Set default values on the customConfig based on the appropriate FormSchema.
                resetCustomConfig = {};
                // eslint-disable-next-line no-unused-expressions
                customFieldFormSchemas[value]?.fields.forEach(f => {
                    if (f.defaultValue !== null) {
                        resetCustomConfig[f.name] = f.defaultValue;
                    }
                });

                // Delete values that don't exist in new taskFormSchema (e.g. containerMove/directoryMove)
                resetTriggerConfig = { ...resetTriggerConfig };
                const taskFormSchema = taskFormSchemas[value];
                Object.keys(resetTriggerConfig).forEach(key => {
                    if (taskFormSchema.fields.find(f => f.name === key) === undefined) {
                        delete resetTriggerConfig[key];
                    }
                });
            }

            return {
                ...state,
                customConfig: resetCustomConfig,
                details: { ...details, [field]: value },
                triggerConfig: resetTriggerConfig,
            };
        }
        case ActionType.UPDATE_TRIGGER_CONFIG: {
            return { ...state, triggerConfig: { ...state.triggerConfig, [action.field]: action.value } };
        }
        case ActionType.UPDATE_CUSTOM_CONFIG: {
            return { ...state, customConfig: { ...state.customConfig, [action.field]: action.value } };
        }
        case ActionType.ADD_CUSTOM_PARAM: {
            const { customParameters } = state;
            const integerIds = Object.keys(customParameters).map(k => parseInt(k, 10));
            const id = integerIds.length > 0 ? Math.max(...integerIds) + 1 : 0;
            return {
                ...state,
                customParameters: {
                    ...customParameters,
                    [id]: { key: '', value: '', id },
                },
            };
        }
        case ActionType.REMOVE_CUSTOM_PARAM: {
            const { id } = action;
            const customParameters = { ...state.customParameters };
            delete customParameters[id];
            return { ...state, customParameters };
        }
        case ActionType.UPDATE_CUSTOM_PARAM: {
            const { customParameters } = state;
            const { id, key, value } = action;
            const keyIsSame = customParameters[id].key === key;
            const valueIsSame = customParameters[id].value === value;

            if (keyIsSame && valueIsSame) {
                // If nothing changed, no-op.
                return state;
            }

            return {
                ...state,
                customParameters: {
                    ...customParameters,
                    [id]: { key, value, id },
                },
            };
        }
        case ActionType.SET_SAVING: {
            const { saving, saveError } = action;
            return { ...state, saving, saveError };
        }
        default: {
            // Throw error for unhandled actions.
            // eslint-disable-next-line @typescript-eslint/no-explicit-any
            throw new Error('Unhandled action! ' + (action as any).type); // Cast action as any to make compiler work.
        }
    }
};

// customParameters is computed based on customConfig and taskFormSchemas
type InitialState = Omit<FormState, 'customParameters' | 'saving' | 'saveError'>;

const initializeFormState = (initialState: InitialState): FormState => {
    const {
        customConfig,
        customFieldFormSchemas,
        details,
        detailsFormSchema,
        rowId,
        taskFormSchemas,
        triggerConfig,
        view,
    } = initialState;
    const _details = { ...details };
    const _triggerConfig = { ...triggerConfig, moveContainer: '', moveDirectory: '' };
    const _customConfig = { ...customConfig };
    const customParameters = {};
    let idCounter = 0;
    const { move } = _triggerConfig;

    // Note: below we check for null on some fields, and undefined on others, this is due to inconsistent behavior on
    // how the server serializes some of the data.

    if (rowId === null) {
        // The server defaults to 0, but does not allow anything less than 1 when saving.
        _triggerConfig.quiet = 1;
        _details.type = detailsFormSchema.fields.find(f => f.name === 'type').defaultValue;
    } else {
        // The server serializes quiet in ms, but expects seconds when saving.
        _triggerConfig.quiet = _triggerConfig.quiet / 1000;
        const formSchema = customFieldFormSchemas[details.pipelineId];

        Object.keys(_customConfig).forEach(key => {
            const value = _customConfig[key];
            const field = formSchema?.fields.find(f => f.name === key);

            if (!field) {
                // If no field is defined in the formSchema then it needs to be rendered as a "custom parameter"
                delete _customConfig[key];
                customParameters[idCounter] = { id: idCounter, key, value };
                idCounter += 1;
            }
        });
    }

    if (move !== undefined) {
        const pipelineIndex = move.indexOf(PIPELINE_FOLDER);

        if (pipelineIndex > -1) {
            // Move path is in the format of: <containerPath>/@pipeline/<directoryPath>
            _triggerConfig.moveContainer = move.substring(0, pipelineIndex);
            _triggerConfig.moveDirectory = move.substring(pipelineIndex + PIPELINE_FOLDER.length);

            if (_triggerConfig.moveDirectory[0] === '/') {
                _triggerConfig.moveDirectory = _triggerConfig.moveDirectory.substring(1);
            }
        } else {
            _triggerConfig.moveContainer = move;
        }
    }

    return {
        customConfig: _customConfig,
        customFieldFormSchemas,
        customParameters,
        details: _details,
        detailsFormSchema,
        saveError: undefined,
        saving: false,
        rowId,
        taskFormSchemas,
        triggerConfig: _triggerConfig,
        view,
    };
};

interface FormStateHook {
    dispatch: Dispatch<FormStateAction>;
    formState: FormState;
}

const useFormState = (
    rowId: number,
    details: Details,
    triggerConfig: TriggerConfiguration,
    customConfig: CustomConfiguration,
    detailsFormSchema: FormSchema,
    taskFormSchemas: Record<string, FormSchema>,
    customFieldFormSchemas: Record<string, FormSchema>
): FormStateHook => {
    const initialState = {
        customConfig,
        customFieldFormSchemas,
        details,
        detailsFormSchema,
        rowId,
        taskFormSchemas,
        triggerConfig,
        view: View.DETAILS,
    };
    const [formState, dispatch] = useReducer<Reducer<FormState, FormStateAction>, InitialState>(
        formStateReducer,
        initialState,
        initializeFormState
    );

    return { formState, dispatch };
};

interface DetailsFormProps {
    dispatch: Dispatch<FormStateAction>;
    formState: FormState;
    onNext: () => void;
    returnUrl: string;
}

const DetailsForm: FC<DetailsFormProps> = ({ dispatch, formState, onNext, returnUrl }) => {
    const { details, detailsFormSchema } = formState;
    const { name, pipelineId } = details;
    const onChange = useCallback((name_, value) => {
        dispatch({ type: ActionType.UPDATE_DETAILS, field: name_, value });
    }, []);
    const nextDisabled = name === null || name.trim() === '' || pipelineId === null;

    return (
        <div className="details-form">
            <AutoForm formSchema={detailsFormSchema} onChange={onChange} values={details} />

            <div className="pipeline-trigger-buttons">
                <button type="button" className="btn btn-primary" disabled={nextDisabled} onClick={onNext}>
                    next
                </button>
                <a href={returnUrl} className="btn btn-default">
                    cancel
                </a>
            </div>
        </div>
    );
};

interface CustomParameterProps {
    customParameter: CustomParameterModel;
    remove: (index) => void;
    update: (index, key, value) => void;
}

const CustomParameter: FC<CustomParameterProps> = ({ customParameter, remove, update }) => {
    const { id, key, value } = customParameter;
    const onRemoveClicked = useCallback(() => remove(id), [remove, id]);
    const onKeyChanged = useCallback((event: ChangeEvent<HTMLInputElement>) => update(id, event.target.value, value), [
        update,
        id,
        value,
    ]);
    const onValueChanged = useCallback((event: ChangeEvent<HTMLInputElement>) => update(id, key, event.target.value), [
        update,
        id,
        key,
    ]);
    return (
        <div className="custom-parameter form-group">
            <div className="col-sm-3">
                <input
                    className="form-control"
                    name={`custom-param-key-${id}`}
                    onChange={onKeyChanged}
                    type="text"
                    value={key}
                />
            </div>

            <div className="col-sm-8">
                <input
                    className="form-control"
                    name={`custom-param-value-${id}`}
                    onChange={onValueChanged}
                    type="text"
                    value={value}
                />
            </div>

            <div className="col-sm-1">
                <button type="button" onClick={onRemoveClicked}>
                    <span className="fa fa-trash" />
                </button>
            </div>
        </div>
    );
};

interface CustomParametersProps {
    customParameters: Record<number, CustomParameterModel>;
    dispatch: Dispatch<FormStateAction>;
}

const CustomParameters: FC<CustomParametersProps> = ({ customParameters, dispatch }) => {
    const add = useCallback(() => dispatch({ type: ActionType.ADD_CUSTOM_PARAM }), []);
    const remove = useCallback(id => {
        dispatch({ type: ActionType.REMOVE_CUSTOM_PARAM, id });
    }, []);
    const update = useCallback((id, key, value) => {
        dispatch({ type: ActionType.UPDATE_CUSTOM_PARAM, id, key, value });
    }, []);

    return (
        <div className="custom-parameters">
            <div className="custom-config__button" onClick={add}>
                <span className="fa fa-plus-square" />
                <span>Add Custom Parameter</span>
            </div>
            {Object.keys(customParameters)
                .sort(naturalSort)
                .map(id => (
                    <CustomParameter key={id} update={update} remove={remove} customParameter={customParameters[id]} />
                ))}
        </div>
    );
};

interface ConfigurationFormProps {
    formState: FormState;
    dispatch: Dispatch<FormStateAction>;
    onBack: () => void;
    onSubmit: () => void;
    returnUrl: string;
}

const ConfigurationForm: FC<ConfigurationFormProps> = props => {
    const { formState, dispatch, onBack, onSubmit, returnUrl } = props;
    const {
        customConfig,
        customFieldFormSchemas,
        customParameters,
        details,
        taskFormSchemas,
        triggerConfig,
    } = formState;
    const { parameterFunction } = triggerConfig;
    const [showAdvanced, setShowAdvanced] = useState<boolean>(false);
    const toggleAdvanced = useCallback(() => setShowAdvanced(advanced => !advanced), []);
    const taskFormSchema = taskFormSchemas[details.pipelineId];
    const customFieldFormSchema = customFieldFormSchemas[details.pipelineId];
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const onTriggerConfigChange = useCallback((name: string, value: any) => {
        dispatch({ type: ActionType.UPDATE_TRIGGER_CONFIG, field: name, value });
    }, []);
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const onCustomConfigChange = useCallback((name: string, value: any) => {
        dispatch({ type: ActionType.UPDATE_CUSTOM_CONFIG, field: name, value });
    }, []);
    const onParamFunctionChange = useCallback(event => {
        dispatch({ type: ActionType.UPDATE_TRIGGER_CONFIG, field: 'parameterFunction', value: event.target.value });
    }, []);

    return (
        <div className="configuration-form">
            <AutoForm formSchema={taskFormSchema} onChange={onTriggerConfigChange} values={triggerConfig} />

            {customFieldFormSchema !== null && (
                <AutoForm formSchema={customFieldFormSchema} onChange={onCustomConfigChange} values={customConfig} />
            )}

            <div className="custom-config form-horizontal">
                <div className="custom-config__button" onClick={toggleAdvanced}>
                    <span className={`fa ${showAdvanced ? 'fa-minus-square' : 'fa-plus-square'}`} />
                    <span>Show Advanced Settings</span>
                </div>

                {showAdvanced && (
                    <div className="advanced-settings">
                        <div className="form-group">
                            <label className="col-sm-3 control-label" htmlFor="parameter-function">
                                Parameter Function
                            </label>
                            <div className="col-sm-9">
                                <textarea
                                    id="parameter-function"
                                    className="form-control"
                                    onChange={onParamFunctionChange}
                                    value={parameterFunction === null ? '' : parameterFunction}
                                />
                            </div>
                        </div>

                        <CustomParameters customParameters={customParameters} dispatch={dispatch} />
                    </div>
                )}
            </div>

            <div className="pipeline-trigger-buttons">
                <button type="button" className="btn btn-primary" onClick={onSubmit}>
                    save
                </button>
                <a href={returnUrl} className="btn btn-default">
                    cancel
                </a>
                <button type="button" className="btn btn-default" onClick={onBack}>
                    back
                </button>
            </div>
        </div>
    );
};

function savePipelineTrigger(formState: FormState): Promise<boolean> {
    const { customConfig, customParameters, details, rowId, triggerConfig } = formState;
    const { moveDirectory, moveContainer, ...restTrigger } = triggerConfig;
    const customParamKey = [];
    const customParamValue = [];

    Object.keys(customParameters).forEach(id => {
        const key = customParameters[id].key.trim();
        const value = customParameters[id].value.trim();

        if (key) {
            customParamKey.push(key);
            customParamValue.push(value);
        }
    });

    Object.keys(customConfig).forEach(key => {
        const value = customConfig[key];
        customParamKey.push(key);
        customParamValue.push(value);
    });

    let move = '';
    const container = moveContainer.trim();
    const directory = moveDirectory.trim();

    if (container || directory) {
        const pipeline = container && !container.endsWith('/') ? '/@pipeline/' : '@pipeline/';
        move = `${container}${pipeline}${directory}`;
    }

    return new Promise((resolve, reject) => {
        Ajax.request({
            url: ActionURL.buildURL('pipeline', 'savePipelineTrigger.api'),
            method: 'POST',
            jsonData: { ...restTrigger, ...details, customParamKey, customParamValue, move, rowId },
            success: Utils.getCallbackWrapper(response => {
                resolve(response.success);
            }),
            failure: Utils.getCallbackWrapper(
                response => {
                    reject(response.exception);
                },
                undefined,
                true
            ),
        });
    });
}

export interface Props {
    customConfig: CustomConfiguration;
    customFieldFormSchemas: Record<string, FormSchema>;
    details: Details;
    detailsFormSchema: FormSchema;
    docsHref: string;
    returnUrl: string;
    rowId: number;
    taskFormSchemas: Record<string, FormSchema>;
    tasksHelpText: Record<string, string>;
    triggerConfig: TriggerConfiguration;
}

export const CreatePipelineTrigger: FC<Props> = props => {
    const {
        customConfig,
        customFieldFormSchemas,
        details,
        detailsFormSchema,
        docsHref,
        returnUrl,
        rowId,
        taskFormSchemas,
        tasksHelpText,
        triggerConfig,
    } = props;
    const { formState, dispatch } = useFormState(
        rowId,
        details,
        triggerConfig,
        customConfig,
        detailsFormSchema,
        taskFormSchemas,
        customFieldFormSchemas
    );
    const { view } = formState;
    const helpText = HELP_TEXT + (tasksHelpText[formState.details.pipelineId] ?? '');
    const showDetails = useCallback(() => dispatch({ type: ActionType.SET_VIEW, view: View.DETAILS }), []);
    const showConfig = useCallback(() => dispatch({ type: ActionType.SET_VIEW, view: View.CONFIGURATION }), []);
    const onSubmit = useCallback(async () => {
        dispatch({ type: ActionType.SET_SAVING, saving: true, saveError: undefined });
        let saveError;

        try {
            const success = await savePipelineTrigger(formState);

            if (success !== true) {
                saveError = 'Error saving pipeline trigger';
            } else {
                window.location.href = returnUrl;
            }
        } catch (error) {
            saveError = error;
        } finally {
            dispatch({ type: ActionType.SET_SAVING, saving: false, saveError });
        }
    }, [formState]);

    // TODO:
    //  - handle dirty state.

    return (
        <div className="create-pipeline-trigger row">
            <div className="col-sm-2">
                <ListGroup>
                    <ListGroupItem active={view === View.DETAILS} href="#details" onClick={showDetails}>
                        Details
                    </ListGroupItem>

                    <ListGroupItem active={view === View.CONFIGURATION} href="#configuration" onClick={showConfig}>
                        Configuration
                    </ListGroupItem>
                </ListGroup>

                <ListGroup className="list-group">
                    <ListGroupItem href={docsHref} target="_blank" rel="noopener noreferrer">
                        Documentation &nbsp;
                        <span className="fa fa-external-link" />
                    </ListGroupItem>
                </ListGroup>
            </div>

            <div className="col-sm-7">
                <Alert bsStyle="info">{helpText}</Alert>

                {view === View.DETAILS && (
                    <DetailsForm dispatch={dispatch} formState={formState} onNext={showConfig} returnUrl={returnUrl} />
                )}

                {view === View.CONFIGURATION && (
                    <ConfigurationForm
                        dispatch={dispatch}
                        formState={formState}
                        onBack={showDetails}
                        onSubmit={onSubmit}
                        returnUrl={returnUrl}
                    />
                )}
            </div>
        </div>
    );
};
