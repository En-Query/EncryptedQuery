import React, { Component } from "react";
import { Table, Checkbox } from "semantic-ui-react";
import format from "date-fns/format";

export default class TableRowWithSlider extends React.Component {
  constructor(props) {
    super(props);
    const { initialState } = this.props;
    this.state = {
      exportSlider: initialState
    };
  }

  slider = () => {
    this.setState({ exportSlider: !this.state.exportSlider }, () => {
      console.log("slider --> ", this.state.exportSlider);
      const { id, startTime, selfUri, status, slide } = this.props;
      slide({
        id,
        startTime,
        selfUri,
        status,
        isExported: this.state.exportSlider
      });
    });
  };

  render() {
    const { id, startTime, selfUri, status } = this.props;
    const { exportSlider } = this.state;
    return (
      <React.Fragment>
        <Table.Row>
          <Checkbox slider onChange={this.slider} />
          <Table.Cell>{id}</Table.Cell>
          <Table.Cell>{format(startTime, "MMM Do YYYY, h:mm:ss A")}</Table.Cell>
          <Table.Cell>{selfUri}</Table.Cell>
          <Table.Cell>{status}</Table.Cell>
        </Table.Row>
      </React.Fragment>
    );
  }
}
