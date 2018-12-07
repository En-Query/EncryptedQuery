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
      queies: [],
      schedules: [],
      date: [],
      type: [],
      results: [],
      scheduleStatus: [],
      scheduleSelfUri: [],
      dataSourceName: localStorage.getItem("dataSourceName")
    };

    //this.handleChange = this.handleChange.bind(this)
  }

  componentDidMount = () => {
    var dataSourceName = localStorage.getItem("dataSourceName");
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
        this.setState(
          {
            schedules: response.data.data,
            scheduleTime: response.data.data[0].startTime,
            scheduleSelfUri: response.data.data[0].selfUri,
            scheduleStatus: response.data.data[0].status
          },
          () => {
            console.log(this.state.scheduleStatus);
            console.log(this.state.schedulesSelfUri);
          }
        );
        localStorage.setItem("scheduleSelfUri", this.state.scheduleSelfUri);
        this.getUpdatedStatus();
      })
      .catch(error => console.log(error.response));
  };

  //prevent memory leak
  componentWillUnmount() {
    clearTimeout(this.timeout);
  }

  getUpdatedStatus = () => {
    //fetch updated status,
    var scheduleSelfUri = localStorage.getItem("scheduleSelfUri");
    axios({
      method: "get",
      url: `${scheduleSelfUri}`,
      headers: {
        Accept: "application/vnd.encryptedquery.enclave+json; version=1"
      }
    })
      .then(response => {
        if (response.data.data.status !== "Complete" && "Failed") {
          this.timeout = setTimeout(() => this.getUpdatedStatus(), 20000);
        }
        console.log(response);
        this.setState(
          {
            dataSourceName: response.data.included[3].name,
            scheduleStatus: response.data.data.status
          },
          () => {
            console.log(this.state.scheduleStatus);
          }
        );
      })
      .catch(error => console.log(error.response));
  };

  handleButtonView = (status, scheduleSelfUri) => {
    switch (status) {
      case `Pending`:
        return <button className="btnNoAction"> NO ACTION ... </button>;
      case `InProgress`:
        return <button> IN PROGRESS ... </button>;
      case `Complete`:
        return (
          <button
            onClick={e => this.getDetails(e, scheduleSelfUri)}
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

  getDetails = async (e, scheduleSelfUri) => {
    //Handle when `GET DETAILS` button is clicked
    // ONLY when status = COMPLETED, goes to the query results page
    e.preventDefault();
    await this.setState({ scheduleSelfUri });
    console.log(this.state.scheduleSelfUri);
    this.props.history.push(`/querier/queryresults`);
  };

  render() {
    const { schedule, schedules, scheduleStatus } = this.state;
    const { match, location, history } = this.props;
    const { match: { params: { scheduleSelfUri } } } = this.props;

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
                  <td>{moment(schedule.startTime).format("llll")}</td>
                  <td>{this.state.scheduleStatus}</td>
                  <td>{schedule.type}</td>
                  <td> {this.state.dataSourceName} </td>
                  <td>
                    {this.handleButtonView(
                      this.state.scheduleStatus,
                      schedule.selfUri
                    )}
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
