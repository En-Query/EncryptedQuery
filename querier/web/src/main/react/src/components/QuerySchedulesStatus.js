import React from "react";
import PropTypes from "prop-types";
import { withRouter } from "react-router-dom";
import axios from "axios";
import moment from "moment";

import createHistory from "history/createBrowserHistory";

import HomePage from "../routes/HomePage";
import "../css/QuerySchedulesStatus.css";

class QuerySchedulesStatus extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      dataSchemas: [],
      queries: [],
      schedules: [],
      date: [],
      status: [],
      type: [],
      results: [],
      schedulesSelfUri: [],
      processingMode: localStorage.getItem("processingMode"),
      dataSourceName: localStorage.getItem("dataSourceName")
    };

    //this.handleChange = this.handleChange.bind(this)
  }

  /*
    How can I retrieve the dataSourceName from each specific schedule?

    Attempting to use only the selfUri's to fetch the data.
  */

  componentDidMount() {
    this.interval = setInterval(() => this.getData(), 10000);
    this.getData();
  }

  componentWillUnmount() {
    clearInterval(this.interval);
  }

  getData = () => {
    var dataSourceName = localStorage.getItem("dataSourceName");
    var querySelfUri = localStorage.getItem("querySelfUri");
    var processingMode = localStorage.getItem("processingMode");
    axios({
      method: "get",
      url: `${querySelfUri}`,
      headers: {
        Accept: "application/vnd.encryptedquery.enclave+json; version=1"
      }
    })
      .then(response => {
        console.log(response);
        this.setState(
          {
            querySchedulesUri: response.data.data.schedulesUri
          },
          () => {
            console.log(this.state.querySchedulesUri);
          }
        );
        localStorage.setItem("querySchedulesUri", this.state.querySchedulesUri);
        var querySchedulesUri = localStorage.getItem("querySchedulesUri");
        return axios({
          method: "get",
          url: `${querySchedulesUri}`,
          headers: {
            Accept: "application/vnd.encryptedquery.enclave+json; version=1"
          }
        });
      })
      .then(response => {
        console.log(response);
        this.setState({
          schedules: response.data.data,
          formatDate: response.data.data.startTime,
          schedulesSelfUri: response.data.data[0].selfUri
        });
        localStorage.setItem("schedulesSelfUri", this.state.schedulesSelfUri);
        var schedulesSelfUri = localStorage.getItem("schedulesSelfUri");
        return axios({
          method: "get",
          url: `${schedulesSelfUri}`,
          headers: {
            Accept: "application/vnd.encryptedquery.enclave+json; version=1"
          }
        });
      })
      .then(response => {
        console.log(response);
        this.setState({
          dataSourceName: response.data.included[0].name
        });
      })
      .catch(error => console.log(error.response));
  };

  handleButtonView = (status, schedulesSelfUri) => {
    switch (status) {
      case `Pending`:
        return <button className="btnNoAction"> No Action </button>;
      case `InProgress`:
        return <button> IN PROGRESS ... </button>;
      case `Complete`:
        return (
          <button
            onClick={e => this.getDetails(e, schedulesSelfUri)}
            type="button"
          >
            GET DETAILS
          </button>
        );
      case `Failed`:
        return <button> Failed </button>;
      default:
        return null;
    }
  };

  getDetails = async (e, schedulesSelfUri) => {
    //Handle when `GET DETAILS` button is clicked
    // ONLY when status = COMPLETED, goes to the query results page
    e.preventDefault();
    await this.setState({ schedulesSelfUri });
    console.log(this.state.schedulesSelfUri);
    this.props.history.push(`/querier/queryresults`);
  };

  render() {
    const { schedule, schedules } = this.state;
    const { match, location, history } = this.props;

    return (
      <div>
        <HomePage />

        <table id="queries">
          <caption> Query Schedules Status </caption>
          <tr>
            <th>Date</th>
            <th>Status</th>
            <th>Type</th>
            <th>Data Source</th>
            <th>Results</th>
          </tr>
          <React.Fragment>
            {this.state.schedules.map(schedule => {
              return (
                <tr>
                  <td key={schedule.startTime}>
                    {moment(schedule.startTime).format("llll")}
                  </td>
                  <td key={schedule.status}>{schedule.status}</td>
                  <td key={schedule.type}>{schedule.type}</td>
                  <td> {this.state.dataSourceName} </td>
                  <td>
                    {" "}
                    {this.handleButtonView(
                      schedule.status,
                      schedule.selfUi
                    )}{" "}
                  </td>
                </tr>
              );
            })}
          </React.Fragment>
        </table>
      </div>
    );
  }
}

export default withRouter(QuerySchedulesStatus);
