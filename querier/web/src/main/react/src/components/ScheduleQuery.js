import React from "react";
import PropTypes from "prop-types";
import { withRouter } from "react-router";
import Datetime from "react-datetime";

import HomePage from "../routes/HomePage";
import CreateQuery from "./CreateQuery";
import QueryStatus from "./QueryStatus";
import "../css/ScheduleQuery.css";

import axios from "axios";
import moment from "moment";
require("react-datetime");

class ScheduleQuery extends React.Component {
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
      querySchemaName: [],
      queryName: [],
      queries: [],
      query: [],
      queryStatus: [],
      querySelfUri: [],
      dataSources: [],
      dataSourceDescription: [],
      dataSourceId: [],
      dataSourceName: [],
      dataSourceSelfUri: [],
      dataSourceType: [],
      date: new Date(),
      computeThreshold: 30000
    };

    this.handleChange = this.handleChange.bind(this);
    this.handleSubmit = this.handleSubmit.bind(this);
  }

  componentDidMount() {
    var querySelfUri = localStorage.getItem("querySelfUri");
    axios({
      method: "get",
      url: `${querySelfUri}`,
      headers: {
        Accept: "application/vnd.encryptedquery.enclave+json; version=1"
      }
    })
      .then(response => {
        console.log(response);
        this.setState({ query: response.data.data }, () => {
          console.log(this.state.query);
        });
        return axios({
          method: "get",
          url: `/querier/api/rest/dataschemas`,
          headers: {
            Accept: "application/vnd.encryptedquery.enclave+json; version=1"
          }
        });
      })
      .then(response => {
        console.log(response);
        this.setState({
          dataSchemas: response.data.data
        });
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
          this.setState(
            {
              querySchemaId: response.data.data.id,
              querySchemaUri: response.data.data.selfUri,
              querySchemaName: response.data.data.name,
              querySchemas: response.data.data,
              selectedId: dataSchema.id
            },
            () => {
              console.log(this.state.querySchemas);
              console.log(this.state.selectedId);
            }
          );
          return axios({
            method: "get",
            url: `/querier/api/rest/dataschemas/${this.state
              .selectedId}/datasources/`,
            headers: {
              Accept: "application/vnd.encryptedquery.enclave+json; version=1"
            }
          });
        })
        .then(response => {
          console.log(response);
          this.setState({
            dataSources: response.data.data
          });
        })
        .catch(error => console.log(error.response));
    }
  };

  dataSchemaChange = e => {
    const dataSource = this.state.dataSources.find(
      dataSource => dataSource.name === e.target.value
    );
    if (dataSource) {
      const { id, name } = dataSource;
      this.setState({
        dataSourceId: id,
        dataSourceName: name
      });
      axios({
        method: "get",
        url: `/querier/api/rest/dataschemas/${this.state
          .selectedId}/datasources/${id}`,
        headers: {
          Accept: "application/vnd.encryptedquery.enclave+json; version=1"
        }
      })
        .then(response => {
          console.log(response);
          this.setState(
            {
              dataSourceId: response.data.data.id,
              dataSourceSelfUri: response.data.data.selfUri,
              dataSourceName: response.data.data.name,
              dataSourceDescription: response.data.data.description,
              dataSourceType: response.data.data.processingMode
            },
            () => {
              console.log(this.state.dataSourceId);
              console.log(this.state.dataSourceSelfUri);
              console.log(this.state.dataSourceName);
              console.log(this.state.dataSourceDescription);
              console.log(this.state.dataSourceType);
            }
          );
          localStorage.setItem("dataSourceName", this.state.dataSourceName);
          localStorage.setItem("processingMode", this.state.dataSourceType);
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
          .selectedId}/queryschemas/${id}/queries/`,
        headers: {
          Accept: "application/vnd.encryptedquery.enclave+json; version=1"
        }
      })
        .then(response => {
          console.log(response);
          this.setState(
            {
              queries: response.data.data
            },
            () => {
              console.log(this.state.queries);
            }
          );
        })
        .catch(error => console.log(error.response));
    }
  };

  queryChange = e => {
    const query = this.state.queries.find(
      query => query.name === e.target.value
    );
    if (query) {
      const { id, name } = query;
      this.setState({
        queryId: id,
        queryName: name
      });
      axios({
        method: "get",
        url: `/querier/api/rest/dataschemas/${this.state
          .selectedId}/queryschemas/${this.state.querySchemaId}/queries/${id}`,
        headers: {
          Accept: "application/vnd.encryptedquery.enclave+json; version=1"
        }
      })
        .then(response => {
          console.log(response);
          this.setState(
            {
              queries: response.data.data,
              queryId: response.data.data[0].id,
              querySchemaUri: response.data.data[0].querySchemaUri,
              querySchedulesUri: response.data.data[0].SchedulesUri,
              querySelfUri: response.data.data[0].selfUri,
              queryStatus: response.data.data[0].status
            },
            () => {
              console.log(this.state.queries);
              console.log(this.state.queryId);
              console.log(this.state.querySchemaUri);
              console.log(this.state.querySchedulesUri);
              console.log(this.state.querySelfUri);
              console.log(this.state.queryStatus);
            }
          );
        })
        .catch(error => console.log(error.response));
    }
  };

  dateChange = date => this.setState({ date });

  onChange = e => {
    this.setState({ [e.target.name]: e.target.value });
    console.log([e.target.value]);
  };

  handleSubmit = e => {
    e.preventDefault();
    axios({
      method: "post",
      url: `/querier/api/rest/dataschemas/${this.state
        .selectedId}/queryschemas/${this.state.querySchemaId}/queries/${this
        .state.queryId}/schedules`,
      headers: {
        Accept: "application/vnd.encryptedquery.enclave+json; version=1",
        "Content-Type": "application/json"
      },
      data: JSON.stringify({
        startTime: this.state.date,
        parameters: {
          computeEncryptionMethod: this.state.computeEncryptionMethod
        },
        dataSource: {
          id: this.state.dataSourceId,
          selfUri: this.state.dataSourceSelfUri
        }
      })
    })
      .then(response => {
        console.log(response);
      })
      .catch(error => console.log(error.response));
    this.props.history.push(`/querier/querystatus`);
  };

  render() {
    const {
      dataSchemas,
      queryName,
      querySchemas,
      dataSources,
      dataSourceDescription,
      dataSourceId,
      dataSourceName,
      dataSourceTpye,
      queries,
      queryStatus
    } = this.state;
    const { match, location, histoy } = this.props;
    var yesterday = Datetime.moment().subtract(1, "day");
    var validDate = function(today) {
      return today.isAfter(yesterday);
    };

    return (
      <div>
        <HomePage />
        <form onSubmit={this.handleSubmit}>
          <div>
            <fieldset>
              <legend>Query Status</legend>
              <br />
              <div className="select-boxes">
                <div>
                  <label>
                    Pick a DataSchema to filter down available
                    DataSources/QuerySchemas:
                  </label>{" "}
                  <select
                    value={this.state.value}
                    onChange={this.handleChange}
                    required
                  >
                    <option value="">Choose DataSchema...</option>
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

                <div>
                  <label>
                    Pick a Data Source that pertains to the Data Schema:
                  </label>{" "}
                  <select
                    value={this.state.value}
                    onChange={this.handleChange}
                    onChange={this.dataSchemaChange}
                  >
                    <option value="">Choose a DataSource...</option>
                    {dataSources &&
                      dataSources.map(dataSource => {
                        return (
                          <option value={dataSource.name}>
                            {dataSource.name}
                          </option>
                        );
                      })}
                  </select>
                </div>
                <br />

                <div>
                  <label>Description of DataSource:</label>
                  <input value={this.state.dataSourceDescription} />
                </div>
                <br />

                <div>
                  <label>job Type of the selected DataSource:</label>
                  <input value={this.state.dataSourceType} />
                </div>
                <br />

                <div>
                  <label>Pick a QuerySchema to use for scheduling:</label>{" "}
                  <select
                    value={this.state.querySchemaName}
                    onChange={this.handleChange}
                    onChange={this.querySchemaChange}
                  >
                    <option value="">Choose QuerySchema...</option>
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

                <div>
                  <label>
                    Pick a Query that was created using the above DS/QS:
                  </label>{" "}
                  <select
                    value={this.state.value}
                    onChange={this.querySchemaChange}
                    onChange={this.queryChange}
                  >
                    <option value="">Choose a Query...</option>
                    {queries &&
                      queries.map(query => {
                        return <option value={query.name}>{query.name}</option>;
                      })}
                  </select>
                </div>
              </div>
            </fieldset>
            <br />
            <br />

            <fieldset>
              <legend>Parameters for Scheduling a Query</legend>
              <br />

              <br />
              <br />
            </fieldset>
            <br />

            <div>
              <label>Choose a start date/time:</label>
              <Datetime
                onChange={this.dateChange}
                value={this.state.date}
                input={false}
                isValidDate={validDate}
                open={true}
                utc={true}
                onClickDay={value => alert("day" + value + "clicked")}
              />
            </div>
          </div>

          <div className="btn-group">
            <span className="input-group-btn">
              <button
                className="btnSubmit"
                handleSubmit={this.handleSubmit}
                type="submit"
              >
                Submit Query
              </button>
              <button
                className="btnReset"
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

export default withRouter(ScheduleQuery);
