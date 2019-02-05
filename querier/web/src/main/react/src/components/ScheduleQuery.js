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

class StreamingParams extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      maxHitsPerSelector: 1,
      runTimeSeconds: 1,
      windowLengthSeconds: 1,
      groupId: ""
    };

    this.handleChange = this.handleChange.bind(this);
  }

  handleChange = e => {
    this.setState({ [e.target.name]: e.target.value });
    this.props.handleChange(e);
    console.log([e.target.value]);
  };

  render() {
    return (
      <div className="params-boxes">
        <div>
          <label>maxHitsPerSelector:</label>
          <input
            type="number"
            name="maxHitsPerSelector"
            value={this.state.maxHitsPerSelector}
            onChange={this.handleChange}
            placeholder="1"
            min="1"
            step="1"
            required
          />
        </div>
        <div>
          <label>runTimeSeconds:</label>
          <input
            type="number"
            name="runTimeSeconds"
            value={this.state.runTimeSeconds}
            onChange={this.handleChange}
            placeholder="1"
            min="1"
            step="1"
            required
          />
        </div>
        <div>
          <label>windowLengthSeconds:</label>
          <input
            type="number"
            name="windowLengthSeconds"
            value={this.state.windowLengthSeconds}
            onChange={this.handleChange}
            placeholder="1"
            min="1"
            step="1"
            required
          />
        </div>
        <div>
          <label>groupId (Optional):</label>
          <input
            type="text"
            name="groupId"
            value={this.state.groupId}
            onChange={this.handleChange}
            placeholder="Enter groupId"
          />
        </div>
      </div>
    );
  }
}

class BatchParams extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      maxHitsPerSelector: 1
    };

    this.handleChange = this.handleChange.bind(this);
  }

  handleChange = e => {
    this.setState({ [e.target.name]: e.target.value });
    this.props.handleChange(e);
    console.log([e.target.value]);
  };

  render() {
    return (
      <div classNam="params-boxes">
        <div>
          <label>maxHitsPerSelector:</label>
          <input
            type="number"
            name="maxHitsPerSelector"
            value={this.state.maxHitsPerSelector}
            onChange={this.handleChange}
            placeholder="1"
            min="1"
            step="1"
            required
          />
        </div>
      </div>
    );
  }
}

