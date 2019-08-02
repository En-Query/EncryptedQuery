import React, { Component, createRef } from "react";
import ReactDOM from "react-dom";
import PropTypes from "prop-types";
import { withRouter } from "react-router-dom";
import {
  Dropdown,
  Segment,
  Header,
  Divider,
  Container,
  Grid,
  List,
  Form,
  Loader,
  Input,
  Table,
  Button,
  Icon,
  Rail,
  Sticky,
  Ref
} from "semantic-ui-react";
import "semantic-ui-css/semantic.min.css";
import { Checkbox, CheckboxGroup } from "react-checkbox-group";

import PageFooter from "./PageFooter";
import PageHeading from "./FixedMenu";
import TableRowWithCheckbox from "./TableRowWithCheckbox";

import axios from "axios";

/*const ReferenceTable = () => (
  <Table celled columns={3}>
    <Table.Header>
      <Table.Row>
        <Table.HeaderCell>Member</Table.HeaderCell>
        <Table.HeaderCell>Data type</Table.HeaderCell>
        <Table.HeaderCell>Description</Table.HeaderCell>
      </Table.Row>
    </Table.Header>

    <Table.Body>
      <Table.Row>
        <Table.Cell>lengthType</Table.Cell>
        <Table.Cell>string</Table.Cell>
        <Table.Cell>Type of length: fixed/variable</Table.Cell>
      </Table.Row>
      <Table.Row>
        <Table.Cell>size</Table.Cell>
        <Table.Cell>int</Table.Cell>
        <Table.Cell>Size of bytes to return</Table.Cell>
      </Table.Row>
      <Table.Row>
        <Table.Cell>maxArrayElements</Table.Cell>
        <Table.Cell>int</Table.Cell>
        <Table.Cell>If array, how many elements to return</Table.Cell>
      </Table.Row>
    </Table.Body>
  </Table>
);*/

