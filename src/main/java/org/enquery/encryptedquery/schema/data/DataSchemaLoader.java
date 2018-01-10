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
package org.enquery.encryptedquery.schema.data;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.enquery.encryptedquery.schema.data.partitioner.DataPartitioner;
import org.enquery.encryptedquery.schema.data.partitioner.PrimitiveTypePartitioner;
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
 * Class to load any data schemas specified in the properties file, 'data.schemas'
 * <p>
 * Schemas should be specified as follows:
 *
 * <pre>
 * {@code
 * <schema>
 *    <schemaName> name of the schema </schemaName>
 *    <element>
 *        <name> element name; note that element names are case sensitive </name>
 *        <type> class name or type name (if Java primitive type) of the element </type>
 *        <isArray> whether or not the schema element is an array within the data.
 *                  Set to true by including this tag with no text or the string "true" (comparison is case-insensitive).
 *                  Omitting this tag or using any other text indicates this element is not an array.</isArray>
 *        <partitioner> optional - Partitioner class for the element; defaults to primitive java type partitioner </partitioner>
 *    </element>
 * </schema>
 * }
 * </pre>
 * 
 * Primitive types must be one of the following: "byte", "short", "int", "long", "float", "double", "char", "string"
 * <p>
 */
public class DataSchemaLoader
{
  private static final Logger logger = LoggerFactory.getLogger(DataSchemaLoader.class);

  private static Set<String> allowedPrimitiveJavaTypes = new HashSet<>(
      Arrays.asList(PrimitiveTypePartitioner.BYTE, PrimitiveTypePartitioner.SHORT, PrimitiveTypePartitioner.INT, PrimitiveTypePartitioner.LONG,
          PrimitiveTypePartitioner.FLOAT, PrimitiveTypePartitioner.DOUBLE, PrimitiveTypePartitioner.CHAR, PrimitiveTypePartitioner.STRING));

