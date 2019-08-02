import React, { Component } from "react";
import { Link } from "react-router-dom";
import {
  Button,
  Form,
  Message,
  Divider,
  Segment,
  Tab,
  Icon,
  Loader,
  Dimmer,
  Table,
  Progress
} from "semantic-ui-react";

import PageHeading from "./FixedMenu";
import PageFooter from "./PageFooter.js";

import axios, { put } from "axios";
import format from "date-fns/format";

import { ExportSchedulesDropdowns } from "./Tabs";
import TableRowWithSlider from "./TableRowWithSlider";

export default class OfflineMode extends Component {
  constructor(props) {
    super(props);
    this.state = {
      file: null,
      fileName: "",
      dataschemas: [],
      dataSchemaName: "",
      queryschemas: [],
      querySchemaName: "",
      queries: [],
      queryName: "",
      queriesUri: "",
      schedules: [],
      exportSlider: false,
      scheduleIdsToExport: [],
      schedulesToExport: {}
    };
  }

  async componentDidMount() {
    //get data for schedules dropdown
    await this.getSchemas();
    await this.handleSchemaChange();
    await this.getQuerySchemas();
    await this.handleQuerySchemaChange();
    await this.getQueryData();
    await this.handleQueryChange();
    await this.getSchedules();
  }

  getSchemas = async () => {
    try {
      const { data } = await axios({
        method: "get",
        url: "/querier/api/rest/dataschemas",
        headers: {
          Accept: "application/vnd.encryptedquery.enclave+json; version=1"
        }
      });
      console.log(data);
      const dataschemas = data.data;

      this.setState({ dataschemas: dataschemas, isLoading: false });

      console.log("This is the dataschema list:", dataschemas);
    } catch (error) {
      console.error(Error(`Error fetching results list: ${error.message}`));
    }
  };

  handleSchemaChange = async (e, { value }) => {
    this.setState({ dataSchemaName: value }, () => {
      console.log("Chosen dataSchema ---> ", this.state.dataSchemaName);
      //also send a request the selfUri of the selected dataSchema
    });
    const schema = this.state.dataschemas.find(schema => schema.name === value);
    if (schema) {
      try {
        const { data } = await axios({
          method: "get",
          url: schema.selfUri,
          headers: {
            Accept: "application/vnd.encryptedquery.enclave+json; version=1"
          }
        });
        console.log(data);
        this.setState({ queryschemaUri: data.data.querySchemasUri });
        console.log(
          "queryschemaUri from dataschema change ---> ",
          this.state.queryschemaUri
        );
      } catch (error) {
        console.error(
          Error(`Error fetching specific schema: ${error.message}`)
        );
      }
    }
    this.getQuerySchemas();
  };

  getQuerySchemas = async () => {
    const { queryschemaUri } = this.state;
    try {
      const { data } = await axios({
        method: "get",
        url: queryschemaUri,
        headers: {
          Accept: "application/vnd.encryptedquery.enclave+json; version=1"
        }
      });
      console.log("Data from queryschemaUri fetch --->", data);
      const queryschemas = data.data;
      this.setState({ queryschemas: queryschemas });
    } catch (error) {
      console.error(Error(`Error getting queryschemas: ${error.message}`));
    }
  };

  handleQuerySchemaChange = async (e, { value }) => {
    //handle queryschema dropdown change
    this.setState({ querySchemaName: value }, () => {
      console.log("Chosen querySchema --->", this.state.querySchemaName);
    });
    const schema = this.state.queryschemas.find(
      schema => schema.name === value
    );
    if (schema) {
      const { id, name, selfUri } = schema;
      this.setState({
        querySchemaId: id,
        querySchemaName: name,
        querySchemaSelfUri: selfUri
      });
      try {
        const { data } = await axios({
          method: "get",
          url: selfUri,
          headers: {
            Accept: "application/vnd.encryptedquery.enclave+json; version=1"
          }
        });
        console.log(
          "Specific querySchema data from selfUri request ---> ",
          data
        );
        const queryschema = data.data;
        this.setState({
          queryschema: queryschema,
          queriesUri: queryschema.queriesUri
        });
        console.log("queryschema ---> ", queryschema);
        console.log("queriesUri --->", this.state.queriesUri);
      } catch (error) {
        console.error(
          Error(`Error getting specific querySchema: ${error.nessage}`)
        );
      }
    }
    this.getQueryData();
  };

  getQueryData = async () => {
    //get query data
    const { queriesUri } = this.state;
    console.log("queriesUri inside getQueryData() ---->", queriesUri);
    try {
      const { data } = await axios({
        method: "get",
        url: queriesUri,
        headers: {
          Accept: "application/vnd.encryptedquery.enclave+json; version=1"
        }
      });
      console.log("Data from get query --->", data);
      const queries = data.data;
      this.setState({ queries: queries });
      console.log("Queries ---->", queries);
    } catch (error) {
      console.error(Error(`Error getting query data ${error.message}`));
    }
  };

