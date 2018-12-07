/*
 * EncryptedQuery is an open source project allowing user to query databases with queries under
 * homomorphic encryption to securing the query and results set from database owner inspection.
 * Copyright (C) 2018 EnQuery LLC
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Affero General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program.
 * If not, see <https://www.gnu.org/licenses/>.
 */
package org.enquery.encryptedquery.flink.kafka;

import java.sql.ResultSet;
import java.util.Properties;

import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.java.DataSet;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
 import org.apache.flink.types.Row;
import org.enquery.encryptedquery.flink.BaseQueryExecutor;

public class Responder extends BaseQueryExecutor {

	private String brokers;
	private String topic;
	private String groupId;
	private Boolean forceFromStart;
	private String offsetLocation;


	public String getBrokers() {
		return brokers;
	}

	public void setBrokers(String brokers) {
		this.brokers = brokers;
	}

	public String getTopic() {
		return topic;
	}

	public void setTopic(String topic) {
		this.topic = topic;
	}

	public String getGroupId() {
		return groupId;
	}

	public void setGroupId(String groupId) {
		this.groupId = groupId;
	}

	public Boolean getForceFromStart() {
    	return forceFromStart;
	}

	public void setForceFromStart(Boolean forceFromStart) {
		this.forceFromStart = forceFromStart;
	}

	public String getOffsetLocation() {
		return offsetLocation;
	}

	public void setOffsetLocation(String offsetLocation) {
		this.offsetLocation = offsetLocation;
	}


	private FlinkKafkaConsumer<String> createStringConsumerForTopic(
			  String topic, String kafkaAddress, String kafkaGroup ) {
			  
			    Properties props = new Properties();
			    props.setProperty("bootstrap.servers", kafkaAddress);
			    props.setProperty("group.id",kafkaGroup);
			    FlinkKafkaConsumer<String> consumer = new FlinkKafkaConsumer<>(
			      topic, new SimpleStringSchema(), props);
			 
			    return consumer;
			}

    FlinkKafkaConsumer<String> flinkKafkaConsumer = createStringConsumerForTopic(
    	      topic, brokers, groupId);
    
	@Override
	protected DataSet<Row> initSource(ExecutionEnvironment env) {
//		JDBCInputFormat inputBuilder = JDBCInputFormat.buildJDBCInputFormat()
//				.setDrivername(driverClassName)
//				.setDBUrl(connectionUrl)
//				.setQuery(sqlQuery)
//				.setRowTypeInfo(getRowTypeInfo())
//				.setResultSetType(ResultSet.TYPE_SCROLL_INSENSITIVE)
//				.finish();

		return env.createInput(null);
	}
}
