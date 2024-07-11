import React, { ChangeEvent, Dispatch, FC, Reducer, useCallback, useEffect, useState, useReducer } from 'react';
import { ActionURL, Ajax, Utils } from '@labkey/api';
import { naturalSort, FormSchema, AutoForm, Alert, cancelEvent } from '@labkey/components';

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
    moveContainer: string; // client side only.
    moveDirectory: string; // client side only.
    parameterFunction: string;
    quiet: number;
    recursive: boolean;
    rowId: number;
}

type CustomConfiguration = Record<string, string | boolean>;

interface CustomParameterModel {
    id: number; // used internally as a key for react
    key: string;
    value: string;
}

enum View {
    DETAILS,
    CONFIGURATION,
}

interface FormState {
    // the custom parameters known by the FormSchema (see FileAnalysisTaskPipeline.getCustomFields)
    customConfig: CustomConfiguration;
    customConfigValid: boolean;
    // customFieldFormSchemas are required so we can properly set defaultValues when Details.pipelineId changes
    customFieldFormSchemas: Record<string, FormSchema>;
    // the parameters not known by the FormSchema, added via "add custom parameter" in UI
    customParameters: Record<number, CustomParameterModel>;
    details: Details;
    detailsFormSchema: FormSchema;
    detailsValid: boolean;
    isDirty: boolean;
    rowId?: number;
    saveError: string;
    saveSuccessful: boolean;
    saving: boolean;
    taskFormSchemas: Record<string, FormSchema>;
    triggerConfig: TriggerConfiguration;
    triggerConfigValid: boolean;
    view: View;
}

// The omitted fields are computed by initializeFormState
type InitialState = Omit<
    FormState,
    | 'customParameters'
    | 'customConfigValid'
    | 'detailsValid'
    | 'isDirty'
    | 'saveError'
    | 'saving'
    | 'saveSuccessful'
    | 'triggerConfigValid'
>;