  handleQueryChange = async (e, { value }) => {
    //handle query dropdown change
    this.setState({ queryName: value }, () => {
      console.log("Chosen query --->", this.state.queryName);
    });
    const query = this.state.queries.find(query => query.name === value);
    if (query) {
      const { id, name, selfUri } = query;
      this.setState({
        queryId: id,
        queryName: name,
        querySelfUri: selfUri
      });
      try {
        const { data } = await axios({
          method: "get",
          url: selfUri,
          headers: {
            Accept: "application/vnd.encryptedquery.enclave+json; version=1"
          }
        });
        console.log("Specific query data --->", data);
        const query = data.data;
        this.setState({
          query: query,
          schedulesUri: query.schedulesUri
        });
        console.log("Data of selected query", query);
        console.log("schedulesUri --->", this.state.schedulesUri);
      } catch (error) {
        console.error(
          Error(`Error handling query dropdown change ${error.message}`)
        );
      }
    }
    this.getSchedules();
  };

  getSchedules = async () => {
    //get schedules for selected query
    const { schedulesUri } = this.state;
    try {
      const { data } = await axios({
        method: "get",
        url: schedulesUri,
        headers: {
          Accept: "application/vnd.encryptedquery.enclave+json; version=1"
        }
      });
      const schedules = data.data;
      console.log("Schedules ---> ", schedules);
      this.setState({ schedules: schedules });
    } catch (error) {
      console.error(Error(`Error getting schedules ${error.message}`));
    }
  };

  slider = ({ id, isExported }) => {
    const obj = this.state.schedules.find(s => Number(s.id) === Number(id));
    if (isExported === true) {
      this.setState({
        schedulesToExport: {
          ...this.state.schedulesToExport,
          [id]: {
            ...obj
          }
        }
      });
    } else {
      const newSchedulesToExport = this.state.schedulesToExport;
      delete newSchedulesToExport[id];
      this.setState(
        {
          schedulesToExport: newSchedulesToExport
        },
        () => {
          console.log(
            "Schedules to export --> ",
            this.state.newSchedulesToExport
          );
        }
      );
    }
  };

  onFormSubmit = e => {
    e.preventDefault(); // Stop form submit
    this.fileUpload(this.state.file);
  };

  fileChange = e => {
    this.setState(
      { file: e.target.files[0], fileName: e.target.files[0].name },
      () => {
        console.log(
          "File chosen --->",
          this.state.file,
          console.log("File name  --->", this.state.fileName)
        );
      }
    );
  };

  fileUpload = file => {
    const formData = new FormData();
    formData.append("file", file);
    axios({
      method: "put",
      url: "/querier/api/rest/offline/datasources",
      data: formData,
      headers: {
        Accept: "application/vnd.encryptedquery.enclave+json; version=1"
      }
    })
      .then(response => {
        console.log(response);
        console.log(response.status);
        this.setState(
          {
            statusCode: response.status
          },
          () => {
            console.log("Status code --->", this.state.statusCode);
          }
        );
      })
      .catch(error => console.log(error));
  };

  resultUpload = file => {
    const formData = new FormData();
    formData.append("file", file);
    axios({
      method: "put",
      url: "/querier/api/rest/offline/result",
      data: formData,
      headers: {
        Accept: "application/vnd.encryptedquery.enclave+json; version=1"
      }
    })
      .then(response => {
        console.log(response);
        console.log(response.status);
        this.setState(
          {
            statusCode: response.status
          },
          () => {
            console.log("Status code --->", this.state.statusCode);
          }
        );
      })
      .catch(error => console.log(error));
  };

  resultFormSubmit = e => {
    e.preventDefault();
    this.resultUpload(this.state.file);
  };

  exportSchedules = async e => {
    //hit rest endpoint for export
    e.preventDefault();
    const { schedulesToExport } = this.state;
    try {
      const { data } = await axios({
        method: "post",
        url: "/querier/api/rest/offline/executions",
        headers: {
          Accept: "application/vnd.encryptedquery.enclave+json; version=1",
          "Content-Type":
            "application/vnd.encryptedquery.enclave+json; version=1"
        },
        data: JSON.stringify({
          scheduleIds: Object.keys(schedulesToExport).map(Number),
          type: "OfflineExecutionExportRequest"
        })
      });
      console.log(data);
      const response = data;
      console.log("One data response ---> ", response);
      this.setState({ scheduleDataToBeExported: response });
      console.log(this.state.scheduleDataToBeExported);
      var blob = new Blob([this.state.scheduleDataToBeExported]);
      var link = document.createElement("a");
      link.href = window.URL.createObjectURL(blob);
      link.download = "exportSchedules.xml";
      link.click();
    } catch (error) {
      console.error(Error(`Error exporting schedules ${error.message}`));
    }
  };

