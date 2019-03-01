import React from "react";
import PropTypes from "prop-types";
import { withRouter } from "react-router";
import { Dropdown } from "semantic-ui-react";
import "semantic-ui-css/semantic.min.css";
import Datetime from "react-datetime";

import LogoSection from "./logo-section.js";
import VerticalNavBar from "./NavigationBar.js";
import CreateQuery from "./CreateQuery";
import QueryStatus from "./QueryStatus";
import "../css/ScheduleQuery.css";

import axios from "axios";
import moment from "moment";
require("react-datetime");

const offsetOptions = [
  {
    text: "From Earliest",
    value: "fromEarliest",
    description: "All data on kafka topic"
  },
  {
    text: "From Latest Commit",
    value: "fromLatestCommit",
    description: "Any new data"
  },
  {
    text: "From Latest",
    value: "fromLatest",
    description: "Data from the last time the query was ran"
  }
];

class StreamingParams extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      kafkaOffset: ""
    };
  }

  render() {
    const { kafkaOffset, onKafkaOffsetSelectionChange, checked } = this.props;

    return (
      <div className="params-boxes">
        <div>
          <label>maxHitsPerSelector:</label>
          <input
            type="number"
            name="maxHitsPerSelector"
            value={this.state.maxHitsPerSelector}
            onChange={this.props.handleChange}
            placeholder="10000"
            min="1"
            step="1"
            required
          />
        </div>
        <div>
          <label>Indefinite runTime:</label>
          <input
            type="checkbox"
            name="checked"
            checked={this.state.checked}
            onChange={e => this.props.handleCheckboxChange(e.target.checked)}
          />
        </div>
        {!checked && (
          <div>
            <label>runTimeSeconds:</label>
            <input
              type="number"
              name="runTimeSeconds"
              value={this.state.runTimeSeconds}
              onChange={this.props.handleChange}
              placeholder="1"
              min="1"
              step="1"
              required
            />
          </div>
        )}
        <div>
          <label>windowLengthSeconds:</label>
          <input
            type="number"
            name="windowLengthSeconds"
            value={this.state.windowLengthSeconds}
            onChange={this.props.handleChange}
            placeholder="1"
            min="1"
            step="1"
            required
          />
        </div>
        <div>
          <label>kafka offset: </label>
          <Dropdown
            placeholder="Select offset position"
            clearable
            fluid
            selection
            options={offsetOptions}
            header="PLEASE SELECT A KAFKA START OFFSET POSITION"
            label="kafkaOffset"
            onChange={onKafkaOffsetSelectionChange}
            value={kafkaOffset}
          />
        </div>
      </div>
    );
  }
}

class BatchParams extends React.Component {
  constructor(props) {
    super(props);

    this.state = {};
  }

  render() {
    return (
      <div classNam="params-boxes">
        <div>
          <label>maxHitsPerSelector:</label>
          <input
            type="number"
            name="maxHitsPerSelector"
            value={this.state.maxHitsPerSelector}
            onChange={this.props.handleChange}
            placeholder="10000"
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
      maxHitsPerSelector: 10000,
      windowLengthSeconds: 1,
      runTimeSeconds: 1,
      kafkaOffset: "",
      checked: false
    };

    this.handleSubmit = this.handleSubmit.bind(this);
    this.handleKafkaOffsetSelectionChange = this.handleKafkaOffsetSelectionChange.bind(
      this
    );
  }

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

  handleChange = e => {
    this.setState({ [e.target.name]: e.target.value });
    console.log([e.target.value]);
  };

  handleKafkaOffsetSelectionChange = (e, data) => {
    console.log(data.value);
    this.setState({ kafkaOffset: data.value }, () => {
      console.log(this.state.kafkaOffset);
    });
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
      this.props.history.push(`/querystatus`);
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
              "stream.window.length.seconds": this.state.windowLengthSeconds,
              "kafka.start.offset": this.state.kafkaOffset,
              "stream.runtime.seconds": !this.state.checked
                ? this.state.runTimeSeconds
                : undefined
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
        this.props.history.push(`/querystatus`);
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
      dataSourceProcessingMode,
      kafkaOffset
    } = this.state;
    const { match, location, histoy } = this.props;
    var yesterday = Datetime.moment().subtract(1, "day");
    var validDate = function(today) {
      return today.isAfter(yesterday);
    };

    return (
      <div>
        <LogoSection />
        <VerticalNavBar />
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
                      <StreamingParams
                        onKafkaOffsetSelectionChange={
                          this.handleKafkaOffsetSelectionChange
                        }
                        kafkaOffset={kafkaOffset}
                        checked={this.state.checked}
                        handleChange={this.handleChange}
                        handleCheckboxChange={newCheckState =>
                          this.setState({ checked: newCheckState })}
                      />
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
                utc={false}
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
