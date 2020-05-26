import React, { PureComponent } from 'react';
import { FileAttachmentForm } from '@labkey/components';
import { ActionURL } from '@labkey/api';

import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faImage, faTimesCircle } from '@fortawesome/free-solid-svg-icons';

class NoImageSelectedDisplay extends PureComponent {
    render () {
        return(
            <div className="sso-fields__null-image">
                <FontAwesomeIcon icon={faImage} color="#DCDCDC" size="6x" />
                <div className="sso-fields__null-image__text">None selected</div>
            </div>
        );
    }
}

interface ImgFileAttachForm_Props {
    text: string;
    imageUrl: string;
    onFileChange: Function;
    handleDeleteLogo: Function;
    fileTitle: string;
    canEdit: boolean;
    index: number;
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

    render() {
        const img = (
            <img
                className={
                    this.props.fileTitle == 'auth_header_logo'
                        ? 'sso-fields__image__header-logo'
                        : 'sso-fields__image__page-logo'
                }
                src={this.state.imageUrl}
                onError={() => {
                    this.setState({ imageUrl: null });
                }}
                alt="Sign in"
            />
        );

        return (
            <div className="sso-logo-pane-container">
                <div className="sso-fields__label">{this.props.text}</div>

                {this.props.canEdit ? (
                    <>
                        <div className="sso-fields__image-holder">
                            {this.state.imageUrl ? (
                                <div>
                                    <div>{img}</div>
                                    <div
                                        className={'sso-fields__delete-img-div'}
                                        onClick={() => {
                                            this.setState({ imageUrl: null });
                                            this.props.handleDeleteLogo(this.props.fileTitle);
                                        }}
                                    >
                                        <FontAwesomeIcon
                                            className={
                                                (this.props.fileTitle == 'auth_header_logo'
                                                    ? 'sso-fields__delete-img--header-logo'
                                                    : 'sso-fields__delete-img--page-logo') +
                                                ' sso-fields__delete-img clickable'
                                            }
                                            icon={faTimesCircle}
                                        />
                                    </div>
                                </div>
                            ) : (
                                <NoImageSelectedDisplay/>
                            )}
                        </div>

                        <div className="sso-fields__file-attachment" id={this.props.fileTitle}>
                            <FileAttachmentForm
                                index={this.props.index}
                                showLabel={false}
                                allowMultiple={false}
                                allowDirectories={false}
                                acceptedFormats=".jpg,.jpeg,.png,.gif,.tif"
                                showAcceptedFormats={false}
                                onFileChange={attachment => {
                                    this.props.onFileChange(attachment, this.props.fileTitle);
                                }}
                            />
                        </div>
                    </>
                ) : (
                    <div className="sso-fields__image-holder--view-only">
                        {this.state.imageUrl ? img : <NoImageSelectedDisplay/>}
                    </div>
                )}
            </div>
        );
    }
}

interface Props {
    headerLogoUrl: string;
    loginLogoUrl: string;
    onFileChange: Function;
    handleDeleteLogo: Function;
    canEdit: boolean;
}

export class SSOFields extends PureComponent<Props> {
    render() {
        return (
            <>
                <ImageAndFileAttachmentForm
                    text="Page Header Logo"
                    imageUrl={ActionURL.getBaseURL(true) + this.props.headerLogoUrl}
                    onFileChange={this.props.onFileChange}
                    handleDeleteLogo={this.props.handleDeleteLogo}
                    fileTitle="auth_header_logo"
                    canEdit={this.props.canEdit}
                    index={1}
                />

                <div className="sso-fields__spacer" />

                <ImageAndFileAttachmentForm
                    text="Login Page Logo"
                    imageUrl={ActionURL.getBaseURL(true) + this.props.loginLogoUrl}
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
