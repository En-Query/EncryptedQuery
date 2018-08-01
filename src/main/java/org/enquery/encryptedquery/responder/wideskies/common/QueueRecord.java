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
 */
package org.enquery.encryptedquery.responder.wideskies.common;

import java.util.List;

public class QueueRecord {
 
	// Hash of Selector
	private int rowIndex;
	
	//Selector Value
	String selector;
	
	// Record data broken into parts
	List<Byte> parts;
	
	public QueueRecord(int rowIndex, String selector, List<Byte> parts) {
		this.rowIndex = rowIndex;
		this.selector = selector;
		this.parts = parts;
	}

	public int getRowIndex() {
		return rowIndex;
	}

	public String getSelector() {
		return selector;
	}

	public List<Byte> getParts() {
		return parts;
	}
	
	@Override
	public String toString() 
	{
		StringBuilder output = new StringBuilder();
		output.append("\n  Selector: " + selector + "\n");
		output.append("  Hash(rowIndex): " + rowIndex + "\n");
		output.append("  Parts count: " + parts.size() + "\n");
		return output.toString();
	}
	
}
