import React from "react";
import PropTypes from "prop-types";
import { withRouter } from "react-router";
import {
  Dropdown,
  Segment,
  Header,
  Divider,
  Container,
  Form,
  Input,
  Radio,
  Popup,
  Button
} from "semantic-ui-react";
import "../css/ScheduleQuery.css";
import Datetime from "react-datetime";

import PageHeading from "./FixedMenu";
import PageFooter from "./PageFooter.js";

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
      query: [],
      queryName: "",
      querySchemaName: "",
      datasources: [],
      dataSourceName: "",
      dataSourcesUri: "",
      processingMode: "",
      description: "",
      kafkaOffset: "",
      maxHitsPerSelector: 10000,
      windowLengthSeconds: 1,
      runTimeSeconds: 1,
      submissionValue: "",
      date: new Date(),
      checked: false
    };

    this.handleSubmit = this.handleSubmit.bind(this);
    this.handleKafkaOffsetSelectionChange = this.handleKafkaOffsetSelectionChange.bind(
      this
    );
  }

  async componentDidMount() {
    await this.getQueryData();
    await this.getDataSources();
    await this.dataSourceChange();
  }

  getQueryData = async () => {
    var querySelfUri = localStorage.getItem("querySelfUri");
    try {
      const { data } = await axios({
        method: "get",
        url: querySelfUri,
        headers: {
          Accept: "application/vnd.encryptedquery.enclave+json; version=1"
        }
      });
      console.log("Data from first call --->", data);
      const query = data.data;
      const includedData = data;

      const dataSourceInfo = includedData.included.filter(
        element => element.type === "DataSchema"
      );

      const dataSourcesUri = dataSourceInfo.map(function(included) {
        return included["dataSourcesUri"];
      });

      const dataSchemaName = dataSourceInfo.map(function(included) {
        return included["name"];
      });

      const querySchemaInfo = includedData.included.filter(
        element => element.type === "QuerySchema"
      );

      const querySchemaName = querySchemaInfo.map(function(included) {
        return included["name"];
      });

      this.setState(
        {
          query: query,
          queryName: query.name,
          dataSourcesUri: dataSourcesUri,
          dataSchemaName: dataSchemaName,
          querySchemaName: querySchemaName,
          schedulesUri: query.schedulesUri
        },
        () => {
          console.log("Query name ---> ", this.state.queryName);
          console.log("Datasources Uri ---> ", this.state.dataSourceUri);
          console.log("Dataschema name ---> ", this.state.dataSchemaName);
          console.log("Queryschema name --->", this.state.querySchemaName);
          console.log("SchedulesUri ---> ", this.state.schedulesUri);
        }
      );
    } catch (error) {
      console.error(Error(`Error getting query data: ${error.message}`));
    }
  };

  getDataSources = async () => {
    const { dataSourcesUri } = this.state;
    console.log("DataSourcesUri for next request ---> ", dataSourcesUri);
    //get data sources
    try {
      const { data } = await axios({
        method: "get",
        url: dataSourcesUri[0],
        headers: {
          Accept: "application/vnd.encryptedquery.enclave+json; version=1"
        }
      });
      const datasources = data.data;
      this.setState({
        datasources: datasources
      });
      console.log("datasources --->", datasources);
    } catch (error) {
      console.error(Error(`Error get data sources: ${error.message}`));
    }
  };

  handleChange = e => {
    this.setState({ [e.target.name]: e.target.value });
    console.log([e.target.name]);
  };

  dataSourceChange = async (e, { value }) => {
    this.setState({ dataSourceName: value }, () => {
      console.log("Chosen data source name ---> ", this.state.dataSourceName);
    });
    const source = this.state.datasources.find(source => source.name === value);
    if (source) {
      const { id, name, selfUri } = source;
      this.setState({
        dataSourceId: id,
        dataSourceName: name,
        dataSourceSelfUri: selfUri
      });
      console.log("Requested datasource selfUri =", selfUri);
      try {
        const { data } = await axios({
          method: "get",
          url: selfUri,
          headers: {
            Accept: "application/vnd.encryptedquery.enclave+json; version=1"
          }
        });
        console.log("Specific datasource data --->", data);
        const datasource = data.data;
        this.setState({
          datasource: datasource,
          processingMode: datasource.processingMode,
          description: datasource.description
        });
        console.log("datasource --->", datasource);
        console.log("processingMode ---> ", this.state.processingMode);
        console.log("description ---->", this.state.description);
      } catch (error) {
        console.error(
          Error(`Error getting data source data: ${error.message}`)
        );
      }
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

  handleSubmissionValue = (e, { value }) => {
    this.setState({ submissionValue: value }, () => {
      console.log("Submission value ---> ", this.state.submissionValue);
    });
  };

  handleSubmit = () => {
    const {
      processingMode,
      date,
      maxHitsPerSelector,
      dataSourceId,
      dataSourceSelfUri,
      windowLengthSeconds,
      kafkaOffset,
      checked,
      runTimeSeconds,
      schedulesUri
    } = this.state;
    if (processingMode === "Batch") {
      console.log("---------------------------------------------");
      console.log("Schedule posting to --->", schedulesUri);
      console.log("---------------------------------------------");
      axios({
        method: "post",
        url: schedulesUri,
        headers: {
          Accept: "application/vnd.encryptedquery.enclave+json; version=1",
          "Content-Type": "application/json"
        },
        data: JSON.stringify({
          startTime: date,
          parameters: {
            maxHitsPerSelector: maxHitsPerSelector
          },
          dataSource: {
            id: dataSourceId,
            selfUri: dataSourceSelfUri
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
        console.log("--------------------------------------------------------");
        console.log("Schedule posted to --->", schedulesUri);
        console.log("--------------------------------------------------------");
        axios({
          method: "post",
          url: schedulesUri,
          headers: {
            Accept: "application/vnd.encryptedquery.enclave+json; version=1",
            "Content-Type": "application/json"
          },
          data: JSON.stringify({
            startTime: date,
            parameters: {
              maxHitsPerSelector: maxHitsPerSelector,
              "stream.window.length.seconds": windowLengthSeconds,
              "kafka.start.offset": kafkaOffset,
              "stream.runtime.seconds": !checked ? runTimeSeconds : undefined
            },
            dataSource: {
              id: dataSourceId,
              selfUri: dataSourceSelfUri
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
      queryName,
      dataSchemaName,
      dataSourceDescription,
      dataSourceName,
      processingMode,
      querySchemaName,
      datasources,
      kafkaOffset,
      submissionValue,
      submissionRadioValue
    } = this.state;
    const { match, location, histoy } = this.props;
    var yesterday = Datetime.moment().subtract(1, "day");
    var validDate = function(today) {
      return today.isAfter(yesterday);
    };

    return (
      <div
        style={{ display: "flex", minHeight: "100vh", flexDirection: "column" }}
      >
        <PageHeading />
        <div style={{ flex: 1 }}>
          <div
            style={{
              display: "flex",
              flexDirection: "column",
              justifyContent: "center",
              alignItems: "center",
              flex: "1"
            }}
          >
            <div style={{ width: "900px" }}>
              <Form onSubmit={this.handleSubmit}>
                <Segment style={{ padding: "5em 1em" }} vertical>
                  <Divider horizontal>Query Schedule Creation</Divider>
                  <Form.Field>
                    <label>Query being used to create this schedule</label>
                    <Input fluid readOnly multiple={false} value={queryName} />
                  </Form.Field>
                  <Form.Field>
                    <label>
                      {" "}
                      The query was created using this dataSchema:{" "}
                    </label>
                    <Input fluid readOnly value={dataSchemaName} />
                  </Form.Field>
                  <Form.Field>
                    <label> Name of Query Schema: </label>
                    <Input fluid readOnly value={querySchemaName} />
                  </Form.Field>

                  <Form.Field required>
                    <label>Choose a datasource:</label>
                    <Dropdown
                      placeholder="Choose a datasource"
                      scrolling
                      clearable
                      fluid
                      selection
                      search
                      noResultsMessage="Search again"
                      multiple={false}
                      options={datasources.map(source => {
                        return {
                          key: source.id,
                          text: source.name,
                          value: source.name
                        };
                      })}
                      header="SELECT A DATASOURCE"
                      value={dataSourceName}
                      onChange={this.dataSourceChange}
                    />
                  </Form.Field>
                  <Form.Field>
                    <label> ProcessingMode of Data Source: </label>
                    <Input
                      value={processingMode}
                      onChange={this.handleChange}
                      readOnly
                    />
                  </Form.Field>
                </Segment>

                {processingMode && (
                  <Segment style={{ padding: "5em 1em" }} vertical>
                    <Divider horizontal>Parameters</Divider>
                    {processingMode && processingMode === "Batch" ? (
                      <BatchParams handleChange={this.handleChange} />
                    ) : processingMode && processingMode === "Streaming" ? (
                      <StreamingParams
                        onKafkaOffsetSelectionChange={
                          this.handleKafkaOffsetSelectionChange
                        }
                        kafkaOffset={kafkaOffset}
                        checked={this.state.checked}
                        handleChange={this.handleChange}
                        handleCheckboxChange={newCheckState =>
                          this.setState({ checked: newCheckState })
                        }
                      />
                    ) : null}
                  </Segment>
                )}

                <Segment style={{ padding: "5em 1em" }} vertical>
                  <Divider horizontal>Choose a Date </Divider>
                  <Container style={{ width: "255px" }}>
                    <div>
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
                  </Container>
                </Segment>

                <Form.Button
                  positive
                  fluid
                  content="Submit Schedule"
                  style={{ marginTop: "50px" }}
                />
              </Form>
            </div>
          </div>
        </div>
        <PageFooter />
      </div>
    );
  }
}

export default ScheduleQuery;