class ScheduleQuery extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      dataSchemas: [],
      querySchemas: [],
      queries: [],
      query: [],
      dataSources: [],
      date: new Date(),
      dataProcessingMode: "",
      maxHitsPerSelector: 1,
      windowLengthSeconds: 1,
      runTimeSeconds: 1,
      groupId: ""
    };

    this.handleSubmit = this.handleSubmit.bind(this);
    this.handleChange = this.handleChange.bind(this);
  }

  /*
    The 3 methods below are for handling [K = V] pairs and adding them to the scheduling parameters.
    This will be implemented as soon as additional parameters are defined.
  */
  // addParamterValues = e => {
  //   e.stopPropagation();
  //   e.preventDefault();
  //   this.setState(
  //     prevState({
  //       parameterValues: [...prevState.parameterValues, ""]
  //     })
  //   );
  // };

  // removeParameterValues(index) {
  //   this.setState({
  //     parameterValues: this.state.parameterValues.filter((_, i) !== index)
  //   });
  // }

  // handleParameterValueChange = index => ({ target: { value } }) => {
  //   //copy array
  //   const parameterValues = [...this.state.parameterValues];
  //   parameterValues[index] = value;
  //   this.setState({ parameterValues });
  // };

  componentDidMount() {
    var querySelfUri = localStorage.getItem("querySelfUri");
    console.log(querySelfUri);
    axios({
      method: "get",
      url: `${querySelfUri}`,
      headers: {
        Accept: "application/vnd.encryptedquery.enclave+json; version=1"
      }
    })
      .then(response => {
        console.log(response);

        const dataSourceInfo = response.data.included.filter(
          element => element.type === "DataSchema"
        );
        const dataSourcesUri = dataSourceInfo.map(function(included) {
          return included["dataSourcesUri"];
        });
        const dataSchemaInfo = response.data.included.filter(
          element => element.type === "DataSchema"
        );
        const dataSchemaName = dataSchemaInfo.map(function(included) {
          return included["name"];
        });
        const querySchemaInfo = response.data.included.filter(
          element => element.type === "QuerySchema"
        );
        const querySchemaName = querySchemaInfo.map(function(included) {
          return included["name"];
        });
        this.setState(
          {
            query: response.data.data,
            queryName: response.data.data.name,
            dataSourcesUri: dataSourcesUri,
            schedulesUri: response.data.data.schedulesUri,
            dataSchemaName: dataSchemaName,
            querySchemaName: querySchemaName
          },
          () => {
            console.log(this.state.query);
            console.log(this.state.queryName);
            console.log(this.state.dataSchemaName);
            console.log(this.state.querySchemaName);
            console.log(this.state.dataSourcesUri);
            console.log(this.state.schedulesUri);
          }
        );
        localStorage.setItem("schedulesUri", this.state.schedulesUri);
        localStorage.setItem("dataSourcesUri", this.state.dataSourcesUri);
        const dataSourceUri = localStorage.getItem("dataSourcesUri");
        return axios({
          method: "get",
          url: `${dataSourceUri}`,
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
      .catch(error => console.log(error));
  }

  dataSourceChange = e => {
    const dataSourceName = this.state.dataSources.find(
      dataSourceName => dataSourceName.name === e.target.value
    );
    if (dataSourceName) {
      const { selfUri, name, id } = dataSourceName;
      this.setState({
        dataSoureName: name,
        dataSourceSelfUri: selfUri,
        dataSourceId: id
      });
      axios({
        method: "get",
        url: `${selfUri}`,
        headers: {
          Accept: "application/vnd.encryptedquery.enclave+json; version=1"
        }
      }).then(response => {
        console.log(response);
        this.setState(
          {
            dataSourceDescription: response.data.data.description,
            dataSourceProcessingMode: response.data.data.processingMode,
            dataSourceId: response.data.data.id
          },
          () => {
            console.log(this.state.dataSourceDescription);
            console.log(this.state.dataSourceProcessingMode);
            console.log(this.state.dataSourceId);
          }
        );
      });
    }
  };

  dateChange = date => this.setState({ date });

  onChange = e => {
    this.setState({ [e.target.name]: e.target.value });
    console.log([e.target.value]);
  };

  handleChange = ({ target: { name, value } }) => {
    this.setState({ [name]: value });
    console.log([name, value]);
  };

  handleSubmit = () => {
    const schedulesUri = localStorage.getItem("schedulesUri");
    if (this.state.dataSourceProcessingMode === "Batch") {
      axios({
        method: "post",
        url: `${schedulesUri}`,
        headers: {
          Accept: "application/vnd.encryptedquery.enclave+json; version=1",
          "Content-Type": "application/json"
        },
        data: JSON.stringify({
          startTime: this.state.date,
          parameters: {
            maxHitsPerSelector: this.state.maxHitsPerSelector
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
    } else {
      {
        axios({
          method: "post",
          url: `${schedulesUri}`,
          headers: {
            Accept: "application/vnd.encryptedquery.enclave+json; version=1",
            "Content-Type": "application/json"
          },
          data: JSON.stringify({
            startTime: this.state.date,
            parameters: {
              maxHitsPerSelector: this.state.maxHitsPerSelector,
              "stream.runtime.seconds": this.state.runTimeSeconds,
              "stream.window.length.seconds": this.state.windowLengthSeconds,
              "kafka.groupId": this.state.groupId
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
      }
    }
  };

  render() {
    const {
      dataSchemas,
      querySchemas,
      queryName,
      dataSchemaName,
      dataSources,
      dataSourceDescription,
      dataSourceId,
      dataSourceName,
      dataSourceType,
      queries,
      queryStatus,
      dataSourceProcessingMode
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
              <legend>Query Schedule Creation</legend>
              <div className="schedule-select-boxes">
                <div>
                  <label>
                    This query being used to create the following schedule:
                  </label>{" "}
                  <input value={this.state.queryName} />
                </div>

                <div>
                  <label>
                    This query was created using this DataSchema:
                  </label>{" "}
                  <input value={this.state.dataSchemaName} />
                </div>

                <div>
                  <label>
                    This query was created using this QuerySchema:
                  </label>{" "}
                  <input value={this.state.querySchemaName} />
                </div>

                <div>
                  <label>
                    Pick a Data Source that pertains to the DataSchema:
                  </label>{" "}
                  <select
                    value={this.state.value}
                    onChange={this.dataSourceChange}
                  >
                    <option value="">Choose a DataSource ...</option>
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
                {dataSources && (
                  <div>
                    <label>Description of DataSource:</label>
                    <input value={this.state.dataSourceDescription} />
                  </div>
                )}
                {dataSources && (
                  <div>
                    <label>DataSourceProcessingMode type:</label>
                    <input value={this.state.dataSourceProcessingMode} />
                  </div>
                )}
              </div>
            </fieldset>

            {dataSourceProcessingMode && (
              <div className="params-boxes">
                <fieldset>
                  <legend>Parameters</legend>
                  <div>
                    {dataSourceProcessingMode &&
                    dataSourceProcessingMode === "Batch" ? (
                      <BatchParams handleChange={this.handleChange} />
                    ) : dataSourceProcessingMode &&
                    dataSourceProcessingMode === "Streaming" ? (
                      <StreamingParams handleChange={this.handleChange} />
                    ) : null}
                  </div>
                </fieldset>
              </div>
            )}

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
                Submit Schedule
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
