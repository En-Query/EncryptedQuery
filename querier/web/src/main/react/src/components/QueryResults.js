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
      time: [],
      user: [],
      querySchedules: [],
      results: {},
      includedData: []
    };

    //this.handleChange = this.handleChange.bind(this)
  }

  componentDidMount() {
    this.interval = setInterval(() => this.getData(), 5000);
    this.getData();
  }

  componentWillUnmount() {
    clearInterval(this.interval);
  }

  getData() {
    var scheduleSelfUri = localStorage.getItem("scheduleSelfUri");
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
        var resultsUri = localStorage.getItem("resultsUri");
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
        localStorage.setItem("resultsSelfUri", this.state.resultsSelfUri);
        var resultsSelfUri = localStorage.getItem("resultsSelfUri");
        return axios({
          method: "get",
          url: `${resultsSelfUri}`,
          headers: {
            Accept: "application/vnd.encryptedquery.enclave+json; version=1"
          }
        });
      })
      .then(response => {
        console.log(response);
        this.setState(
          {
            resultStatus: response.data.data.status,
            resultId: response.data.data.id,
            startTime: response.data.data.startTime,
            resultType: response.data.data.type,
            retrievalsUri: response.data.data.retrievalsUri,
            includedData: response.data.included,
            queryName: response.data.included[1].name,
            dataSourceName: response.data.included[0].name,
            processingMode: response.data.included[0].processingMode,
            scheduleStartTime: response.data.included[3].startTime
          },
          () => {
            console.log(this.state.queryName);
            console.log(this.state.retrievalsUri);
            console.log(this.state.includedData);
            console.log(this.state.dataSourceName);
            console.log(this.state.processingMode);
            console.log(this.state.scheduleStartTime);
          }
        );
        localStorage.setItem("retrievalsUri", this.state.retrievalsUri);
      })
      .catch(error => console.log(error.response));
  }

  handleChange = e => {
    e.preventDefault();
  };

  getDownload = e => {
    //Handle download action
    var resultsUri = localStorage.getItem("retrievalsUri");
    axios({
      method: "post",
      url: `${resultsUri}`,
      headers: {
        Accept: "application/vnd.encryptedquery.enclave+json; version=1"
      }
    })
      .then(response => {
        console.log(response);
        this.setState({
          downloadResult: response.data.data,
          retrievalSelfUri: response.data.data.selfUri,
          decryptionsUri: response.data.data.decryptionsUri
        });
        localStorage.setItem("retrievalSelfUri", this.state.retrievalSelfUri);
        localStorage.setItem("decryptionsUri", this.state.decryptionsUri);
        this.props.history.push(`/querier/queryresults`);
      })
      .catch(error => console.log(error.response));
  };

  getDecrypted = e => {
    //Handle Decrypt action
    var retrievalSelfUri = localStorage.getItem("retrievalSelfUri");
    var decryptionsUri = localStorage.getItem("decryptionsUri");
    axios({
      method: "post",
      url: `${decryptionsUri}`,
      headers: {
        Accept: "application/vnd.encryptedquery.enclave+json; version=1"
      }
    })
      .then(response => {
        console.log(response);
        this.setState({
          decryptedResponse: response.data.data
        });
        this.props.history.push(`/querier/queryresults`);
      })
      .catch(error => console.log(error.repsonse));
  };

  handleButtonView = status => {
    switch (status) {
      case `Ready`:
        return (
          <button onClick={this.getDownload} type="button">
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
      querySchedules
    } = this.state;
    const { match, location, history } = this.props;

    return (
      <div>
        <HomePage />

        <fieldset>
          <legend> Query Information </legend>
          <br />
          <div className="queryInformation">
            <div>
              <label>Query Name:</label>
              <input value={this.state.queryName} />
            </div>
            <br />

            <div>
              <label>Data Source:</label>
              <input value={this.state.dataSourceName} />
            </div>
            <br />
            <div>
              <label>Batch or Streaming:</label>
              <input value={this.state.processingMode} />
            </div>
            <br />
            <div>
              <label>Schedule Info:</label>
              <input />
            </div>
            <br />
          </div>
        </fieldset>
        <br />
        <br />

        <table id="results">
          <caption> Retrievals + Results </caption>
          <tr>
            <th>Date/Time</th>
            <th>result_ID</th>
            <th>Processing Mode</th>
            <th>Type</th>
            <th>Status</th>
            <th>Action</th>
          </tr>
          {Object.values(results).map(result => {
            return (
              <tr>
                <td>{moment(this.state.scheduleStartTime).format("llll")}</td>
                <td key={result.id}>{result.id}</td>
                <td> {this.state.processingMode} </td>
                <td key={result.type}>{result.type}</td>
                <td key={result.status}>{result.status}</td>
                <td> {this.handleButtonView(result.status)}</td>
              </tr>
            );
          })}
        </table>
      </div>
    );
  }
}

export default withRouter(QueryResults);
