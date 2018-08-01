/*
 * Copyright 2017 EnQuery.
 * This product includes software licensed to EnQuery under 
 * one or more license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * 
 * This file has been modified from its original source.
 */
package org.enquery.encryptedquery.responder.wideskies.jdbc.dao;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.sql.Types;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

/**
 * Converts database types to Java class types.
 */
public class DataTypeConversion {

    /**
     * Translates a data type from an integer (java.sql.Types value) to a string
     * that represents the corresponding class.
     * 
     * @param type
     *            The java.sql.Types value to convert to its corresponding class.
     * @return The class that corresponds to the given java.sql.Types
     *         value, or Object.class if the type has no known mapping.
     */
    public static Object getObject(int type, int columnIndex, ResultSet rs) {
        Object result = Object.class;
try {
        switch (type) {
            case Types.CHAR:
            case Types.VARCHAR:
            case Types.LONGVARCHAR:
                result = rs.getString(columnIndex);
                break;

            case Types.NUMERIC:
            case Types.DECIMAL:
                result = rs.getBigDecimal(columnIndex);
                break;

            case Types.BIT:
                result = rs.getBoolean(columnIndex);
                break;

            case Types.TINYINT:
                result = rs.getByte(columnIndex);
                break;

            case Types.SMALLINT:
                result = rs.getShort(columnIndex);
                break;

            case Types.INTEGER:
                result = rs.getInt(columnIndex);
                break;

            case Types.BIGINT:
                result = rs.getLong(columnIndex);
                break;

            case Types.REAL:
            case Types.FLOAT:
                result = rs.getFloat(columnIndex);
                break;

            case Types.DOUBLE:
                result = rs.getDouble(columnIndex);
                break;

            case Types.BINARY:
            case Types.VARBINARY:
            case Types.LONGVARBINARY:
                result = rs.getBytes(columnIndex);
                break;

            case Types.DATE:
                result = rs.getDate(columnIndex).toString();
                break;

            case Types.TIME:
                result = rs.getTime(columnIndex).toString();
                break;

            case Types.TIMESTAMP:
            	Timestamp dt = rs.getTimestamp(columnIndex);
            	DateFormat df = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");  
            	String text = df.format(dt);
                result = text; //rs.getTimestamp(columnIndex).toString();
                break;
        }
} catch (Exception ex) {
	ex.printStackTrace();
}
        return result;
    }
}