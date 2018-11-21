import React from 'react';



class RunParameters extends React.Component {
  constructor(props) {
    super(props)
    this.state = {}
    props.values.map((v, i) => {
      this.state[v] = false
    })
  }

  onChange(key, value) {
    this.setState({ [key]: value }, (state) => {
      this.props.onChange(this.state)
    })
  }

  render() {
    return (
      <div className="list-group-item form-group">
        {this.props.values.map((value, i) => (
          <div className="checkbox" key={i}>
            <label>
              <input
                onChange={(e) => this.onChange(value, e.target.checked)}
                type='checkbox'
                value={this.state[value]} />
              {value}
            </label>
          </div>
        ))}
      </div>
    )}
  }

  class Page extends React.Component {

  constructor(props) {
      super(props)
      this.state = {}
  }

  onChange(name, values) {
      this.setState({ [name]: values })
  }

  render() {
    const parameters = ["dataPartitionBitSize", "hashBitSize", "bitSet", "paillierBitSize", "Certainty"]


    return (
        <div className="container">
            <div className="row">
              <form className="form">

                <div className="list-group col-xs-6">
                    <h4>Run Parameters</h4>
                    <RunParameters
                        onChange={(values) => this.onChange('parameters', values)}
                        values={parameters}
                    />
                </div>

                <button
                  className="btn btn-primary"
                  onClick={(e) => {
                    console.log(this.state);
                    e.preventDefault();
                  }}>Save
                </button>
              </form>
          </div>
      </div>
    );
  }
}

export default(RunParameters)
