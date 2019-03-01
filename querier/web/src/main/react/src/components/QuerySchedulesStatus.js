import React from "react";
import PropTypes from "prop-types";
import { withRouter } from "react-router-dom";
import axios from "axios";
import moment from "moment";

import createHistory from "history/createBrowserHistory";

import LogoSection from "./logo-section.js";
import VerticalNavBar from "./NavigationBar.js";

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
      scheduleSelfUri: []
    };

    //this.handleChange = this.handleChange.bind(this)
  }

  componentDidMount = () => {
    const querySelfUri = localStorage.getItem("querySelfUri");
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
        const querySchedulesUri = localStorage.getItem("querySchedulesUri");
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
            scheduleSelfUri: response.data.data[0].selfUri
          },
          () => {
            console.log(this.state.scheduleSelfUri);
          }
        );
        this.getUpdatedStatus();
      })
      .catch(error => console.log(error));
  };

  //prevent memory leak
  componentWillUnmount() {
    clearTimeout(this.timeout);
  }

  getUpdatedStatus = () => {
    //fetch updated status,
    const schedules = this.state.schedules;
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
    )
      .then(response => {
        console.log(response);
        const isIncomplete = response.some(
          response => response.data.data.status !== "Complete"
        );
        const dataSourceNames = response.flatMap(
          ({ data: { included } }, index) =>
            included.reduce((memo, { type, name }) => {
              if (type === "DataSource") {
                memo.push(name);
              }
              return memo;
            }, [])
        );
        console.log(dataSourceNames);

        if (isIncomplete) {
          this.timeout = setTimeout(() => this.getUpdatedStatus(), 20000);
        } else {
          this.setState(prevState => ({
            schedules: prevState.schedules.map(schedule => {
              if (schedule.status === "Pending") {
                return {
                  ...schedule,
                  status: "Complete",
                  dataSourceNames: dataSourceNames
                };
              }
              return {
                ...schedule,
                dataSourceNames
              };
            })
          }));
        }
      })
      .catch(error => console.log(error));
  };

  handleButtonView = (status, scheduleSelfUri) => {
    switch (status) {
      case `Pending`:
        return <button className="btnNoAction"> PENDING ... </button>;
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
        return <button> FAILED </button>;
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
    localStorage.setItem("scheduleSelfUri", this.state.scheduleSelfUri);
    try {
      const schedulesSelfUri = localStorage.getItem("scheduleSelfUri");
      const response = await axios({
        method: "get",
        url: `${schedulesSelfUri}`,
        headers: {
          Accept: "application/vnd.encryptedquery.enclave+json; version=1"
        }
      });
      const resultsUri = response.data.data.resultsUri;

      this.setState({ resultsUri: resultsUri }, () => {
        console.log(
          "ResultsUri being stored to localStorage from scheduleSelfUri response",
          resultsUri
        );
      });
      localStorage.setItem("resultsUri", resultsUri);
    } catch (error) {
      return "Error assigning resultsUri to localStorage", error;
    }

    this.props.history.push(`/queryresults`);
  };

  render() {
    const { schedule, schedules, dataSourceInfo, dataSourceNames } = this.state;
    const { match, location, history } = this.props;
    const { match: { params: { scheduleSelfUri } } } = this.props;

    return (
      <div>
        <LogoSection />
        <VerticalNavBar />
        <table id="queries">
          <caption> Query Schedules Status </caption>
          <tr>
            <th>Date</th>
            <th>Status</th>
            <th>Type</th>
            <th>Schedule_ID</th>
            <th>Data Source</th>
            <th>Results</th>
          </tr>
          {Object.values(schedules).map((schedule, index) => {
            return (
              <tr>
                <td>{moment(schedule.startTime).format("llll")}</td>
                <td>{schedule.status}</td>
                <td>{schedule.type}</td>
                <td>{schedule.id}</td>
                <td>
                  {schedule.dataSourceNames && (
                    <div>{schedule.dataSourceNames[index]}</div>
                  )}
                </td>
                <td>
                  {this.handleButtonView(schedule.status, schedule.selfUri)}
                </td>
              </tr>
            );
          })}
        </table>
      </div>
    );
  }
}

export default withRouter(QuerySchedulesStatus);