  static
  {
    logger.info("Loading pre-configured data schemas: ");
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
   * Initializes the static {@link DataSchemaRegistry} with a list of available data schema names.
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
   * Initializes the static {@link DataSchemaRegistry} with a list of available data schema names.
   * 
   * @param hdfs
   *          If true, specifies that the data schema is an hdfs file; if false, that it is a regular file.
   * @param fs
   *          Used only when {@code hdfs} is true; the {@link FileSystem} handle for the hdfs in which the data schema exists
   * @throws PIRException
   *           - failed to initialize the data schemas because they could not be read or are invalid.
   */
  public static void initialize(boolean hdfs, FileSystem fs) throws PIRException
  {
    String dataSchemas = SystemConfiguration.getProperty("data.schemas", "none");
    if (dataSchemas.equals("none"))
    {
      return;
    }

    String[] dataSchemaFiles = dataSchemas.split(",");
    try
    {
      for (String schemaFile : dataSchemaFiles)
      {
        DataSchema dataSchema = readSchemaFile(schemaFile, fs, hdfs);
        DataSchemaRegistry.put(dataSchema);
      }
    } catch (IOException e)
    {
      throw new PIRException("Error reading data schema", e);
    }
  }

  private static DataSchema readSchemaFile(String schemaFile, FileSystem fs, boolean hdfs) throws IOException, PIRException
  {
    logger.info("Loading data schemaFile = " + schemaFile + " hdfs = " + hdfs);

    // Parse and load the schema file into a DataSchema object; place in the schemaMap
    DataSchemaLoader loader = new DataSchemaLoader();
    InputStream is;
    if (hdfs)
    {
      logger.info("hdfs: filePath = " + schemaFile);
      is = fs.open(fs.makeQualified(new Path(schemaFile)));
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
  public DataSchemaLoader()
  {}

  /**
   * Returns the data schema as defined in XML format on the given stream.
   * 
   * @param stream
   *          The source of the XML data schema description.
   * @return The data schema.
   * @throws IOException
   *           A problem occurred reading from the given stream.
   * @throws PIRException
   *           The schema description is invalid.
   */
  public DataSchema loadSchema(InputStream stream) throws IOException, PIRException
  {
    // Read the XML schema file.
    Document doc = parseXMLDocument(stream);

    // Extract the schemaName
    NodeList schemaNameList = doc.getElementsByTagName("schemaName");
    if (schemaNameList.getLength() != 1)
    {
      throw new PIRException("schemaNameList.getLength() = " + schemaNameList.getLength() + " -- should be one schema per xml file");
    }
    String schemaName = schemaNameList.item(0).getTextContent().trim();
    logger.info("schemaName = " + schemaName);

    // Create the data schema holder
    DataSchema dataSchema = new DataSchema(schemaName);

    // Extract and populate the elements
    NodeList nList = doc.getElementsByTagName("element");
    for (int i = 0; i < nList.getLength(); i++)
    {
      Node nNode = nList.item(i);
      if (nNode.getNodeType() == Node.ELEMENT_NODE)
      {
        extractElementNode((Element) nNode, dataSchema);
      }
    }

    return dataSchema;
  }

  /**
   * Parses and normalizes the XML document available on the given stream.
   * 
   * @param stream
   *          The input stream.
   * @return A {@link Document} representing the XML document.
   * @throws IOException
   *           - Failed to read schema file
   * @throws PIRException
   *           - Schema description is invalid
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
   * Extracts a data schema element node's contents
   * 
   * @param eElement
   *          A data schema element node.
   * @param schema
   *          The data schema
   * @throws PIRException
   *           - Schema description is invalid
   */
  private void extractElementNode(Element eElement, DataSchema schema) throws PIRException
  {
    // Pull out the element name and type attributes.
    String name = eElement.getElementsByTagName("name").item(0).getTextContent().trim();
    String type = eElement.getElementsByTagName("type").item(0).getTextContent().trim();
    schema.getTypeMap().put(name, type);

    // An empty isArray or one whose value evaluates to "true" is an array; otherwise (including absence) the element is not an array
    Node isArrayNode = eElement.getElementsByTagName("isArray").item(0);
    if (isArrayNode != null)
    {
      String isArrayValue = isArrayNode.getTextContent().trim().toLowerCase();
      String isArray = isArrayValue.isEmpty() ? "true" : isArrayValue;
      if (isArray.equals("true"))
      {
        schema.getArrayElements().add(name);
      }
    }

    // Pull and check the partitioner class -- if the partitioner tag doesn't exist, then
    // it defaults to the PrimitiveTypePartitioner
    String partitionerTypeName = PrimitiveTypePartitioner.class.getName();
    boolean isPrimitivePartitioner = true;
    if (eElement.getElementsByTagName("partitioner").item(0) != null)
    {
      partitionerTypeName = eElement.getElementsByTagName("partitioner").item(0).getTextContent().trim();
      isPrimitivePartitioner = partitionerTypeName.equals(PrimitiveTypePartitioner.class.getName());
    }

    DataPartitioner partitioner;
    if (isPrimitivePartitioner)
    {
      // Validate the primitive partitioner can only be used for primitive element types.
      validateIsPrimitiveType(type);
      partitioner = new PrimitiveTypePartitioner();
    }
    else
    {
      partitioner = instantiatePartitioner(partitionerTypeName);
    }

    // Place in the appropriate structures
    schema.getPartitionerTypeMap().put(name, partitionerTypeName);
    schema.getPartitionerInstances().put(partitionerTypeName, partitioner);

    logger.info("name = " + name + " javaType = " + type + " isArray = " + schema.getArrayElements().contains(name) + " partitioner " + partitionerTypeName);
  }

  /**
   * Checks the given type name is a supported Java primitive type, and throws a PIRException if not.
   *
   * @param typeName
   *          The type name to check.
   * @throws PIRException
   *           -
   */
  private void validateIsPrimitiveType(String typeName) throws PIRException
  {
    if (!allowedPrimitiveJavaTypes.contains(typeName.toLowerCase()))
    {
      throw new PIRException("javaType = " + typeName + " is not one of the allowed javaTypes: " + " byte, short, int, long, float, double, char, string");
    }
  }

  /**
   * Creates a new instance of a class with the given type name.
   * 
   * Throws an exception if the class cannot be instantiated, or it does not implement the required interface.
   *
   * @param partitionerTypeName
   *          The name of the {@link DataPartitioner} subclass to instantiate.
   * @return An instance of the named {@link DataPartitioner} subclass.
   * @throws PIRException
   *           -
   */
  private DataPartitioner instantiatePartitioner(String partitionerTypeName) throws PIRException
  {
    Object obj;
    try
    {
      @SuppressWarnings("unchecked")
      Class<? extends DataPartitioner> c = (Class<? extends DataPartitioner>) Class.forName(partitionerTypeName);
      obj = c.newInstance();
    } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | ClassCastException e)
    {
      throw new PIRException("partitioner = " + partitionerTypeName + " cannot be instantiated or does not implement DataPartitioner.", e);
    }

    return (DataPartitioner) obj;
  }
}
