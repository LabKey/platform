import React, { FC, memo, PureComponent, ReactNode } from 'react';
import { FileAttachmentForm } from '@labkey/components';
import classNames from 'classnames';

const NoImageSelectedDisplay: FC = memo(() => (
    <div className="sso-fields__null-image">
        <span className="fa fa-5x fa-image" style={{ color: '#DCDCDC' }} />
        <div className="sso-fields__null-image__text">None selected</div>
    </div>
));

interface ImgFileAttachForm_Props {
    canEdit: boolean;
    fileTitle: string;
    handleDeleteLogo: Function;
    imageUrl: string;
    index: number;
    onFileChange: Function;
    text: string;
}

interface ImgFileAttachForm_State {
    imageUrl: string;
}

export class ImageAndFileAttachmentForm extends PureComponent<ImgFileAttachForm_Props, ImgFileAttachForm_State> {
    constructor(props) {
        super(props);
        this.state = {
            imageUrl: this.props.imageUrl,
        };
    }

    render(): ReactNode {
        const { canEdit, fileTitle, handleDeleteLogo, onFileChange } = this.props;
        const { imageUrl } = this.state;
        const iconClassName = classNames('fa', 'fa-times-circle', 'sso-fields__delete-img clickable', {
            'sso-fields__delete-img--header-logo': fileTitle === 'auth_header_logo',
            'sso-fields__delete-img--page-logo': fileTitle !== 'auth_header_logo',
        });
        const imageClassName = classNames({
            'sso-fields__image__header-logo': fileTitle === 'auth_header_logo',
            'sso-fields__image__page-log': fileTitle !== 'auth_header_logo',
        });
        const img = (
            <img
                className={imageClassName}
                src={imageUrl}
                onError={() => this.setState({ imageUrl: null })}
                alt="Sign in"
            />
        );

        return (
            <div className="sso-logo-pane-container">
                <div className="sso-fields__label">{this.props.text}</div>

                {canEdit ? (
                    <>
                        <div className="sso-fields__image-holder">
                            {imageUrl ? (
                                <div>
                                    <div>{img}</div>
                                    <div
                                        className="sso-fields__delete-img-div"
                                        onClick={() => {
                                            this.setState({ imageUrl: null });
                                            handleDeleteLogo(fileTitle);
                                        }}
                                    >
                                        <span className={iconClassName} />
                                    </div>
                                </div>
                            ) : (
                                <NoImageSelectedDisplay />
                            )}
                        </div>

                        <div className="sso-fields__file-attachment" id={fileTitle}>
                            <FileAttachmentForm
                                index={this.props.index}
                                showLabel={false}
                                allowMultiple={false}
                                allowDirectories={false}
                                acceptedFormats=".jpg,.jpeg,.png,.gif,.tif"
                                showAcceptedFormats={false}
                                onFileChange={attachment => {
                                    onFileChange(attachment, fileTitle);
                                }}
                            />
                        </div>
                    </>
                ) : (
                    <div className="sso-fields__image-holder--view-only">
                        {imageUrl ? img : <NoImageSelectedDisplay />}
                    </div>
                )}
            </div>
        );
    }
}

interface Props {
    canEdit: boolean;
    handleDeleteLogo: Function;
    headerLogoUrl: string;
    loginLogoUrl: string;
    onFileChange: Function;
}

export class SSOFields extends PureComponent<Props> {
    render() {
        return (
            <>
                <ImageAndFileAttachmentForm
                    text="Page Header Logo"
                    imageUrl={this.props.headerLogoUrl}
                    onFileChange={this.props.onFileChange}
                    handleDeleteLogo={this.props.handleDeleteLogo}
                    fileTitle="auth_header_logo"
                    canEdit={this.props.canEdit}
                    index={1}
                />

                <div className="sso-fields__spacer" />

                <ImageAndFileAttachmentForm
                    text="Login Page Logo"
                    imageUrl={this.props.loginLogoUrl}
                    onFileChange={this.props.onFileChange}
                    handleDeleteLogo={this.props.handleDeleteLogo}
                    fileTitle="auth_login_page_logo"
                    canEdit={this.props.canEdit}
                    index={2}
                />
            </>
        );
    }
}