  //Tab panes for upload datasource/schemas, export for schedules, import for results

  render() {
    const {
      dataschemas,
      dataSchemaName,
      queryschemas,
      querySchemaName,
      queries,
      queryName,
      schedules,
      schedulesToExport,
      fileName,
      statusCode
    } = this.state;
    const panes = [
      {
        menuItem: "Import Datasource",
        render: () => (
          <Tab.Pane attached={false}>
            <Message>
              The file picker below is used to import & upload the xml file that
              contains both the Datas Sources and Data Schemas.
            </Message>
            <Form onSubmit={this.onFormSubmit}>
              <Segment style={{ padding: "5em 1em" }} vertical>
                <Form.Field>
                  <label>File input & upload </label>
                  <Button
                    as="label"
                    htmlFor="file"
                    type="button"
                    animated="fade"
                  >
                    <Button.Content visible>
                      <Icon name="file" />
                    </Button.Content>
                    <Button.Content hidden>Choose a File</Button.Content>
                  </Button>
                  <input
                    type="file"
                    id="file"
                    hidden
                    onChange={this.fileChange}
                  />
                  <Form.Input
                    fluid
                    label="File Chosen: "
                    placeholder="Use the above bar to browse your file system"
                    readOnly
                    value={this.state.fileName}
                  />
                  <Button style={{ marginTop: "20px" }} type="submit">
                    Upload
                  </Button>
                  {statusCode && statusCode === 200 ? (
                    <Progress
                      style={{ marginTop: "20px" }}
                      percent={100}
                      success
                      progress
                    >
                      File Upload Success
                    </Progress>
                  ) : statusCode && statusCode === 500 ? (
                    <Progress
                      style={{ marginTop: "20px" }}
                      percent={100}
                      error
                      active
                      progress
                    >
                      File Upload Failed
                    </Progress>
                  ) : null}
                </Form.Field>
              </Segment>
            </Form>
          </Tab.Pane>
        )
      },
      {
        menuItem: "Export Schedule(s)",
        render: () => (
          <React.Fragment>
            <ExportSchedulesDropdowns
              dropdownOptionsDataSchema={{
                placeholder: "Select data schema",
                options: dataschemas.map(schema => {
                  return {
                    key: schema.id,
                    text: schema.name,
                    value: schema.name
                  };
                }),
                header: "PLEASE SELECT A DATASCHEMA",
                value: dataSchemaName,
                onChange: this.handleSchemaChange
              }}
              dropdownOptionsQuerySchema={{
                placeholder: "Select query schema",
                options: queryschemas.map(schema => {
                  return {
                    key: schema.id,
                    text: schema.name,
                    value: schema.name
                  };
                }),

                header: "PLEASE SELECT A QUERYSCHEMA",
                value: querySchemaName,
                onChange: this.handleQuerySchemaChange
              }}
              dropdownOptionsQuery={{
                placeholder: " Select query ",
                options: queries.map(query => {
                  return {
                    key: query.id,
                    text: query.name,
                    value: query.name
                  };
                }),

                header: "PLEASE SELECT A QUERYSCHEMA",
                value: queryName,
                onChange: this.handleQueryChange
              }}
            />
            <Message attached="bottom" warning>
              <Icon name="help" />
              Don't see any Queries?&nbsp;
              <Link to="/createquery">Create one here</Link>
              &nbsp;if you don't.
            </Message>
            <Divider style={{ padding: "2em" }} horizontal>
              Schedule(s) table
            </Divider>
            <Segment>
              <Dimmer active={schedules && schedules.length < 1}>
                <Loader inverted>Waiting for Query selection ...</Loader>
              </Dimmer>
              <Message
                attached
                header="Exporting Schedule(s)"
                content="To export 1-to-many schedules please click the slider next to each schedule."
              />
              {schedules && (
                <Table celled compact definition>
                  <Table.Header fullWidth>
                    <Table.Row>
                      <Table.HeaderCell />
                      <Table.HeaderCell>Id</Table.HeaderCell>
                      <Table.HeaderCell>Scheduled For</Table.HeaderCell>
                      <Table.HeaderCell>Self Uri</Table.HeaderCell>
                      <Table.HeaderCell>Status</Table.HeaderCell>
                    </Table.Row>
                  </Table.Header>

                  <Table.Body>
                    {Object.values(schedules).map(
                      ({ id, startTime, selfUri, status }) => {
                        return (
                          <>
                            {status === "Pending" ? (
                              <TableRowWithSlider
                                id={id}
                                key={id}
                                startTime={startTime}
                                selfUri={selfUri}
                                status={status}
                                initialState={Object.keys(
                                  schedulesToExport
                                ).includes(id)}
                                slide={this.slider.bind(this)}
                              />
                            ) : null}
                          </>
                        );
                      }
                    )}
                  </Table.Body>
                  <Table.Footer fullWidth>
                    <Table.Row>
                      <Table.HeaderCell />
                      <Table.HeaderCell colSpan="4">
                        {" "}
                        <Button
                          floated="right"
                          icon
                          labelPosition="left"
                          primary
                          size="small"
                          onClick={this.getSchedules}
                        >
                          <Icon name="refresh" /> Refresh Schedules
                        </Button>
                      </Table.HeaderCell>
                    </Table.Row>
                  </Table.Footer>
                </Table>
              )}
            </Segment>
            <Divider style={{ padding: "2em" }} horizontal>
              EXPORT SCHEDULE(S) TABLE
            </Divider>
            <Segment>
              <Dimmer active={schedules && schedules.length < 1}>
                <Loader inverted />
              </Dimmer>
              <Message>
                The schedules listed below will be exported upon button
                interaction
              </Message>
              {schedules && (
                <Table celled compact definition>
                  <Table.Header fullWidth>
                    <Table.Row>
                      <Table.HeaderCell>Id</Table.HeaderCell>
                      <Table.HeaderCell>Scheduled For</Table.HeaderCell>
                      <Table.HeaderCell>Self Uri</Table.HeaderCell>
                      <Table.HeaderCell>Status</Table.HeaderCell>
                    </Table.Row>
                  </Table.Header>

                  <Table.Body>
                    {Object.keys(schedulesToExport).map(key => {
                      const schedule = schedulesToExport[key];
                      return (
                        <Table.Row>
                          <Table.Cell>{schedule.id}</Table.Cell>
                          <Table.Cell>
                            {format(
                              schedule.startTime,
                              "MMM Do YYYY, h:mm:ss A"
                            )}
                          </Table.Cell>
                          <Table.Cell>{schedule.selfUri}</Table.Cell>
                          <Table.Cell>{schedule.status}</Table.Cell>
                        </Table.Row>
                      );
                    })}
                  </Table.Body>
                  <Table.Footer fullWidth>
                    <Table.Row>
                      <Table.HeaderCell colSpan="4">
                        <Button
                          floated="left"
                          icon
                          labelPosition="left"
                          primary
                          size="small"
                          onClick={this.exportSchedules}
                        >
                          <Icon name="send" />
                          Export All
                        </Button>
                      </Table.HeaderCell>
                    </Table.Row>
                  </Table.Footer>
                </Table>
              )}
            </Segment>
          </React.Fragment>
        )
      },
      {
        menuItem: "Import Results",
        render: () => (
          <Tab.Pane attached={false}>
            <Message>Some message about importing results</Message>
            <Form onSubmit={this.resultFormSubmit}>
              <Segment style={{ padding: "5em 1em" }} vertical>
                <Form.Field>
                  <label>Result File Upload</label>
                  <Button
                    as="label"
                    htmlFor="file"
                    type="button"
                    animated="fade"
                  >
                    <Button.Content visible>
                      <Icon name="file" />
                    </Button.Content>
                    <Button.Content hidden>Choose a File</Button.Content>
                  </Button>
                  <input
                    type="file"
                    id="file"
                    hidden
                    onChange={this.fileChange}
                  />
                  <Form.Input
                    fluid
                    label="File Chosen: "
                    placeholder="Use the above bar to browse your file system"
                    readOnly
                    value={this.state.fileName}
                  />
                  <Button style={{ marginTop: "20px" }} type="submit">
                    Upload
                  </Button>
                  {statusCode && statusCode === 200 ? (
                    <Progress
                      style={{ marginTop: "20px" }}
                      percent={100}
                      success
                      progress
                    >
                      File Upload Success
                    </Progress>
                  ) : statusCode && statusCode === 500 ? (
                    <Progress
                      style={{ marginTop: "20px" }}
                      percent={100}
                      error
                      active
                      progress
                    >
                      File Upload Failed
                    </Progress>
                  ) : null}
                </Form.Field>
              </Segment>
            </Form>
          </Tab.Pane>
        )
      }
    ];
    return (
      <React.Fragment>
        <div
          style={{
            display: "flex",
            minHeight: "100vh",
            flexDirection: "column"
          }}
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
              <div style={{ width: "1100px" }}>
                <Segment style={{ padding: "5em 1em" }} vertical>
                  <Divider horizontal>OFFLINE USAGE</Divider>
                  <Tab menu={{ pointing: true }} panes={panes} />
                </Segment>
              </div>
            </div>
          </div>
          <PageFooter />
        </div>
      </React.Fragment>
    );
  }
}
