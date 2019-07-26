import React, { Component } from "react";
import { Table, Checkbox, Dropdown, Input } from "semantic-ui-react";

const listTypes = [
  "byte_list",
  "short_list",
  "int_list",
  "long_list",
  "float_list",
  "double_list",
  "char_list",
  "string_list",
  "byteArray_list",
  "ip4_list",
  "ip6_list",
  "ISO8601Date_list"
];

export default class TableRowWithSlider extends React.Component {
  constructor(props) {
    super(props);
    const { initialState } = this.props;
    this.state = {
      fieldCheckbox: initialState
    };
  }

  checkbox = () => {
    this.setState({ fieldCheckbox: !this.state.fieldCheckbox }, () => {
      console.log("checkbox --> ", this.state.fieldCheckbox);
      const { name, dataType, position, box } = this.props;
      box({
        name,
        dataType,
        position,
        isChecked: this.state.fieldCheckbox
      });
    });
  };

  render() {
    const { name, dataType, position, handleChange } = this.props;
    const { fieldCheckbox } = this.state;
    return (
      <React.Fragment>
        <Table.Row>
          <Checkbox
            style={{ marginTop: "20px", marginLeft: "10px" }}
            checkbox
            onChange={this.checkbox}
          />
          <Table.Cell>{name}</Table.Cell>
          <Table.Cell>{dataType}</Table.Cell>
          <Table.Cell>{position}</Table.Cell>
          <Table.Cell style={{ textAlign: "center" }}>
            <div>
              {this.props.dataType &&
              (this.props.dataType === "string" ||
                this.props.dataType === "byteArray") ? (
                <Input
                  onChange={e => handleChange(e, name)}
                  value={this.state.size}
                  type="number"
                  name="size"
                  min="1"
                  placeholder="1"
                  disabled={!this.state.fieldCheckbox}
                />
              ) : null}
            </div>
          </Table.Cell>
          <Table.Cell style={{ textAlign: "center" }}>
            <div>
              {listTypes.includes(this.props.dataType) ? (
                <Input
                  onChange={e => handleChange(e, name)}
                  value={this.state.maxArrayElements}
                  type="number"
                  name="maxArrayElements"
                  placeholder="1"
                  min="1"
                  max="100"
                  disabled={!this.state.fieldCheckbox}
                />
              ) : null}
            </div>
          </Table.Cell>
        </Table.Row>
      </React.Fragment>
    );
  }
}
