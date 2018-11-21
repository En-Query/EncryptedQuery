import React from 'react';
import { BrowserRouter, Route, Switch } from 'react-router-dom';
import HomePage from './HomePage';
import CreateQuerySchema from '../components/CreateQuerySchema';
import CreateQuery from '../components/CreateQuery';
import QueryStatus from '../components/QueryStatus';
import ScheduleQuery from '../components/ScheduleQuery';
import QueryResults from '../components/QueryResults';
import QuerySchedulesStatus from '../components/QuerySchedulesStatus';

export default () =>
	(<BrowserRouter>
		<Switch>

			<Route path="/querier" component={HomePage} exact />
			<Route path="/querier/createqueryschema" component={CreateQuerySchema} exact />
			<Route path="/querier/createquery" component={CreateQuery} exact />
			<Route path="/querier/querystatus" component={QueryStatus} exact />
			<Route path="/querier/schedulequery" component={ScheduleQuery} />
			<Route path="/querier/queryschedulesstatus" component={QuerySchedulesStatus} exact />
			<Route path="/querier/queryresults" component={QueryResults} exact />

		</Switch>
	</BrowserRouter>);
