import React from "react";
import ReactDOM from "react-dom";
import PropTypes from "prop-types";
import { withRouter } from "react-router-dom";
import { Checkbox, CheckboxGroup } from "react-checkbox-group";

import HomePage from "../routes/HomePage";
import CreateQuery from "./CreateQuery";
import "../css/CreateQuerySchema.css";

import axios from "axios";
require("axios-debug")(axios);

class CreateQuerySchema extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      schemas: [],
      fields: [],
      selectorField: [],
      size: {},
      lengthType: {},
      maxArrayElements: {},
      fieldNames: []
    };

    this.onDataSchemaChange = this.onDataSchemaChange.bind(this);
    this.handleSubmit = this.handleSubmit.bind(this);
    this.handleCancel = this.handleCancel.bind(this);
  }

  componentDidMount() {
    axios({
      method: "get",
      url: "/querier/api/rest/dataschemas",
      headers: {
        Accept: "application/vnd.encryptedquery.enclave+json; version=1"
      }
    })
      .then(response => {
        console.log(response);
        this.setState({ schemas: response.data.data });
      })
      .catch(error => console.log(error.response));
  }

  onDataSchemaChange = event => {
    const schema = this.state.schemas.find(
      schema => schema.name === event.target.value
    );
    //console.log(schema.uri)
    if (schema) {
      //schema now has the entire object
      axios({
        method: "get",
        url: `${schema.selfUri}`,
        headers: {
          Accept: "application/vnd.encryptedquery.enclave+json; version=1"
        }
      })
        .then(response => {
          console.log(response);
          this.setState(
            {
              fields: response.data.data.fields,
              selectedId: response.data.data.id,
              querySchemasUri: response.data.data.querySchemasUri
            },
            () => {
              console.log(this.state.selectedId);
              console.log(this.state.querySchemasUri);
            }
          );
          localStorage.setItem("querySchemasUri", this.state.querySchemasUri);
        })
        .catch(error => console.log(error.response));
    }
    // Can't find key somehow
  };

  onSizeChange = e => {
    e.persist();
    const { fieldNames } = this.state;
    const lastCheckedFieldName = fieldNames[fieldNames.length - 1];
    this.setState(prevState => {
      return {
        size: {
          ...prevState.size,
          [lastCheckedFieldName]: e.target.value
        }
      };
    });
    console.log([e.target.name]);
  };

  onChangeMaxArrayElements = e => {
    e.persist();
    const { fieldNames } = this.state;
    const lastCheckedFieldName = fieldNames[fieldNames.length - 1];
    this.setState(prevState => {
      return {
        maxArrayElements: {
          ...prevState.maxArrayElements,
          [lastCheckedFieldName]: e.target.value
        }
      };
    });
    console.log([e.target.name]);
  };

  handleChangeQsName = event => {
    this.setState({ qsName: event.target.value });
  };

  handleSelectorFieldChange = event => {
    this.setState({ selectorField: event.target.value });
  };

  handleCancel = event => {
    event.target.reset();
  };

  fieldNamesChanged = newFieldNames => {
    this.setState({
      submitDisabled: !newFieldNames.length,
      fieldNames: newFieldNames,
      size: {
        [newFieldNames[newFieldNames.length - 1]]: 1,
        ...this.state.size
      },
      maxArrayElements: {
        [newFieldNames[newFieldNames.length - 1]]: 1,
        ...this.state.maxArrayElements
      }
    });
  };

  updateSelectorField = e => {
    this.setState({ selectorField: e.target.value });
  };

  updateLengthType = e => {
    e.persist();
    const { fieldNames } = this.state;
    const lastCheckedFieldName = fieldNames[fieldNames.length - 1];
    console.log("e", e);
    this.setState(prevState => {
      let lengthType = { ...prevState.lengthType };
      lengthType[lastCheckedFieldName] = e.target.value;
      return {
        lengthType
      };
    });
    console.log(this.state.lengthType);
  };

  handleSubmit = event => {
    event.preventDefault();
    const fields = this.state.fieldNames.map(fieldName => ({
      name: fieldName,
      lengthType: this.state.lengthType[fieldName],
      size: this.state.size[fieldName],
      maxArrayElements: this.state.maxArrayElements[fieldName]
    }));
    const querySchemaUri = localStorage.getItem("querySchemasUri");
    axios({
      method: "post",
      url: `${querySchemaUri}`,
      headers: {
        Accept: "application/vnd.encryptedquery.enclave+json; version=1",
        "Content-Type": "application/json"
      },
      data: JSON.stringify({
        name: this.state.qsName,
        selectorField: this.state.selectorField,
        fields: fields
      })
    })
      .then(response => {
        console.log(response);
      })
      .catch(error => console.log(error.response));
    this.props.history.push("/querier/createquery");
  };

  render() {
    const {
      schemas,
      fields,
      qsName,
      selectorField,
      size,
      lengthType,
      maxArrayElements,
      fieldNames
    } = this.state;
    const lastCheckedFieldName = fieldNames[fieldNames.length - 1];

    return (
      <div>
        <HomePage />
        <form onSubmit={this.handleSubmit}>
          <fieldset>
            <legend>
              <h2>QuerySchema Information</h2>
            </legend>
            <div className="CQS-info-boxes">
              <div>
                <label>QuerySchema name (will be saved as):</label>
                <input
                  value={this.state.qsName}
                  onChange={this.handleChangeQsName}
                  placeholder="example -- QuerySchema1"
                  required
                />
              </div>
              <div>
                <label>
                  {" "}
                  Pick the dataschema to describe your data file:
                </label>{" "}
                <select
                  name="schemaName"
                  value={this.state.value}
                  onChange={this.onDataSchemaChange}
                >
                  <option value="">Choose Dataschema ...</option>
                  {schemas &&
                    schemas.length > 0 &&
                    schemas.map(schema => {
                      return <option value={schema.name}>{schema.name}</option>;
                    })}
                </select>
              </div>
            </div>
          </fieldset>
          <div>
            <fieldset>
              <legend>
                <h2> DataSchema Fields </h2>
              </legend>
              <div className="selectorField-div">
                <div>
                  <label>Selector Field:</label>{" "}
                  <select
                    value={this.state.selectorField}
                    onChange={this.updateSelectorField}
                    required
                  >
                    <option value="">Pick Selector Field...</option>
                    {fields &&
                      fields.map(field => {
                        return <option value={field.name}>{field.name}</option>;
                      })}
                  </select>
                </div>{" "}
                {selectorField && (
                  <fieldset>
                    <div>
                      <legend>Choose field names</legend>
                      <CheckboxGroup
                        checkboxDepth={5}
                        name="fieldNames"
                        value={this.state.fieldNames}
                        onChange={this.fieldNamesChanged}
                        required
                      >
                        {fields &&
                          fields.map(field => {
                            return (
                              <li>
                                <Checkbox value={field.name} />
                                {field.name}
                              </li>
                            );
                          })}
                      </CheckboxGroup>
                    </div>
                  </fieldset>
                )}
              </div>
              <div className="CQS-input-boxes">
                {lastCheckedFieldName && (
                  <div>
                    <label>Length Type:</label>
                    <select
                      value={this.state.lengthType[lastCheckedFieldName] || ""}
                      onChange={this.updateLengthType}
                      required
                    >
                      <option value="">Select Length Type...</option>
                      <option value="fixed">Fixed</option>
                      <option value="variable">Variable</option>
                    </select>
                  </div>
                )}
                {lastCheckedFieldName && (
                  <div>
                    <label>Size:</label>
                    <input
                      value={this.state.size[lastCheckedFieldName] || 1}
                      onChange={this.onSizeChange}
                      type="number"
                      name="size"
                      min="1"
                      placeholder="1"
                      required
                    />
                  </div>
                )}
                {lastCheckedFieldName && (
                  <div>
                    <label>MaxArray Elements:</label>
                    <input
                      value={
                        this.state.maxArrayElements[lastCheckedFieldName] || 1
                      }
                      onChange={this.onChangeMaxArrayElements}
                      type="number"
                      name="maxArrayElements"
                      placeholder="1"
                      min="1"
                      max="100"
                      required
                    />
                  </div>
                )}
              </div>
            </fieldset>
          </div>

          <div className="btn-group">
            <span className="input-group-btn">
              <button
                className="btnSubmit"
                handleSubmit={this.handleSubmit}
                type="submit"
                disabled={this.state.submitDisabled}
              >
                Submit{" "}
              </button>
              <button
                className="btnReset"
                handleCancel={this.handleCancel}
                type="reset"
                onClick={() => {
                  alert("Clearing current field values.");
                }}
              >
                Reset
              </button>
            </span>
          </div>
        </form>
      </div>
    );
  }
}

export default withRouter(CreateQuerySchema);
