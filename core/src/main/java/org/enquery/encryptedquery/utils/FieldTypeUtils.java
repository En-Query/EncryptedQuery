package org.enquery.encryptedquery.utils;

public class FieldTypeUtils {

	public static int getSizeByType(String dataType) {
		int bits = 0;
		switch (dataType) {
		case "byte":
			bits = Byte.SIZE;
			break;
		case "short":
			bits = Short.SIZE;
			break;
		case "int":
			bits = Integer.SIZE;
			break;
		case "long":
			bits = Long.SIZE;
			break;
		case "float":
			bits = Float.SIZE;
			break;
		case "double":
			bits = Double.SIZE;
			break;
		case "char":
			bits = Character.SIZE;
			break;
		case "string":
		case "byteArray":
			// String and ByteArray sizes are determined by the query schema element size
			// value
			break;
		case "ip4":
			bits = Integer.SIZE;
			break;
		case "ip6":
			bits = 128;
			break;
		case "ISO8601Date":
			bits = Long.SIZE;
			break;
		default:
			throw new RuntimeException("Data type = " + dataType + " not recognized!");
		}
		return bits / 8;
	}
}
