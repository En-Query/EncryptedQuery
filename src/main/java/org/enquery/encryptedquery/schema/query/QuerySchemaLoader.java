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
package org.enquery.encryptedquery.schema.query;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.enquery.encryptedquery.utils.PIRException;
import org.enquery.encryptedquery.utils.SystemConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Class to load any query schemas specified in the properties file, 'query.schemas'
 * <p>
 * Schemas should be specified as follows:
 *
 * <pre>
 * {@code
 * <schema>
 *    <schemaName> name of the schema </schemaName>
 *    <dataSchemaName> name of the data schema over which this query is run </dataSchemaName>
 *    <selectorName> name of the element in the data schema that will be the selector </selectorName>
 *    <elements>
 *       <name> element name of element in the data schema to include in the query response; just
 *              as with the data schema, the element name is case sensitive</name>
 *    </elements>
 *    <filter> (optional) name of the filter class to use to filter the data </filter>
 *    <filterNames> (optional)
 *       <name> element name of element in the data schema to apply pre-processing filters </name>
 *    </filterNames>
 *    <additional> (optional) additional fields for the query schema, in <key,value> pairs
 *       <field>
 *            <key> key corresponding the the field </key>
 *            <value> value corresponding to the field </value>
 *       </field>  
 *    </additional>
 *   </schema>
 * }
 * </pre>
 * <p>
 * TODO: Allow the schema to specify how many array elements to return per element, if the element is an array type
 */
public class QuerySchemaLoader
{
  private static final Logger logger = LoggerFactory.getLogger(QuerySchemaLoader.class);

  static
  {
    logger.info("Loading query schemas: ");

    try
    {
      initialize();
    } catch (PIRException e)
    {
      logger.error(e.getLocalizedMessage());
    }
  }

  /* Kept for compatibility */
  /**
   * Initializes the static {@link QuerySchemaRegistry} with a list of query schema names.
   * 
   * @throws PIRException
   *           - failed to initialize
   */
  public static void initialize() throws PIRException
  {
    initialize(false, null);
  }

  /* Kept for compatibility */
  /**
   * Initializes the static {@link QuerySchemaRegistry} with a list of available query schema names.
   * 
   * @param hdfs
   *          If true, specifies that the query schema is an hdfs file; if false, that it is a regular file.
   * @param fs
   *          Used only when {@code hdfs} is true; the {@link FileSystem} handle for the hdfs in which the query schema exists
   * @throws PIRException
   *           - failed to initialize the query schemas because they could not be read or are invalid.
   */
  public static void initialize(boolean hdfs, FileSystem fs) throws PIRException
  {
    String querySchemas = SystemConfiguration.getProperty("query.schemas", "none");
    if (querySchemas.equals("none"))
    {
      logger.info("query.schemas = none");
      return;
    }

    String[] querySchemaFiles = querySchemas.split(",");
    try
    {
      for (String schemaFile : querySchemaFiles)
      {
        QuerySchema querySchema = readSchemaFile(schemaFile, fs, hdfs);
        QuerySchemaRegistry.put(querySchema);
      }
    } catch (IOException e)
    {
      throw new PIRException("Error reading query schema", e);
    }
  }

  private static QuerySchema readSchemaFile(String schemaFile, FileSystem fs, boolean hdfs) throws IOException, PIRException
  {
    logger.info("Loading query schemaFile = " + schemaFile);

    // Parse and load the schema file into a QuerySchema object.
    QuerySchemaLoader loader = new QuerySchemaLoader();
    InputStream is;
    if (hdfs)
    {
      logger.info("hdfs: filePath = " + schemaFile);
      is = fs.open(new Path(schemaFile));
    }
    else
    {
      logger.info("localFS: inputFile = " + schemaFile);
      is = new FileInputStream(schemaFile);
    }

    try
    {
      return loader.loadSchema(is);
    } finally
    {
      is.close();
    }
  }

  /**
   * Default constructor.
   */
  public QuerySchemaLoader()
  {

  }

