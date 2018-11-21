import React from "react";
import PropTypes from "prop-types";
import { withRouter } from "react-router-dom";
import createHistory from "history/createBrowserHistory";

import HomePage from "../routes/HomePage";
import CreateQuerySchema from "./CreateQuerySchema";
import CreateQuery from "./CreateQuery";
import "../css/QueryStatus.css";

import axios from "axios";

class QueryStatus extends React.Component {
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
      queryNames: [],
      queryStatus: [],
      queries: [],
      querySelfUri: []
    };

    this.handleChange = this.handleChange.bind(this);
    this.getSchedule = this.getSchedule.bind(this);
  }

  //invoke data fetch function inside interval timeout as well as outside of it
  componentDidMount() {
    axios({
      method: "get",
      url: `/querier/api/rest/dataschemas`,
      headers: {
        Accept: "application/vnd.encryptedquery.enclave+json; version=1"
      }
    })
      .then(response => {
        console.log(response);
        this.setState({ dataSchemas: response.data.data });
      })
      .catch(error => console.log(error.response));
  }

  //prevent memory leak
  componentWillUnmount() {
    clearInterval(this.interval);
  }

  updateQuerySchema = e => {
    this.setState({ querySchemaName: e.target.value });
  };

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
          console.log(JSON.stringify(dataSchema.selfUri));
          console.log(dataSchema.id);
          this.setState(
            {
              querySchemas: response.data.data,
              selectedId: dataSchema.id
            },
            () => {
              console.log(this.state.querySchemas);
              console.log(this.state.selectedId);
            }
          );
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
              queries: response.data.data,
              queryId: response.data.data[0].id,
              queryName: response.data.data[0].name,
              queryStatus: response.data.data[0].status,
              querySelfUri: response.data.data[0].selfUri
            },
            () => {
              console.log(this.state.queries);
              console.log(this.state.queryId);
              console.log(this.state.queryName);
              console.log(this.state.querySelfUri);
            }
          );
        })
        .catch(error => console.log(error.response));
    }
  };

  handleButtonView = (status, querySelfUri) => {
    switch (status) {
      case `Created`:
        return <button className="btnNoAction"> NO ACTION </button>;
      case `Encrypting`:
        return <button> ENCRYPTING ... </button>;
      case `Encrypted`:
        return (
          <button onClick={this.getSchedule} type="button">
            SCHEDULE
          </button>
        );
      case `Scheduled`:
        return (
          <button onClick={e => this.getView(e, querySelfUri)} type="button">
            VIEW{" "}
          </button>
        );
      case `Failed`:
        return <button className="btnFailed"> Failed </button>;
      default:
        return null;
    }
  };

  getSchedule = (e, querySelfUri) => {
    // Handle schedule button interaction
    // Only when status = ENCRYPTED, goes to Schedule Query Page.
    e.preventDefault();
    localStorage.setItem("querySelfUri", this.state.querySelfUri);
    this.props.history.push(`/querier/schedulequery`);
  };

  getView = async (e, querySelfUri) => {
    // Handle schedule button interaction
    // Only when status = SCHEDULED, goes to Query Schedules Status Page
    e.preventDefault();
    await this.setState({ querySelfUri });
    console.log(this.state.querySelfUri);
    localStorage.setItem("querySelfUri", this.state.querySelfUri);
    this.props.history.push(`/querier/queryschedulesstatus`);
  };

  render() {
    const {
      dataSchemas,
      dataSchemaId,
      querySchemas,
      querySchemaName,
      querySchema,
      queryNames,
      queryStatus,
      queries,
      query,
      status
    } = this.state;
    const { match: { params: { querySelfUri } } } = this.props;

    return (
      <div>
        <HomePage />
        <form onSubmit={this.handleSubmit}>
          <fieldset>
            <legend>Query Status</legend>
            <br />
            <div>
              <label>
                Pick a DataSchema to filter down available QuerySchemas:
              </label>{" "}
              <select value={this.state.value} onChange={this.handleChange}>
                <option value="">Choose DataSchema ...</option>
                {dataSchemas &&
                  dataSchemas.length > 0 &&
                  dataSchemas.map(dataSchema => {
                    return (
                      <option value={dataSchema.name}>{dataSchema.name}</option>
                    );
                  })}
              </select>
            </div>
            <br />

            <div>
              <label>
                Pick a QuerySchema to view its corresponding queries status:
              </label>{" "}
              <select
                value={this.state.querySchemaName}
                onChange={this.handleChange}
                onChange={this.querySchemaChange}
              >
                <option value="">Choose QuerySchema ...</option>
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
          </fieldset>
          <br />
        </form>

        <table id="queries">
          <caption> Queries </caption>
          <tr>
            <th>Query Name</th>
            <th>Status</th>
            <th>SelffUri</th>
            <th>Action</th>
          </tr>
          <React.Fragment>
            {this.state.queries.map(query => {
              return (
                <tr>
                  <td key={query.name}>{query.name}</td>
                  <td key={query.status}>{query.status}</td>
                  <td key={query.selfUri}>{query.selfUri}</td>
                  <td>
                    {" "}
                    {this.handleButtonView(query.status, query.selfUri)}{" "}
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

export default withRouter(QueryStatus);