enum ActionType {
    ADD_CUSTOM_PARAM = 'ADD_CUSTOM_PARAM',
    REMOVE_CUSTOM_PARAM = 'REMOVE_CUSTOM_PARAM',
    SET_SAVING = 'SET_SAVING',
    SET_VIEW = 'SET_VIEW',
    UPDATE_CUSTOM_CONFIG = 'UPDATE_CUSTOM_CONFIG',
    UPDATE_CUSTOM_PARAM = 'UPDATE_CUSTOM_PARAM',
    UPDATE_DETAILS = 'UPDATE_DETAILS',
    UPDATE_TRIGGER_CONFIG = 'UPDATE_TRIGGER_CONFIG',
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
interface FieldAction<T = any> {
    field: string;
    type: ActionType.UPDATE_DETAILS | ActionType.UPDATE_TRIGGER_CONFIG | ActionType.UPDATE_CUSTOM_CONFIG;
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
    id: number;
    type: ActionType.REMOVE_CUSTOM_PARAM;
}

interface UpdateCustomParamAction {
    id: number;
    key: string;
    type: ActionType.UPDATE_CUSTOM_PARAM;
    value: string;
}

interface SetSavingAction {
    saveError: string;
    saveSuccessful: boolean;
    saving: boolean;
    type: ActionType.SET_SAVING;
}

type FormStateAction =
    | FieldAction
    | ViewAction
    | AddCustomParamAction
    | RemoveCustomParamAction
    | UpdateCustomParamAction
    | SetSavingAction;

const validateValues = (formSchema: FormSchema, values: Record<string, any>): boolean => {
    // Not every trigger type has a custom form schema, so this can be null/undefined.
    if (!formSchema) return true;

    let isValid = true;

    formSchema.fields.forEach(field => {
        const value = values[field.name];

        if (field.required) {
            if (value === null || value === undefined) {
                isValid = false;
            }
            if (typeof value === 'string' && value.trim() === '') {
                isValid = false;
            }
        }
    });

    return isValid;
};

const formStateReducer = (state: FormState, action: FormStateAction): FormState => {
    switch (action.type) {
        case ActionType.SET_VIEW: {
            // Don't navigate to the Configuration form if the user hasn't filled out the required fields in Details.
            if (!state.detailsValid) {
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

                if (taskFormSchema) {
                    Object.keys(resetTriggerConfig).forEach(key => {
                        if (taskFormSchema.fields.find(f => f.name === key) === undefined) {
                            delete resetTriggerConfig[key];
                        }
                    });
                }
            }

            const newDetails = { ...details, [field]: value };

            return {
                ...state,
                customConfig: resetCustomConfig,
                details: newDetails,
                detailsValid: validateValues(state.detailsFormSchema, newDetails),
                isDirty: true,
                triggerConfig: resetTriggerConfig,
            };
        }
        case ActionType.UPDATE_TRIGGER_CONFIG: {
            const triggerConfig = { ...state.triggerConfig, [action.field]: action.value };
            const formSchema = state.taskFormSchemas[state.details.pipelineId];
            return {
                ...state,
                isDirty: true,
                triggerConfig,
                triggerConfigValid: validateValues(formSchema, triggerConfig),
            };
        }
        case ActionType.UPDATE_CUSTOM_CONFIG: {
            const customConfig = { ...state.customConfig, [action.field]: action.value };
            const formSchema = state.customFieldFormSchemas[state.details.pipelineId];
            return {
                ...state,
                isDirty: true,
                customConfig,
                customConfigValid: validateValues(formSchema, customConfig),
            };
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
                isDirty: true,
            };
        }
        case ActionType.REMOVE_CUSTOM_PARAM: {
            const { id } = action;
            const customParameters = { ...state.customParameters };
            delete customParameters[id];
            return { ...state, customParameters, isDirty: true };
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
                isDirty: true,
            };
        }
        case ActionType.SET_SAVING: {
            const { saving, saveError, saveSuccessful } = action;
            return { ...state, saving, saveError, saveSuccessful };
        }
        default: {
            // Throw error for unhandled actions.
            // eslint-disable-next-line @typescript-eslint/no-explicit-any
            throw new Error('Unhandled action! ' + (action as any).type); // Cast action as any to make compiler work.
        }
    }
};

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

        // populate default values of custom config.
        customFieldFormSchemas[_details.pipelineId]?.fields.forEach(field => {
            if (field.defaultValue) {
                _customConfig[field.name] = field.defaultValue;
            }
        });
    } else {
        // The server serializes quiet in ms, but expects seconds when saving.
        _triggerConfig.quiet = _triggerConfig.quiet / 1000;
        const formSchema = customFieldFormSchemas[_details.pipelineId];

        Object.keys(_customConfig).forEach(key => {
            const value = _customConfig[key];
            const field = formSchema?.fields.find(f => f.name === key);

            if (field && field.type === 'checkbox') {
                // The server serializes custom config values as strings, so we need to convert them to booleans if
                // they are to be rendered as checkboxes.
                _customConfig[field.name] = value === 'true';
            }

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
        customConfigValid: validateValues(customFieldFormSchemas[_details.pipelineId], _customConfig),
        customFieldFormSchemas,
        customParameters,
        details: _details,
        detailsFormSchema,
        detailsValid: validateValues(detailsFormSchema, _details),
        isDirty: false,
        saveError: undefined,
        saving: false,
        saveSuccessful: false,
        rowId,
        taskFormSchemas,
        triggerConfig: _triggerConfig,
        triggerConfigValid: validateValues(taskFormSchemas[_details.pipelineId], _triggerConfig),
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
    const onChange = useCallback((name_, value) => {
        dispatch({ type: ActionType.UPDATE_DETAILS, field: name_, value });
    }, []);

    return (
        <div className="details-form">
            <AutoForm formSchema={detailsFormSchema} onChange={onChange} values={details} />

            <div className="pipeline-trigger-buttons">
                <button type="button" className="btn btn-primary" disabled={!formState.detailsValid} onClick={onNext}>
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
    const onKeyChanged = useCallback(
        (event: ChangeEvent<HTMLInputElement>) => update(id, event.target.value, value),
        [update, id, value]
    );
    const onValueChanged = useCallback(
        (event: ChangeEvent<HTMLInputElement>) => update(id, key, event.target.value),
        [update, id, key]
    );
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
    dispatch: Dispatch<FormStateAction>;
    formState: FormState;
    onBack: () => void;
    onSubmit: () => void;
    returnUrl: string;
}

const ConfigurationForm: FC<ConfigurationFormProps> = props => {
    const { formState, dispatch, onBack, onSubmit, returnUrl } = props;
    const {
        customConfig,
        customConfigValid,
        customFieldFormSchemas,
        customParameters,
        details,
        detailsValid,
        saving,
        taskFormSchemas,
        triggerConfig,
        triggerConfigValid,
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
    const saveDisabled = !customConfigValid || !detailsValid || !triggerConfigValid || saving;

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
                <button disabled={saveDisabled} type="button" className="btn btn-primary" onClick={onSubmit}>
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
    const container = moveContainer === undefined ? '' : moveContainer.trim();
    const directory = moveDirectory === undefined ? '' : moveDirectory.trim();

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
    const { isDirty, saveSuccessful, saveError, saving, view } = formState;
    const helpText = HELP_TEXT + (tasksHelpText[formState.details.pipelineId] ?? '');
    const showDetails = useCallback(
        (event = undefined) => {
            if (event) cancelEvent(event);
            dispatch({ type: ActionType.SET_VIEW, view: View.DETAILS });
        },
        [dispatch]
    );
    const showConfig = useCallback(
        (event = undefined) => {
            if (event) cancelEvent(event);
            dispatch({ type: ActionType.SET_VIEW, view: View.CONFIGURATION });
        },
        [dispatch]
    );
    const onSubmit = useCallback(async () => {
        dispatch({ type: ActionType.SET_SAVING, saving: true, saveError: undefined, saveSuccessful: false });
        let _saveError;
        let success = false;

        try {
            success = await savePipelineTrigger(formState);

            if (success !== true) {
                _saveError = 'Error saving pipeline trigger';
            }
        } catch (error) {
            _saveError = error;
        } finally {
            dispatch({ type: ActionType.SET_SAVING, saving: false, saveError: _saveError, saveSuccessful: success });
        }
    }, [dispatch, formState]);

    useEffect(() => {
        const beforeUnload = (event: BeforeUnloadEvent): void => {
            if (!saveSuccessful && (saving || isDirty)) {
                event.preventDefault();
                event.returnValue = true;
            }
        };
        window.addEventListener('beforeunload', beforeUnload);

        return () => {
            window.removeEventListener('beforeunload', beforeUnload);
        };
    }, [isDirty, saving, saveSuccessful]);

    useEffect(() => {
        if (saveSuccessful) {
            window.setTimeout(() => (window.location.href = returnUrl), 1000);
        }
    }, [returnUrl, saveSuccessful]);

    return (
        <div className="create-pipeline-trigger row">
            <div className="col-sm-2">
                <div className="list-group">
                    <a
                        className={`list-group-item ${view === View.DETAILS ? 'active' : ''}`}
                        onClick={showDetails}
                        href="#"
                    >
                        Details
                    </a>

                    <a
                        className={`list-group-item ${view === View.CONFIGURATION ? 'active' : ''}`}
                        onClick={showConfig}
                        href="#"
                    >
                        Configuration
                    </a>
                </div>

                <ul className="list-group">
                    <a className="list-group-item" href={docsHref} target="_blank" rel="noopener noreferrer">
                        Documentation &nbsp;
                        <span className="fa fa-external-link" />
                    </a>
                </ul>
            </div>

            <div className="col-sm-7">
                {saveSuccessful && <Alert bsStyle="success">Trigger saved successfully</Alert>}
                {saveError !== undefined && <Alert bsStyle="danger">{saveError}</Alert>}
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