  /**
   * Returns the query schema as defined in XML format on the given stream.
   * 
   * @param stream
   *          The source of the XML query schema description.
   * @return The query schema.
   * @throws IOException
   *           A problem occurred reading from the given stream.
   * @throws PIRException
   *           The schema description is invalid.
   */
  public QuerySchema loadSchema(InputStream stream) throws IOException, PIRException
  {
    // Read in and parse the XML file.
    Document doc = parseXMLDocument(stream);

    // Used to build the final schema.
    QuerySchemaBuilder schemaBuilder = new QuerySchemaBuilder();

    // Extract the schemaName.
    String schemaName = extractValue(doc, "schemaName");
    schemaBuilder.setName(schemaName);
    logger.info("schemaName = " + schemaName);

    // Extract the dataSchemaName.
    String dataSchemaName = extractValue(doc, "dataSchemaName");
    schemaBuilder.setDataSchemaName(dataSchemaName);
    logger.info("dataSchemaName = " + dataSchemaName);

    // Extract the selectorName.
    String selectorName = extractValue(doc, "selectorName");
    schemaBuilder.setSelectorName(selectorName);
    logger.info("selectorName = " + selectorName);

    // Extract the tableName
    String tableName = extractValue(doc, "tableName", "N/A");
    schemaBuilder.setTableName(tableName);
    if (!tableName.equalsIgnoreCase("N/A")) {
    	logger.info("tableName = {}", tableName);
    }
    // Extract the tableName
    String databaseQuery = extractValue(doc, "databaseQuery", "N/A");
    schemaBuilder.setDatabaseQuery(databaseQuery);
    if (!databaseQuery.equalsIgnoreCase("N/A")) {
        logger.info("databaseQuery = {}", databaseQuery);
    }

    // Extract the query elements.
    NodeList elementsList = doc.getElementsByTagName("elements");
    if (elementsList.getLength() != 1)
    {
      throw new PIRException("elementsList.getLength() = " + elementsList.getLength() + " -- should be 1");
    }
    Element elements = (Element) elementsList.item(0);

    LinkedHashSet<String> elementNames = new LinkedHashSet<>();
    NodeList nList = elements.getElementsByTagName("name");
    for (int i = 0; i < nList.getLength(); i++)
    {
      Node nNode = nList.item(i);
      if (nNode.getNodeType() == Node.ELEMENT_NODE)
      {
        elementNames.add(nNode.getFirstChild().getNodeValue().trim());
      }
    }
    schemaBuilder.setQueryElementNames(elementNames);

    // Extract the filter, if it exists
    if (doc.getElementsByTagName("filter").item(0) != null)
    {
      schemaBuilder.setFilterTypeName(doc.getElementsByTagName("filter").item(0).getTextContent().trim());
    }

    // Create a filter over the query elements.
    schemaBuilder.setFilteredElementNames(extractFilteredElementNames(doc));

    // Extract the additional fields, if they exists
    Map<String,String> additionalFields = new HashMap<>();
    if (doc.getElementsByTagName("additional").item(0) != null)
    {
      NodeList fieldList = doc.getElementsByTagName("field");
      int numFields = fieldList.getLength();
      if (numFields == 0)
      {
        throw new PIRException("numFields = " + numFields + " -- should be at least one");
      }
      for (int i = 0; i < numFields; ++i)
      {
        Element fields = (Element) fieldList.item(i);
        NodeList kv = fields.getChildNodes();
        additionalFields.put(getNodeValue("key", kv), getNodeValue("value", kv));
      }
    }
    schemaBuilder.setAdditionalFields(additionalFields);

    // Create and return the query schema object.
    return schemaBuilder.build();
  }

  /**
   * Parses and normalizes the XML document available on the given stream.
   * 
   * @param stream
   *          The input stream.
   * @return A Document representing the XML document.
   * @throws IOException
   *           - failed to read input
   * @throws PIRException
   *           - file could not be parsed
   */
  private Document parseXMLDocument(InputStream stream) throws IOException, PIRException
  {
    Document doc;
    try
    {
      DocumentBuilder dBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
      doc = dBuilder.parse(stream);
    } catch (ParserConfigurationException | SAXException e)
    {
      throw new PIRException("Schema parsing error", e);
    }
    doc.getDocumentElement().normalize();
    logger.info("Root element: " + doc.getDocumentElement().getNodeName());

    return doc;
  }

  /**
   * Returns the possibly empty set of element names over which the filter is applied, maintaining document order.
   *
   * @param doc
   *          An XML document specifying names upon which we will filter the query.
   * @return The set of names upon which we will filter the query.
   * @throws PIRException
   *           - Filter lists not found
   */
  private Set<String> extractFilteredElementNames(Document doc) throws PIRException
  {
    Set<String> filteredNamesSet = new HashSet<>();

    NodeList filterNamesList = doc.getElementsByTagName("filterNames");
    if (filterNamesList.getLength() != 0)
    {
      if (filterNamesList.getLength() > 1)
      {
        throw new PIRException("filterNamesList.getLength() = " + filterNamesList.getLength() + " -- should be 0 or 1");
      }

      // Extract element names from the list.
      NodeList filterNList = ((Element) filterNamesList.item(0)).getElementsByTagName("name");
      for (int i = 0; i < filterNList.getLength(); i++)
      {
        Node nNode = filterNList.item(i);
        if (nNode.getNodeType() == Node.ELEMENT_NODE)
        {
          // Pull the name and add to the set.
          String name = nNode.getFirstChild().getNodeValue().trim();
          filteredNamesSet.add(name);

          logger.info("filterName = " + name);
        }
      }
    }
    return filteredNamesSet;
  }

  /**
   * Extracts a top level, single value from the XML structure.
   * 
   * Throws an exception if there is not exactly one tag with the given name.
   *
   * @param doc
   *          The XML document from which we extract data
   * @param tagName
   *          The name of the tag we wish to extract from the {@code doc}
   * @return The text content of the tag.
   * @throws PIRException
   *           - XML Document is Empty
   */
  private String extractValue(Document doc, String tagName) throws PIRException
  {
    NodeList itemList = doc.getElementsByTagName(tagName);
    if (itemList.getLength() != 1)
    {
      throw new PIRException("itemList.getLength() = " + itemList.getLength() + " -- should be 1");
    }
    return itemList.item(0).getTextContent().trim();
  }
  
  private String extractValue(Document doc, String tagName, String valueIfNull) throws PIRException
  {
	  NodeList itemList = doc.getElementsByTagName(tagName);
	  if (itemList.getLength() != 1 )
	  { 
		  if (valueIfNull == null) {
			  throw new PIRException("No XML tag and Value if Null is null");
		  } else {
			  return valueIfNull;
		  }
	  }
	  return itemList.item(0).getTextContent().trim();
  }

  /**
   * Extracts the value corresponding to a given tag from the XML nodeList
   * 
   * @param tagName
   *          The name of the tag for which to extract the value
   * @param nodes
   *          The NodeList
   * @return The given value
   */
  private String getNodeValue(String tagName, NodeList nodes)
  {
    String value = "";

    for (int x = 0; x < nodes.getLength(); x++)
    {
      Node node = nodes.item(x);
      if (node.getNodeName().equals(tagName))
      {
        value = node.getChildNodes().item(0).getNodeValue().trim();
      }
    }
    return value;
  }
}
