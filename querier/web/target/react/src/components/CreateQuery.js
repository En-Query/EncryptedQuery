import React from "react";
import PropTypes from "prop-types";
import { withRouter } from "react-router";

import HomePage from "../routes/HomePage";
import CreateQuerySchema from "./CreateQuerySchema";
import RunParameters from "./RunParameters";
import "../css/CreateQuery.css";

import axios from "axios";
require("axios-debug")(axios);

class CreateQuery extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      dataSchemas: [],
      dataSchemaId: [],
      dataSchemaUri: [],
      dataSchemaName: [],
      querySchemas: [],
      querySchemaUri: [],
      querySchemaId: [],
      queryName: [],
      querySchemaName: [],
      queriesUri: [],
      dataSources: [],
      dataSourceName: [],
      dataSourceUri: [],
      selectedId: [],
      action: [],
      bitSet: 32,
      certainty: 128,
      hashBitSize: 15,
      paillierBitSize: 3072,
      dataPartitionBitSize: 8,
      embedSelector: [],
      selectorValues: [],
      selectorField: []
    };

    this.handleChange = this.handleChange.bind(this);
    this.handleSubmit = this.handleSubmit.bind(this);
  }

  addSelectorValues = e => {
    e.stopPropagation();
    e.preventDefault();
    this.setState(prevState => ({
      selectorValues: [...prevState.selectorValues, ""]
    }));
  };

  removeSelectorValue(index) {
    this.setState({
      selectorValues: this.state.selectorValues.filter((_, i) => i !== index)
    });
  }

  handleSelectorValueChange = index => ({ target: { value } }) => {
    //makes separate copy of array.
    const selectorValues = [...this.state.selectorValues];
    selectorValues[index] = value;
    this.setState({ selectorValues });
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

  handleChange = e => {
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
              querySchemaId: response.data.data[0].id,
              querySchemaName: response.data.data[0].name,
              querySchemaUri: response.data.data[0].selfUri,
              querySchemas: response.data.data,
              selectedId: dataSchema.id
            },
            () => {
              console.log(this.state.querySchemaId);
              console.log(this.state.querySchemaName);
              console.log(this.state.querySchemaUri);
              console.log(this.state.querySchemas);
              console.log(this.state.selectedId);
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
      const { id, name } = querySchema;
      this.setState({
        querySchemaId: id,
        querySchemaName: name
      });
      axios({
        method: "get",
        url: `/querier/api/rest/dataschemas/${this.state
          .selectedId}/queryschemas/${id}`,
        headers: {
          Accept: "application/vnd.encryptedquery.enclave+json; version=1"
        }
      })
        .then(response => {
          console.log(response);
          this.setState(
            {
              querySchemaId: response.data.data.id,
              querySchemaUri: response.data.data.selfUri,
              querySchemaName: response.data.data.name,
              selectorField: response.data.data.selectorField
            },
            () => {
              console.log(this.state.querySchemaId);
              console.log(this.state.querySchemaUri);
              console.log(this.state.querySchemaName);
              console.log(this.state.selectorField);
            }
          );
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

  updateAction = e => {
    this.setState({ action: e.target.value });
  };

  updateMethod = e => {
    this.setState({ method: e.target.value });
  };

  handleSubmit(e) {
    e.preventDefault();
    axios({
      method: "post",
      url: `${this.state.querySchemaUri}/queries`,
      headers: {
        Accept: "application/vnd.encryptedquery.enclave+json; version=1",
        "Content-Type": "application/json"
      },
      data: JSON.stringify({
        name: this.state.queryName,
        parameters: {
          dataPartitionBitSize: this.state.dataPartitionBitSize,
          hashBitSize: this.state.hashBitSize,
          paillierBitSize: this.state.paillierBitSize,
          bitSet: this.state.bitSet,
          certainty: this.state.certainty
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
            <div>
              <div>
                <label>
                  {" "}
                  <h4>Query name (will be saved as):</h4>
                </label>
                <input
                  value={this.state.queryName}
                  onChange={this.updateQueryName}
                  placeholder="Name of Query (e.g. Phone Query)"
                  required
                />
              </div>
              <br />
              <div>
                <label>
                  Pick a DataSchema to filter down available QuerySchemas:
                </label>{" "}
                <select value={this.state.value} onChange={this.handleChange}>
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
              <br />
              <br />
              <div>
                <label>Pick the QuerySchema to build the query:</label>{" "}
                <select
                  value={this.state.value}
                  onChange={this.handleChange}
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
              <br />
              <br />
              <label>SelectorField used to create querySchema:</label>
              <input value={this.state.selectorField} />
              <br />
            </div>
          </fieldset>
          <br />
          <br />
          <fieldset>
            <legend>Run Parameters For Query Generation</legend>
            <br />
            <br />
            <label>BitSet:</label>
            <input
              value={this.state.bitSet}
              onChange={this.onChange}
              type="number"
              name="bitSet"
              min="0"
              placeholder="32"
              required
            />
            <br />
            <br />
            <label>Certainty:</label>
            <input
              value={this.state.certainty}
              onChange={this.onChange}
              type="number"
              name="certainty"
              min="0"
              placeholder="128"
              required
            />
            <br />
            <br />
            <label>hashBitSize:</label>
            <input
              value={this.state.hashBitSize}
              onChange={this.onChange}
              type="number"
              name="hashBitSize"
              placeholder="15"
              min="8"
              max="22"
              required
            />
            <br />
            <br />
            <label>paillierBitSize:</label>
            <input
              value={this.state.paillierBitSize}
              onChange={this.onChange}
              type="number"
              name="paillierBitSize"
              min="0"
              max="3072"
              placeholder="3072"
              step="1024"
              required
            />
            <br />
            <br />
            <label>dataPartitionBitSize:</label>
            <input
              value={this.state.dataPartitionBitSize}
              onChange={this.onChange}
              type="number"
              name="dataPartitionBitSize"
              placeholder="8"
              min="8"
              step="8"
              max="32"
              required
            />
            <br />
            <br />
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
            <br />
            <br />
            <div>
              <label>Selector Values:</label>{" "}
              <button type="button" onClick={this.addSelectorValues}>
                Add
              </button>
              {this.state.selectorValues.map((value, index) => (
                <input
                  key={index}
                  type="text"
                  value={value}
                  placeholder="Enter slector values"
                  onChange={this.handleSelectorValueChange(index)}
                  required
                />
              ))}
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
              <br />
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
