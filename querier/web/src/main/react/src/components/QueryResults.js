import React from "react";
import PropTypes from "prop-types";
import { withRouter } from "react-router-dom";
import axios from "axios";
import moment from "moment";
import "../css/QueryResults.css";

import HomePage from "../routes/HomePage";

class QueryResults extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      queryScheduleStatus: [],
      queryScheduleType: [],
      queryScheduleDate: [],
      querySchedules: [],
      results: {},
      includedData: []
    };
  }

  componentDidMount() {
    this.interval = setInterval(() => this.getData(), 7000);
    this.getData();
  }

  componentWillUnmount() {
    clearInterval(this.interval);
  }

  getData = () => {
    const scheduleSelfUri = localStorage.getItem("scheduleSelfUri");
    console.log(scheduleSelfUri);
    axios({
      method: "get",
      url: `${scheduleSelfUri}`,
      headers: {
        Accept: "application/vnd.encryptedquery.enclave+json; version=1"
      }
    })
      .then(response => {
        console.log(response);
        this.setState({
          resultsUri: response.data.data.resultsUri
        });
        localStorage.setItem(`resultsUri`, this.state.resultsUri);
        const resultsUri = localStorage.getItem("resultsUri");
        return axios({
          method: "get",
          url: `${resultsUri}`,
          headers: {
            Accept: "application/vnd.encryptedquery.enclave+json; version=1"
          }
        });
      })
      .then(response => {
        console.log(response);
        this.setState(
          {
            results: response.data.data,
            resultsSelfUri: response.data.data[0].selfUri
          },
          () => {
            console.log(this.state.results);
            console.log(this.state.resultsSelfUri);
          }
        );
      })
      .catch(error => console.log(error));
  };

  handleButtonView = (status, resultsSelfUri) => {
    switch (status) {
      case `Ready`:
        return (
          <button
            onClick={e => this.getDownload(e, resultsSelfUri)}
            type="button"
          >
            DOWNLOAD
          </button>
        );
      case `Downloading`:
        return <button> DOWNLOADING ... </button>;
      case `Downloaded`:
        return (
          <button onClick={this.getDecrypted} type="button">
            DECRYPT{" "}
          </button>
        );
      case `Decrypting`:
        return <button> DECRYPTING ...</button>;
      case `Decrypted`:
        return <button> DECRYPTED </button>;
      default:
        return null;
    }
  };

  getDownload = async (e, resultsSelfUri) => {
    //handle download action
    console.log(this.state.resultsSelfUri);
    e.preventDefault();
    await this.setState({ resultsSelfUri });
    localStorage.setItem("resultSelfUri", this.state.resultsSelfUri);
    const resultSelfUri = localStorage.getItem("resultSelfUri");
    axios({
      method: "get",
      url: `${resultsSelfUri}`,
      headers: {
        Accept: "application/vnd.encryptedquery.enclave+json; version=1"
      }
    })
      .then(response => {
        console.log(response);
        const scheduleInfo = response.data.included.filter(
          element => element.type === "Schedule"
        );
        const scheduleStartTime = scheduleInfo.map(function(included) {
          return included["startTime"];
        });
        console.log("This is the date", scheduleStartTime);
        const resultQueryInfo = response.data.included.filter(
          element => element.type === "Query"
        );
        const queryName = resultQueryInfo.map(function(included) {
          return included["name"];
        });
        const resultDataSourceInfo = response.data.included.filter(
          element => element.type === "DataSource"
        );
        const dataSourceName = resultDataSourceInfo.map(function(included) {
          return included["name"];
        });
        const dataSourceMode = resultDataSourceInfo.map(function(included) {
          return included["processingMode"];
        });

        this.setState(
          {
            resultStatus: response.data.data.status,
            resultId: response.data.data.id,
            startTime: response.data.data.startTime,
            resultType: response.data.data.type,
            retrievalsUri: response.data.data.retrievalsUri,
            includedData: response.data.included,
            queryName: queryName,
            dataSourceName: dataSourceName,
            dataSourceMode: dataSourceMode,
            scheduleStartTime: scheduleStartTime
          },
          () => {
            console.log(this.state.queryName);
            console.log(this.state.retrievalsUri);
            console.log(this.state.includedData);
            console.log(this.state.dataSourceName);
            console.log(this.state.dataSourceMode);
            console.log(this.state.scheduleStartTime);
          }
        );
        localStorage.setItem("retrievalsUri", this.state.retrievalsUri);
        const retrievalSelfUri = localStorage.getItem("retrievalsUri");
        console.log(retrievalSelfUri);
        return axios({
          method: "post",
          url: `${retrievalSelfUri}`,
          headers: {
            Accept: "application/vnd.encryptedquery.enclave+json; version=1"
          }
        });
      })
      .then(response => {
        console.log(response);
        this.setState({
          decryptionsUri: response.data.data.decryptionsUri
        });
        localStorage.setItem("decryptionsUri", this.state.decryptionsUri);
        this.props.history.push(`/querier/queryresults`);
      })
      .catch(error => console.log(error));
  };

  getDecrypted = e => {
    //Handle Decrypt action
    const decryptionsUri = localStorage.getItem("decryptionsUri");
    axios({
      method: "post",
      url: `${decryptionsUri}`,
      headers: {
        Accept: "application/vnd.encryptedquery.enclave+json; version=1"
      }
    })
      .then(response => {
        console.log(response);
        this.props.history.push(`/querier/queryresults`);
      })
      .catch(error => console.log(error));
  };

  render() {
    const {
      results,
      queries,
      queryScheduleStatus,
      queryScheduleType,
      queryScheduleDate,
      queryName,
      user,
      includedData,
      querySchedules,
      scheduleStartTime,
      dataSourceMode,
      dataSourceName
    } = this.state;
    const { match, location, history } = this.props;

    return (
      <div>
        <HomePage />
        {queryName && (
          <div>
            <fieldset>
              <legend> Query Information </legend>
              <br />
              <div className="queryInformation">
                <div>
                  <label>Query Name:</label>
                  <input value={queryName} />
                </div>
                <br />

                <div>
                  <label>Data Source:</label>
                  <input value={dataSourceName} />
                </div>
                <br />
                <div>
                  <label>Batch or Streaming:</label>
                  <input value={dataSourceMode} />
                </div>
                <br />
                <div>
                  <label>Schedule Info:</label>
                  <input
                    value={moment(scheduleStartTime[0]).format("llll")}
                  />{" "}
                  />
                </div>
                <br />
              </div>
            </fieldset>
          </div>
        )}
        <br />
        <br />

        <table id="results">
          <caption> Retrievals + Results </caption>
          <tr>
            <th>result_ID</th>
            <th>Processing Mode</th>
            <th>Type</th>
            <th>Status</th>
            <th>Action</th>
          </tr>
          {Object.values(results).map(result => {
            return (
              <tr>
                <td key={result.id}>{result.id}</td>
                <td> {this.state.dataSourceMode} </td>
                <td key={result.type}>{result.type}</td>
                <td key={result.status}>{result.status}</td>
                <td> {this.handleButtonView(result.status, result.selfUri)}</td>
              </tr>
            );
          })}
        </table>
      </div>
    );
  }
}

export default withRouter(QueryResults);
