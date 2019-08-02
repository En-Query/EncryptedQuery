import React from "react";
import {
  BrowserRouter,
  Route,
  Switch,
  BrowserHistory,
  HashRouter
} from "react-router-dom";
import HomepageLayout from "../components/HomepageLayout.js";
import CreateQuerySchema from "../components/CreateQuerySchema";
import CreateQuery from "../components/CreateQuery";
import QueryStatus from "../components/QueryStatus";
import ScheduleQuery from "../components/ScheduleQuery";
import QueryResults from "../components/QueryResults";
import QuerySchedulesStatus from "../components/QuerySchedulesStatus";
import PageNotFound from "../components/PageNotFound";
import OfflineMode from "../components/OfflineMode";
import Versions from "../components/Versions";
import Overview from "../components/Overview";

export default () => (
  <HashRouter>
    <Switch>
      <Route path="/" component={HomepageLayout} exact />
      <Route path="/createqueryschema" component={CreateQuerySchema} exact />
      <Route path="/createquery" component={CreateQuery} exact />
      <Route path="/querystatus" component={QueryStatus} exact />
      <Route path="/schedulequery" component={ScheduleQuery} exact />
      <Route
        path="/queryschedulesstatus"
        component={QuerySchedulesStatus}
        exact
      />
      <Route path="/queryresults" component={QueryResults} exact />
      <Route path="/offline" component={OfflineMode} exact />
      <Route path="/versions" component={Versions} exact />
      <Route path="/overview" component={Overview} />
      <Route component={PageNotFound} />
    </Switch>
  </HashRouter>
);