class CreateQuerySchema extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      dataschemas: [],
      dataSchemaName: "",
      querySchemaName: "",
      fields: [],
      selectorField: "",
      size: {},
      maxArrayElements: {},
      fieldNames: [],
      isLoading: true,
      fieldCheckbox: false,
      selectedFields: []
    };
  }

  async componentDidMount() {
    await this.getSchemas();
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
      const dataschemas = data.data;

      this.setState({ dataschemas: dataschemas, isLoading: false });

      console.log("This is the dataschema list:", dataschemas);
    } catch (error) {
      console.error(Error(`Error fetching results list: ${error.message}`));
    }
  };

  handleSchemaChange = (e, { value }) => {
    this.setState({ dataSchemaName: value }, () => {
      console.log("Chosen dataSchema ---> ", this.state.dataSchemaName);
      //also send a request the selfUri of the selected dataSchema
    });
    const schema = this.state.dataschemas.find(schema => schema.name === value);
    if (schema) {
      axios({
        method: "get",
        url: schema.selfUri,
        headers: {
          Accept: "application/vnd.encryptedquery.enclave+json; version=1"
        }
      })
        .then(response => {
          console.log(response);
          this.setState({
            fields: response.data.data.fields,
            selectedId: response.data.data.id,
            querySchemasUri: response.data.data.querySchemasUri
          });
          console.log(this.state.fields);
          console.log(this.state.selectedId);
          console.log(this.state.querySchemasUri);
          localStorage.setItem("querySchemasUri", this.state.querySchemasUri);
        })
        .catch(error => console.log(error.response));
    }
  };

  handleQuerySchemaNameChange = e => {
    this.setState({ querySchemaName: e.target.value });
  };

  handleSelectorFieldChange = (e, { value }) => {
    this.setState({ selectorField: value }, () => {
      console.log("Chosen selectorField ---> ", this.state.selectorField);
    });
  };

  handleChange = (e, name) => {
    const { selectedFields } = this.state;
    if (selectedFields.some(elem => elem.name === name)) {
      this.setState({
        selectedFields: [
          ...selectedFields.map(elem =>
              elem.name !== name
                ? elem
                : {
                    ...elem,
                    name,
                    [e.target.name]: e.target.value
                  }
          )
        ]
      });
    }
  };

  handleCancel = event => {
    event.target.reset();
  };

  handleSubmit = event => {
    const { selectedFields, selectorField, querySchemaName } = this.state;
    console.log(JSON.stringify(selectedFields));
    event.preventDefault();
    const querySchemaUri = localStorage.getItem("querySchemasUri");
    axios({
      method: "post",
      url: `${querySchemaUri}`,
      headers: {
        Accept: "application/vnd.encryptedquery.enclave+json; version=1",
        "Content-Type": "application/json"
      },
      data: JSON.stringify({
        name: querySchemaName,
        selectorField: selectorField,
        fields: selectedFields
      })
    })
      .then(response => {
        console.log(response);
      })
      .catch(error => console.log(error.response));
    this.props.history.push("/createquery");
  };

  checkbox = ({ name, isChecked }) => {
    //handle check box of each fieldName
    if (isChecked === true) {
      //checked conditional
      this.setState(
        { selectedFields: [...this.state.selectedFields, { name }] },
        () => {
          console.log(
            "callback in  isChecked if conditional",
            this.state.selectedFields
          );
        }
      );
    } else {
      this.setState(
        {
          selectedFields: this.state.selectedFields.filter(f => f.name !== name)
        },
        () => {
          console.log(
            `box unchecked, deleted from object --->`,
            this.state.selectedFields
          );
        }
      );
    }
  };

  render() {
    const {
      dataschemas,
      dataSchemaName,
      querySchemaName,
      fields,
      selectorField,
      fieldNames,
      size,
      maxArrayElements,
      selectedFields
    } = this.state;

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
            <div style={{ width: "1100px" }}>
              <Form onSubmit={this.handleSubmit}>
                <Segment style={{ padding: "5em 1em" }} vertical>
                  <Divider horizontal>Query Schema Information</Divider>
                  <Form.Field required>
                    <label>Query Schema Name: </label>
                    <Input
                      placeholder="Enter Query Schema Name"
                      name="querySchemaName"
                      value={querySchemaName}
                      onChange={this.handleQuerySchemaNameChange}
                      required
                    />
                  </Form.Field>
                  <Form.Field required>
                    <label>Select a Data Schema</label>
                    <Dropdown
                      placeholder="Select data schema"
                      scrolling
                      clearable
                      fluid
                      selection
                      search
                      noResultsMessage="Try a different Search"
                      multiple={false}
                      options={dataschemas.map(schema => {
                        return {
                          key: schema.id,
                          text: schema.name,
                          value: schema.name
                        };
                      })}
                      header="PLEASE SELECT A DATASCHEMA"
                      value={dataSchemaName}
                      onChange={this.handleSchemaChange}
                    />
                  </Form.Field>
                  <Form.Field required>
                    <label>Choose a Selector Field </label>
                    <Dropdown
                      placeholder="Pick a selector field"
                      clearable
                      fluid
                      selection
                      search
                      noResultsMessage="Please search again"
                      multiple={false}
                      options={fields.map(field => {
                        return {
                          key: field.id,
                          text: field.name,
                          value: field.name
                        };
                      })}
                      header="CHOOSE A SELECTOR FIELD"
                      value={selectorField}
                      onChange={this.handleSelectorFieldChange}
                      required
                    />
                  </Form.Field>

                  {dataSchemaName && (
                    <>
                      <Divider style={{ marginTop: "2em" }} horizontal>
                        Field(s) Selection
                      </Divider>
                      <Table inverted celled compact definition selectable>
                        <Table.Header fullWidth>
                          <Table.Row>
                            <Table.HeaderCell />
                            <Table.HeaderCell>Name</Table.HeaderCell>
                            <Table.HeaderCell>Data Type</Table.HeaderCell>
                            <Table.HeaderCell>Position</Table.HeaderCell>
                            <Table.HeaderCell textAlign="center">
                              Max Size
                            </Table.HeaderCell>
                            <Table.HeaderCell textAlign="center">
                              MaxArrayElements
                            </Table.HeaderCell>
                          </Table.Row>
                        </Table.Header>

                        <Table.Body>
                          {Object.values(fields).map(
                            ({ name, dataType, position }) => {
                              return (
                                <React.Fragment>
                                  <TableRowWithCheckbox
                                    id={name}
                                    name={name}
                                    dataType={dataType}
                                    position={position}
                                    initialState={Object.keys(
                                      selectedFields
                                    ).includes(name)}
                                    box={this.checkbox.bind(this)}
                                    handleChange={this.handleChange}
                                  />
                                </React.Fragment>
                              );
                            }
                          )}
                        </Table.Body>
                      </Table>
                      <Form.Button
                        positive
                        fluid
                        content="Submit"
                        style={{
                          marginTop: "3em",
                          display: "flex",
                          justifyContent: "center"
                        }}
                      />
                    </>
                  )}
                </Segment>
              </Form>
            </div>
          </div>
        </div>
        <PageFooter />
      </div>
    );
  }
}

export default CreateQuerySchema;

/*export default class StickyContent extends Component {
  contextRef = createRef();

  render() {
    return (
      <div>
        <PageHeading />
        <div>
          <Grid stretched centered columns={3} style={{ marginTop: "3em" }}>
            <Grid.Column>
              <Ref innerRef={this.contextRef}>
                <Container>
                  <CreateQuerySchema />

                  <Rail position="left">
                    <Sticky offset={90} context={this.contextRef}>
                      <Header as="h3">Reference</Header>
                      <ReferenceTable />
                    </Sticky>
                  </Rail>

                  <Rail position="right">
                    <Sticky offset={90} context={this.contextRef}>
                      <Header as="h3">Overview</Header>
                      <List style={{ padding: "5px" }}>
                        <List.Item>Overview</List.Item>
                        <List.Item>Examples</List.Item>
                        <List.Item>Tutorial</List.Item>
                      </List>
                    </Sticky>
                  </Rail>
                </Container>
              </Ref>
            </Grid.Column>
          </Grid>
        </div>
        <PageFooter />
      </div>
    );
  }
}*/
