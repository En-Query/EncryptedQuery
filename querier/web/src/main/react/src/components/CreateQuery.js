import React from "react";
import PropTypes from "prop-types";
import { withRouter } from "react-router";

import HomePage from "../routes/HomePage";
import CreateQuerySchema from "./CreateQuerySchema";
import "../css/CreateQuery.css";

import axios from "axios";
require("axios-debug")(axios);

class CreateQuery extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      dataSchemas: [],
      querySchemas: [],
      dataSources: [],
      dataChunkSize: 3,
      embedSelector: [],
      selectorField: [],
      selectorValue: "",
      selectorValues: []
    };

    this.dataSchemaChange = this.dataSchemaChange.bind(this);
    this.handleSubmit = this.handleSubmit.bind(this);
  }

  addSelectorValue = e => {
    e.stopPropagation();
    e.preventDefault();
    this.setState(prevState => ({
      selectorValues: [...prevState.selectorValues, prevState.selectorValue],
      selectorValue: ""
    }));
  };

  removeSelectorValue(index) {
    this.setState({
      selectorValues: this.state.selectorValues.filter((_, i) => i !== index)
    });
  }

  handleSelectorValueChange = ({ target: { value } }) => {
    //makes separate copy of array.
    this.setState({ selectorValue: value });
  };

  componentDidMount() {
    axios({
      method: "get",
      url: `/querier/api/rest/dataschemas`,
      headers: {
        Accept: "application/vnd.encryptedquery.enclave+json; version=1"
      }
    })
      .then(response => {
        console.log(response);
        this.setState({ dataSchemas: response.data.data });
      })
      .catch(error => console.log(error.response));
  }

  dataSchemaChange = e => {
    const dataSchema = this.state.dataSchemas.find(
      dataSchema => dataSchema.name === e.target.value
    );
    if (dataSchema) {
      axios({
        method: "get",
        url: `${dataSchema.selfUri}/queryschemas/`,
        headers: {
          Accept: "application/vnd.encryptedquery.enclave+json; version=1"
        }
      })
        .then(response => {
          console.log(response);
          console.log(JSON.stringify(dataSchema.selfUri));
          console.log(dataSchema.id);
          this.setState(
            {
              querySchemaSelfUri: response.data.data[0].selfUri,
              querySchemas: response.data.data
            },
            () => {
              console.log(this.state.querySchemaSelfUri);
              console.log(this.state.querySchemas);
            }
          );
        })
        .catch(error => console.log(error.response));
    }
  };

  querySchemaChange = e => {
    const querySchema = this.state.querySchemas.find(
      querySchema => querySchema.name === e.target.value
    );
    if (querySchema) {
      const { id, name, selfUri } = querySchema;
      this.setState(
        {
          querySchemaId: id,
          querySchemaName: name,
          querySchemaSelfUri: selfUri
        },
        () => {
          console.log(this.state.querySchemaId);
          console.log(this.state.querySchemaSelfUri);
        }
      );
      axios({
        method: "get",
        url: `${selfUri}`,
        headers: {
          Accept: "application/vnd.encryptedquery.enclave+json; version=1"
        }
      })
        .then(response => {
          console.log(response);
          this.setState(
            {
              querySchemaName: response.data.data.name,
              selectorField: response.data.data.selectorField,
              queriesUri: response.data.data.queriesUri
            },
            () => {
              console.log(this.state.querySchemaName);
              console.log(this.state.selectorField);
              console.log(this.state.queriesUri);
            }
          );
          localStorage.setItem("queriesUri", this.state.queriesUri);
        })
        .catch(error => console.log(error.response));
    }
  };

  onChange = e => {
    this.setState({ [e.target.name]: e.target.value });
    console.log([e.target.value]);
  };

  updateQueryName = e => {
    this.setState({ queryName: e.target.value });
  };

  updateEmbedSelector = e => {
    this.setState({ embedSelector: e.target.value });
  };

  handleSubmit(e) {
    e.preventDefault();
    const queriesUri = localStorage.getItem("queriesUri");
    axios({
      method: "post",
      url: `${queriesUri}`,
      headers: {
        Accept: "application/vnd.encryptedquery.enclave+json; version=1",
        "Content-Type": "application/json"
      },
      data: JSON.stringify({
        name: this.state.queryName,
        parameters: {
          dataChunkSize: this.state.dataChunkSize
        },
        selectorValues: this.state.selectorValues,
        embedSelector: this.state.embedSelector
      })
    })
      .then(response => {
        console.log(response);
      })
      .catch(error => console.log(error.response));
    this.props.history.push("/querier/querystatus");
  }

  render() {
    const {
      dataSchemas,
      dataSchemaId,
      dataSourceUri,
      dataSource,
      querySchemas,
      querySchemaId,
      querySchemaName,
      querySchemaUri,
      queryName,
      selectorValues,
      selectorField,
      bitSet,
      certainty,
      hashBitSize
    } = this.state;

    return (
      <div>
        <HomePage />
        <form onSubmit={this.handleSubmit}>
          <fieldset>
            <legend>Query Information</legend>
            <div className="CreateQuery-selectboxes">
              <div>
                <label> Query name (will be saved as): </label>
                <input
                  value={this.state.queryName}
                  onChange={this.updateQueryName}
                  placeholder="Name of Query (e.g. Phone Query)"
                  required
                />
              </div>
              <div>
                <label>
                  Pick a DataSchema to filter available QuerySchemas:
                </label>{" "}
                <select
                  value={this.state.value}
                  onChange={this.dataSchemaChange}
                >
                  <option value="">Choose DataSchema..</option>
                  {dataSchemas &&
                    dataSchemas.length > 0 &&
                    dataSchemas.map(dataSchema => {
                      return (
                        <option value={dataSchema.name}>
                          {dataSchema.name}
                        </option>
                      );
                    })}
                </select>
              </div>
              <div>
                <label>Pick the QuerySchema to build the query:</label>{" "}
                <select
                  value={this.state.value}
                  onChange={this.dataSchemaChange}
                  onChange={this.querySchemaChange}
                  required
                >
                  <option value="">Choose QuerySchema ... </option>
                  {querySchemas &&
                    querySchemas.map(querySchema => {
                      return (
                        <option value={querySchema.name}>
                          {querySchema.name}
                        </option>
                      );
                    })}
                </select>
              </div>
              <div>
                <label>SelectorField used to create querySchema:</label>
                <input value={this.state.selectorField} />
              </div>
            </div>
          </fieldset>
          <br />
          <br />
          <fieldset>
            <legend>Run Parameters For Query Generation</legend>
            <div className="CreateQuery-runParams">
              <div>
                <label>dataChunkSize:</label>
                <input
                  value={this.state.dataChunkSize}
                  onChange={this.onChange}
                  type="number"
                  name="dataChunkSize"
                  placeholder="1"
                  min="1"
                  step="1"
                  max="50"
                  required
                />
              </div>
              <div>
                <label>Embed Selector:</label>
                <select
                  value={this.state.embedSelector}
                  onChange={this.updateEmbedSelector}
                  required
                >
                  <option value="">True or False ...</option>
                  <option value="true">True</option>
                  <option value="false">False</option>
                </select>
              </div>
              <div>
                <label>Selector Values:</label>{" "}
                <input
                  type="text"
                  value={this.state.selectorValue}
                  placeholder="Enter selector value"
                  onChange={this.handleSelectorValueChange}
                  required={!this.state.selectorValues.length}
                />
                <button type="button" onClick={this.addSelectorValue}>
                  Add
                </button>
              </div>
              <ul>
                {this.state.selectorValues.map((value, index) => {
                  return (
                    <li key={index}>
                      {value}
                      <button
                        type="button"
                        onClick={this.removeSelectorValue.bind(this, index)}
                      >
                        Remove
                      </button>
                    </li>
                  );
                })}
              </ul>
            </div>
          </fieldset>
          <div className="btn-group">
            <span className="input-group-btn">
              <button
                className="btnCreate"
                handleSubmit={this.handleSubmit}
                type="submit"
              >
                Create Query
              </button>
              <button
                className="btnCancel"
                handleCancel={this.handleCancel}
                type="reset"
                onClick={() => {
                  alert(
                    "Are you sure you want to cancel? Doing so will reset this page."
                  );
                }}
              >
                Cancel
              </button>
            </span>
          </div>
        </form>
      </div>
    );
  }
}

export default CreateQuery;
