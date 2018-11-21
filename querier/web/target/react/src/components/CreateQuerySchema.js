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
      selectedId: [],
      qsName: [],
      selectorField: [],
      size: {},
      lengthType: {},
      maxArrayElements: {},
      fieldNames: [],
      querySchemaId: []
    };

    this.handleChange = this.handleChange.bind(this);
    this.handleSubmit = this.handleSubmit.bind(this);
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

  handleChange = event => {
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
          this.setState({
            fields: response.data.data.fields,
            selectedId: response.data.data.id
          });
          console.log(this.state.selectedId);
          console.log(this.state.fields);
        })
        .catch(error => console.log(error.response));
    }
    // Can't find key somehow
  };

  onChange = e => {
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
    console.log("newFiledNames", newFieldNames);
    this.setState({ fieldNames: newFieldNames });
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

    axios({
      method: "post",
      url: `/querier/api/rest/dataschemas/${this.state
        .selectedId}/queryschemas`,
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
        this.setState({
          querySchemaId: response.data.data.id
        });
        console.log(this.state.selectorField);
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
            <legend>Create QuerySchema</legend>
            <div>
              <label>
                <h4>QuerySchema name (will be saved as):</h4>
              </label>
              <input
                value={this.state.qsName}
                onChange={this.handleChangeQsName}
                placeholder="QuerySchema1"
                required
              />
            </div>
            <br />
            <label>Pick the dataschema to describe your data file:</label>{" "}
            <select
              name="schemaName"
              value={this.state.value}
              onChange={this.handleChange}
            >
              <option value="">Choose Dataschema..</option>
              {schemas &&
                schemas.length > 0 &&
                schemas.map(schema => {
                  return <option value={schema.name}>{schema.name}</option>;
                })}
            </select>
            <br />
            <br />
            <h2> DataSchema Fields </h2>
            <br />
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
            <br />
            <br />
            <br />
            <fieldset>
              <legend>Choose field names</legend>
              <br />
              <CheckboxGroup
                checkboxDepth={5}
                name="fieldNames"
                value={this.state.fieldNames}
                onChange={this.fieldNamesChanged}
              >
                {fields &&
                  fields.map(field => {
                    return (
                      <label>
                        <Checkbox value={field.name} />
                        {field.name}
                      </label>
                    );
                  })}
                <br />
              </CheckboxGroup>
            </fieldset>
            <br />
            <br />
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
            <br />
            {lastCheckedFieldName && (
              <div>
                <label>Size:</label>
                <input
                  value={this.state.size[lastCheckedFieldName] || 1}
                  onChange={this.onChange}
                  type="number"
                  name="size"
                  min="1"
                  required
                />
              </div>
            )}
            <br />
            {lastCheckedFieldName && (
              <div>
                <label>MaxArray Elements:</label>
                <input
                  value={this.state.maxArrayElements[lastCheckedFieldName] || 1}
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
            <br />
          </fieldset>

          <div className="btn-group">
            <span className="input-group-btn">
              <button
                className="btnSubmit"
                handleSubmit={this.handleSubmit}
                type="submit"
              >
                Submit
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
