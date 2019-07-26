import React from "react";
import PropTypes from "prop-types";
import { withRouter } from "react-router-dom";
import axios from "axios";
import format from "date-fns/format";

import createHistory from "history/createBrowserHistory";
import PageHeading from "./FixedMenu";
import PageFooter from "./PageFooter";

import styled from "styled-components";

import {
  Table,
  Button,
  Segment,
  Header,
  Icon,
  Divider,
  Message
} from "semantic-ui-react";
import _ from "lodash";

const indexTuple = source =>
  source.reduce((prev, [k, v]) => ((prev[k] = v), prev), {});

class QuerySchedulesStatus extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      schedules: [],
      dataSourceNames: [],
      resultUris: {},
      column: null,
      direciton: null,
      exportSlider: false
    };

    //this.handleChange = this.handleChange.bind(this)
  }

  async componentDidMount() {
    await this.getSchedulesList();
    await this.getScheduleResultUris();
    await this.getDataSourceNames();
    this.interval = setInterval(() => this.getSchedulesList(), 30000);
  }

  componentWillUnmount() {
    //clear the interval
    clearInterval(this.interval);
  }

  getSchedulesList = async () => {
    const querySelfUri = localStorage.getItem("querySelfUri");
    try {
      const data = await axios({
        method: "get",
        url: querySelfUri,
        headers: {
          Accept: "application/vnd.encryptedquery.enclave+json; version=1"
        }
      });
      console.log("data from querySelfUri request", data);
      const querySelfUriResponse = data.data.data;
      console.log("querySelfUriResponse --->", querySelfUriResponse);

      this.setState(
        {
          querySchedulesUri: querySelfUriResponse.schedulesUri,
          queryName: querySelfUriResponse.name
        },
        () => {
          console.log(this.state.querySchedulesUri);
          console.log(this.state.queryName);
        }
      );
      const querySchedulesUriResponse = await axios({
        method: "get",
        url: this.state.querySchedulesUri,
        headers: {
          Accept: "application/vnd.encryptedquery.enclave+json; version=1"
        }
      });
      console.log(
        "data from querySchedulesUri request",
        querySchedulesUriResponse
      );
      //data.data will produce the data object as well as the included object
      //data.data.data will produce just the data of the data object.
      const schedules = querySchedulesUriResponse.data.data;
      this.setState({
        schedules
      });
    } catch (error) {
      console.error("Error getting querySelfUri", error);
    }
  };

  getScheduleResultUris = async () => {
    //gets result URIs of each schedule
    const { schedules } = this.state;

    console.log("Schedules inside getScheduleResultUris()", schedules);

    const fetchSelfUri = ({ id, selfUri }) =>
      axios({
        method: "get",
        url: selfUri,
        headers: {
          Accept: "application/vnd.encryptedquery.enclave+json; version=1"
        }
      }).then(({ data }) => [[id], data.data.resultsUri]);
    try {
      const resultUriList = await Promise.all(schedules.map(fetchSelfUri));
      console.log("resultUriList --->", resultUriList);
      const resultUris = indexTuple(resultUriList);
      console.log("These are the resultUris from each schedule: ", resultUris);

      this.setState({ resultUris });
    } catch (error) {
      console.error(
        "Error getting each schedule and setting the resultUri",
        error
      );
    }
  };

  getDataSourceNames = async () => {
    const schedules = this.state.schedules;
    // const { schedules } = this.state;

    console.log("Schedules data inside getDataSourceNames()", schedules);

    try {
      Promise.all(
        schedules.map(schedule =>
          axios({
            method: "get",
            url: schedule.selfUri,
            headers: {
              Accept: "application/vnd.encryptedquery.enclave+json; version=1"
            }
          })
        )
      ).then(response => {
        console.log(response);
        const dataSourceNames = response.flatMap(
          ({ data: { included } }, idx) =>
            included.reduce((memo, { type, name }) => {
              if (type === "DataSource") {
                memo.push(name);
              }
              return memo;
            }, [])
        );
        console.log(dataSourceNames);
        this.setState(prevState => ({
          schedules: prevState.schedules.map((schedule, idx) => {
            return {
              ...schedule,
              dataSourceNames
            };
          })
        }));
      });
    } catch (error) {
      console.error("Error getting data source names", error);
    }
  };

  getResultDetails = async id => {
    const resultsUri = this.state.resultUris[id];
    console.log(`Load queryResults page with this resultsUri ${resultsUri}`);

    console.log(`Setting ${resultsUri} to localstorage item resultsUri `);
    localStorage.setItem("resultsUri", resultsUri);

    //page redirect
    this.props.history.push(`/queryresults`);
  };

  renderButtonView = (idx, status) => {
    const { id } = this.state.schedules[idx];
    const resultsUri = this.state.resultUris[id];

    switch (status) {
      case `Pending`:
        return <button className="btnNoAction"> PENDING ... </button>;
      case `InProgress`:
        return <button> IN PROGRESS ... </button>;
      case `Complete`:
        return (
          <button onClick={() => this.getResultDetails(id)} type="button">
            GET DETAILS
          </button>
        );
      case `Failed`:
        return <button> FAILED </button>;
      default:
        return null;
    }
  };

  handleSort = clickedColumn => () => {
    const { column, queries, direction } = this.state;

    if (column !== clickedColumn) {
      this.setState({
        column: clickedColumn,
        data: _.sortBy(queries, [clickedColumn]),
        direction: "ascending"
      });
      return;
    }

    this.setState({
      data: queries.reverse(),
      direction: direction === "ascending" ? "descending" : "ascending"
    });
  };

  render() {
    const {
      schedules,
      column,
      direction,
      dataSourceNames,
      queryName
    } = this.state;
    const { match, location, history } = this.props;

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
            <div style={{ width: "1300px" }}>
              <Segment style={{ padding: "7em 1em" }} vertical>
                <Message
                  header={`${queryName} scheduled jobs`}
                  content={`Schedules for ${queryName} are shown below. Data Source names are displayed. Jobs may take a while to complete, this page automatically updates.`}
                />
                <Divider style={{ marginTop: "2em" }} horizontal>
                  Query Schedules Status
                </Divider>
                <Table sortable compact celled>
                  <Table.Header>
                    <Table.Row>
                      <Table.HeaderCell
                        sorted={column === "date" ? direction : null}
                        onClick={this.handleSort("date")}
                      >
                        Date
                      </Table.HeaderCell>
                      <Table.HeaderCell
                        sorted={column === "type" ? direction : null}
                        onClick={this.handleSort("type")}
                      >
                        Type
                      </Table.HeaderCell>
                      <Table.HeaderCell
                        sorted={column === "id" ? direction : null}
                        onClick={this.handleSort("id")}
                      >
                        Schedule Id
                      </Table.HeaderCell>
                      <Table.HeaderCell
                        sorted={column === "datasource" ? direction : null}
                        onClick={this.handleSort("datasource")}
                      >
                        Data Source
                      </Table.HeaderCell>
                      <Table.HeaderCell
                        sorted={column === "status" ? direction : null}
                        onClick={this.handleSort("status")}
                      >
                        Status
                      </Table.HeaderCell>
                      <Table.HeaderCell>Results</Table.HeaderCell>
                    </Table.Row>
                  </Table.Header>
                  <Table.Body>
                    {Object.values(schedules).map(
                      (
                        { startTime, type, status, id, dataSourceNames, index },
                        idx
                      ) => {
                        return (
                          <Table.Row>
                            <Table.Cell>
                              {" "}
                              {format(startTime, "MMMM Do YYYY, h:mm:ss A")}
                            </Table.Cell>
                            <Table.Cell>{type}</Table.Cell>
                            <Table.Cell>{id}</Table.Cell>
                            <Table.Cell>
                              {dataSourceNames && dataSourceNames[idx]}
                            </Table.Cell>
                            <Table.Cell>{status}</Table.Cell>
                            <Table.Cell>
                              {this.renderButtonView(idx, status)}
                            </Table.Cell>
                          </Table.Row>
                        );
                      }
                    )}
                  </Table.Body>
                </Table>
              </Segment>
            </div>
          </div>
        </div>
        <PageFooter />
      </div>
    );
  }
}

export default withRouter(QuerySchedulesStatus);
